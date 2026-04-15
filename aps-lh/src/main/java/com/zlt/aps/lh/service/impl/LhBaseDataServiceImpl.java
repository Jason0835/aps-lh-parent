package com.zlt.aps.lh.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zlt.aps.lh.api.constant.LhScheduleConstant;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.entity.LhCleaningPlan;
import com.zlt.aps.lh.api.domain.entity.LhMachineInfo;
import com.zlt.aps.lh.api.domain.entity.LhSpecifyMachine;
import com.zlt.aps.lh.api.enums.DeleteFlagEnum;
import com.zlt.aps.lh.api.enums.ScheduleStepEnum;
import com.zlt.aps.lh.mapper.FactoryMonthPlanProductionFinalResultMapper;
import com.zlt.aps.lh.mapper.MpFactoryProductionVersionMapper;
import com.zlt.aps.lh.mapper.LhCleaningPlanMapper;
import com.zlt.aps.lh.mapper.LhMachineInfoMapper;
import com.zlt.aps.lh.mapper.LhShiftFinishQtyMapper;
import com.zlt.aps.lh.mapper.LhScheduleResultMapper;
import com.zlt.aps.lh.mapper.LhSpecifyMachineMapper;
import com.zlt.aps.lh.mapper.MdmDevMaintenancePlanMapper;
import com.zlt.aps.lh.mapper.MdmDevicePlanShutMapper;
import com.zlt.aps.lh.mapper.LhMachineOnlineInfoMapper;
import com.zlt.aps.lh.mapper.LhRepairCapsuleMapper;
import com.zlt.aps.lh.mapper.MdmMaterialInfoMapper;
import com.zlt.aps.lh.mapper.MdmMonthSurplusMapper;
import com.zlt.aps.lh.mapper.MdmSkuLhCapacityMapper;
import com.zlt.aps.lh.mapper.MdmSkuMouldRelMapper;
import com.zlt.aps.lh.mapper.MdmWorkCalendarMapper;
import com.zlt.aps.lh.exception.ScheduleDomainExceptionHelper;
import com.zlt.aps.lh.exception.ScheduleErrorCode;
import com.zlt.aps.lh.service.ILhBaseDataService;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import com.zlt.aps.mdm.api.domain.entity.MdmDevMaintenancePlan;
import com.zlt.aps.mdm.api.domain.entity.MdmDevicePlanShut;
import com.zlt.aps.lh.api.domain.entity.LhMachineOnlineInfo;
import com.zlt.aps.lh.api.domain.entity.LhRepairCapsule;
import com.zlt.aps.mdm.api.domain.entity.MdmMaterialInfo;
import com.zlt.aps.mdm.api.domain.entity.MdmMonthSurplus;
import com.zlt.aps.mdm.api.domain.entity.MdmSkuLhCapacity;
import com.zlt.aps.mdm.api.domain.entity.MdmSkuMouldRel;
import com.zlt.aps.mdm.api.domain.entity.MdmWorkCalendar;
import com.zlt.aps.mp.api.domain.entity.FactoryMonthPlanProductionFinalResult;
import com.zlt.aps.mp.api.domain.entity.MpFactoryProductionVersion;
import com.zlt.aps.lh.api.domain.entity.LhScheduleResult;
import com.zlt.aps.lh.api.domain.entity.LhShiftFinishQty;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 硫化排程基础数据服务实现
 * <p>负责加载排程所需的所有基础数据到上下文</p>
 *
 * @author APS
 */
@Slf4j
@Service
public class LhBaseDataServiceImpl implements ILhBaseDataService {

    /** 机台启用状态，对应字典 sys_enable_disable */
    private static final String MACHINE_STATUS_ENABLED = "1";

    /** 排产版本已定稿（与 MpFactoryProductionVersion.isFinal 一致） */
    private static final String PRODUCTION_VERSION_IS_FINAL = "1";

    /** 查询最新排产版本时返回前两条，用于判断是否存在多条数据 */
    private static final String FINAL_PRODUCTION_VERSION_LIMIT_TWO = "LIMIT 2";

    @Resource
    private FactoryMonthPlanProductionFinalResultMapper monthPlanMapper;

    @Resource
    private MpFactoryProductionVersionMapper mpFactoryProductionVersionMapper;

    @Resource
    private MdmWorkCalendarMapper workCalendarMapper;

    @Resource
    private MdmSkuLhCapacityMapper skuLhCapacityMapper;

