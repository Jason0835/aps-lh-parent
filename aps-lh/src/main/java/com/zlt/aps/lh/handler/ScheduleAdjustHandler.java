package com.zlt.aps.lh.handler;

import com.zlt.aps.lh.api.constant.LhScheduleConstant;
import com.zlt.aps.lh.component.TargetScheduleQtyResolver;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.api.domain.vo.LhShiftConfigVO;
import com.zlt.aps.lh.api.domain.entity.LhUnscheduledResult;
import com.zlt.aps.lh.api.domain.entity.LhScheduleResult;
import com.zlt.aps.lh.api.enums.ScheduleStepEnum;
import com.zlt.aps.lh.api.enums.ScheduleTypeEnum;
import com.zlt.aps.lh.api.enums.SkuTagEnum;
import com.zlt.aps.lh.engine.strategy.IEndingJudgmentStrategy;
import com.zlt.aps.lh.util.MonthPlanDayQtyUtil;
import com.zlt.aps.lh.util.ShiftFieldUtil;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import com.zlt.aps.lh.api.domain.entity.LhMachineOnlineInfo;
import com.zlt.aps.lh.api.domain.entity.LhScheFinishQty;
import com.zlt.aps.mdm.api.domain.entity.MdmSkuLhCapacity;
import com.zlt.aps.mp.api.domain.entity.FactoryMonthPlanProductionFinalResult;
import com.zlt.aps.mp.api.domain.entity.MpAdjustResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * S4.3 排程调整与SKU归集处理器
 * <p>基于前日排程修正产量，从月计划获取SKU，按结构归集，计算硫化余量，标记收尾/续作状态</p>
 *
 * @author APS
 */
@Slf4j
@Component
public class ScheduleAdjustHandler extends AbsScheduleStepHandler {

    /** 无排产目标量未排产提示 */
    private static final String NO_PLAN_QTY_REASON_TEMPLATE = "物料：%s 没有排产目标量，不进行排产";
    /** 无窗口计划量但存在余量/正向结转目标量提示 */
    private static final String TARGET_QTY_ONLY_WARN_TEMPLATE =
            "物料：%s 当前排程窗口没有计划量，但存在月计划余量/正向结转目标量[%d]，继续排产";
    /** 满排模式下无窗口计划量仍继续排产提示 */
    private static final String FULL_CAPACITY_WARN_TEMPLATE =
            "物料：%s 当前排程窗口没有计划量，但按产能满排模式生成排产目标量[%d]，继续排产";

    @Resource
    private IEndingJudgmentStrategy endingJudgmentStrategy;
    @Resource
    private TargetScheduleQtyResolver targetScheduleQtyResolver;

