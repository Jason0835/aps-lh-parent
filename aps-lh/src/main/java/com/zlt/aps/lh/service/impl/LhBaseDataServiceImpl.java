package com.zlt.aps.lh.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zlt.aps.lh.api.constant.LhScheduleConstant;
import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.entity.LhCleaningPlan;
import com.zlt.aps.lh.api.domain.entity.LhMachineInfo;
import com.zlt.aps.lh.api.domain.entity.LhSpecifyMachine;
import com.zlt.aps.lh.api.enums.DeleteFlagEnum;
import com.zlt.aps.lh.mapper.FactoryMonthPlanProductionFinalResultMapper;
import com.zlt.aps.lh.mapper.LhCleaningPlanMapper;
import com.zlt.aps.lh.mapper.LhMachineInfoMapper;
import com.zlt.aps.lh.mapper.LhShiftFinishQtyMapper;
import com.zlt.aps.lh.mapper.LhSpecifyMachineMapper;
import com.zlt.aps.lh.mapper.MdmDevMaintenancePlanMapper;
import com.zlt.aps.lh.mapper.MdmDevicePlanShutMapper;
import com.zlt.aps.lh.mapper.MdmLhMachineOnlineInfoMapper;
import com.zlt.aps.lh.mapper.MdmLhRepairCapsuleMapper;
import com.zlt.aps.lh.mapper.MdmMaterialInfoMapper;
import com.zlt.aps.lh.mapper.MdmMonthSurplusMapper;
import com.zlt.aps.lh.mapper.MdmSkuLhCapacityMapper;
import com.zlt.aps.lh.mapper.MdmSkuMouldRelMapper;
import com.zlt.aps.lh.mapper.MdmWorkCalendarMapper;
import com.zlt.aps.lh.service.ILhBaseDataService;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import com.zlt.aps.mdm.api.domain.entity.MdmDevMaintenancePlan;
import com.zlt.aps.mdm.api.domain.entity.MdmDevicePlanShut;
import com.zlt.aps.mdm.api.domain.entity.MdmLhMachineOnlineInfo;
import com.zlt.aps.mdm.api.domain.entity.MdmLhRepairCapsule;
import com.zlt.aps.mdm.api.domain.entity.MdmMaterialInfo;
import com.zlt.aps.mdm.api.domain.entity.MdmMonthSurplus;
import com.zlt.aps.mdm.api.domain.entity.MdmSkuLhCapacity;
import com.zlt.aps.mdm.api.domain.entity.MdmSkuMouldRel;
import com.zlt.aps.mdm.api.domain.entity.MdmWorkCalendar;
import com.zlt.aps.mp.api.domain.entity.FactoryMonthPlanProductionFinalResult;
import com.zlt.aps.mps.domain.LhShiftFinishQty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    private static final String MACHINE_STATUS_ENABLED = "0";

    @Resource
    private FactoryMonthPlanProductionFinalResultMapper monthPlanMapper;

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
    private MdmLhMachineOnlineInfoMapper lhMachineOnlineInfoMapper;

    @Resource
    private LhSpecifyMachineMapper lhSpecifyMachineMapper;

    @Resource
    private MdmLhRepairCapsuleMapper lhRepairCapsuleMapper;

    @Resource
    private MdmDevMaintenancePlanMapper devMaintenancePlanMapper;

    @Override
    public void loadAllBaseData(LhScheduleContext context) {
        String factoryCode = context.getFactoryCode();
        Date scheduleDate = context.getScheduleDate();
        Date targetDate = context.getScheduleTargetDate();

        // 加载排程时间范围 [T日-T+3日）左闭右开
        Date startDate = LhScheduleTimeUtil.clearTime(scheduleDate);
        Date endDate = LhScheduleTimeUtil.addDays(startDate, LhScheduleConstant.SCHEDULE_DAYS + 1);

        // 获取年月信息（按排程目标日取月计划所属年月）
        Calendar cal = Calendar.getInstance();
        cal.setTime(targetDate);
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        int yearMonth = year * 100 + month;

        // 1. 加载月生产计划
        loadMonthPlan(context, factoryCode, yearMonth);

        // 2. 加载工作日历
        loadWorkCalendar(context, factoryCode, startDate, endDate);

        // 3. 加载SKU日硫化产能
        loadSkuLhCapacity(context, factoryCode);

        // 4. 加载设备停机计划
        loadDevicePlanShut(context, factoryCode, startDate, endDate);

        // 5. 加载SKU与模具关系
        loadSkuMouldRel(context, factoryCode);

        // 6. 加载硫化机台信息
        loadMachineInfo(context, factoryCode);

        // 7. 加载模具清洗计划
        loadCleaningPlan(context, factoryCode, startDate, endDate);

        // 8. 加载月底计划余量
        loadMonthSurplus(context, factoryCode, year, month);

        // 9. 加载各班次完成量
        loadShiftFinishQty(context, factoryCode, context.getScheduleDate());

        // 10. 加载物料信息
        loadMaterialInfo(context, factoryCode);

        // 11. 加载MES硫化在机信息（取T-1日在机信息）
        Date previousDay = LhScheduleTimeUtil.addDays(startDate, -1);
        loadMachineOnlineInfo(context, factoryCode, previousDay);

        // 12. 加载硫化定点机台
        loadSpecifyMachine(context, factoryCode);

        // 13. 加载硫化机胶囊已使用次数
        loadCapsuleUsage(context, factoryCode);

        // 14. 加载设备保养计划
        loadMaintenancePlan(context, factoryCode);

        // 15. todo 加载前日硫化排程结果信息（前日日期=目标日 -1）

        log.info("基础数据加载完成, 工厂: {}, 目标日: {}, T日: {}", factoryCode, context.getScheduleTargetDate(), scheduleDate);
    }


    /**
     * 加载月生产计划
     *
     * @param context     排程上下文
     * @param factoryCode 分厂编号
     * @param yearMonth   年月（如202603）
     */
    private void loadMonthPlan(LhScheduleContext context, String factoryCode, int yearMonth) {
        LambdaQueryWrapper<FactoryMonthPlanProductionFinalResult> w = new LambdaQueryWrapper<FactoryMonthPlanProductionFinalResult>()
                .eq(FactoryMonthPlanProductionFinalResult::getFactoryCode, factoryCode)
                .eq(FactoryMonthPlanProductionFinalResult::getYearMonth, yearMonth)
                .eq(FactoryMonthPlanProductionFinalResult::getIsDelete, DeleteFlagEnum.NORMAL.getCode());
        String productionVersion = context.getProductionVersion();
        if (productionVersion != null && !productionVersion.isEmpty()) {
            w.eq(FactoryMonthPlanProductionFinalResult::getProductionVersion, productionVersion);
        }
        List<FactoryMonthPlanProductionFinalResult> monthPlanList = monthPlanMapper.selectList(w);
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
     * 加载月底计划余量，按GroupKey建立Map
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
                monthSurplusMap.put(surplus.getGroupKey(), surplus);
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
        List<MdmLhMachineOnlineInfo> machineOnlineInfoList = lhMachineOnlineInfoMapper.selectList(
                new LambdaQueryWrapper<MdmLhMachineOnlineInfo>()
                        .eq(MdmLhMachineOnlineInfo::getFactoryCode, factoryCode)
                        .ge(MdmLhMachineOnlineInfo::getOnlineDate, onlineDay)
                        .lt(MdmLhMachineOnlineInfo::getOnlineDate, onlineDayNext)
                        .and(w -> w.eq(MdmLhMachineOnlineInfo::getIsDelete, DeleteFlagEnum.NORMAL.getCode())
                                .or()
                                .isNull(MdmLhMachineOnlineInfo::getIsDelete)));
        Map<String, MdmLhMachineOnlineInfo> machineOnlineInfoMap = new HashMap<>(32);
        if (machineOnlineInfoList != null) {
            for (MdmLhMachineOnlineInfo onlineInfo : machineOnlineInfoList) {
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
        List<MdmLhRepairCapsule> capsuleUsageList = lhRepairCapsuleMapper.selectList(
                new LambdaQueryWrapper<MdmLhRepairCapsule>()
                        .eq(MdmLhRepairCapsule::getFactoryCode, factoryCode)
                        .eq(MdmLhRepairCapsule::getIsDelete, DeleteFlagEnum.NORMAL.getCode()));
        Map<String, MdmLhRepairCapsule> capsuleUsageMap = new HashMap<>(32);
        if (capsuleUsageList != null) {
            for (MdmLhRepairCapsule capsule : capsuleUsageList) {
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
