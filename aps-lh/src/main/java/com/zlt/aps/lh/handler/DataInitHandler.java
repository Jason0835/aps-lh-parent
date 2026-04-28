package com.zlt.aps.lh.handler;

import com.zlt.aps.lh.api.constant.LhScheduleConstant;
import com.zlt.aps.lh.api.constant.LhScheduleParamConstant;
import com.zlt.aps.lh.api.domain.dto.MachineCleaningWindowDTO;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.dto.MachineScheduleDTO;
import com.zlt.aps.lh.api.domain.vo.LhShiftConfigVO;
import com.zlt.aps.lh.api.domain.dto.ValidationResult;
import com.zlt.aps.lh.api.domain.entity.LhMachineInfo;
import com.zlt.aps.lh.api.domain.entity.LhMouldCleanPlan;
import com.zlt.aps.lh.api.enums.CleaningTypeEnum;
import com.zlt.aps.lh.api.enums.MachineStopTypeEnum;
import com.zlt.aps.lh.api.enums.ScheduleStepEnum;
import com.zlt.aps.lh.engine.chain.DataValidationChain;
import com.zlt.aps.lh.exception.ScheduleDomainExceptionHelper;
import com.zlt.aps.lh.exception.ScheduleErrorCode;
import com.zlt.aps.lh.service.ILhBaseDataService;
import com.zlt.aps.lh.service.ILhShiftConfigService;
import com.zlt.aps.lh.service.impl.RollingScheduleHandoffService;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import com.zlt.aps.mdm.api.domain.entity.MdmDevicePlanShut;
import com.zlt.aps.lh.api.domain.entity.LhMachineOnlineInfo;
import com.zlt.aps.lh.api.domain.entity.LhRepairCapsule;
import com.zlt.aps.mdm.api.domain.entity.MdmMaterialInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * S4.2 基础数据初始化处理器
 * <p>加载所有基础数据并通过责任链校验，封装成排程可用的对象结构</p>
 *
 * @author APS
 */
@Slf4j
@Component
public class DataInitHandler extends AbsScheduleStepHandler {

    @Resource
    private DataValidationChain dataValidationChain;

    @Resource
    private ILhBaseDataService baseDataService;

    @Resource
    private ILhShiftConfigService lhShiftConfigService;

    @Resource
    private RollingScheduleHandoffService rollingScheduleHandoffService;

    @Override
    protected void doHandle(LhScheduleContext context) {
        // S4.2.1 解析班次配置（无表数据则用默认模板），并写入上下文
        try {
            lhShiftConfigService.resolveAndAttachScheduleShifts(context);
        } catch (IllegalArgumentException e) {
            log.error("班次配置非法: {}", e.getMessage());
            ScheduleDomainExceptionHelper.interrupt(context, ScheduleStepEnum.S4_2_DATA_INIT,
                    ScheduleErrorCode.DATA_INCOMPLETE, "班次配置非法: " + e.getMessage());
            return;
        }

        // S4.2.2 加载所有基础数据
        loadBaseData(context);
        if (context.isInterrupted()) {
            return;
        }

        // S4.2.3 执行数据校验链（组内聚合模式会收集全部错误后再失败）
        ValidationResult result = dataValidationChain.validateWithResult(context);
        if (result.isFailed()) {
            log.warn("数据校验未通过，共 {} 条错误，明细: {}", result.getErrors().size(), result.getFormattedErrors());
            ScheduleDomainExceptionHelper.interrupt(context, ScheduleStepEnum.S4_2_DATA_INIT,
                    ScheduleErrorCode.DATA_INCOMPLETE, result.getSummaryMessage(), result.getErrors());
            return;
        }

        // S4.2.4 封装标准化数据对象（初始化机台排程状态）
        buildStandardDataObjects(context);

        List<LhShiftConfigVO> windowShifts = context.getScheduleWindowShifts();
        LhScheduleTimeUtil.initShiftRuntimeStateMap(context, windowShifts);
        // 强制重排时保留窗口基础数据，跳过前批次继承，从窗口起点重新计算。
        if (context.getScheduleConfig().isForceRescheduleEnabled()) {
            log.info("启用强制重排模式，跳过滚动排程衔接");
        } else {
            // 滚动排程衔接：将前批次重叠班次继承到本次，推进机台状态
            rollingScheduleHandoffService.apply(context);
            if (context.isInterrupted()) {
                return;
            }
        }

        log.info("基础数据初始化完成, 机台数量: {}, 月计划SKU数: {}",
                context.getMachineInfoMap().size(), context.getMonthPlanList().size());
    }

