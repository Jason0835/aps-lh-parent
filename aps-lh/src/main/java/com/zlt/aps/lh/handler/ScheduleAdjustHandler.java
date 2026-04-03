package com.zlt.aps.lh.handler;

import com.zlt.aps.lh.api.constant.LhScheduleConstant;
import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.api.domain.entity.LhScheduleResult;
import com.zlt.aps.lh.api.enums.ScheduleStepEnum;
import com.zlt.aps.lh.api.enums.ScheduleTypeEnum;
import com.zlt.aps.lh.api.enums.SkuTagEnum;
import com.zlt.aps.lh.engine.strategy.IEndingJudgmentStrategy;
import com.zlt.aps.lh.mapper.LhScheduleResultMapper;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import com.zlt.aps.mdm.api.domain.entity.MdmLhMachineOnlineInfo;
import com.zlt.aps.mdm.api.domain.entity.MdmMonthSurplus;
import com.zlt.aps.mdm.api.domain.entity.MdmSkuLhCapacity;
import com.zlt.aps.mp.api.domain.entity.FactoryMonthPlanProductionFinalResult;
import com.zlt.aps.lh.api.domain.entity.LhShiftFinishQty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * S4.3 排程调整与SKU归集处理器
 * <p>基于前日排程修正产量，从月计划获取SKU，按结构归集，计算硫化余量，标记收尾/续作状态</p>
 *
 * @author APS
 */
@Slf4j
@Component
public class ScheduleAdjustHandler extends AbsScheduleStepHandler {

    @Resource
    private LhScheduleResultMapper scheduleResultMapper;

    @Resource
    private IEndingJudgmentStrategy endingJudgmentStrategy;

    @Override
    protected void doHandle(LhScheduleContext context) {
        // S4.3.1 加载前日排程并调整欠/超产量
        adjustPreviousSchedule(context);

        // S4.3.2 按产品结构归集SKU，计算硫化余量
        gatherSkuByStructure(context);

        // S4.3.3 标注收尾SKU（3天内可收尾）
        markEndingSkus(context);

        // S4.3.4 区分续作SKU和新增SKU
        classifyContinuousAndNewSkus(context);

        log.info("排程调整与SKU归集完成, 续作SKU: {}个, 新增SKU: {}个",
                context.getContinuousSkuList().size(), context.getNewSpecSkuList().size());
    }

    /**
     * 加载前日排程并修正欠/超产差额
     * <p>
     * 欠产（夜班计划 > 实际完成）：将差额追加到T日早班的计划量中<br/>
     * 超产（实际完成 > 计划）：可冲抵后续班次计划<br/>
     * 已收尾的SKU从续作列表中去除
     * </p>
     *
     * @param context 排程上下文
     */
    private void adjustPreviousSchedule(LhScheduleContext context) {
        Date targetDate = context.getScheduleTargetDate();
        String factoryCode = context.getFactoryCode();

        // 查询上一目标日的排程结果（库表 schedule_date 存排程目标日）
        Date previousDate = LhScheduleTimeUtil.addDays(targetDate, -1);
        List<LhScheduleResult> previousScheduleList =
                scheduleResultMapper.selectPreviousSchedule(previousDate, factoryCode);

        if (previousScheduleList == null || previousScheduleList.isEmpty()) {
            log.info("未找到前日排程数据, 日期: {}", LhScheduleTimeUtil.getDateStr(previousDate));
            context.setPreviousScheduleResultList(new ArrayList<>());
            return;
        }

        // 处理前日排程：识别欠产量（班次3为夜班，即T日的夜班）
        for (LhScheduleResult result : previousScheduleList) {
            // 获取夜班（class3 = T+1日夜班，即前日的夜班）计划量和完成量
            int nightPlanQty = result.getClass3PlanQty() != null ? result.getClass3PlanQty() : 0;
            int nightFinishQty = result.getClass3FinishQty() != null ? result.getClass3FinishQty() : 0;

            // 计算夜班欠产量
            int deficit = nightPlanQty - nightFinishQty;
            if (deficit > 0) {
                // 欠产：在T日早班（class1）追加欠产量
                int currentClass1Plan = result.getClass1PlanQty() != null ? result.getClass1PlanQty() : 0;
                result.setClass1PlanQty(currentClass1Plan + deficit);
                log.debug("欠产调整: 机台[{}] SKU[{}] 夜班欠产[{}]条, 追加至T日早班",
                        result.getLhMachineCode(), result.getMaterialCode(), deficit);
            }
        }

        context.setPreviousScheduleResultList(previousScheduleList);
        log.info("前日排程加载并调整完成, 数量: {}", previousScheduleList.size());
    }

