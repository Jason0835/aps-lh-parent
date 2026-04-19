/**
 * Copyright (c) 2008, 智立通（厦门）科技有限公司 All rights reserved。
 */
package com.zlt.aps.lh.engine.strategy.impl;

import com.zlt.aps.lh.api.constant.LhScheduleConstant;
import com.zlt.aps.lh.api.constant.LhScheduleParamConstant;
import com.zlt.aps.lh.api.domain.dto.MachineCleaningWindowDTO;
import com.zlt.aps.lh.api.domain.dto.MachineScheduleDTO;
import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.api.domain.vo.LhShiftConfigVO;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.engine.strategy.ICapacityCalculateStrategy;
import com.zlt.aps.lh.engine.strategy.IFirstInspectionBalanceStrategy;
import com.zlt.aps.lh.engine.strategy.IMachineMatchStrategy;
import com.zlt.aps.lh.engine.strategy.IMouldChangeBalanceStrategy;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import com.zlt.aps.lh.util.ShiftCapacityResolverUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 局部搜索机台分配器
 * <p>用于在小规模候选机台场景下，基于 DFS + 剪枝选择当前 SKU 的首选机台。</p>
 *
 * @author APS
 */
@Slf4j
@Component
public class LocalSearchMachineAllocatorStrategy {

    /** 每分钟毫秒数 */
    private static final long MILLIS_PER_MINUTE = 60_000L;
    /** 不可行分支的惩罚分 */
    private static final long INFEASIBLE_PENALTY = 1_000_000L;

    /**
     * 选择当前 SKU 的首选机台。
     *
     * @param context 排程上下文
     * @param windowSkuList 局部搜索 SKU 窗口（第 0 个必须为当前 SKU）
     * @param currentCandidates 当前 SKU 的候选机台列表
     * @param shifts 排程班次窗口
     * @param machineMatch 机台匹配策略
     * @param mouldChangeBalance 换模均衡策略
     * @param inspectionBalance 首检均衡策略
     * @param capacityCalculate 产能计算策略
     * @return 选出的首选机台；若无法稳定给出则返回 null
     */
    public MachineScheduleDTO selectBestMachine(LhScheduleContext context,
                                                List<SkuScheduleDTO> windowSkuList,
                                                List<MachineScheduleDTO> currentCandidates,
                                                List<LhShiftConfigVO> shifts,
                                                IMachineMatchStrategy machineMatch,
                                                IMouldChangeBalanceStrategy mouldChangeBalance,
                                                IFirstInspectionBalanceStrategy inspectionBalance,
                                                ICapacityCalculateStrategy capacityCalculate) {
        // 基础输入不完整时直接放弃局部搜索，交给外层常规选机逻辑兜底
        if (CollectionUtils.isEmpty(windowSkuList) || CollectionUtils.isEmpty(currentCandidates) || CollectionUtils.isEmpty(shifts)) {
            return null;
        }
        // 设置搜索时间预算，避免局部搜索阻塞主排程流程
        long deadlineMs = System.currentTimeMillis() + context.getScheduleConfig().getLocalSearchTimeBudgetMs();
        int[] bestFeasibleCountHolder = new int[]{-1};
        long[] bestPenaltyHolder = new long[]{Long.MAX_VALUE};
        String[] bestFirstMachineCodeHolder = new String[]{null};

        Map<String, Date> virtualMachineEndTimeMap = new HashMap<>(16);
        List<LocalSearchReservationToken> reservationStack = new ArrayList<>(16);

        dfsSelect(context, windowSkuList, 0, currentCandidates, machineMatch, mouldChangeBalance, inspectionBalance,
                capacityCalculate, shifts, virtualMachineEndTimeMap, reservationStack, 0, 0L, null, deadlineMs,
                bestFeasibleCountHolder, bestPenaltyHolder, bestFirstMachineCodeHolder);

        // DFS 只记录首台机台编码，这里回填对应机台对象给上层使用
        if (StringUtils.isEmpty(bestFirstMachineCodeHolder[0])) {
            return null;
        }
        for (MachineScheduleDTO machine : currentCandidates) {
            if (machine != null && bestFirstMachineCodeHolder[0].equals(machine.getMachineCode())) {
                log.debug("局部搜索选机完成, 首选机台: {}, 可行数: {}, 评分: {}",
                        bestFirstMachineCodeHolder[0], bestFeasibleCountHolder[0], bestPenaltyHolder[0]);
                return machine;
            }
        }
        return null;
    }