    /**
     * 从数据库加载所有排程所需基础数据
     * <p>包括排产版本、月生产计划、工作日历、SKU日硫化产能、设备停机计划、SKU与模具关系、
     * 硫化机台信息、模具清洗计划、月底计划余量、各班次完成量、物料信息、
     * MES硫化在机信息、硫化定点机台、硫化机胶囊已使用次数、设备保养计划、前日硫化排程结果</p>
     *
     * @param context 排程上下文
     */
    private void loadBaseData(LhScheduleContext context) {
        baseDataService.loadAllBaseData(context);
        log.info("基础数据加载完成");
    }

    /**
     * 将基础数据封装为排程过程中使用的标准化机台状态对象
     * <p>
     * 为每台硫化机台初始化 {@link MachineScheduleDTO}，包含：
     * 机台基本信息、在产规格（从MES在机信息获取）、
     * 设备停机信息、清洗计划、保养/维修计划、胶囊使用次数等
     * </p>
     *
     * @param context 排程上下文
     */
    private void buildStandardDataObjects(LhScheduleContext context) {
        Map<String, MachineScheduleDTO> machineScheduleMap = new LinkedHashMap<>(context.getMachineInfoMap().size());

        for (Map.Entry<String, LhMachineInfo> entry : context.getMachineInfoMap().entrySet()) {
            String machineCode = entry.getKey();
            LhMachineInfo machineInfo = entry.getValue();

            MachineScheduleDTO dto = new MachineScheduleDTO();
            dto.setMachineCode(machineCode);
            dto.setMachineName(machineInfo.getMachineName());
            // 模台数
            dto.setMaxMoldNum(machineInfo.getMaxMoldNum() != null ? machineInfo.getMaxMoldNum() : 1);
            dto.setStatus(machineInfo.getStatus());
            dto.setDimensionMinimum(machineInfo.getDimensionMinimum());
            dto.setDimensionMaximum(machineInfo.getDimensionMaximum());
            dto.setMachineOrder(machineInfo.getMachineOrder() != null ? machineInfo.getMachineOrder() : 0);
            dto.setMouldSetCode(machineInfo.getMouldSetCode());

            // 初始化在产规格（来自MES在机信息）
            dto.setCurrentMaterialCode(null);
            dto.setCurrentMaterialDesc(null);
            dto.setPreviousMaterialCode(null);
            dto.setPreviousMaterialDesc(null);
            if (context.getMachineOnlineInfoMap().containsKey(machineCode)) {
                LhMachineOnlineInfo onlineInfo = context.getMachineOnlineInfoMap().get(machineCode);
                dto.setCurrentMaterialCode(onlineInfo.getMaterialCode());
                dto.setCurrentMaterialDesc(onlineInfo.getSpecDesc());
                MdmMaterialInfo currentMaterial = context.getMaterialInfoMap().get(onlineInfo.getMaterialCode());
                if (currentMaterial != null) {
                    dto.setCurrentMaterialDesc(currentMaterial.getMaterialDesc());
                    dto.setPreviousSpecCode(currentMaterial.getSpecifications());
                    dto.setPreviousProSize(currentMaterial.getProSize());
                }
            }

            // 初始化设备停机与维修信息（取 beginDate 最早的为准）
            for (MdmDevicePlanShut planShut : context.getDevicePlanShutList()) {
                if (machineCode.equals(planShut.getMachineCode())) {
                    if (dto.getPlanStopStartTime() == null
                            || (planShut.getBeginDate() != null && planShut.getBeginDate().before(dto.getPlanStopStartTime()))) {
                        dto.setPlanStopStartTime(planShut.getBeginDate());
                        dto.setPlanStopEndTime(planShut.getEndDate());
                        dto.setStopType(planShut.getMachineStopType());
                    }
                    MachineStopTypeEnum stopTypeEnum = MachineStopTypeEnum.getByCode(planShut.getMachineStopType());
                    // 计划性维修仅保留停机窗口，避免在初始化阶段直接抬高机台准备就绪时间。
                    if (stopTypeEnum == MachineStopTypeEnum.TEMPORARY_FAULT) {
                        dto.setHasRepairPlan(true);
                        dto.setRepairPlanTime(earlier(dto.getRepairPlanTime(), planShut.getBeginDate()));
                    }
                }
            }

            // 初始化保养计划
            if (context.getMaintenancePlanMap().containsKey(machineCode)) {
                Date maintenanceTime = LhScheduleTimeUtil.parseFlexibleDateTime(
                        context.getMaintenancePlanMap().get(machineCode).getOperTime());
                if (maintenanceTime != null) {
                    dto.setHasMaintenancePlan(true);
                    dto.setMaintenancePlanTime(maintenanceTime);
                }
            }

            // 初始化清洗计划
            attachCleaningPlanInfo(context, machineCode, dto);

            // 初始化胶囊使用次数
            if (context.getCapsuleUsageMap().containsKey(machineCode)) {
                LhRepairCapsule capsule = context.getCapsuleUsageMap().get(machineCode);
                dto.setCapsuleUsageCount(capsule.getReplaceCapsuleCount() != null ? capsule.getReplaceCapsuleCount() : 0);
                dto.setCapsuleUsageCount2(capsule.getReplaceCapsuleCount2() != null ? capsule.getReplaceCapsuleCount2() : 0);
            }

            // 初始化各班次可用状态（默认全部可用）
            Arrays.fill(dto.getShiftAvailable(), true);
            dto.setEstimatedEndTime(resolveInitialEstimatedEndTime(context, machineCode));

            machineScheduleMap.put(machineCode, dto);
        }

        context.setMachineScheduleMap(machineScheduleMap);
        context.setInitialMachineScheduleMap(copyMachineStateMap(machineScheduleMap));
        log.info("机台排程状态对象封装完成, 机台数量: {}", machineScheduleMap.size());
    }

