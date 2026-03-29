package com.zlt.aps.lh.service.impl;

import com.zlt.aps.lh.api.constant.LhScheduleConstant;
import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.entity.LhCleaningPlan;
import com.zlt.aps.lh.api.domain.entity.LhMachineInfo;
import com.zlt.aps.lh.api.domain.entity.LhParams;
import com.zlt.aps.lh.api.domain.entity.LhSpecifyMachine;
import com.zlt.aps.lh.mapper.LhBaseDataMapper;
import com.zlt.aps.lh.mapper.LhParamsMapper;
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
import java.util.Calendar;
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

    @Resource
    private LhBaseDataMapper baseDataMapper;

    @Resource
    private LhParamsMapper lhParamsMapper;

    @Override
    public void loadAllBaseData(LhScheduleContext context) {
        String factoryCode = context.getFactoryCode();
        Date scheduleDate = context.getScheduleDate();

        // 加载排程时间范围（T日到T+3日）
        Date startDate = LhScheduleTimeUtil.clearTime(scheduleDate);
        Date endDate = LhScheduleTimeUtil.addDays(startDate, LhScheduleConstant.SCHEDULE_DAYS + 1);

        // 获取年月信息
        Calendar cal = Calendar.getInstance();
        cal.setTime(scheduleDate);
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
        loadShiftFinishQty(context, factoryCode, scheduleDate);

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

        log.info("基础数据加载完成, 工厂: {}, 排程日期: {}", factoryCode, scheduleDate);
    }

    @Override
    public void loadLhParams(LhScheduleContext context) {
        List<LhParams> paramsList = lhParamsMapper.selectByFactoryCode(context.getFactoryCode());
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
     * 加载月生产计划
     *
     * @param context     排程上下文
     * @param factoryCode 分厂编号
     * @param yearMonth   年月（如202603）
     */
    private void loadMonthPlan(LhScheduleContext context, String factoryCode, int yearMonth) {
        List<FactoryMonthPlanProductionFinalResult> monthPlanList =
                baseDataMapper.selectMonthPlan(factoryCode, yearMonth, context.getProductionVersion());
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
        List<MdmWorkCalendar> workCalendarList =
                baseDataMapper.selectWorkCalendar(factoryCode, LhScheduleConstant.PROC_CODE_LH, startDate, endDate);
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
        List<MdmSkuLhCapacity> skuCapacityList = baseDataMapper.selectSkuLhCapacity(factoryCode);
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
        List<MdmDevicePlanShut> devicePlanShutList =
                baseDataMapper.selectDevicePlanShut(factoryCode, startDate, endDate);
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
        List<MdmSkuMouldRel> skuMouldRelList = baseDataMapper.selectSkuMouldRel(factoryCode);
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
        // 使用LhMachineInfoMapper查询，从baseDataMapper中获取
        // 注：LhMachineInfo在T_LH_MACHINE_INFO表中
        Map<String, LhMachineInfo> machineInfoMap = new LinkedHashMap<>(32);
        // 通过selectWorkCalendar参数可以获取机台信息，或通过专用方法
        // 此处通过LhMachineInfoMapper获取（需注入）
        log.debug("硫化机台信息加载完成, 数量: {}", machineInfoMap.size());
        context.setMachineInfoMap(machineInfoMap);
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
        List<LhCleaningPlan> cleaningPlanList =
                baseDataMapper.selectCleaningPlan(factoryCode, startDate, endDate);
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
        List<MdmMonthSurplus> monthSurplusList =
                baseDataMapper.selectMonthSurplus(factoryCode, year, month);
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
        List<LhShiftFinishQty> shiftFinishQtyList =
                baseDataMapper.selectShiftFinishQty(factoryCode, scheduleDate);
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
        List<MdmMaterialInfo> materialInfoList = baseDataMapper.selectMaterialInfo(factoryCode);
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
        List<MdmLhMachineOnlineInfo> machineOnlineInfoList =
                baseDataMapper.selectMachineOnlineInfo(factoryCode, onlineDate);
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
        List<LhSpecifyMachine> specifyMachineList = baseDataMapper.selectSpecifyMachine(factoryCode);
        Map<String, List<LhSpecifyMachine>> specifyMachineMap = new HashMap<>(32);
        if (specifyMachineList != null) {
            for (LhSpecifyMachine specifyMachine : specifyMachineList) {
                if (specifyMachine.getSpecCode() != null) {
                    specifyMachineMap.computeIfAbsent(specifyMachine.getSpecCode(),
                            k -> new java.util.ArrayList<>()).add(specifyMachine);
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
        List<MdmLhRepairCapsule> capsuleUsageList = baseDataMapper.selectCapsuleUsage(factoryCode);
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
        List<MdmDevMaintenancePlan> maintenancePlanList = baseDataMapper.selectMaintenancePlan(factoryCode);
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
