package com.zlt.aps.lh.component;

import com.zlt.aps.lh.api.constant.LhScheduleConstant;
import com.zlt.aps.lh.api.constant.LhScheduleParamConstant;
import com.zlt.aps.lh.api.domain.dto.MachineCleaningWindowDTO;
import com.zlt.aps.lh.api.domain.dto.MachineScheduleDTO;
import com.zlt.aps.lh.api.domain.dto.ShiftProductionControlDTO;
import com.zlt.aps.lh.api.domain.dto.SkuDailyScheduleDemandDTO;
import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.api.domain.vo.LhShiftConfigVO;
import com.zlt.aps.lh.api.enums.ScheduleTargetModeEnum;
import com.zlt.aps.lh.context.LhScheduleConfig;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.engine.strategy.IMachineMatchStrategy;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import com.zlt.aps.lh.util.MachineCleaningOverlapUtil;
import com.zlt.aps.lh.util.ShiftCapacityResolverUtil;
import com.zlt.aps.lh.util.ShiftProductionControlUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 排产目标量解析器
 * <p>统一承载”按需求排产 / 按产能满排”的目标量口径，避免分散判断。</p>
 *
 * @author APS
 */
@Slf4j
@Component
public class TargetScheduleQtyResolver {

    @Resource
    private IMachineMatchStrategy machineMatchStrategy;

    /**
     * 解析 SKU 的初始目标排产量。
     * <p>非收尾场景目标量严格受窗口计划量/余量控制，不上调胎胚库存；
     * 收尾场景且多机台合计产能足够时，才允许上调到胎胚库存。</p>
     *
     * @param context 排程上下文
     * @param sku SKU
     * @return 初始目标排产量
     */
    public int resolveInitialTargetQty(LhScheduleContext context, SkuScheduleDTO sku) {
        if (Objects.isNull(sku)) {
            return 0;
        }
        int pendingQty = Math.max(0, sku.getPendingQty());
        if (pendingQty <= 0) {
            return 0;
        }
        // 优先计算多机台合计产能（自带缓存），同时用于满排封顶和产能上限
        int totalAvailableCapacity = calcSkuTotalAvailableCapacityInWindow(context, sku);
        int upperLimitQty;
        if (isFullCapacityMode(context)) {
            // 满排模式：优先用多机台合计产能封顶，无候选机台时回退到理论窗口产能
            upperLimitQty = totalAvailableCapacity > 0
                    ? totalAvailableCapacity
                    : resolveTheoreticalWindowCapacity(context, sku);
        } else {
            upperLimitQty = pendingQty;
        }
        int targetQty = Math.max(0, Math.min(pendingQty, upperLimitQty));
        // 多机台总产能封顶，避免排产量超过所有可用机台合计产能
        if (totalAvailableCapacity > 0) {
            targetQty = Math.min(targetQty, totalAvailableCapacity);
        }
        return targetQty;
    }

