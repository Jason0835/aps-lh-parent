package com.zlt.aps.lh.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.dto.MachineScheduleDTO;
import com.zlt.aps.lh.api.domain.dto.ValidationResult;
import com.zlt.aps.lh.api.domain.entity.LhMachineInfo;
import com.zlt.aps.lh.api.domain.entity.LhParams;
import com.zlt.aps.lh.api.enums.DeleteFlagEnum;
import com.zlt.aps.lh.api.enums.ScheduleStepEnum;
import com.zlt.aps.lh.engine.chain.DataValidationChain;
import com.zlt.aps.lh.mapper.LhParamsMapper;
import com.zlt.aps.lh.service.ILhBaseDataService;
import com.zlt.aps.mdm.api.domain.entity.MdmDevicePlanShut;
import com.zlt.aps.mdm.api.domain.entity.MdmLhMachineOnlineInfo;
import com.zlt.aps.mdm.api.domain.entity.MdmLhRepairCapsule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;
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
    private LhParamsMapper lhParamsMapper;

    @Override
    protected void doHandle(LhScheduleContext context) {
        // S4.2.1 先加载硫化参数
        loadLhParams(context);

        // S4.2.2 加载所有基础数据
        loadBaseData(context);
        if (context.isInterrupted()) {
            return;
        }

        // S4.2.3 执行数据校验链（组内聚合模式会收集全部错误后再失败）
        ValidationResult result = dataValidationChain.validateWithResult(context);
        if (result.isFailed()) {
            log.warn("数据校验未通过，共 {} 条错误，明细: {}", result.getErrors().size(), result.getFormattedErrors());
            context.interruptSchedule(result.getSummaryMessage());
            return;
        }

        // S4.2.4 封装标准化数据对象（初始化机台排程状态）
        buildStandardDataObjects(context);

        log.info("基础数据初始化完成, 机台数量: {}, 月计划SKU数: {}",
                context.getMachineInfoMap().size(), context.getMonthPlanList().size());
    }

    /**
     * 按分厂加载硫化参数到上下文
     */
    private void loadLhParams(LhScheduleContext context) {
        List<LhParams> paramsList = lhParamsMapper.selectList(
                new LambdaQueryWrapper<LhParams>()
                        .eq(LhParams::getFactoryCode, context.getFactoryCode())
                        .eq(LhParams::getIsDelete, DeleteFlagEnum.NORMAL.getCode()));
        if (paramsList != null) {
            for (LhParams param : paramsList) {
                if (param.getParamCode() != null && param.getParamValue() != null) {
                    context.getLhParamsMap().put(param.getParamCode(), param.getParamValue());
                }
            }
        }
        log.info("硫化参数加载完成, 参数数量: {}", context.getLhParamsMap().size());
    }

    /**
     * 从数据库加载所有排程所需基础数据
     * <p>包括排产版本、月生产计划、工作日历、SKU日硫化产能、设备停机计划、SKU与模具关系、
     * 硫化机台信息、模具清洗计划、月底计划余量、各班次完成量、物料信息、
     * MES硫化在机信息、硫化定点机台、硫化机胶囊已使用次数、设备保养计划</p>
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
            if (context.getMachineOnlineInfoMap().containsKey(machineCode)) {
                MdmLhMachineOnlineInfo onlineInfo = context.getMachineOnlineInfoMap().get(machineCode);
                dto.setCurrentMaterialCode(onlineInfo.getMaterialCode());
            }

            // 初始化设备停机信息（取最近一条计划停机，beginDate最早的为准）
            for (MdmDevicePlanShut planShut : context.getDevicePlanShutList()) {
                if (machineCode.equals(planShut.getMachineCode())) {
                    dto.setPlanStopStartTime(planShut.getBeginDate());
                    dto.setPlanStopEndTime(planShut.getEndDate());
                    dto.setStopType(planShut.getMachineStopType());
                    break;
                }
            }

            // 初始化保养计划
            if (context.getMaintenancePlanMap().containsKey(machineCode)) {
                dto.setHasMaintenancePlan(true);
            }

            // 初始化胶囊使用次数
            if (context.getCapsuleUsageMap().containsKey(machineCode)) {
                MdmLhRepairCapsule capsule = context.getCapsuleUsageMap().get(machineCode);
                dto.setCapsuleUsageCount(capsule.getReplaceCapsuleCount() != null ? capsule.getReplaceCapsuleCount() : 0);
                dto.setCapsuleUsageCount2(capsule.getReplaceCapsuleCount2() != null ? capsule.getReplaceCapsuleCount2() : 0);
            }

            // 初始化各班次可用状态（默认全部可用）
            Arrays.fill(dto.getShiftAvailable(), true);

            machineScheduleMap.put(machineCode, dto);
        }

        context.setMachineScheduleMap(machineScheduleMap);
        log.info("机台排程状态对象封装完成, 机台数量: {}", machineScheduleMap.size());
    }

    @Override
    protected String getStepName() {
        return ScheduleStepEnum.S4_2_DATA_INIT.getDescription();
    }
}
