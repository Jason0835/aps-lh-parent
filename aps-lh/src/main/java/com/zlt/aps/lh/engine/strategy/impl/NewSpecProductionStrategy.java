/**
 * Copyright (c) 2008, 智立通（厦门）科技有限公司 All rights reserved。
 */
package com.zlt.aps.lh.engine.strategy.impl;

import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.context.LhScheduleConfig;
import com.zlt.aps.lh.api.domain.dto.MachineScheduleDTO;
import com.zlt.aps.lh.api.domain.vo.LhShiftConfigVO;
import com.zlt.aps.lh.api.domain.dto.ShiftRuntimeState;
import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.api.domain.entity.LhScheduleResult;
import com.zlt.aps.lh.api.domain.entity.LhUnscheduledResult;
import com.zlt.aps.lh.api.enums.NewSpecFailReasonEnum;
import com.zlt.aps.lh.api.enums.ScheduleTypeEnum;
import com.zlt.aps.lh.engine.strategy.ICapacityCalculateStrategy;
import com.zlt.aps.lh.engine.strategy.IFirstInspectionBalanceStrategy;
import com.zlt.aps.lh.engine.strategy.IMachineMatchStrategy;
import com.zlt.aps.lh.engine.strategy.IMouldChangeBalanceStrategy;
import com.zlt.aps.lh.engine.strategy.IProductionStrategy;
import com.zlt.aps.lh.util.ShiftFieldUtil;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import com.zlt.aps.lh.util.ShiftCapacityResolverUtil;
import com.zlt.aps.lh.util.SingleMouldShiftQtyUtil;
import com.zlt.aps.lh.component.OrderNoGenerator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 新增规格排产策略实现
 * <p>处理新增规格上机的排产逻辑, 包括机台匹配、换模均衡、首检分配、产能计算等</p>
 *
 * @author APS
 */
@Slf4j
@Component("newSpecProductionStrategy")
public class NewSpecProductionStrategy implements IProductionStrategy {

    @Resource
    private OrderNoGenerator orderNoGenerator;
    @Resource
    private LocalSearchMachineAllocatorStrategy localSearchMachineAllocator;

    @Override
    public String getStrategyType() {
        return ScheduleTypeEnum.NEW_SPEC.getCode();
    }

    @Override
    public String getStrategyName() {
        return "newSpecProductionStrategy";
    }

    @Override
    public void scheduleTypeBlockChange(LhScheduleContext context) {
        // 新增策略不处理活字块，空实现
    }

    @Override
    public void scheduleContinuousEnding(LhScheduleContext context) {
        // 新增策略不处理续作收尾，空实现
    }

    @Override
    public void allocateShiftPlanQty(LhScheduleContext context) {
        log.info("新增排产 - 班次计划量分配, 新增排程结果数: {}",
                context.getScheduleResultList().stream().filter(r -> "02".equals(r.getScheduleType())).count());
        // 班次计划量已在scheduleNewSpecs中随生成结果时分配完毕，此处为空实现
    }

    @Override
    public void adjustEmbryoStock(LhScheduleContext context) {
        log.info("新增排产 - 胎胚库存调整");
        // 新增SKU的胎胚库存调整
        for (LhScheduleResult result : context.getScheduleResultList()) {
            if (!"02".equals(result.getScheduleType())) {
                continue;
            }
            // 检查胎胚库存是否满足，若不足则削减计划量
            String embryoCode = result.getEmbryoCode();
            if (embryoCode == null) {
                continue;
            }
            SkuScheduleDTO sku = findSkuDto(context, result.getMaterialCode());
            if (sku == null || sku.getEmbryoStock() < 0) {
                continue;
            }
            int totalPlan = ShiftFieldUtil.resolveScheduledQty(result);
            if (totalPlan > sku.getEmbryoStock()) {
                // 库存不足，按比例削减各班次计划量
                double ratio = (double) sku.getEmbryoStock() / totalPlan;
                scaleShiftPlanQty(context, result, ratio);
                refreshResultSummary(context, result);
            }
        }
    }

    @Override
    public void scheduleReduceMould(LhScheduleContext context) {
        // 新增策略不处理降模，空实现
    }