    @Resource
    private MdmDevicePlanShutMapper devicePlanShutMapper;

    @Resource
    private MdmSkuMouldRelMapper skuMouldRelMapper;

    @Resource
    private LhMachineInfoMapper lhMachineInfoMapper;

    @Resource
    private LhCleaningPlanMapper lhCleaningPlanMapper;

    @Resource
    private MdmMonthSurplusMapper monthSurplusMapper;

    @Resource
    private LhShiftFinishQtyMapper lhShiftFinishQtyMapper;

    @Resource
    private MdmMaterialInfoMapper mdmMaterialInfoMapper;

    @Resource
    private LhMachineOnlineInfoMapper lhMachineOnlineInfoMapper;

    @Resource
    private LhSpecifyMachineMapper lhSpecifyMachineMapper;

    @Resource
    private LhRepairCapsuleMapper lhRepairCapsuleMapper;

    @Resource
    private MdmDevMaintenancePlanMapper devMaintenancePlanMapper;

    @Resource
    private LhScheduleResultMapper lhScheduleResultMapper;

    @Override
    public void loadAllBaseData(LhScheduleContext context) {
        String factoryCode = context.getFactoryCode();
        Date scheduleDate = context.getScheduleDate();
        Date targetDate = context.getScheduleTargetDate();

        // 加载排程时间范围：[startDate, endDate) 覆盖 T～T+(SCHEDULE_DAYS-1)，与连续排程窗口日历日一致
        Date startDate = LhScheduleTimeUtil.clearTime(scheduleDate);
        int scheduleDays = LhScheduleTimeUtil.getScheduleDays(context);
        Date endDate = LhScheduleTimeUtil.addDays(startDate, scheduleDays);

        // 获取年月信息（按排程目标日取月计划所属年月）
        Calendar cal = Calendar.getInstance();
        cal.setTime(targetDate);
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        int yearMonth = year * 100 + month;

        // 1. 加载定稿排产版本
        loadFinalProductionVersion(context, factoryCode, year, month);
        if (context.isInterrupted()) {
            return;
        }

        // 2. 加载月生产计划
        loadMonthPlan(context, factoryCode, yearMonth);

        // 3. 加载工作日历
        loadWorkCalendar(context, factoryCode, startDate, endDate);

        // 4. 加载SKU日硫化产能
        loadSkuLhCapacity(context, factoryCode);

        // 5. 加载设备停机计划
        loadDevicePlanShut(context, factoryCode, startDate, endDate);

        // 6. 加载SKU与模具关系
        loadSkuMouldRel(context, factoryCode);

        // 7. 加载硫化机台信息
        loadMachineInfo(context, factoryCode);

        // 8. 加载模具清洗计划
        loadCleaningPlan(context, factoryCode, startDate, endDate);

        // 9. 加载月底计划余量
        loadMonthSurplus(context, factoryCode, year, month);

        // 10. 加载各班次完成量
        loadShiftFinishQty(context, factoryCode, scheduleDate);

        // 11. 加载物料信息
        loadMaterialInfo(context, factoryCode);

        // 12. 加载MES硫化在机信息（取T-1日在机信息）
        Date previousDay = LhScheduleTimeUtil.addDays(startDate, -1);
        loadMachineOnlineInfo(context, factoryCode, previousDay);

        // 13. 加载硫化定点机台
        loadSpecifyMachine(context, factoryCode);

        // 14. 加载硫化机胶囊已使用次数
        loadCapsuleUsage(context, factoryCode);

        // 15. 加载设备保养计划
        loadMaintenancePlan(context, factoryCode);

        // 16. 加载前日硫化排程结果
        loadPreviousScheduleResults(context, factoryCode, targetDate);

        log.info("基础数据加载完成, 工厂: {}, 目标日: {}, T日: {}",
                factoryCode, LhScheduleTimeUtil.formatDate(targetDate), LhScheduleTimeUtil.formatDate(scheduleDate));
    }