    /**
     * 从月度计划获取T日SKU数据，按产品结构归集，计算硫化余量
     * <p>
     * 硫化余量 = 月度计划量 - 硫化已完成合格量<br/>
     * 若月底计划余量表中有数据，优先使用该数据作为余量
     * </p>
     *
     * @param context 排程上下文
     */
    private void gatherSkuByStructure(LhScheduleContext context) {
        List<FactoryMonthPlanProductionFinalResult> monthPlanList = context.getMonthPlanList();
        if (monthPlanList == null || monthPlanList.isEmpty()) {
            log.warn("月生产计划为空，无法归集SKU");
            return;
        }

        // 按结构归集SKU（key=结构名称，value=该结构下的SKU排程DTO列表）
        Map<String, List<SkuScheduleDTO>> structureSkuMap = new LinkedHashMap<>();

        for (FactoryMonthPlanProductionFinalResult plan : monthPlanList) {
            // 计算硫化余量
            int surplusQty = calculateSurplusQty(context, plan);
            // 余量为0说明已完成，跳过
            if (surplusQty <= 0) {
                continue;
            }

            SkuScheduleDTO dto = buildSkuScheduleDTO(context, plan, surplusQty);

            String structureName = plan.getStructureName() != null ? plan.getStructureName() : "未知结构";
            structureSkuMap.computeIfAbsent(structureName, k -> new ArrayList<>()).add(dto);
        }

        context.setStructureSkuMap(structureSkuMap);
        int totalSkuCount = structureSkuMap.values().stream().mapToInt(List::size).sum();
        log.info("SKU按结构归集完成, 结构数量: {}, SKU总数: {}", structureSkuMap.size(), totalSkuCount);
    }

    /**
     * 计算SKU的硫化余量
     * <p>
     * 优先使用月底计划余量表（T_MDM_MONTH_SURPLUS）中的数据<br/>
     * 若无数据，则通过 月度计划总量 - 各班次完成量之和 计算
     * </p>
     *
     * @param context 排程上下文
     * @param plan    月生产计划记录
     * @return 硫化余量
     */
    private int calculateSurplusQty(LhScheduleContext context, FactoryMonthPlanProductionFinalResult plan) {
        String materialCode = plan.getMaterialCode();
        String factoryCode = plan.getFactoryCode();

        // 先从月底计划余量Map中获取
        String groupKey = factoryCode + "|*|" + materialCode;
        MdmMonthSurplus monthSurplus = context.getMonthSurplusMap().get(groupKey);
        if (monthSurplus != null && monthSurplus.getPlanSurplusQty() != null) {
            return monthSurplus.getPlanSurplusQty().intValue();
        }

        // 若无余量数据，用月计划总量减去各班次完成量
        int totalPlanQty = plan.getTotalQty() != null ? plan.getTotalQty() : 0;
        int finishedQty = calculateFinishedQty(context, plan);
        return Math.max(0, totalPlanQty - finishedQty);
    }

    /**
     * 计算指定SKU的已完成量（汇总各班次完成量）
     *
     * @param context 排程上下文
     * @param plan    月生产计划记录
     * @return 已完成量
     */
    private int calculateFinishedQty(LhScheduleContext context, FactoryMonthPlanProductionFinalResult plan) {
        // 从前日排程结果汇总完成量
        int finishedQty = 0;
        for (LhScheduleResult result : context.getPreviousScheduleResultList()) {
            if (plan.getMaterialCode() != null && plan.getMaterialCode().equals(result.getMaterialCode())) {
                finishedQty += safeInt(result.getClass1FinishQty());
                finishedQty += safeInt(result.getClass2FinishQty());
                finishedQty += safeInt(result.getClass3FinishQty());
                finishedQty += safeInt(result.getClass4FinishQty());
                finishedQty += safeInt(result.getClass5FinishQty());
                finishedQty += safeInt(result.getClass6FinishQty());
                finishedQty += safeInt(result.getClass7FinishQty());
                finishedQty += safeInt(result.getClass8FinishQty());
            }
        }
        return finishedQty;
    }