    @Override
    public void scheduleNewSpecs(LhScheduleContext context,
                                 IMachineMatchStrategy machineMatch,
                                 IMouldChangeBalanceStrategy mouldChangeBalance,
                                 IFirstInspectionBalanceStrategy inspectionBalance,
                                 ICapacityCalculateStrategy capacityCalculate) {
        log.info("新增排产 - 执行新增规格排产, 新增SKU数: {}", context.getNewSpecSkuList().size());

        List<LhShiftConfigVO> shifts = LhScheduleTimeUtil.getScheduleShifts(context, context.getScheduleDate());
        int scheduledCount = 0;
        Map<String, Integer> unscheduledReasonCountMap = new LinkedHashMap<>(8);

        Iterator<SkuScheduleDTO> iterator = context.getNewSpecSkuList().iterator();
        while (iterator.hasNext()) {
            SkuScheduleDTO sku = iterator.next();

            // 1. 匹配候选机台
            List<MachineScheduleDTO> candidates = machineMatch.matchMachines(context, sku);
            if (candidates.isEmpty()) {
                addUnscheduledResult(context, sku, "无可用硫化机台", unscheduledReasonCountMap);
                iterator.remove();
                continue;
            }

            // 1.1 小规模候选机台场景下，优先使用局部搜索给出首选机台
            MachineScheduleDTO preferredMachine = selectPreferredMachineByLocalSearch(
                    context, sku, candidates, shifts, machineMatch, mouldChangeBalance, inspectionBalance, capacityCalculate);
            boolean preferredMachineTried = false;

            // 2. 基于策略选择最优机台，失败后排除并继续选择下一台
            boolean scheduled = false;
            NewSpecFailReasonEnum failReason = NewSpecFailReasonEnum.MACHINE_SELECTION_FAILED;
            Set<String> excludedMachineCodes = new HashSet<>(candidates.size());
            while (true) {
                MachineScheduleDTO candidateMachine;
                if (!preferredMachineTried && preferredMachine != null
                        && StringUtils.isNotEmpty(preferredMachine.getMachineCode())
                        && !excludedMachineCodes.contains(preferredMachine.getMachineCode())) {
                    candidateMachine = preferredMachine;
                    preferredMachineTried = true;
                } else {
                    candidateMachine = machineMatch.selectBestMachine(
                            context, sku, candidates, excludedMachineCodes);
                }
                if (candidateMachine == null) {
                    break;
                }
                String machineCode = candidateMachine.getMachineCode();
                if (StringUtils.isEmpty(machineCode)) {
                    failReason = selectHigherPriorityFailReason(
                            failReason, NewSpecFailReasonEnum.MACHINE_SELECTION_FAILED);
                    break;
                }

                // 3. 计算机台可开工时间（考虑机台当前预计完工和能力策略约束）
                Date endingTime = candidateMachine.getEstimatedEndTime() != null
                        ? candidateMachine.getEstimatedEndTime() : new Date();
                Date machineReadyTime = capacityCalculate.calculateStartTime(context,
                        machineCode, endingTime);

                // 4. 先分配换模窗口，失败则继续尝试下一台候选机台
                Date mouldChangeStartTime = mouldChangeBalance.allocateMouldChange(context, machineReadyTime);
                if (mouldChangeStartTime == null) {
                    excludedMachineCodes.add(machineCode);
                    failReason = selectHigherPriorityFailReason(
                            failReason, NewSpecFailReasonEnum.MOULD_CHANGE_SHIFT_ALLOCATE_FAILED);
                    continue;
                }

                // 5. 换模完成后分配首检窗口，若失败需回滚已占用换模资源
                Date mouldChangeCompleteTime = LhScheduleTimeUtil.addHours(
                        mouldChangeStartTime, LhScheduleTimeUtil.getMouldChangeTotalHours(context));
                Date inspectionTime = inspectionBalance.allocateInspection(context,
                        machineCode, mouldChangeCompleteTime);
                if (inspectionTime == null) {
                    mouldChangeBalance.rollbackMouldChange(context, mouldChangeStartTime);
                    excludedMachineCodes.add(machineCode);
                    failReason = selectHigherPriorityFailReason(
                            failReason, NewSpecFailReasonEnum.FIRST_INSPECTION_SHIFT_ALLOCATE_FAILED);
                    continue;
                }

                // 6. 基于首检完成时间生成新增规格排产结果，并校验当日是否有有效产能
                Date productionStartTime = LhScheduleTimeUtil.addHours(
                        inspectionTime, LhScheduleTimeUtil.getFirstInspectionHours(context));
                int machineMouldQty = ShiftCapacityResolverUtil.resolveMachineMouldQty(candidateMachine);
                LhScheduleResult result = buildNewSpecScheduleResult(
                        context, candidateMachine, sku, productionStartTime, mouldChangeStartTime,
                        mouldChangeCompleteTime, shifts, machineMouldQty);
                if (result == null || result.getDailyPlanQty() == null || result.getDailyPlanQty() <= 0) {
                    // 无有效产能时回滚首检和换模占用，避免影响后续SKU排产
                    inspectionBalance.rollbackInspection(context, inspectionTime);
                    mouldChangeBalance.rollbackMouldChange(context, mouldChangeStartTime);
                    excludedMachineCodes.add(machineCode);
                    failReason = selectHigherPriorityFailReason(
                            failReason, NewSpecFailReasonEnum.NO_CAPACITY_IN_SCHEDULE_WINDOW);
                    continue;
                }

                // 7. 排产成功后落地结果并刷新机台状态，当前SKU结束尝试
                sku.setMouldQty(machineMouldQty);
                context.getScheduleResultList().add(result);
                updateMachineState(context, candidateMachine, sku, result);
                registerMachineAssignment(context, machineCode, result);
                scheduledCount++;
                iterator.remove();
                scheduled = true;
                log.debug("新增排产完成, SKU: {}, 机台: {}, 机台就绪: {}, 换模开始: {}, 换模结束: {}, 首检开始: {}, 开产时间: {}",
                        sku.getMaterialCode(), machineCode,
                        LhScheduleTimeUtil.formatDateTime(machineReadyTime),
                        LhScheduleTimeUtil.formatDateTime(mouldChangeStartTime),
                        LhScheduleTimeUtil.formatDateTime(mouldChangeCompleteTime),
                        LhScheduleTimeUtil.formatDateTime(inspectionTime),
                        LhScheduleTimeUtil.formatDateTime(productionStartTime));
                break;
            }

            if (!scheduled) {
                // 所有候选机台都失败，记录未排产原因并移出待排队列
                addUnscheduledResult(context, sku, failReason.getDescription(), unscheduledReasonCountMap);
                iterator.remove();
            }
        }
        log.info("新增排产完成, 成功: {}, 未排: {}, 原因分布: {}",
                scheduledCount,
                unscheduledReasonCountMap.values().stream().mapToInt(Integer::intValue).sum(),
                unscheduledReasonCountMap);
    }