    /**
     * 深度优先搜索选择首选机台。
     *
     * @param context 排程上下文
     * @param windowSkuList 局部搜索 SKU 窗口
     * @param level 当前层级
     * @param firstLevelCandidates 第 0 层候选机台
     * @param machineMatch 机台匹配策略
     * @param mouldChangeBalance 换模均衡策略
     * @param inspectionBalance 首检均衡策略
     * @param capacityCalculate 产能计算策略
     * @param shifts 排程班次窗口
     * @param virtualMachineEndTimeMap 虚拟机台结束时间
     * @param reservationStack 预占资源栈
     * @param currentFeasibleCount 当前已排可行数量
     * @param currentPenalty 当前累计惩罚分
     * @param firstMachineCode 分支首台机台编码
     * @param deadlineMs 搜索截止时间
     * @param bestFeasibleCountHolder 当前最优可行数量
     * @param bestPenaltyHolder 当前最优惩罚分
     * @param bestFirstMachineCodeHolder 当前最优首台机台编码
     * @return void
     */
    private void dfsSelect(LhScheduleContext context,
                           List<SkuScheduleDTO> windowSkuList,
                           int level,
                           List<MachineScheduleDTO> firstLevelCandidates,
                           IMachineMatchStrategy machineMatch,
                           IMouldChangeBalanceStrategy mouldChangeBalance,
                           IFirstInspectionBalanceStrategy inspectionBalance,
                           ICapacityCalculateStrategy capacityCalculate,
                           List<LhShiftConfigVO> shifts,
                           Map<String, Date> virtualMachineEndTimeMap,
                           List<LocalSearchReservationToken> reservationStack,
                           int currentFeasibleCount,
                           long currentPenalty,
                           String firstMachineCode,
                           long deadlineMs,
                           int[] bestFeasibleCountHolder,
                           long[] bestPenaltyHolder,
                           String[] bestFirstMachineCodeHolder) {
        // 超时立即停止，确保在时间预算内返回
        if (System.currentTimeMillis() > deadlineMs) {
            return;
        }
        int totalLevel = windowSkuList.size();
        // 到达叶子节点后尝试更新最优解
        if (level >= totalLevel) {
            updateBestResult(currentFeasibleCount, currentPenalty, firstMachineCode,
                    bestFeasibleCountHolder, bestPenaltyHolder, bestFirstMachineCodeHolder);
            return;
        }

        // 剪枝：理论可行上限已落后于当前最优时，直接跳过该分支
        int maxFeasibleUpperBound = currentFeasibleCount + (totalLevel - level);
        if (maxFeasibleUpperBound < bestFeasibleCountHolder[0]) {
            return;
        }
        if (maxFeasibleUpperBound == bestFeasibleCountHolder[0] && currentPenalty >= bestPenaltyHolder[0]) {
            return;
        }

        SkuScheduleDTO currentSku = windowSkuList.get(level);
        List<MachineScheduleDTO> candidates = level == 0 ? firstLevelCandidates : machineMatch.matchMachines(context, currentSku);
        if (CollectionUtils.isEmpty(candidates)) {
            // 本层无可行机台，按不可行分支处理并继续探索后续层
            dfsSelect(context, windowSkuList, level + 1, firstLevelCandidates, machineMatch, mouldChangeBalance,
                    inspectionBalance, capacityCalculate, shifts, virtualMachineEndTimeMap, reservationStack,
                    currentFeasibleCount, currentPenalty + INFEASIBLE_PENALTY, firstMachineCode, deadlineMs,
                    bestFeasibleCountHolder, bestPenaltyHolder, bestFirstMachineCodeHolder);
            return;
        }

        boolean hasFeasibleBranch = false;
        for (int i = 0; i < candidates.size(); i++) {
            if (System.currentTimeMillis() > deadlineMs) {
                break;
            }
            MachineScheduleDTO candidateMachine = candidates.get(i);
            LocalSearchReservationToken token = reserveAssignment(context, currentSku, candidateMachine,
                    mouldChangeBalance, inspectionBalance, capacityCalculate, shifts, virtualMachineEndTimeMap, i);
            if (token == null) {
                continue;
            }
            hasFeasibleBranch = true;
            String machineCode = token.getMachineCode();
            Date previousEndTime = virtualMachineEndTimeMap.put(machineCode, token.getSpecEndTime());
            reservationStack.add(token);

            String nextFirstMachineCode = StringUtils.isEmpty(firstMachineCode) ? machineCode : firstMachineCode;
            dfsSelect(context, windowSkuList, level + 1, firstLevelCandidates, machineMatch, mouldChangeBalance,
                    inspectionBalance, capacityCalculate, shifts, virtualMachineEndTimeMap, reservationStack,
                    currentFeasibleCount + 1, currentPenalty + token.getPenaltyScore(), nextFirstMachineCode, deadlineMs,
                    bestFeasibleCountHolder, bestPenaltyHolder, bestFirstMachineCodeHolder);

            // 回溯：撤销本层预占，恢复现场后继续尝试下一个候选机台
            rollbackReservation(context, token, mouldChangeBalance, inspectionBalance);
            reservationStack.remove(reservationStack.size() - 1);
            if (previousEndTime == null) {
                virtualMachineEndTimeMap.remove(machineCode);
            } else {
                virtualMachineEndTimeMap.put(machineCode, previousEndTime);
            }
        }

        if (!hasFeasibleBranch) {
            dfsSelect(context, windowSkuList, level + 1, firstLevelCandidates, machineMatch, mouldChangeBalance,
                    inspectionBalance, capacityCalculate, shifts, virtualMachineEndTimeMap, reservationStack,
                    currentFeasibleCount, currentPenalty + INFEASIBLE_PENALTY, firstMachineCode, deadlineMs,
                    bestFeasibleCountHolder, bestPenaltyHolder, bestFirstMachineCodeHolder);
        }
    }