    /**
     * 加载前日硫化排程结果
     *
     * @param context     排程上下文
     * @param factoryCode 分厂编号
     * @param targetDate  排程目标日
     * @return
     */
    private void loadPreviousScheduleResults(LhScheduleContext context, String factoryCode, Date targetDate) {
        Date previousDate = LhScheduleTimeUtil.addDays(targetDate, -1);
        List<LhScheduleResult> list = lhScheduleResultMapper.selectList(new LambdaQueryWrapper<LhScheduleResult>()
                .eq(LhScheduleResult::getFactoryCode, factoryCode)
                .eq(LhScheduleResult::getScheduleDate, previousDate)
                .eq(LhScheduleResult::getIsDelete, DeleteFlagEnum.NORMAL.getCode()));
        if (list == null || list.isEmpty()) {
            log.info("未找到前日排程数据, 日期: {}", LhScheduleTimeUtil.formatDate(previousDate));
            context.setPreviousScheduleResultList(new ArrayList<>());
            return;
        }
        context.setPreviousScheduleResultList(list);
        log.info("前日排程基础数据加载完成, 数量: {}, 日期: {}", list.size(), LhScheduleTimeUtil.formatDate(previousDate));
    }

    /**
     * 加载定稿排产版本：工厂 + 年 + 月 + 已定稿且未删除；无数据则中断；多条时取更新时间最新一条（再按主键降序）
     *
     * @param context     排程上下文
     * @param factoryCode 分厂编号
     * @param year        年份
     * @param month       月份（1-12）
     */
    private void loadFinalProductionVersion(LhScheduleContext context, String factoryCode, int year, int month) {
        // 仅查询前两条：第一条用于取值，第二条用于判断是否存在多条记录
        List<MpFactoryProductionVersion> list = mpFactoryProductionVersionMapper.selectList(
                wrapFinalProductionVersion(factoryCode, year, month)
                        .orderByDesc(MpFactoryProductionVersion::getUpdateTime)
                        .orderByDesc(MpFactoryProductionVersion::getId)
                        .last(FINAL_PRODUCTION_VERSION_LIMIT_TWO));
        String locationText = formatFactoryYearMonth(context.getFactoryDisplayName(), year, month);
        if (CollectionUtils.isEmpty(list)) {
            log.error("定稿排产版本无数据, 工厂: {}, 年: {}, 月: {}", factoryCode, year, month);
            interruptByDataIncomplete(context, String.format("%s 未找到定稿排产版本数据", locationText));
            return;
        }
        if (list.size() > 1) {
            log.warn("定稿排产版本存在多条，已按更新时间最新取值, 工厂: {}, 年: {}, 月: {}",
                    factoryCode, year, month);
        }
        MpFactoryProductionVersion row = list.get(0);
        String pv = row.getProductionVersion();
        if (StringUtils.isEmpty(pv)) {
            log.error("定稿排产版本号为空, 工厂: {}, 年: {}, 月: {}, id: {}",
                    factoryCode, year, month, row.getId());
            interruptByDataIncomplete(context, String.format("%s 的定稿排产版本号为空", locationText));
            return;
        }
        context.setProductionVersion(pv);
        log.debug("定稿排产版本加载完成, productionVersion: {}", pv);
    }

    /**
     * 中断排程并返回基础数据不完整错误
     *
     * @param context 排程上下文
     * @param message 异常消息
     * @return
     */
    private void interruptByDataIncomplete(LhScheduleContext context, String message) {
        ScheduleDomainExceptionHelper.interrupt(
                context,
                ScheduleStepEnum.S4_2_DATA_INIT,
                ScheduleErrorCode.DATA_INCOMPLETE,
                message
        );
    }

    /**
     * 统一格式化异常提示中的工厂与年月信息
     *
     * @param factoryName 分厂名称
     * @param year        年份
     * @param month       月份（1-12）
     * @return
     */
    private String formatFactoryYearMonth(String factoryName, int year, int month) {
        String yearMonthText = String.format("%04d-%02d", year, month);
        return String.format("工厂【%s】 计划月份【%s】", factoryName, yearMonthText);
    }

    /** 定稿排产版本：工厂 + 年月 + 已定稿 + 未删除 */
    private LambdaQueryWrapper<MpFactoryProductionVersion> wrapFinalProductionVersion(
            String factoryCode, int year, int month) {
        return new LambdaQueryWrapper<MpFactoryProductionVersion>()
                .eq(MpFactoryProductionVersion::getFactoryCode, factoryCode)
                .eq(MpFactoryProductionVersion::getYear, year)
                .eq(MpFactoryProductionVersion::getMonth, month)
                .eq(MpFactoryProductionVersion::getIsFinal, PRODUCTION_VERSION_IS_FINAL)
                .eq(MpFactoryProductionVersion::getIsDelete, DeleteFlagEnum.NORMAL.getCode());
    }