    /**
     * 根据月生产计划构建SKU排程DTO
     *
     * @param context    排程上下文
     * @param plan       月生产计划记录
     * @param surplusQty 硫化余量
     * @return SKU排程DTO
     */
    private SkuScheduleDTO buildSkuScheduleDTO(LhScheduleContext context,
                                               FactoryMonthPlanProductionFinalResult plan,
                                               int surplusQty) {
        SkuScheduleDTO dto = new SkuScheduleDTO();
        dto.setMaterialCode(plan.getMaterialCode());
        dto.setMaterialDesc(plan.getMaterialDesc());
        dto.setStructureName(plan.getStructureName());
        dto.setEmbryoCode(plan.getEmbryoCode());
        dto.setMainMaterialDesc(plan.getMainMaterialDesc());
        dto.setSpecCode(plan.getSpecifications());
        dto.setProSize(plan.getProSize());
        dto.setPattern(plan.getPattern());
        dto.setMainPattern(plan.getMainPattern());
        dto.setBrand(plan.getBrand());

        // 计划量信息
        dto.setMonthPlanQty(plan.getTotalQty() != null ? plan.getTotalQty() : 0);
        dto.setSurplusQty(surplusQty);
        dto.setPendingQty(surplusQty);
        dto.setDailyPlanQty(plan.getDayVulcanizationQty() != null ? plan.getDayVulcanizationQty() : 0);

        // 产能信息（从SKU日硫化产能Map获取）
        MdmSkuLhCapacity capacity = context.getSkuLhCapacityMap().get(plan.getMaterialCode());
        if (capacity != null) {
            // 硫化时间（秒），curingTime来自月计划，若无则用600秒（10分钟）作为默认
            int lhTimeSeconds = plan.getCuringTime() != null ? plan.getCuringTime() : 3600;
            dto.setLhTimeSeconds(lhTimeSeconds);
            dto.setShiftCapacity(capacity.getClassCapacity() != null ? capacity.getClassCapacity() : 0);
            dto.setMouldQty(1);
//            dto.setMouldQty(capacity.getMouldQty() != null ? capacity.getMouldQty() : 1);
        } else {
            // 无产能数据时使用默认值
            dto.setLhTimeSeconds(3600);
            dto.setMouldQty(1);
        }

        fillDailyCapacity(dto, capacity);

        // 优先级信息
        dto.setSupplyChainPriority(plan.getProductionType());
        dto.setDeliveryLocked(isDeliveryLocked(plan));

        // 施工阶段
        dto.setConstructionStage(plan.getConstructionStage());

        // 示方书信息
        dto.setEmbryoNo(plan.getEmbryoNo());
        dto.setTextNo(plan.getTextNo());
        dto.setLhNo(plan.getLhNo());

        // 版本信息
        dto.setMonthPlanVersion(plan.getMonthPlanVersion());
        dto.setProductionVersion(plan.getProductionVersion());

        // 默认标记为常规
        dto.setSkuTag(SkuTagEnum.NORMAL.getCode());

        return dto;
    }

    /**
     * 填充日硫化产能，供统一收尾判定策略（待排量与日产对比）使用
     *
     * @param dto      SKU排程DTO（需已设置 dailyPlanQty、shiftCapacity）
     * @param capacity SKU硫化产能主数据，可为null
     */
    private void fillDailyCapacity(SkuScheduleDTO dto, MdmSkuLhCapacity capacity) {
        int dailyCap = 0;
        if (capacity != null) {
            if (capacity.getApsCapacity() != null && capacity.getApsCapacity() > 0) {
                dailyCap = capacity.getApsCapacity();
            } else if (capacity.getStandardCapacity() != null && capacity.getStandardCapacity() > 0) {
                dailyCap = capacity.getStandardCapacity();
            }
        }
        if (dailyCap <= 0 && dto.getShiftCapacity() > 0) {
            dailyCap = dto.getShiftCapacity() * LhScheduleConstant.DEFAULT_SHIFTS_PER_DAY;
        }
        if (dailyCap <= 0 && dto.getDailyPlanQty() > 0) {
            dailyCap = dto.getDailyPlanQty();
        }
        dto.setDailyCapacity(dailyCap);
    }