    @Override
    protected void doHandle(LhScheduleContext context) {
        // S4.3.1 前日排程欠/超产量调整
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
     * 对前日排程结果做欠/超产量调整（原地修改列表）。
     * <p>
     * 欠产（夜班计划 > 实际完成）：将差额追加到T日早班的计划量中<br/>
     * 超产（实际完成 > 计划）：可冲抵后续班次计划<br/>
     * 已收尾的SKU从续作列表中去除
     * </p>
     *
     * @param context 排程上下文
     */
    private void adjustPreviousSchedule(LhScheduleContext context) {
        List<LhScheduleResult> previousScheduleList = context.getPreviousScheduleResultList();
        if (previousScheduleList == null || previousScheduleList.isEmpty()) {
            return;
        }
        Map<String, Integer> carryForwardQtyMap = new LinkedHashMap<>();
        for (LhScheduleResult result : previousScheduleList) {
            int plannedQty = ShiftFieldUtil.sumPlanQty(result, LhScheduleConstant.MAX_SHIFT_SLOT_COUNT);
            int finishedQty = resolveActualFinishedQty(context, result);
            int diffQty = plannedQty - finishedQty;
            if (diffQty != 0) {
                carryForwardQtyMap.merge(result.getMaterialCode(), diffQty, Integer::sum);
                log.debug("欠产/超产传导: 机台[{}] SKU[{}] 计划[{}] 完成[{}] 净值[{}]",
                        result.getLhMachineCode(), result.getMaterialCode(), plannedQty, finishedQty, diffQty);
            }
        }
        context.setCarryForwardQtyMap(carryForwardQtyMap);
        log.info("前日排程欠产/超产净值归集完成, 记录数: {}, 影响SKU数: {}",
                previousScheduleList.size(), carryForwardQtyMap.size());
    }

    /**
     * 从月度计划获取T日SKU数据，按产品结构归集，计算硫化余量
     * <p>
     * 硫化余量 = 月度计划量 - 硫化已完成量
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
            SurplusCalculation surplus = calculateSurplusQty(context, plan);
            SkuScheduleDTO dto = buildSkuScheduleDTO(context, plan, surplus);

            // 产品结构为空，跳过
            if (StringUtils.isEmpty(plan.getStructureName())) {
                continue;
            }

            int targetScheduleQty = dto.resolveTargetScheduleQty();

            // 当前无排产目标量时，直接记未排产并跳过。
            if (targetScheduleQty <= 0) {
                addNoPlanUnscheduledResult(context, dto);
                continue;
            }

            // 排程窗口没有计划量但存在余量/正向结转目标量时，允许继续排产，并给出明确告警。
            if (dto.getWindowPlanQty() <= 0) {
                if (getTargetScheduleQtyResolver().isFullCapacityMode(context)) {
                    log.warn(String.format(FULL_CAPACITY_WARN_TEMPLATE, dto.getMaterialCode(), targetScheduleQty));
                } else {
                    log.warn(String.format(TARGET_QTY_ONLY_WARN_TEMPLATE, dto.getMaterialCode(), targetScheduleQty));
                }
            }

            structureSkuMap.computeIfAbsent(plan.getStructureName(), k -> new ArrayList<>()).add(dto);
        }

        context.setStructureSkuMap(structureSkuMap);
        int totalSkuCount = structureSkuMap.values().stream().mapToInt(List::size).sum();
        log.info("SKU按结构归集完成, 结构数量: {}, SKU总数: {}", structureSkuMap.size(), totalSkuCount);
    }

    /**
     * 计算SKU的硫化余量
     * <p>
     * 统一按 月度计划总量 - 已完成量 计算，不再读取月余量表（T_MDM_MONTH_SURPLUS）
     * </p>
     *
     * @param context 排程上下文
     * @param plan    月生产计划记录
     * @return 硫化余量
     */
    private SurplusCalculation calculateSurplusQty(LhScheduleContext context, FactoryMonthPlanProductionFinalResult plan) {
        // 统一按月计划总量减前日已完成量计算余量，不再使用月余量表兜底。
        int totalPlanQty = plan.getTotalQty() != null ? plan.getTotalQty() : 0;
        int finishedQty = calculateFinishedQty(context, plan);
        return new SurplusCalculation(Math.max(0, totalPlanQty - finishedQty));
    }

    /**
     * 计算指定SKU的已完成量（汇总各班次完成量）
     *
     * @param context 排程上下文
     * @param plan    月生产计划记录
     * @return 已完成量
     */
    private int calculateFinishedQty(LhScheduleContext context, FactoryMonthPlanProductionFinalResult plan) {
        // 优先使用基础数据阶段按物料汇总好的月累计完成量（截至T-1）。
        String materialCode = plan.getMaterialCode();
        if (StringUtils.isNotEmpty(materialCode)) {
            Integer monthFinishedQty = context.getMaterialMonthFinishedQtyMap().get(materialCode);
            if (monthFinishedQty != null) {
                return Math.max(monthFinishedQty, 0);
            }
        }

        // 兜底：从前日排程结果汇总完成量，且优先复用实际完成量表。
        int finishedQty = 0;
        for (LhScheduleResult result : context.getPreviousScheduleResultList()) {
            if (StringUtils.isNotEmpty(materialCode) && materialCode.equals(result.getMaterialCode())) {
                finishedQty += resolveActualFinishedQty(context, result);
            }
        }
        return finishedQty;
    }

    /**
     * 根据月生产计划构建SKU排程DTO
     *
     * @param context    排程上下文
     * @param plan       月生产计划记录
     * @param surplus 硫化余量
     * @return SKU排程DTO
     */
    private SkuScheduleDTO buildSkuScheduleDTO(LhScheduleContext context,
                                               FactoryMonthPlanProductionFinalResult plan,
                                               SurplusCalculation surplus) {
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
        dto.setFinishedQty(Math.max(0, dto.getMonthPlanQty() - surplus.getSurplusQty()));
        int carryForwardQty = Math.max(0, context.getCarryForwardQtyMap().getOrDefault(plan.getMaterialCode(), 0));
        int windowPlanQty = MonthPlanDayQtyUtil.resolveWindowPlanQty(
                plan, context.getScheduleDate(), context.getScheduleTargetDate());
        dto.setWindowPlanQty(windowPlanQty);
        dto.setSurplusQty(surplus.getSurplusQty());
        dto.setPendingQty(windowPlanQty + carryForwardQty);
        dto.setDailyPlanQty(plan.getDayVulcanizationQty() != null ? plan.getDayVulcanizationQty() : 0);

        // 产能信息（从SKU日硫化产能Map获取）
        MdmSkuLhCapacity capacity = context.getSkuLhCapacityMap().get(plan.getMaterialCode());
        if (capacity != null) {
            // 硫化时间（秒），curingTime来自月计划，若无则用3600秒（1小时）作为默认
            int lhTimeSeconds = plan.getCuringTime() != null ? plan.getCuringTime() : 3600;
            dto.setLhTimeSeconds(lhTimeSeconds);
            dto.setShiftCapacity(capacity.getClassCapacity() != null ? capacity.getClassCapacity() : 0);
        } else {
            // 无产能数据时使用默认值
            dto.setLhTimeSeconds(3600);
        }

        // 填充日硫化产能
        fillDailyCapacity(dto, capacity);
        dto.setTargetScheduleQty(getTargetScheduleQtyResolver().resolveInitialTargetQty(context, dto));

        // 优先级信息
        dto.setSupplyChainPriority(plan.getProductionType());
        dto.setDeliveryLocked(isDeliveryLocked(context, plan.getMaterialCode()));
        dto.setDelayDays(resolveDelayDays(context, plan));
        dto.setHighPriorityPendingQty(safeInt(plan.getHeightProductionQty()));
        dto.setCycleProductionPendingQty(safeInt(plan.getCycleProductionQty()));
        dto.setMidPriorityPendingQty(safeInt(plan.getMidProductionQty()));
        dto.setConventionProductionPendingQty(safeInt(plan.getConventionProductionQty()));

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
     * 追加“无计划量不排产”的未排结果。
     *
     * @param context 排程上下文
     * @param sku SKU排程DTO
     */
    private void addNoPlanUnscheduledResult(LhScheduleContext context, SkuScheduleDTO sku) {
        String reason = String.format(NO_PLAN_QTY_REASON_TEMPLATE, sku.getMaterialCode());
        log.warn(reason);

        LhUnscheduledResult unscheduled = new LhUnscheduledResult();
        unscheduled.setFactoryCode(context.getFactoryCode());
        unscheduled.setBatchNo(context.getBatchNo());
        unscheduled.setScheduleDate(context.getScheduleTargetDate());
        unscheduled.setMonthPlanVersion(sku.getMonthPlanVersion());
        unscheduled.setProductionVersion(sku.getProductionVersion());
        unscheduled.setMaterialCode(sku.getMaterialCode());
        unscheduled.setStructureName(sku.getStructureName());
        unscheduled.setMaterialDesc(sku.getMaterialDesc());
        unscheduled.setMainMaterialDesc(sku.getMainMaterialDesc());
        unscheduled.setSpecCode(sku.getSpecCode());
        unscheduled.setEmbryoCode(sku.getEmbryoCode());
        unscheduled.setUnscheduledQty(0);
        unscheduled.setUnscheduledReason(reason);
        unscheduled.setMouldQty(sku.getMouldQty());
        unscheduled.setDataSource("0");
        unscheduled.setIsDelete(0);
        context.getUnscheduledResultList().add(unscheduled);
    }

    private int resolveActualFinishedQty(LhScheduleContext context, LhScheduleResult result) {
        String key = result.getLhMachineCode() + "_" + result.getMaterialCode();
        LhScheFinishQty finishQty = context.getScheFinishQtyMap().get(key);
        if (Objects.nonNull(finishQty)) {
            return safeFinishQty(finishQty.getClass1FinishQty())
                    + safeFinishQty(finishQty.getClass2FinishQty())
                    + safeFinishQty(finishQty.getClass3FinishQty());
        }
        List<LhShiftConfigVO> shifts = context.getScheduleWindowShifts();
        if (CollectionUtils.isEmpty(shifts)) {
            shifts = LhScheduleTimeUtil.getScheduleShifts(context, context.getScheduleDate());
        }
        int finishedQty = 0;
        for (LhShiftConfigVO shift : shifts) {
            finishedQty += safeInt(ShiftFieldUtil.getShiftFinishQty(result, shift.getShiftIndex()));
        }
        return finishedQty;
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
     * @param context 排程上下文
     * @param materialCode 物料编码
     * @return true-有锁定交期
     */
    private boolean isDeliveryLocked(LhScheduleContext context, String materialCode) {
        if (StringUtils.isEmpty(materialCode)) {
            return false;
        }
        List<MpAdjustResult> adjustResults = context.getMpAdjustResultMap().get(materialCode);
        if (CollectionUtils.isEmpty(adjustResults)) {
            return false;
        }
        for (MpAdjustResult adjustResult : adjustResults) {
            if (StringUtils.equals("1", StringUtils.trimToEmpty(adjustResult.getIsLockSchedule()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算延迟上机天数。
     *
     * @param context 排程上下文
     * @param plan 月生产计划
     * @return 延迟天数；无首个计划日时返回 -1
     */
    private int resolveDelayDays(LhScheduleContext context, FactoryMonthPlanProductionFinalResult plan) {
        if (context.getScheduleDate() == null) {
            return -1;
        }
        int firstPlannedDay = MonthPlanDayQtyUtil.resolveFirstPlannedDay(plan);
        if (firstPlannedDay < 0) {
            return -1;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(context.getScheduleDate());
        int scheduleDayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
        return Math.max(scheduleDayOfMonth - firstPlannedDay, 0);
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
        Map<String, List<SkuScheduleDTO>> skuByMaterialMap = buildSkuByMaterialMap(context);

        // 先按机台最近MES在机记录反找SKU，命中的SKU按机台顺序消费，避免同一SKU被重复占用。
        for (Map.Entry<String, LhMachineOnlineInfo> entry : context.getMachineOnlineInfoMap().entrySet()) {
            assignContinuousSku(entry.getKey(), entry.getValue(), skuByMaterialMap, continuousSkuList);
        }

        for (List<SkuScheduleDTO> skuList : context.getStructureSkuMap().values()) {
            for (SkuScheduleDTO sku : skuList) {
                if (StringUtils.equals(ScheduleTypeEnum.CONTINUOUS.getCode(), sku.getScheduleType())) {
                    continue;
                }
                // 未命中MES在机记录的SKU按新增规格处理。
                sku.setScheduleType(ScheduleTypeEnum.NEW_SPEC.getCode());
                sku.setContinuousMachineCode(null);
                newSpecSkuList.add(sku);
            }
        }

        context.setContinuousSkuList(continuousSkuList);
        context.setNewSpecSkuList(newSpecSkuList);
        log.info("续作/新增SKU区分完成, 续作: {}个, 新增: {}个", continuousSkuList.size(), newSpecSkuList.size());
    }

    /**
     * 按物料编码归集待排SKU，保持原有归集顺序供机台依次消费
     *
     * @param context 排程上下文
     * @return 物料编码 -> 待匹配SKU列表
     */
    private Map<String, List<SkuScheduleDTO>> buildSkuByMaterialMap(LhScheduleContext context) {
        Map<String, List<SkuScheduleDTO>> skuByMaterialMap = new LinkedHashMap<>();
        for (List<SkuScheduleDTO> skuList : context.getStructureSkuMap().values()) {
            for (SkuScheduleDTO sku : skuList) {
                sku.setScheduleType(null);
                sku.setContinuousMachineCode(null);
                if (StringUtils.isEmpty(sku.getMaterialCode())) {
                    continue;
                }
                skuByMaterialMap.computeIfAbsent(sku.getMaterialCode(), k -> new ArrayList<>()).add(sku);
            }
        }
        return skuByMaterialMap;
    }

    /**
     * 按机台最近MES在机记录匹配续作SKU
     *
     * @param machineCode      机台编码
     * @param onlineInfo       机台最近MES在机记录
     * @param skuByMaterialMap 物料编码 -> 待匹配SKU列表
     * @param continuousSkuList 续作SKU列表
     */
    private void assignContinuousSku(String machineCode, LhMachineOnlineInfo onlineInfo,
                                     Map<String, List<SkuScheduleDTO>> skuByMaterialMap,
                                     List<SkuScheduleDTO> continuousSkuList) {
        if (onlineInfo == null
                || StringUtils.isEmpty(machineCode)
                || StringUtils.isEmpty(onlineInfo.getMaterialCode())) {
            return;
        }
        List<SkuScheduleDTO> matchedSkuList = skuByMaterialMap.get(onlineInfo.getMaterialCode());
        if (CollectionUtils.isEmpty(matchedSkuList)) {
            return;
        }
        // 同一物料存在多条SKU时，按归集顺序逐条消费，避免重复占用。
        SkuScheduleDTO matchedSku = matchedSkuList.remove(0);
        matchedSku.setScheduleType(ScheduleTypeEnum.CONTINUOUS.getCode());
        matchedSku.setContinuousMachineCode(machineCode);
        continuousSkuList.add(matchedSku);
    }

    /**
     * 安全获取Integer值，null时返回0
     *
     * @param value Integer值
     * @return int值
     */
    private int safeInt(Integer value) {
        return Objects.nonNull(value) ? value : 0;
    }

    /**
     * 安全获取完成量值，null时返回0。
     *
     * @param value 完成量
     * @return 整数件数
     */
    private int safeFinishQty(BigDecimal value) {
        return Objects.nonNull(value) ? value.intValue() : 0;
    }

    /**
     * 获取目标排产量解析器。
     *
     * @return 目标排产量解析器
     */
    private TargetScheduleQtyResolver getTargetScheduleQtyResolver() {
        return Objects.nonNull(targetScheduleQtyResolver)
                ? targetScheduleQtyResolver
                : new TargetScheduleQtyResolver();
    }

    /**
     * 余量计算结果。
     */
    private static class SurplusCalculation {

        private final int surplusQty;

        private SurplusCalculation(int surplusQty) {
            this.surplusQty = surplusQty;
        }

        public int getSurplusQty() {
            return surplusQty;
        }
    }

    @Override
    protected String getStepName() {
        return ScheduleStepEnum.S4_3_ADJUST_AND_GATHER.getDescription();
    }
}