    /**
     * 计算 SKU 在当前排程窗口内所有可用机台的合计产能。
     * <p>用于收尾判断（规则2）和多机台排产目标量封顶。
     * 遍历候选机台列表，对每台机台按班次计算可用产能并汇总。</p>
     *
     * @param context 排程上下文
     * @param sku     SKU排程DTO
     * @return 多台可用机台在窗口内的合计可排产量
     */
    public int calcSkuTotalAvailableCapacityInWindow(LhScheduleContext context, SkuScheduleDTO sku) {
        if (Objects.isNull(context) || Objects.isNull(sku)) {
            return 0;
        }
        // 产能缓存命中直接返回，避免三阶段重复计算
        Map<String, Integer> cache = context.getSkuTotalCapacityCache();
        if (cache != null && cache.containsKey(sku.getMaterialCode())) {
            return cache.get(sku.getMaterialCode());
        }
        IMachineMatchStrategy strategy = getMachineMatchStrategy();
        if (Objects.isNull(strategy)) {
            return 0;
        }
        List<MachineScheduleDTO> candidates = strategy.matchMachines(context, sku);
        if (CollectionUtils.isEmpty(candidates)) {
            return 0;
        }
        List<LhShiftConfigVO> shifts = resolveScheduleShifts(context);
        if (CollectionUtils.isEmpty(shifts)) {
            return 0;
        }
        int shiftCapacity = sku.getShiftCapacity();
        int lhTimeSeconds = sku.getLhTimeSeconds();
        if (shiftCapacity <= 0 && lhTimeSeconds <= 0) {
            return 0;
        }

        int totalCapacity = 0;
        for (MachineScheduleDTO machine : candidates) {
            if (machine == null) {
                continue;
            }
            int machineMouldQty = ShiftCapacityResolverUtil.resolveMachineMouldQty(machine);
            int runtimeShiftCapacity = ShiftCapacityResolverUtil.resolveRuntimeShiftCapacity(
                    context, machine, shiftCapacity);
            if (runtimeShiftCapacity <= 0 && lhTimeSeconds <= 0) {
                continue;
            }
            int machineCapacity = 0;
            for (LhShiftConfigVO shift : shifts) {
                ShiftProductionControlDTO control = ShiftProductionControlUtil.resolveEffectiveControl(
                        context, shift, shift.getShiftStartDateTime());
                if (Objects.isNull(control) || !control.isCanSchedule()) {
                    continue;
                }
                long availableSeconds = (control.getEffectiveEndTime().getTime()
                        - control.getEffectiveStartTime().getTime()) / 1000L;
                int shiftMaxQty = ShiftCapacityResolverUtil.resolveShiftCapacity(
                        runtimeShiftCapacity,
                        lhTimeSeconds,
                        machineMouldQty,
                        ShiftCapacityResolverUtil.resolveShiftDurationSeconds(shift),
                        availableSeconds);
                shiftMaxQty = ShiftProductionControlUtil.deductCapacityByControl(control, shiftMaxQty, machineMouldQty);
                if (shiftMaxQty > 0) {
                    machineCapacity += shiftMaxQty;
                }
            }
            totalCapacity += machineCapacity;
        }
        int result = Math.max(0, totalCapacity);
        // 写入缓存，后续同一SKU的收尾判定和日计划上调可直接命中
        if (cache != null) {
            cache.put(sku.getMaterialCode(), result);
        }
        log.debug("SKU多机台合计产能计算, materialCode: {}, 候选机台数: {}, 合计产能: {}",
                sku.getMaterialCode(), candidates.size(), result);
        return result;
    }

    /**
     * 解析 SKU 在指定日期的目标排产量。
     * <p>非收尾场景目标量严格等于当天 dayPlanQty 扣除继承/锁定后的剩余量；
     * 收尾场景允许上调到胎胚库存，上调量放到最后一天。</p>
     *
     * @param context      排程上下文
     * @param sku          SKU排程DTO
     * @param dailyDemand  日维度需求
     * @param isEnding     是否收尾
     * @param isLastDay    是否为排程窗口最后一天
     * @param monthRemainQty 月计划剩余量（窗口级别）
     * @return 日目标排产量
     */
    public int resolveDailyTargetQty(LhScheduleContext context,
                                     SkuScheduleDTO sku,
                                     SkuDailyScheduleDemandDTO dailyDemand,
                                     boolean isEnding,
                                     boolean isLastDay,
                                     int monthRemainQty) {
        if (Objects.isNull(dailyDemand)) {
            return 0;
        }
        int baseTarget = Math.max(0, dailyDemand.getDayPlanQty()
                - dailyDemand.getInheritedQty()
                + dailyDemand.getCarryForwardQty());
        if (baseTarget <= 0) {
            return 0;
        }
        int targetQty = baseTarget;
        if (isEnding && isLastDay) {
            // 收尾最后一天：允许上调到胎胚库存，但不超过月计划余量
            int embryoStock = Math.max(0, sku.getEmbryoStock());
            // 前几天的窗口计划量总和
            int previousDaysTotal = resolvePreviousDaysTotalFromDailyDemand(sku.getDailyDemandList(), dailyDemand);
            int embryoUpgradeQty = Math.max(0, embryoStock - previousDaysTotal);
            targetQty = Math.max(baseTarget, Math.min(embryoUpgradeQty, monthRemainQty));
        }
        // 不超过月计划余量
        targetQty = Math.min(targetQty, Math.max(0, monthRemainQty));
        return Math.max(0, targetQty);
    }