    /**
     * 加载月生产计划
     *
     * @param context     排程上下文
     * @param factoryCode 分厂编号
     * @param yearMonth   年月（如202603）
     */
    private void loadMonthPlan(LhScheduleContext context, String factoryCode, int yearMonth) {
        LambdaQueryWrapper<FactoryMonthPlanProductionFinalResult> wrapper = new LambdaQueryWrapper<FactoryMonthPlanProductionFinalResult>()
                .eq(FactoryMonthPlanProductionFinalResult::getFactoryCode, factoryCode)
                .eq(FactoryMonthPlanProductionFinalResult::getYearMonth, yearMonth)
                .eq(FactoryMonthPlanProductionFinalResult::getIsDelete, DeleteFlagEnum.NORMAL.getCode());
        String productionVersion = context.getProductionVersion();
        if (StringUtils.isNotEmpty(productionVersion)) {
            wrapper.eq(FactoryMonthPlanProductionFinalResult::getProductionVersion, productionVersion);
        }
        List<FactoryMonthPlanProductionFinalResult> monthPlanList = monthPlanMapper.selectList(wrapper);
        context.setMonthPlanList(monthPlanList != null ? monthPlanList : context.getMonthPlanList());
        log.debug("月生产计划加载完成, 数量: {}", context.getMonthPlanList().size());
    }

    /**
     * 加载工作日历
     *
     * @param context     排程上下文
     * @param factoryCode 分厂编号
     * @param startDate   开始日期
     * @param endDate     结束日期
     */
    private void loadWorkCalendar(LhScheduleContext context, String factoryCode, Date startDate, Date endDate) {
        List<MdmWorkCalendar> workCalendarList = workCalendarMapper.selectList(
                new LambdaQueryWrapper<MdmWorkCalendar>()
                        .eq(MdmWorkCalendar::getFactoryCode, factoryCode)
                        .eq(MdmWorkCalendar::getProcCode, LhScheduleConstant.PROC_CODE_LH)
                        .ge(MdmWorkCalendar::getProductionDate, startDate)
                        .lt(MdmWorkCalendar::getProductionDate, endDate)
                        .eq(MdmWorkCalendar::getIsDelete, DeleteFlagEnum.NORMAL.getCode()));
        context.setWorkCalendarList(workCalendarList != null ? workCalendarList : context.getWorkCalendarList());
        log.debug("工作日历加载完成, 数量: {}", context.getWorkCalendarList().size());
    }

    /**
     * 加载SKU日硫化产能，按物料编码建立Map
     *
     * @param context     排程上下文
     * @param factoryCode 分厂编号
     */
    private void loadSkuLhCapacity(LhScheduleContext context, String factoryCode) {
        List<MdmSkuLhCapacity> skuCapacityList = skuLhCapacityMapper.selectList(
                new LambdaQueryWrapper<MdmSkuLhCapacity>()
                        .eq(MdmSkuLhCapacity::getFactoryCode, factoryCode)
                        .eq(MdmSkuLhCapacity::getIsDelete, DeleteFlagEnum.NORMAL.getCode()));
        Map<String, MdmSkuLhCapacity> skuLhCapacityMap = new HashMap<>(64);
        if (skuCapacityList != null) {
            for (MdmSkuLhCapacity capacity : skuCapacityList) {
                if (capacity.getMaterialCode() != null) {
                    skuLhCapacityMap.put(capacity.getMaterialCode(), capacity);
                }
            }
        }
        context.setSkuLhCapacityMap(skuLhCapacityMap);
        log.debug("SKU日硫化产能加载完成, 数量: {}", skuLhCapacityMap.size());
    }

    /**
     * 加载设备停机计划
     *
     * @param context     排程上下文
     * @param factoryCode 分厂编号
     * @param startDate   开始日期
     * @param endDate     结束日期
     */
    private void loadDevicePlanShut(LhScheduleContext context, String factoryCode, Date startDate, Date endDate) {
        List<MdmDevicePlanShut> devicePlanShutList = devicePlanShutMapper.selectList(
                new LambdaQueryWrapper<MdmDevicePlanShut>()
                        .eq(MdmDevicePlanShut::getFactoryCode, factoryCode)
                        .le(MdmDevicePlanShut::getBeginDate, endDate)
                        .ge(MdmDevicePlanShut::getEndDate, startDate)
                        .eq(MdmDevicePlanShut::getIsDelete, DeleteFlagEnum.NORMAL.getCode()));
        context.setDevicePlanShutList(devicePlanShutList != null ? devicePlanShutList : context.getDevicePlanShutList());
        log.debug("设备停机计划加载完成, 数量: {}", context.getDevicePlanShutList().size());
    }