    private Date resolveInitialEstimatedEndTime(LhScheduleContext context, String machineCode) {
        Date latestSpecEndTime = null;
        for (com.zlt.aps.lh.api.domain.entity.LhScheduleResult result : context.getPreviousScheduleResultList()) {
            if (!machineCode.equals(result.getLhMachineCode()) || result.getSpecEndTime() == null) {
                continue;
            }
            if (latestSpecEndTime == null || result.getSpecEndTime().after(latestSpecEndTime)) {
                latestSpecEndTime = result.getSpecEndTime();
            }
        }
        if (latestSpecEndTime != null) {
            return latestSpecEndTime;
        }
        if (context.getMachineOnlineInfoMap().containsKey(machineCode)) {
            List<LhShiftConfigVO> shifts = context.getScheduleWindowShifts();
            if (!shifts.isEmpty() && shifts.get(0).getShiftStartDateTime() != null) {
                return shifts.get(0).getShiftStartDateTime();
            }
        }
        List<LhShiftConfigVO> shifts = context.getScheduleWindowShifts();
        if (!shifts.isEmpty() && shifts.get(0).getShiftStartDateTime() != null) {
            return shifts.get(0).getShiftStartDateTime();
        }
        return context.getScheduleDate();
    }

    private Map<String, MachineScheduleDTO> copyMachineStateMap(Map<String, MachineScheduleDTO> sourceMap) {
        Map<String, MachineScheduleDTO> snapshot = new LinkedHashMap<>(sourceMap.size());
        for (Map.Entry<String, MachineScheduleDTO> entry : sourceMap.entrySet()) {
            MachineScheduleDTO source = entry.getValue();
            MachineScheduleDTO copy = new MachineScheduleDTO();
            copy.setMachineCode(source.getMachineCode());
            copy.setMachineName(source.getMachineName());
            copy.setCurrentMaterialCode(source.getCurrentMaterialCode());
            copy.setCurrentMaterialDesc(source.getCurrentMaterialDesc());
            copy.setPreviousMaterialCode(source.getPreviousMaterialCode());
            copy.setPreviousMaterialDesc(source.getPreviousMaterialDesc());
            copy.setPreviousSpecCode(source.getPreviousSpecCode());
            copy.setPreviousProSize(source.getPreviousProSize());
            copy.setEstimatedEndTime(source.getEstimatedEndTime());
            snapshot.put(entry.getKey(), copy);
        }
        return snapshot;
    }