    /**
     * 计算排在当前日之前的各日目标量之和。
     *
     * @param dailyDemandList 日需求列表
     * @param currentDemand   当前日需求
     * @return 之前各日目标量之和
     */
    private int resolvePreviousDaysTotalFromDailyDemand(List<SkuDailyScheduleDemandDTO> dailyDemandList,
                                                         SkuDailyScheduleDemandDTO currentDemand) {
        if (CollectionUtils.isEmpty(dailyDemandList) || Objects.isNull(currentDemand)) {
            return 0;
        }
        int total = 0;
        for (SkuDailyScheduleDemandDTO demand : dailyDemandList) {
            if (demand == currentDemand) {
                break;
            }
            total += Math.max(0, demand.getTargetQty());
        }
        return total;
    }

    /**
     * 收尾场景：升级最后一天的日剩余量，允许上调到胎胚库存。
     * <p>上调量 = max(0, embryoStock - 其他日期目标量之和)，上调量放到最后一天。
     * 非收尾场景不调用此方法。</p>
     *
     * @param context          排程上下文
     * @param sku              SKU排程DTO
     * @param dailyRemainingMap 日剩余量 Map（会被原地修改）
     */
    public void applyEndingDailyDemandUpgrade(LhScheduleContext context,
                                              SkuScheduleDTO sku,
                                              Map<Date, Integer> dailyRemainingMap) {
        if (Objects.isNull(sku) || dailyRemainingMap == null || dailyRemainingMap.isEmpty()) {
            return;
        }
        int embryoStock = Math.max(0, sku.getEmbryoStock());
        if (embryoStock <= 0) {
            return;
        }
        // 找到最后一天
        Date lastDate = null;
        for (Date date : dailyRemainingMap.keySet()) {
            if (lastDate == null || date.after(lastDate)) {
                lastDate = date;
            }
        }
        if (lastDate == null) {
            return;
        }
        // 计算除最后一天外其他日期的目标量之和
        int otherDaysTotal = 0;
        for (Map.Entry<Date, Integer> entry : dailyRemainingMap.entrySet()) {
            if (!entry.getKey().equals(lastDate)) {
                otherDaysTotal += entry.getValue() != null ? entry.getValue() : 0;
            }
        }
        Integer lastDayRemaining = dailyRemainingMap.get(lastDate);
        if (lastDayRemaining == null) {
            return;
        }
        // 上调量 = max(0, embryoStock - otherDaysTotal)，不超过月计划余量和多机台合计产能
        int monthRemainQty = Math.max(0, sku.getSurplusQty());
        int embryoUpgradeQty = Math.max(0, embryoStock - otherDaysTotal);
        embryoUpgradeQty = Math.min(embryoUpgradeQty, monthRemainQty);
        // 上调后总量不能超过多机台合计产能，否则上调无效
        int totalAvailableCapacity = calcSkuTotalAvailableCapacityInWindow(context, sku);
        if (totalAvailableCapacity > 0) {
            int availableForLastDay = Math.max(0, totalAvailableCapacity - otherDaysTotal);
            embryoUpgradeQty = Math.min(embryoUpgradeQty, availableForLastDay);
        }
        if (embryoUpgradeQty > lastDayRemaining) {
            dailyRemainingMap.put(lastDate, embryoUpgradeQty);
            log.info("SKU多机台收尾产能计算, materialCode: {}, 窗口计划量合计: {}, 月计划余量: {}, 胎胚库存: {}, "
                            + "之前日期合计: {}, 最后日期: {}, 原目标量: {}, 上调后: {}, 多机台合计产能: {}",
                    sku.getMaterialCode(), otherDaysTotal + lastDayRemaining, monthRemainQty, embryoStock,
                    otherDaysTotal, LhScheduleTimeUtil.formatDate(lastDate), lastDayRemaining,
                    embryoUpgradeQty, totalAvailableCapacity);
        } else {
            log.debug("SKU收尾无需上调, materialCode: {}, 余量: {}, 胎胚库存: {}, 最后日期目标量: {}, 上调量候选: {}",
                    sku.getMaterialCode(), monthRemainQty, embryoStock, lastDayRemaining, embryoUpgradeQty);
        }
    }