    /**
     * 加载SKU与模具关系，按物料编码建立Map
     *
     * @param context     排程上下文
     * @param factoryCode 分厂编号
     */
    private void loadSkuMouldRel(LhScheduleContext context, String factoryCode) {
        List<MdmSkuMouldRel> skuMouldRelList = skuMouldRelMapper.selectList(
                new LambdaQueryWrapper<MdmSkuMouldRel>()
                        .eq(MdmSkuMouldRel::getFactoryCode, factoryCode)
                        .eq(MdmSkuMouldRel::getIsDelete, DeleteFlagEnum.NORMAL.getCode()));
        Map<String, List<MdmSkuMouldRel>> skuMouldRelMap = new HashMap<>(64);
        if (skuMouldRelList != null) {
            for (MdmSkuMouldRel rel : skuMouldRelList) {
                if (rel.getMaterialCode() != null) {
                    skuMouldRelMap.computeIfAbsent(rel.getMaterialCode(), k -> new java.util.ArrayList<>()).add(rel);
                }
            }
        }
        context.setSkuMouldRelMap(skuMouldRelMap);
        log.debug("SKU与模具关系加载完成, SKU数量: {}", skuMouldRelMap.size());
    }

    /**
     * 加载硫化机台信息，按机台编号建立LinkedHashMap（保持顺序）
     *
     * @param context     排程上下文
     * @param factoryCode 分厂编号
     */
    private void loadMachineInfo(LhScheduleContext context, String factoryCode) {
        List<LhMachineInfo> list = lhMachineInfoMapper.selectList(
                new LambdaQueryWrapper<LhMachineInfo>()
                        .eq(LhMachineInfo::getFactoryCode, factoryCode)
                        .eq(LhMachineInfo::getStatus, MACHINE_STATUS_ENABLED)
                        .eq(LhMachineInfo::getIsDelete, DeleteFlagEnum.NORMAL.getCode())
                        .orderByAsc(LhMachineInfo::getMachineOrder));
        Map<String, LhMachineInfo> machineInfoMap = new LinkedHashMap<>(32);
        if (list != null) {
            for (LhMachineInfo info : list) {
                if (info.getMachineCode() != null) {
                    machineInfoMap.put(info.getMachineCode(), info);
                }
            }
        }
        context.setMachineInfoMap(machineInfoMap);
        log.debug("硫化机台信息加载完成, 数量: {}", machineInfoMap.size());
    }

    /**
     * 加载模具清洗计划
     *
     * @param context     排程上下文
     * @param factoryCode 分厂编号
     * @param startDate   开始日期
     * @param endDate     结束日期
     */
    private void loadCleaningPlan(LhScheduleContext context, String factoryCode, Date startDate, Date endDate) {
        List<String> machineCodes = new ArrayList<>(context.getMachineInfoMap().keySet());
        List<LhCleaningPlan> cleaningPlanList;
        if (machineCodes.isEmpty()) {
            cleaningPlanList = Collections.emptyList();
        } else {
            cleaningPlanList = lhCleaningPlanMapper.selectList(
                    new LambdaQueryWrapper<LhCleaningPlan>()
                            .in(LhCleaningPlan::getLhMachineCode, machineCodes)
                            .ge(LhCleaningPlan::getPlanTime, startDate)
                            .lt(LhCleaningPlan::getPlanTime, endDate)
                            .and(w -> w.eq(LhCleaningPlan::getIsDelete, DeleteFlagEnum.NORMAL.getCode())
                                    .or()
                                    .isNull(LhCleaningPlan::getIsDelete)));
        }
        context.setCleaningPlanList(cleaningPlanList != null ? cleaningPlanList : context.getCleaningPlanList());
        log.debug("模具清洗计划加载完成, 数量: {}", context.getCleaningPlanList().size());
    }