    private Date earlier(Date current, Date candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null || candidate.before(current)) {
            return candidate;
        }
        return current;
    }

    private Date later(Date current, Date candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null || candidate.after(current)) {
            return candidate;
        }
        return current;
    }

    /**
     * 挂载机台清洗计划明细，并回填兼容摘要字段。
     *
     * @param context 排程上下文
     * @param machineCode 机台编号
     * @param dto 机台状态对象
     */
    private void attachCleaningPlanInfo(LhScheduleContext context, String machineCode, MachineScheduleDTO dto) {
        List<MachineCleaningWindowDTO> cleaningWindowList = new ArrayList<>();
        for (LhMouldCleanPlan cleaningPlan : context.getCleaningPlanList()) {
            if (!machineCode.equals(cleaningPlan.getLhCode())) {
                continue;
            }
            MachineCleaningWindowDTO cleaningWindow = buildCleaningWindow(context, cleaningPlan);
            if (cleaningWindow == null) {
                continue;
            }
            cleaningWindowList.add(cleaningWindow);
            if (CleaningTypeEnum.DRY_ICE.getCode().equals(cleaningWindow.getCleanType())) {
                dto.setHasDryIceCleaning(true);
            }
            if (CleaningTypeEnum.SAND_BLAST.getCode().equals(cleaningWindow.getCleanType())) {
                dto.setHasSandBlastCleaning(true);
            }
            dto.setCleaningPlanTime(earlier(dto.getCleaningPlanTime(), cleaningWindow.getCleanStartTime()));
        }
        cleaningWindowList.sort(Comparator.comparing(MachineCleaningWindowDTO::getCleanStartTime,
                Comparator.nullsLast(Date::compareTo)));
        dto.setCleaningWindowList(cleaningWindowList);
    }

    /**
     * 构建机台清洗时间窗口。
     *
     * @param context 排程上下文
     * @param cleaningPlan 模具清洗计划
     * @return 清洗时间窗口
     */
    private MachineCleaningWindowDTO buildCleaningWindow(LhScheduleContext context, LhMouldCleanPlan cleaningPlan) {
        if (cleaningPlan == null || cleaningPlan.getCleanTime() == null) {
            return null;
        }
        String cleanType = cleaningPlan.getCleanType();
        int cleanDurationHours = 0;
        int readyDurationHours = 0;
        if (CleaningTypeEnum.DRY_ICE.getCode().equals(cleanType)) {
            cleanDurationHours = context.getParamIntValue(LhScheduleParamConstant.DRY_ICE_DURATION_HOURS,
                    LhScheduleConstant.DRY_ICE_DURATION_HOURS);
            readyDurationHours = cleanDurationHours;
        } else if (CleaningTypeEnum.SAND_BLAST.getCode().equals(cleanType)) {
            cleanDurationHours = context.getParamIntValue(LhScheduleParamConstant.SAND_BLAST_WITH_INSPECTION_HOURS,
                    LhScheduleConstant.SAND_BLAST_WITH_INSPECTION_HOURS);
            // 喷砂总停机口径用于班次扣减；机台再次可开产时间仍沿用喷砂清洗原时长，
            // 后续换模/换活字块时间继续由各自排产链路单独叠加，避免重复计时。
            readyDurationHours = context.getParamIntValue(LhScheduleParamConstant.SAND_BLAST_DURATION_HOURS,
                    LhScheduleConstant.SAND_BLAST_DURATION_HOURS);
        } else {
            return null;
        }

        // 清洗计划与设备计划停机重叠时，清洗必须顺延到停机结束后执行。
        Date adjustedCleanStartTime = resolveAdjustedCleaningStartTime(
                context, cleaningPlan.getLhCode(), cleaningPlan.getCleanTime(), cleanDurationHours);

        MachineCleaningWindowDTO cleaningWindow = new MachineCleaningWindowDTO();
        cleaningWindow.setCleanType(cleanType);
        cleaningWindow.setLeftRightMould(cleaningPlan.getLeftRightMould());
        cleaningWindow.setCleanStartTime(adjustedCleanStartTime);
        cleaningWindow.setCleanEndTime(LhScheduleTimeUtil.addHours(adjustedCleanStartTime, cleanDurationHours));
        cleaningWindow.setReadyTime(LhScheduleTimeUtil.addHours(adjustedCleanStartTime, readyDurationHours));
        return cleaningWindow;
    }

    /**
     * 解析清洗窗口的有效开始时刻。
     * <p>若清洗窗口与机台计划停机窗口重叠，则将清洗开始顺延到命中停机窗口的结束时刻。</p>
     *
     * @param context 排程上下文
     * @param machineCode 机台编码
     * @param originalStartTime 原始清洗开始时刻
     * @param cleanDurationHours 清洗时长（小时）
     * @return 顺延后的清洗开始时刻
     */
    private Date resolveAdjustedCleaningStartTime(LhScheduleContext context,
                                                  String machineCode,
                                                  Date originalStartTime,
                                                  int cleanDurationHours) {
        if (context == null
                || originalStartTime == null
                || cleanDurationHours <= 0
                || StringUtils.isEmpty(machineCode)
                || context.getDevicePlanShutList() == null
                || context.getDevicePlanShutList().isEmpty()) {
            return originalStartTime;
        }
        Date adjustedStartTime = originalStartTime;
        while (true) {
            Date adjustedEndTime = LhScheduleTimeUtil.addHours(adjustedStartTime, cleanDurationHours);
            Date latestOverlapStopEndTime = null;
            for (MdmDevicePlanShut planShut : context.getDevicePlanShutList()) {
                if (planShut == null
                        || !StringUtils.equals(machineCode, planShut.getMachineCode())
                        || planShut.getBeginDate() == null
                        || planShut.getEndDate() == null
                        || !planShut.getBeginDate().before(planShut.getEndDate())) {
                    continue;
                }
                // 命中重叠：停机开始早于清洗结束，且停机结束晚于清洗开始。
                if (planShut.getBeginDate().before(adjustedEndTime)
                        && planShut.getEndDate().after(adjustedStartTime)) {
                    latestOverlapStopEndTime = later(latestOverlapStopEndTime, planShut.getEndDate());
                }
            }
            if (latestOverlapStopEndTime == null || !latestOverlapStopEndTime.after(adjustedStartTime)) {
                return adjustedStartTime;
            }
            // 顺延到重叠停机窗口结束后，继续判断是否命中后续停机窗口。
            adjustedStartTime = latestOverlapStopEndTime;
        }
    }

    @Override
    protected String getStepName() {
        return ScheduleStepEnum.S4_2_DATA_INIT.getDescription();
    }
}