    /**
     * 按机台实际开产时间收敛目标排产量。
     *
     * @param context 排程上下文
     * @param sku SKU
     * @param machine 机台
     * @param productionStartTime 实际开产时间
     * @param shifts 排程窗口班次
     * @return 收敛后的目标排产量
     */
    public int refineTargetQtyByMachineCapacity(LhScheduleContext context,
                                                SkuScheduleDTO sku,
                                                MachineScheduleDTO machine,
                                                Date switchStartTime,
                                                Date productionStartTime,
                                                List<LhShiftConfigVO> shifts) {
        if (Objects.isNull(sku)) {
            return 0;
        }
        int currentTargetQty = sku.resolveTargetScheduleQty();
        if (currentTargetQty <= 0 || !isFullCapacityMode(context)) {
            return Math.max(currentTargetQty, 0);
        }
        int actualCapacityQty = resolveActualWindowCapacity(context, sku, machine, switchStartTime, productionStartTime, shifts);
        if (actualCapacityQty <= 0) {
            return 0;
        }
        return Math.min(currentTargetQty, actualCapacityQty);
    }

    /**
     * 判断当前是否为按产能满排模式。
     *
     * @param context 排程上下文
     * @return true-按产能满排，false-按需求排产
     */
    public boolean isFullCapacityMode(LhScheduleContext context) {
        LhScheduleConfig scheduleConfig = context != null ? context.getScheduleConfig() : null;
        if (Objects.isNull(scheduleConfig)) {
            return LhScheduleConstant.ENABLE_FULL_CAPACITY_SCHEDULING == 1;
        }
        return scheduleConfig.getScheduleTargetMode() == ScheduleTargetModeEnum.CAPACITY_FULL;
    }

    /**
     * 解析理论窗口产能上限。
     *
     * @param context 排程上下文
     * @param sku SKU
     * @return 理论窗口产能上限
     */
    private int resolveTheoreticalWindowCapacity(LhScheduleContext context, SkuScheduleDTO sku) {
        if (Objects.isNull(sku) || sku.getShiftCapacity() <= 0) {
            return Math.max(0, sku != null ? sku.getPendingQty() : 0);
        }
        List<LhShiftConfigVO> shifts = resolveScheduleShifts(context);
        if (CollectionUtils.isEmpty(shifts)) {
            return Math.max(0, sku.getPendingQty());
        }
        int mouldQty = ShiftCapacityResolverUtil.resolveMachineMouldQty(sku.getMouldQty());
        int totalCapacity = 0;
        for (LhShiftConfigVO shift : shifts) {
            ShiftProductionControlDTO control = ShiftProductionControlUtil.resolveEffectiveControl(
                    context, shift, shift.getShiftStartDateTime());
            if (Objects.isNull(control) || !control.isCanSchedule()) {
                continue;
            }
            long availableSeconds = (control.getEffectiveEndTime().getTime() - control.getEffectiveStartTime().getTime()) / 1000L;
            int shiftMaxQty = ShiftCapacityResolverUtil.resolveShiftCapacity(
                    sku.getShiftCapacity(),
                    sku.getLhTimeSeconds(),
                    mouldQty,
                    ShiftCapacityResolverUtil.resolveShiftDurationSeconds(shift),
                    availableSeconds);
            totalCapacity += ShiftProductionControlUtil.deductCapacityByControl(control, shiftMaxQty, mouldQty);
        }
        return Math.max(0, totalCapacity);
    }