    /**
     * 更新当前最优解。
     *
     * @param feasibleCount 可行数量
     * @param penaltyScore 惩罚分
     * @param firstMachineCode 首台机台编码
     * @param bestFeasibleCountHolder 最优可行数量
     * @param bestPenaltyHolder 最优惩罚分
     * @param bestFirstMachineCodeHolder 最优首台机台编码
     * @return void
     */
    private void updateBestResult(int feasibleCount,
                                  long penaltyScore,
                                  String firstMachineCode,
                                  int[] bestFeasibleCountHolder,
                                  long[] bestPenaltyHolder,
                                  String[] bestFirstMachineCodeHolder) {
        if (StringUtils.isEmpty(firstMachineCode)) {
            return;
        }
        if (feasibleCount > bestFeasibleCountHolder[0]
                || (feasibleCount == bestFeasibleCountHolder[0] && penaltyScore < bestPenaltyHolder[0])) {
            bestFeasibleCountHolder[0] = feasibleCount;
            bestPenaltyHolder[0] = penaltyScore;
            bestFirstMachineCodeHolder[0] = firstMachineCode;
        }
    }

    /**
     * 预占单个 SKU 在某机台上的关键资源。
     *
     * @param context 排程上下文
     * @param sku 待排 SKU
     * @param machine 候选机台
     * @param mouldChangeBalance 换模均衡策略
     * @param inspectionBalance 首检均衡策略
     * @param capacityCalculate 产能计算策略
     * @param shifts 排程班次窗口
     * @param virtualMachineEndTimeMap 虚拟机台结束时间
     * @param machineRank 候选排序位次（越小越优）
     * @return 预占令牌；不可行返回 null
     */
    private LocalSearchReservationToken reserveAssignment(LhScheduleContext context,
                                                          SkuScheduleDTO sku,
                                                          MachineScheduleDTO machine,
                                                          IMouldChangeBalanceStrategy mouldChangeBalance,
                                                          IFirstInspectionBalanceStrategy inspectionBalance,
                                                          ICapacityCalculateStrategy capacityCalculate,
                                                          List<LhShiftConfigVO> shifts,
                                                          Map<String, Date> virtualMachineEndTimeMap,
                                                          int machineRank) {
        if (machine == null || StringUtils.isEmpty(machine.getMachineCode())) {
            return null;
        }
        String machineCode = machine.getMachineCode();
        // 按“机台准备 -> 换模 -> 首检 -> 产能估算”的顺序串行预占资源
        Date machineEndTime = resolveMachineEndTime(machine, virtualMachineEndTimeMap);
        Date machineReadyTime = capacityCalculate.calculateStartTime(context, machineCode, machineEndTime);
        Date mouldChangeStartTime = mouldChangeBalance.allocateMouldChange(context, machineReadyTime);
        if (mouldChangeStartTime == null) {
            return null;
        }
        Date mouldChangeCompleteTime = LhScheduleTimeUtil.addHours(
                mouldChangeStartTime, LhScheduleTimeUtil.getMouldChangeTotalHours(context));
        Date inspectionTime = inspectionBalance.allocateInspection(context, machineCode, mouldChangeCompleteTime);
        if (inspectionTime == null) {
            // 首检失败需要回滚换模，避免污染全局资源状态
            mouldChangeBalance.rollbackMouldChange(context, mouldChangeStartTime);
            return null;
        }
        Date productionStartTime = LhScheduleTimeUtil.addHours(inspectionTime, LhScheduleTimeUtil.getFirstInspectionHours(context));
        LocalSearchCapacityEstimate capacityEstimate = estimateCapacity(
                context, sku, machine, productionStartTime, shifts);
        if (capacityEstimate.getTotalQty() <= 0 || capacityEstimate.getSpecEndTime() == null) {
            // 产能不可行时回滚已占用的首检/换模窗口
            inspectionBalance.rollbackInspection(context, inspectionTime);
            mouldChangeBalance.rollbackMouldChange(context, mouldChangeStartTime);
            return null;
        }
        long penaltyScore = capacityEstimate.getPenaltyScore() + machineRank;
        return new LocalSearchReservationToken(machineCode, mouldChangeStartTime, inspectionTime,
                capacityEstimate.getSpecEndTime(), penaltyScore);
    }