    /**
     * 选择优先级更高的失败原因，便于保留最接近真实阻塞点的未排产原因。
     *
     * @param currentReason 当前失败原因
     * @param candidateReason 新候选失败原因
     * @return 优先级更高的失败原因
     */
    private NewSpecFailReasonEnum selectHigherPriorityFailReason(NewSpecFailReasonEnum currentReason,
                                                                 NewSpecFailReasonEnum candidateReason) {
        return candidateReason.getPriority() >= currentReason.getPriority()
                ? candidateReason : currentReason;
    }

    /**
     * 使用局部搜索选择当前SKU的首选机台。
     * <p>若配置关闭、阈值不命中或搜索失败，返回null并自动回退原贪心流程。</p>
     *
     * @param context 排程上下文
     * @param currentSku 当前SKU
     * @param candidates 候选机台
     * @param shifts 排程班次窗口
     * @param machineMatch 机台匹配策略
     * @param mouldChangeBalance 换模均衡策略
     * @param inspectionBalance 首检均衡策略
     * @param capacityCalculate 产能计算策略
     * @return 局部搜索首选机台；无法给出时返回null
     */
    private MachineScheduleDTO selectPreferredMachineByLocalSearch(LhScheduleContext context,
                                                                   SkuScheduleDTO currentSku,
                                                                   List<MachineScheduleDTO> candidates,
                                                                   List<LhShiftConfigVO> shifts,
                                                                   IMachineMatchStrategy machineMatch,
                                                                   IMouldChangeBalanceStrategy mouldChangeBalance,
                                                                   IFirstInspectionBalanceStrategy inspectionBalance,
                                                                   ICapacityCalculateStrategy capacityCalculate) {
        if (!shouldUseLocalSearch(context, candidates)) {
            return null;
        }
        List<SkuScheduleDTO> windowSkuList = buildLocalSearchWindow(context, currentSku);
        if (CollectionUtils.isEmpty(windowSkuList)) {
            return null;
        }
        return localSearchMachineAllocator.selectBestMachine(
                context, windowSkuList, candidates, shifts, machineMatch, mouldChangeBalance, inspectionBalance, capacityCalculate);
    }