    /**
     * 加载月底计划余量，按物料编号建立Map
     *
     * @param context     排程上下文
     * @param factoryCode 分厂编号
     * @param year        年份
     * @param month       月份
     */
    private void loadMonthSurplus(LhScheduleContext context, String factoryCode, int year, int month) {
        List<MdmMonthSurplus> monthSurplusList = monthSurplusMapper.selectList(
                new LambdaQueryWrapper<MdmMonthSurplus>()
                        .eq(MdmMonthSurplus::getFactoryCode, factoryCode)
                        .eq(MdmMonthSurplus::getYear, year)
                        .eq(MdmMonthSurplus::getMonth, month)
                        .eq(MdmMonthSurplus::getIsDelete, DeleteFlagEnum.NORMAL.getCode()));
        Map<String, MdmMonthSurplus> monthSurplusMap = new HashMap<>(64);
        if (monthSurplusList != null) {
            for (MdmMonthSurplus surplus : monthSurplusList) {
                if (StringUtils.isNotEmpty(surplus.getMaterialCode())) {
                    monthSurplusMap.put(surplus.getMaterialCode(), surplus);
                }
            }
        }
        context.setMonthSurplusMap(monthSurplusMap);
        log.debug("月底计划余量加载完成, 数量: {}", monthSurplusMap.size());
    }

    /**
     * 加载各班次完成量，按machineCode+materialCode建立Map
     *
     * @param context      排程上下文
     * @param factoryCode  分厂编号
     * @param scheduleDate 排程日期
     */
    private void loadShiftFinishQty(LhScheduleContext context, String factoryCode, Date scheduleDate) {
        Date day = LhScheduleTimeUtil.clearTime(scheduleDate);
        List<LhShiftFinishQty> shiftFinishQtyList = lhShiftFinishQtyMapper.selectList(
                new LambdaQueryWrapper<LhShiftFinishQty>()
                        .eq(LhShiftFinishQty::getFactoryCode, factoryCode)
                        .eq(LhShiftFinishQty::getScheduleDate, day)
                        .eq(LhShiftFinishQty::getIsDelete, DeleteFlagEnum.NORMAL.getCode()));
        Map<String, LhShiftFinishQty> shiftFinishQtyMap = new HashMap<>(64);
        if (shiftFinishQtyList != null) {
            for (LhShiftFinishQty finishQty : shiftFinishQtyList) {
                String key = finishQty.getLhMachineCode() + "_" + finishQty.getMaterialCode();
                shiftFinishQtyMap.put(key, finishQty);
            }
        }
        context.setShiftFinishQtyMap(shiftFinishQtyMap);
        log.debug("各班次完成量加载完成, 数量: {}", shiftFinishQtyMap.size());
    }

    /**
     * 加载物料信息，按物料编码建立Map
     *
     * @param context     排程上下文
     * @param factoryCode 分厂编号
     */
    private void loadMaterialInfo(LhScheduleContext context, String factoryCode) {
        List<MdmMaterialInfo> materialInfoList = mdmMaterialInfoMapper.selectList(
                new LambdaQueryWrapper<MdmMaterialInfo>()
                        .eq(MdmMaterialInfo::getFactoryCode, factoryCode)
                        .eq(MdmMaterialInfo::getIsDelete, DeleteFlagEnum.NORMAL.getCode()));
        Map<String, MdmMaterialInfo> materialInfoMap = new HashMap<>(256);
        if (materialInfoList != null) {
            for (MdmMaterialInfo materialInfo : materialInfoList) {
                if (materialInfo.getMaterialCode() != null) {
                    materialInfoMap.put(materialInfo.getMaterialCode(), materialInfo);
                }
            }
        }
        context.setMaterialInfoMap(materialInfoMap);
        log.debug("物料信息加载完成, 数量: {}", materialInfoMap.size());
    }

    /**
     * 加载MES硫化在机信息，按机台编号建立Map
     *
     * @param context     排程上下文
     * @param factoryCode 分厂编号
     * @param onlineDate  在机日期（T-1日）
     */
    private void loadMachineOnlineInfo(LhScheduleContext context, String factoryCode, Date onlineDate) {
        Date onlineDay = LhScheduleTimeUtil.clearTime(onlineDate);
        Date onlineDayNext = LhScheduleTimeUtil.addDays(onlineDay, 1);
        List<LhMachineOnlineInfo> machineOnlineInfoList = lhMachineOnlineInfoMapper.selectList(
                new LambdaQueryWrapper<LhMachineOnlineInfo>()
                        .eq(LhMachineOnlineInfo::getFactoryCode, factoryCode)
                        .ge(LhMachineOnlineInfo::getOnlineDate, onlineDay)
                        .lt(LhMachineOnlineInfo::getOnlineDate, onlineDayNext)
                        .and(w -> w.eq(LhMachineOnlineInfo::getIsDelete, DeleteFlagEnum.NORMAL.getCode())
                                .or()
                                .isNull(LhMachineOnlineInfo::getIsDelete)));
        Map<String, LhMachineOnlineInfo> machineOnlineInfoMap = new HashMap<>(32);
        if (machineOnlineInfoList != null) {
            for (LhMachineOnlineInfo onlineInfo : machineOnlineInfoList) {
                if (onlineInfo.getLhCode() != null) {
                    machineOnlineInfoMap.put(onlineInfo.getLhCode(), onlineInfo);
                }
            }
        }
        context.setMachineOnlineInfoMap(machineOnlineInfoMap);
        log.debug("MES硫化在机信息加载完成, 数量: {}", machineOnlineInfoMap.size());
    }