    /**
     * 判断SKU是否有交期锁定（周程滚动调整有锁定上机日期）
     *
     * @param plan 月生产计划记录
     * @return true-有锁定交期
     */
    private boolean isDeliveryLocked(FactoryMonthPlanProductionFinalResult plan) {
        // 若高优先级数量 > 0 或者有发货要求，视为有交期锁定
        return plan.getHeightQty() != null && plan.getHeightQty() > 0;
    }

    /**
     * 标注收尾SKU
     * <p>委托收尾判定策略接口，与续作收尾判定、排序规则保持一致</p>
     *
     * @param context 排程上下文
     */
    private void markEndingSkus(LhScheduleContext context) {
        int endingCount = 0;
        for (List<SkuScheduleDTO> skuList : context.getStructureSkuMap().values()) {
            for (SkuScheduleDTO sku : skuList) {
                if (endingJudgmentStrategy.isEnding(context, sku)) {
                    sku.setSkuTag(SkuTagEnum.ENDING.getCode());
                    int endingDays = endingJudgmentStrategy.calculateEndingDays(context, sku);
                    if (endingDays < 0) {
                        // 班产缺失无法折算班次数时，收尾日保守记为 1
                        sku.setEndingDaysRemaining(1);
                    } else {
                        sku.setEndingDaysRemaining(endingDays);
                    }
                    endingCount++;
                }
            }
        }
        log.info("收尾SKU标注完成, 收尾数量: {}", endingCount);
    }

    /**
     * 区分续作SKU和新增SKU
     * <p>
     * 续作SKU：MES在机信息显示当前正在生产的规格，直接延续<br/>
     * 新增SKU：月计划中需要上机但当前未在产的规格，需换模上机
     * </p>
     *
     * @param context 排程上下文
     */
    private void classifyContinuousAndNewSkus(LhScheduleContext context) {
        List<SkuScheduleDTO> continuousSkuList = new ArrayList<>();
        List<SkuScheduleDTO> newSpecSkuList = new ArrayList<>();

        for (List<SkuScheduleDTO> skuList : context.getStructureSkuMap().values()) {
            for (SkuScheduleDTO sku : skuList) {
                // 判断是否为续作：从MES在机信息中查找该SKU是否已在某台机台上生产
                String continuousMachineCode = findContinuousMachine(context, sku.getMaterialCode());
                if (continuousMachineCode != null) {
                    // 续作SKU：记录所在机台
                    sku.setScheduleType(ScheduleTypeEnum.CONTINUOUS.getCode());
                    sku.setContinuousMachineCode(continuousMachineCode);
                    continuousSkuList.add(sku);
                } else {
                    // 新增SKU：需要换模上机
                    sku.setScheduleType(ScheduleTypeEnum.NEW_SPEC.getCode());
                    newSpecSkuList.add(sku);
                }
            }
        }

        context.setContinuousSkuList(continuousSkuList);
        context.setNewSpecSkuList(newSpecSkuList);
        log.info("续作/新增SKU区分完成, 续作: {}个, 新增: {}个", continuousSkuList.size(), newSpecSkuList.size());
    }

    /**
     * 在MES在机信息中查找该SKU是否当前正在某机台上生产
     *
     * @param context      排程上下文
     * @param materialCode 物料编码（SKU）
     * @return 机台编号，null表示未在产
     */
    private String findContinuousMachine(LhScheduleContext context, String materialCode) {
        if (materialCode == null) {
            return null;
        }
        for (Map.Entry<String, MdmLhMachineOnlineInfo> entry
                : context.getMachineOnlineInfoMap().entrySet()) {
            MdmLhMachineOnlineInfo onlineInfo = entry.getValue();
            if (materialCode.equals(onlineInfo.getMaterialCode())) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 安全获取Integer值，null时返回0
     *
     * @param value Integer值
     * @return int值
     */
    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    @Override
    protected String getStepName() {
        return ScheduleStepEnum.S4_3_ADJUST_AND_GATHER.getDescription();
    }
}