    /**
     * 解析机台在剩余窗口内的实际可排产量。
     *
     * @param context 排程上下文
     * @param sku SKU
     * @param machine 机台
     * @param productionStartTime 实际开产时间
     * @param shifts 排程窗口班次
     * @return 实际可排产量
     */
    private int resolveActualWindowCapacity(LhScheduleContext context,
                                            SkuScheduleDTO sku,
                                            MachineScheduleDTO machine,
                                            Date switchStartTime,
                                            Date productionStartTime,
                                            List<LhShiftConfigVO> shifts) {
        if (Objects.isNull(context)
                || Objects.isNull(sku)
                || Objects.isNull(machine)
                || Objects.isNull(productionStartTime)
                || CollectionUtils.isEmpty(shifts)) {
            return 0;
        }
        int shiftCapacity = ShiftCapacityResolverUtil.resolveRuntimeShiftCapacity(
                context, machine, sku.getShiftCapacity());
        int lhTimeSeconds = sku.getLhTimeSeconds();
        if (shiftCapacity <= 0 && lhTimeSeconds <= 0) {
            return 0;
        }
        int mouldQty = ShiftCapacityResolverUtil.resolveMachineMouldQty(machine);
        Date firstProductionStartTime = ShiftProductionControlUtil.resolveFirstSchedulableStartIgnoringCleaning(
                context,
                machine.getMachineCode(),
                productionStartTime,
                shifts,
                shiftCapacity,
                lhTimeSeconds,
                mouldQty);
        if (firstProductionStartTime == null) {
            return 0;
        }
        List<MachineCleaningWindowDTO> cleaningWindowList = resolveEffectiveCleaningWindowList(
                machine, switchStartTime, firstProductionStartTime);
        int dryIceLossQty = context.getParamIntValue(
                LhScheduleParamConstant.DRY_ICE_LOSS_QTY, LhScheduleConstant.DRY_ICE_LOSS_QTY);
        int dryIceDurationHours = context.getParamIntValue(
                LhScheduleParamConstant.DRY_ICE_DURATION_HOURS, LhScheduleConstant.DRY_ICE_DURATION_HOURS);

        Date cursorStartTime = firstProductionStartTime;
        int totalQty = 0;
        boolean started = false;
        for (LhShiftConfigVO shift : shifts) {
            if (!started) {
                if (cursorStartTime.before(shift.getShiftEndDateTime())) {
                    started = true;
                } else {
                    continue;
                }
            }
            ShiftProductionControlDTO control = ShiftProductionControlUtil.resolveEffectiveControl(context, shift, cursorStartTime);
            if (Objects.isNull(control) || !control.isCanSchedule()) {
                continue;
            }
            Date effectiveStartTime = control.getEffectiveStartTime();
            Date effectiveEndTime = control.getEffectiveEndTime();
            int shiftMaxQty = ShiftCapacityResolverUtil.resolveShiftCapacityWithDowntime(
                    context.getDevicePlanShutList(),
                    cleaningWindowList,
                    machine.getMachineCode(),
                    effectiveStartTime,
                    effectiveEndTime,
                    shiftCapacity,
                    lhTimeSeconds,
                    mouldQty,
                    ShiftCapacityResolverUtil.resolveShiftDurationSeconds(shift),
                    dryIceLossQty,
                    dryIceDurationHours);
            shiftMaxQty = ShiftProductionControlUtil.deductCapacityByControl(control, shiftMaxQty, mouldQty);
            if (shiftMaxQty <= 0) {
                continue;
            }
            totalQty += shiftMaxQty;
            cursorStartTime = effectiveEndTime;
        }
        return Math.max(totalQty, 0);
    }

    /**
     * 解析用于排产估算的清洗窗口。
     *
     * @param machine 机台
     * @param switchStartTime 切换开始时间
     * @param firstProductionStartTime 首个可排产开始时间
     * @return 有效清洗窗口列表
     */
    private List<MachineCleaningWindowDTO> resolveEffectiveCleaningWindowList(MachineScheduleDTO machine,
                                                                              Date switchStartTime,
                                                                              Date firstProductionStartTime) {
        if (Objects.isNull(machine) || CollectionUtils.isEmpty(machine.getCleaningWindowList())) {
            return new ArrayList<>(0);
        }
        return new ArrayList<>(MachineCleaningOverlapUtil.excludeOverlapWindows(
                machine.getCleaningWindowList(), switchStartTime, firstProductionStartTime));
    }

    /**
     * 获取排程窗口班次。
     *
     * @param context 排程上下文
     * @return 班次列表
     */
    private List<LhShiftConfigVO> resolveScheduleShifts(LhScheduleContext context) {
        if (Objects.isNull(context)) {
            return new ArrayList<>(0);
        }
        if (!CollectionUtils.isEmpty(context.getScheduleWindowShifts())) {
            return context.getScheduleWindowShifts();
        }
        if (Objects.isNull(context.getScheduleDate())) {
            return new ArrayList<>(0);
        }
        return LhScheduleTimeUtil.buildDefaultScheduleShifts(context, context.getScheduleDate());
    }

    /**
     * 获取机台匹配策略（防御性获取）。
     *
     * @return 机台匹配策略
     */
    private IMachineMatchStrategy getMachineMatchStrategy() {
        return machineMatchStrategy;
    }
}