    /**
     * 判断是否启用局部搜索。
     *
     * @param context 排程上下文
     * @param candidates 候选机台列表
     * @return true-启用，false-不启用
     */
    private boolean shouldUseLocalSearch(LhScheduleContext context, List<MachineScheduleDTO> candidates) {
        if (CollectionUtils.isEmpty(candidates)) {
            return false;
        }
        LhScheduleConfig scheduleConfig = context.getScheduleConfig();
        if (scheduleConfig == null || !scheduleConfig.isLocalSearchEnabled()) {
            return false;
        }
        return candidates.size() < scheduleConfig.getLocalSearchMachineThreshold();
    }

    /**
     * 构建局部搜索窗口（当前SKU + 后续若干SKU）。
     *
     * @param context 排程上下文
     * @param currentSku 当前SKU
     * @return 局部搜索SKU窗口
     */
    private List<SkuScheduleDTO> buildLocalSearchWindow(LhScheduleContext context, SkuScheduleDTO currentSku) {
        List<SkuScheduleDTO> allNewSkuList = context.getNewSpecSkuList();
        int skuIndex = allNewSkuList.indexOf(currentSku);
        if (skuIndex < 0) {
            List<SkuScheduleDTO> fallbackList = new ArrayList<>(1);
            fallbackList.add(currentSku);
            return fallbackList;
        }
        int depth = context.getScheduleConfig() != null ? context.getScheduleConfig().getLocalSearchDepth() : 1;
        int endIndex = Math.min(allNewSkuList.size(), skuIndex + depth);
        return new ArrayList<>(allNewSkuList.subList(skuIndex, endIndex));
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 构建新增规格排程结果，并按班次分配计划量
     */
    private LhScheduleResult buildNewSpecScheduleResult(LhScheduleContext context,
                                                         MachineScheduleDTO machine,
                                                         SkuScheduleDTO sku,
                                                         Date startTime,
                                                         Date mouldChangeStartTime,
                                                         Date mouldChangeEndTime,
                                                         List<LhShiftConfigVO> shifts,
                                                         int mouldQty) {
        LhScheduleResult result = new LhScheduleResult();
        result.setFactoryCode(context.getFactoryCode());
        result.setBatchNo(context.getBatchNo());
        result.setOrderNo(generateOrderNo(context));
        result.setLhMachineCode(machine.getMachineCode());
        result.setLhMachineName(machine.getMachineName());
        result.setMaterialCode(sku.getMaterialCode());
        result.setMaterialDesc(sku.getMaterialDesc());
        result.setSpecCode(sku.getSpecCode());
        result.setSpecDesc(sku.getSpecDesc());
        result.setEmbryoCode(sku.getEmbryoCode());
        result.setMainMaterialDesc(sku.getMainMaterialDesc());
        result.setStructureName(sku.getStructureName());
        result.setScheduleDate(context.getScheduleTargetDate());
        result.setLhTime(sku.getLhTimeSeconds());
        result.setMouldQty(mouldQty);
        result.setSingleMouldShiftQty(SingleMouldShiftQtyUtil.resolveSingleMouldShiftQty(context, sku, mouldQty));
        result.setDailyPlanQty(0);
        result.setTotalDailyPlanQty(sku.getMonthPlanQty());
        result.setMouldSurplusQty(sku.getSurplusQty());
        result.setIsEnd("0");
        result.setIsDelivery(sku.isDeliveryLocked() ? "1" : "0");
        result.setIsRelease("0");
        result.setDataSource("0");
        result.setIsDelete(0);
        result.setScheduleType("02");
        result.setIsChangeMould("1");
        result.setConstructionStage(sku.getConstructionStage());
        result.setEmbryoNo(sku.getEmbryoNo());
        result.setTextNo(sku.getTextNo());
        result.setLhNo(sku.getLhNo());
        result.setMonthPlanVersion(sku.getMonthPlanVersion());
        result.setProductionVersion(sku.getProductionVersion());
        result.setIsTrial(sku.isTrial() ? "1" : "0");
        result.setMachineOrder(machine.getMachineOrder());
        result.setRealScheduleDate(context.getScheduleDate());
        result.setProductionStatus("0");
        result.setMouldCode(resolveMouldCode(context, sku.getMaterialCode()));

        // 按班次分配计划量
        int pendingQty = sku.getPendingQty() > 0 ? sku.getPendingQty() : sku.getWindowPlanQty();
        distributeToShifts(context, result, shifts, startTime,
                sku.getShiftCapacity(), sku.getLhTimeSeconds(), mouldQty, pendingQty);
        refreshResultSummary(context, result);
        return result;
    }

    /**
     * 将计划量分配到各班次（从开产时间开始）
     *
     * @return 未排产的剩余量
     */
    private int distributeToShifts(LhScheduleContext context,
                                   LhScheduleResult result,
                                   List<LhShiftConfigVO> shifts,
                                   Date startTime,
                                   int shiftCapacity,
                                   int lhTimeSeconds,
                                   int mouldQty,
                                   int remaining) {
        if (lhTimeSeconds <= 0 || mouldQty <= 0 || remaining <= 0 || startTime == null) {
            return remaining;
        }
        Map<Integer, ShiftRuntimeState> stateMap = context.getShiftRuntimeStateMap();

        boolean started = false;
        for (LhShiftConfigVO shift : shifts) {
            if (remaining <= 0) {
                break;
            }
            if (!started) {
                if (startTime.before(shift.getShiftEndDateTime())) {
                    started = true;
                } else {
                    continue;
                }
            }

            Date effectiveStart = startTime.after(shift.getShiftStartDateTime()) ? startTime : shift.getShiftStartDateTime();
            if (!effectiveStart.before(shift.getShiftEndDateTime())) {
                continue;
            }

            long availableSeconds = (shift.getShiftEndDateTime().getTime() - effectiveStart.getTime()) / 1000L;
            if (availableSeconds <= 0) {
                continue;
            }

            int shiftMaxQty = ShiftCapacityResolverUtil.resolveShiftCapacity(
                    shift, effectiveStart, shiftCapacity, lhTimeSeconds, mouldQty);
            if (shiftMaxQty <= 0) {
                continue;
            }

            int shiftQty = Math.min(remaining, shiftMaxQty);
            if (shiftQty > 0) {
                setShiftPlanQty(result, shift.getShiftIndex(), shiftQty, effectiveStart, shift.getShiftEndDateTime());
                remaining -= shiftQty;
                startTime = shift.getShiftEndDateTime();

                if (!CollectionUtils.isEmpty(stateMap)) {
                    ShiftRuntimeState st = stateMap.get(shift.getShiftIndex());
                    if (st != null) {
                        st.setRemainingCapacity(Math.max(0, shiftMaxQty - shiftQty));
                    }
                }
            }
        }
        return remaining;
    }

    private void setShiftPlanQty(LhScheduleResult result, int shiftIndex, int qty, Date startTime, Date endTime) {
        ShiftFieldUtil.setShiftPlanQty(result, shiftIndex, qty, startTime, endTime);
    }

    private int calcTotalPlanQty(LhScheduleResult result, List<LhShiftConfigVO> shifts) {
        int total = 0;
        for (LhShiftConfigVO s : shifts) {
            Integer q = ShiftFieldUtil.getShiftPlanQty(result, s.getShiftIndex());
            total += (q != null ? q : 0);
        }
        return total;
    }

    private void scaleShiftPlanQty(LhScheduleContext context, LhScheduleResult result, double ratio) {
        List<LhShiftConfigVO> shifts = LhScheduleTimeUtil.getScheduleShifts(context, context.getScheduleDate());
        for (LhShiftConfigVO s : shifts) {
            int idx = s.getShiftIndex();
            Integer qty = ShiftFieldUtil.getShiftPlanQty(result, idx);
            if (qty != null && qty > 0) {
                setShiftPlanQty(result, idx, (int) (qty * ratio),
                        ShiftFieldUtil.getShiftStartTime(result, idx),
                        ShiftFieldUtil.getShiftEndTime(result, idx));
            }
        }
    }

    /**
     * 刷新结果行的汇总计划量和规格结束时间。
     *
     * @param context 排程上下文
     * @param result 排程结果
     */
    private void refreshResultSummary(LhScheduleContext context, LhScheduleResult result) {
        if (result == null) {
            return;
        }
        ShiftFieldUtil.syncDailyPlanQty(result);
        List<LhShiftConfigVO> shifts = context.getScheduleWindowShifts();
        if (CollectionUtils.isEmpty(shifts)) {
            shifts = LhScheduleTimeUtil.getScheduleShifts(context, context.getScheduleDate());
        }
        int lhTimeSeconds = result.getLhTime() != null ? result.getLhTime() : 0;
        int mouldQty = ShiftCapacityResolverUtil.resolveMachineMouldQty(
                result.getMouldQty() != null ? result.getMouldQty() : 0);
        Date specEndTime = calcSpecEndTime(result, shifts, lhTimeSeconds, mouldQty);
        result.setSpecEndTime(specEndTime);
        result.setTdaySpecEndTime(specEndTime);
    }

    private Date calcSpecEndTime(LhScheduleResult result, List<LhShiftConfigVO> shifts, int lhTimeSeconds, int mouldQty) {
        if (lhTimeSeconds <= 0 || mouldQty <= 0) {
            return null;
        }
        for (int i = shifts.size() - 1; i >= 0; i--) {
            LhShiftConfigVO shift = shifts.get(i);
            Integer planQty = ShiftFieldUtil.getShiftPlanQty(result, shift.getShiftIndex());
            if (planQty == null || planQty <= 0) {
                continue;
            }
            Date shiftStart = ShiftFieldUtil.getShiftStartTime(result, shift.getShiftIndex());
            if (shiftStart == null) {
                return shift.getShiftEndDateTime();
            }
            long secondsNeeded = (long) Math.ceil((double) planQty / mouldQty) * lhTimeSeconds;
            return new Date(shiftStart.getTime() + secondsNeeded * 1000L);
        }
        return null;
    }

    private String resolveMouldCode(LhScheduleContext context, String materialCode) {
        if (!context.getSkuMouldRelMap().containsKey(materialCode)) {
            return null;
        }
        return context.getSkuMouldRelMap().get(materialCode).stream()
                .map(rel -> rel.getMouldCode())
                .filter(code -> code != null && !code.trim().isEmpty())
                .distinct()
                .collect(Collectors.joining(","));
    }

    private void updateMachineState(LhScheduleContext context, MachineScheduleDTO machine, SkuScheduleDTO sku, LhScheduleResult result) {
        machine.setCurrentMaterialCode(sku.getMaterialCode());
        machine.setCurrentMaterialDesc(sku.getMaterialDesc());
        machine.setPreviousSpecCode(sku.getSpecCode());
        machine.setPreviousProSize(sku.getProSize());
        machine.setEstimatedEndTime(result.getSpecEndTime());
    }

    /**
     * 生成工单号（使用线程安全的OrderNoGenerator）
     */
    private String generateOrderNo(LhScheduleContext context) {
        return orderNoGenerator.generateOrderNo(context.getScheduleTargetDate());
    }

    /**
     * 添加未排产记录
     */
    private void addUnscheduledResult(LhScheduleContext context, SkuScheduleDTO sku, String reason) {
        LhUnscheduledResult unscheduled = new LhUnscheduledResult();
        unscheduled.setFactoryCode(context.getFactoryCode());
        unscheduled.setBatchNo(context.getBatchNo());
        unscheduled.setMaterialCode(sku.getMaterialCode());
        unscheduled.setMaterialDesc(sku.getMaterialDesc());
        unscheduled.setScheduleDate(context.getScheduleTargetDate());
        unscheduled.setUnscheduledReason(reason);
        unscheduled.setUnscheduledQty(sku.getPendingQty());
        unscheduled.setStructureName(sku.getStructureName());
        unscheduled.setSpecCode(sku.getSpecCode());
        unscheduled.setEmbryoCode(sku.getEmbryoCode());
        unscheduled.setMouldQty(sku.getMouldQty());
        unscheduled.setDataSource("0");
        unscheduled.setIsDelete(0);
        context.getUnscheduledResultList().add(unscheduled);
        log.debug("新增SKU未排产, SKU: {}, 原因: {}", sku.getMaterialCode(), reason);
    }

    /**
     * 添加未排产记录并累计原因分布
     */
    private void addUnscheduledResult(LhScheduleContext context, SkuScheduleDTO sku, String reason,
                                      Map<String, Integer> reasonCountMap) {
        addUnscheduledResult(context, sku, reason);
        reasonCountMap.merge(reason, 1, Integer::sum);
    }

    /**
     * 注册机台排程分配记录
     */
    private void registerMachineAssignment(LhScheduleContext context, String machineCode, LhScheduleResult result) {
        context.getMachineAssignmentMap()
                .computeIfAbsent(machineCode, k -> new ArrayList<>())
                .add(result);
    }

    /**
     * 在所有SKU列表中查找指定materialCode的DTO
     */
    private SkuScheduleDTO findSkuDto(LhScheduleContext context, String materialCode) {
        for (SkuScheduleDTO sku : context.getNewSpecSkuList()) {
            if (materialCode.equals(sku.getMaterialCode())) {
                return sku;
            }
        }
        for (SkuScheduleDTO sku : context.getContinuousSkuList()) {
            if (materialCode.equals(sku.getMaterialCode())) {
                return sku;
            }
        }
        return null;
    }
}