    /**
     * 加载硫化定点机台，按规格代码建立Map
     *
     * @param context     排程上下文
     * @param factoryCode 分厂编号
     */
    private void loadSpecifyMachine(LhScheduleContext context, String factoryCode) {
        List<LhSpecifyMachine> specifyMachineList = lhSpecifyMachineMapper.selectList(
                new LambdaQueryWrapper<LhSpecifyMachine>()
                        .eq(LhSpecifyMachine::getFactoryCode, factoryCode)
                        .eq(LhSpecifyMachine::getIsDelete, DeleteFlagEnum.NORMAL.getCode()));
        Map<String, List<LhSpecifyMachine>> specifyMachineMap = new HashMap<>(32);
        if (specifyMachineList != null) {
            for (LhSpecifyMachine specifyMachine : specifyMachineList) {
                if (specifyMachine.getSpecCode() != null) {
                    specifyMachineMap.computeIfAbsent(specifyMachine.getSpecCode(),
                            k -> new ArrayList<>()).add(specifyMachine);
                }
            }
        }
        context.setSpecifyMachineMap(specifyMachineMap);
        log.debug("硫化定点机台加载完成, 规格数量: {}", specifyMachineMap.size());
    }

    /**
     * 加载硫化机胶囊已使用次数，按机台编号建立Map
     *
     * @param context     排程上下文
     * @param factoryCode 分厂编号
     */
    private void loadCapsuleUsage(LhScheduleContext context, String factoryCode) {
        List<LhRepairCapsule> capsuleUsageList = lhRepairCapsuleMapper.selectList(
                new LambdaQueryWrapper<LhRepairCapsule>()
                        .eq(LhRepairCapsule::getFactoryCode, factoryCode)
                        .eq(LhRepairCapsule::getIsDelete, DeleteFlagEnum.NORMAL.getCode()));
        Map<String, LhRepairCapsule> capsuleUsageMap = new HashMap<>(32);
        if (capsuleUsageList != null) {
            for (LhRepairCapsule capsule : capsuleUsageList) {
                if (capsule.getLhCode() != null) {
                    capsuleUsageMap.put(capsule.getLhCode(), capsule);
                }
            }
        }
        context.setCapsuleUsageMap(capsuleUsageMap);
        log.debug("硫化机胶囊使用次数加载完成, 数量: {}", capsuleUsageMap.size());
    }

    /**
     * 加载设备保养计划，按机台编号建立Map
     *
     * @param context     排程上下文
     * @param factoryCode 分厂编号
     */
    private void loadMaintenancePlan(LhScheduleContext context, String factoryCode) {
        List<MdmDevMaintenancePlan> maintenancePlanList = devMaintenancePlanMapper.selectList(
                new LambdaQueryWrapper<MdmDevMaintenancePlan>()
                        .eq(MdmDevMaintenancePlan::getFactoryCode, factoryCode)
                        .eq(MdmDevMaintenancePlan::getDelFlag, DeleteFlagEnum.NORMAL.getCode()));
        Map<String, MdmDevMaintenancePlan> maintenancePlanMap = new HashMap<>(32);
        if (maintenancePlanList != null) {
            for (MdmDevMaintenancePlan plan : maintenancePlanList) {
                if (plan.getDevCode() != null) {
                    maintenancePlanMap.put(plan.getDevCode(), plan);
                }
            }
        }
        context.setMaintenancePlanMap(maintenancePlanMap);
        log.debug("设备保养计划加载完成, 数量: {}", maintenancePlanMap.size());
    }

}