    /**
     * 回滚预占资源。
     *
     * @param context 排程上下文
     * @param token 预占令牌
     * @param mouldChangeBalance 换模均衡策略
     * @param inspectionBalance 首检均衡策略
     * @return void
     */
    private void rollbackReservation(LhScheduleContext context,
                                     LocalSearchReservationToken token,
                                     IMouldChangeBalanceStrategy mouldChangeBalance,
                                     IFirstInspectionBalanceStrategy inspectionBalance) {
        if (token == null) {
            return;
        }
        inspectionBalance.rollbackInspection(context, token.getInspectionTime());
        mouldChangeBalance.rollbackMouldChange(context, token.getMouldChangeStartTime());
    }

    /**
     * 估算从指定开产时间起在班次窗口内的可排产量与收尾时间。
     *
     * @param context 排程上下文
     * @param sku 待排 SKU
     * @param machine 候选机台
     * @param productionStartTime 开产时间
     * @param shifts 排程班次窗口
     * @return 产能估算结果
     */
    private LocalSearchCapacityEstimate estimateCapacity(LhScheduleContext context,
                                                         SkuScheduleDTO sku,
                                                         MachineScheduleDTO machine,
                                                         Date productionStartTime,
                                                         List<LhShiftConfigVO> shifts) {
        if (sku == null || machine == null || productionStartTime == null || CollectionUtils.isEmpty(shifts)) {
            return LocalSearchCapacityEstimate.empty();
        }
        int lhTimeSeconds = sku.getLhTimeSeconds();
        int mouldQty = ShiftCapacityResolverUtil.resolveMachineMouldQty(machine);
        int shiftCapacity = sku.getShiftCapacity();
        int remainingQty = sku.getPendingQty() > 0 ? sku.getPendingQty() : sku.getWindowPlanQty();
        if (lhTimeSeconds <= 0 || remainingQty <= 0) {
            return LocalSearchCapacityEstimate.empty();
        }
        List<MachineCleaningWindowDTO> cleaningWindowList = CollectionUtils.isEmpty(machine.getCleaningWindowList())
                ? new ArrayList<>() : machine.getCleaningWindowList();
        int dryIceLossQty = context.getParamIntValue(
                LhScheduleParamConstant.DRY_ICE_LOSS_QTY, LhScheduleConstant.DRY_ICE_LOSS_QTY);
        int dryIceDurationHours = context.getParamIntValue(
                LhScheduleParamConstant.DRY_ICE_DURATION_HOURS, LhScheduleConstant.DRY_ICE_DURATION_HOURS);

        Date cursorStartTime = productionStartTime;
        Date specEndTime = null;
        int totalQty = 0;
        boolean started = false;

        for (LhShiftConfigVO shift : shifts) {
            if (remainingQty <= 0) {
                break;
            }
            if (!started) {
                if (cursorStartTime.before(shift.getShiftEndDateTime())) {
                    started = true;
                } else {
                    continue;
                }
            }
            Date effectiveStartTime = cursorStartTime.after(shift.getShiftStartDateTime())
                    ? cursorStartTime : shift.getShiftStartDateTime();
            if (!effectiveStartTime.before(shift.getShiftEndDateTime())) {
                continue;
            }

            // 统一按班产主口径或回退公式估算残班/整班计划量。
            int shiftMaxQty = ShiftCapacityResolverUtil.resolveShiftCapacityWithDowntime(
                    context.getDevicePlanShutList(),
                    cleaningWindowList,
                    machine.getMachineCode(),
                    effectiveStartTime,
                    shift.getShiftEndDateTime(),
                    shiftCapacity,
                    lhTimeSeconds,
                    mouldQty,
                    ShiftCapacityResolverUtil.resolveShiftDurationSeconds(shift),
                    dryIceLossQty,
                    dryIceDurationHours);
            if (shiftMaxQty <= 0) {
                continue;
            }

            int allocationQty = Math.min(remainingQty, shiftMaxQty);
            if (allocationQty <= 0) {
                continue;
            }
            totalQty += allocationQty;
            remainingQty -= allocationQty;
            specEndTime = ShiftCapacityResolverUtil.resolveShiftPlanEndTime(
                    context.getDevicePlanShutList(),
                    cleaningWindowList,
                    machine.getMachineCode(),
                    effectiveStartTime,
                    shift.getShiftEndDateTime(),
                    allocationQty,
                    shiftMaxQty);
            // 当前班次结束后再推进到下一班次，避免跨班次重叠计算
            cursorStartTime = shift.getShiftEndDateTime();
        }

        if (totalQty <= 0 || specEndTime == null) {
            return LocalSearchCapacityEstimate.empty();
        }
        long penaltyScore = productionStartTime.getTime() / MILLIS_PER_MINUTE + (long) remainingQty * INFEASIBLE_PENALTY;
        return new LocalSearchCapacityEstimate(totalQty, specEndTime, penaltyScore);
    }

    /**
     * 获取机台计算用结束时间（优先使用虚拟状态）。
     *
     * @param machine 候选机台
     * @param virtualMachineEndTimeMap 虚拟机台结束时间
     * @return 机台结束时间
     */
    private Date resolveMachineEndTime(MachineScheduleDTO machine, Map<String, Date> virtualMachineEndTimeMap) {
        Date virtualEndTime = virtualMachineEndTimeMap.get(machine.getMachineCode());
        if (virtualEndTime != null) {
            return virtualEndTime;
        }
        if (machine.getEstimatedEndTime() != null) {
            return machine.getEstimatedEndTime();
        }
        return new Date();
    }
}

/**
 * 局部搜索预占令牌
 *
 * @author APS
 */
final class LocalSearchReservationToken {

    private final String machineCode;
    private final Date mouldChangeStartTime;
    private final Date inspectionTime;
    private final Date specEndTime;
    private final long penaltyScore;

    LocalSearchReservationToken(String machineCode, Date mouldChangeStartTime, Date inspectionTime,
                                Date specEndTime, long penaltyScore) {
        this.machineCode = machineCode;
        this.mouldChangeStartTime = mouldChangeStartTime;
        this.inspectionTime = inspectionTime;
        this.specEndTime = specEndTime;
        this.penaltyScore = penaltyScore;
    }

    String getMachineCode() {
        return machineCode;
    }

    Date getMouldChangeStartTime() {
        return mouldChangeStartTime;
    }

    Date getInspectionTime() {
        return inspectionTime;
    }

    Date getSpecEndTime() {
        return specEndTime;
    }

    long getPenaltyScore() {
        return penaltyScore;
    }
}

/**
 * 局部搜索产能估算结果
 *
 * @author APS
 */
final class LocalSearchCapacityEstimate {

    private final int totalQty;
    private final Date specEndTime;
    private final long penaltyScore;

    LocalSearchCapacityEstimate(int totalQty, Date specEndTime, long penaltyScore) {
        this.totalQty = totalQty;
        this.specEndTime = specEndTime;
        this.penaltyScore = penaltyScore;
    }

    static LocalSearchCapacityEstimate empty() {
        return new LocalSearchCapacityEstimate(0, null, Long.MAX_VALUE);
    }

    int getTotalQty() {
        return totalQty;
    }

    Date getSpecEndTime() {
        return specEndTime;
    }

    long getPenaltyScore() {
        return penaltyScore;
    }
}
