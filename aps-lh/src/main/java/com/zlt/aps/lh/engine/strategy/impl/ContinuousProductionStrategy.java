/**
 * Copyright (c) 2008, 智立通（厦门）科技有限公司 All rights reserved。
 */
package com.zlt.aps.lh.engine.strategy.impl;

import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.dto.MachineScheduleDTO;
import com.zlt.aps.lh.api.domain.vo.LhShiftConfigVO;
import com.zlt.aps.lh.api.domain.dto.ShiftRuntimeState;
import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.api.domain.entity.LhScheduleResult;
import com.zlt.aps.lh.api.enums.ScheduleTypeEnum;
import com.zlt.aps.lh.engine.strategy.ICapacityCalculateStrategy;
import com.zlt.aps.lh.engine.strategy.IEndingJudgmentStrategy;
import com.zlt.aps.lh.engine.strategy.IFirstInspectionBalanceStrategy;
import com.zlt.aps.lh.engine.strategy.IMachineMatchStrategy;
import com.zlt.aps.lh.engine.strategy.IMouldChangeBalanceStrategy;
import com.zlt.aps.lh.engine.strategy.IProductionStrategy;
import com.zlt.aps.lh.util.LeftRightMouldUtil;
import com.zlt.aps.lh.util.ShiftFieldUtil;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import com.zlt.aps.lh.util.ShiftCapacityResolverUtil;
import com.zlt.aps.lh.util.SingleMouldShiftQtyUtil;
import com.zlt.aps.lh.component.OrderNoGenerator;
import com.zlt.aps.mdm.api.domain.entity.MdmMaterialInfo;
import com.zlt.aps.mdm.api.domain.entity.MdmSkuMouldRel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 续作排产策略实现
 * <p>处理续作场景下的排产逻辑, 包括换活字块、收尾判定、班次分配、库存调整、降模等</p>
 *
 * @author APS
 */
@Slf4j
@Component("continuousProductionStrategy")
public class ContinuousProductionStrategy implements IProductionStrategy {

    @Resource
    private OrderNoGenerator orderNoGenerator;

    @Resource
    private IEndingJudgmentStrategy endingJudgmentStrategy;

    @Override
    public String getStrategyType() {
        return ScheduleTypeEnum.CONTINUOUS.getCode();
    }

    @Override
    public String getStrategyName() {
        return "continuousProductionStrategy";
    }

    @Override
    public void scheduleTypeBlockChange(LhScheduleContext context) {
        log.info("续作排产 - 收尾后衔接排产, 机台数: {}", context.getMachineScheduleMap().size());

        List<LhShiftConfigVO> shifts = LhScheduleTimeUtil.getScheduleShifts(context, context.getScheduleDate());

        // 基于续作收尾阶段回写后的真实收尾时间，按机台收尾先后衔接同产品结构/换活字块
        List<MachineScheduleDTO> endingMachines = context.getMachineScheduleMap().values().stream()
                .filter(m -> m.isEnding() && m.getEstimatedEndTime() != null)
                .sorted(Comparator.comparing(MachineScheduleDTO::getEstimatedEndTime))
                .collect(Collectors.toList());

        for (MachineScheduleDTO machine : endingMachines) {
            // 第一步：同产品结构直接续作，命中后本机台不再尝试换活字块。
            SkuScheduleDTO sameStructureSku = findSameStructureContinuousSku(context, machine);
            if (sameStructureSku != null
                    && appendFollowUpResult(context, machine, sameStructureSku, machine.getEstimatedEndTime(), shifts, false)) {
                continue;
            }

            // 第二步：同胎胚同规格不同花纹，执行换活字块衔接。
            SkuScheduleDTO typeBlockSku = findTypeBlockChangeSku(context, machine);
            if (typeBlockSku == null) {
                continue;
            }
            appendFollowUpResult(context, machine, typeBlockSku, calcTypeBlockStartTime(context, machine), shifts, true);
        }
    }

    @Override
    public void scheduleContinuousEnding(LhScheduleContext context) {
        log.info("续作排产 - 续作收尾判定, 续作SKU数: {}", context.getContinuousSkuList().size());

        List<LhShiftConfigVO> shifts = LhScheduleTimeUtil.getScheduleShifts(context, context.getScheduleDate());

        for (SkuScheduleDTO sku : context.getContinuousSkuList()) {
            String machineCode = sku.getContinuousMachineCode();
            MachineScheduleDTO machine = context.getMachineScheduleMap().get(machineCode);
            if (machine == null) {
                continue;
            }

            boolean isEnding = endingJudgmentStrategy.isEnding(context, sku);

            // 创建排程结果（续作从班次1开始）
            Date startTime = shifts.isEmpty() ? new Date() : shifts.get(0).getShiftStartDateTime();
            int machineMouldQty = ShiftCapacityResolverUtil.resolveMachineMouldQty(machine);
            sku.setMouldQty(machineMouldQty);
            LhScheduleResult result = buildScheduleResult(
                    context, machine, sku, startTime, shifts, machineMouldQty, isEnding);
            if (result != null) {
                result.setScheduleType("01");
                result.setIsEnd(isEnding ? "1" : "0");
                context.getScheduleResultList().add(result);
                registerMachineAssignment(context, machineCode, result);

                // 如果是收尾，更新机台收尾信息
                if (isEnding && result.getSpecEndTime() != null) {
                    machine.setEnding(true);
                    machine.setEstimatedEndTime(result.getSpecEndTime());
                }
            }
        }
    }

    @Override
    public void allocateShiftPlanQty(LhScheduleContext context) {
        log.info("续作排产 - 班次计划量分配");

        List<LhShiftConfigVO> shifts = LhScheduleTimeUtil.getScheduleShifts(context, context.getScheduleDate());

        for (LhScheduleResult result : context.getScheduleResultList()) {
            if (!"01".equals(result.getScheduleType())) {
                continue;
            }
            // 重新按班次分配（夜->早->中顺序按可用量分配）
            redistributeShiftQty(result, shifts);
        }
    }

    @Override
    public void adjustEmbryoStock(LhScheduleContext context) {
        log.info("续作排产 - 胎胚库存调整");
        List<LhShiftConfigVO> shifts = LhScheduleTimeUtil.getScheduleShifts(context, context.getScheduleDate());

        // 收尾SKU优先占用胎胚库存，普通SKU再按顺序扣减
        Map<String, Integer> embryoStockMap = buildEmbryoStockMap(context);

        // 先处理收尾SKU
        for (LhScheduleResult result : context.getScheduleResultList()) {
            if ("1".equals(result.getIsEnd())) {
                adjustResultByEmbryoStock(result, embryoStockMap, shifts);
            }
        }
        // 再处理普通SKU
        for (LhScheduleResult result : context.getScheduleResultList()) {
            if (!"1".equals(result.getIsEnd())) {
                adjustResultByEmbryoStock(result, embryoStockMap, shifts);
            }
        }
    }

    @Override
    public void scheduleReduceMould(LhScheduleContext context) {
        log.info("续作排产 - 降模排产");
        List<LhShiftConfigVO> shifts = LhScheduleTimeUtil.getScheduleShifts(context, context.getScheduleDate());

        // 按materialCode分组找出同SKU多机台情况
        Map<String, List<LhScheduleResult>> skuResultMap = new HashMap<>();
        for (LhScheduleResult result : context.getScheduleResultList()) {
            if ("01".equals(result.getScheduleType())) {
                skuResultMap.computeIfAbsent(result.getMaterialCode(), k -> new ArrayList<>()).add(result);
            }
        }

        for (Map.Entry<String, List<LhScheduleResult>> entry : skuResultMap.entrySet()) {
            List<LhScheduleResult> skuResults = entry.getValue();
            if (skuResults.size() <= 1) {
                continue;
            }

            int targetQty = 0;
            SkuScheduleDTO skuDto = findSkuDto(context, entry.getKey());
            if (skuDto != null) {
                targetQty = skuDto.getPendingQty() > 0 ? skuDto.getPendingQty() : skuDto.getWindowPlanQty();
            }

            // 总计划量超过当前窗口待排量时才降模
            int totalPlanQty = skuResults.stream().mapToInt(ShiftFieldUtil::resolveScheduledQty).sum();
            if (targetQty <= 0 || totalPlanQty <= targetQty) {
                continue;
            }

            // 按胶囊使用次数升序，减少胶囊使用次数少的机台的计划（胶囊已使用次数少的优先下机）
            skuResults.sort(Comparator.comparingInt(r -> {
                MachineScheduleDTO m = context.getMachineScheduleMap().get(r.getLhMachineCode());
                return m != null ? m.getCapsuleUsageCount() : 0;
            }));

            int remaining = targetQty;
            for (LhScheduleResult result : skuResults) {
                int allocation = Math.min(remaining, ShiftFieldUtil.resolveScheduledQty(result));
                redistributeShiftQty(result, shifts, allocation);
                remaining -= allocation;
                if (allocation <= 0) {
                    // 当前结果已被完全降为0，保留收尾标记，避免后续继续参与续作产量判断。
                    result.setIsEnd("1");
                }
            }
        }
    }

    @Override
    public void scheduleNewSpecs(LhScheduleContext context,
                                 IMachineMatchStrategy machineMatch,
                                 IMouldChangeBalanceStrategy mouldChangeBalance,
                                 IFirstInspectionBalanceStrategy inspectionBalance,
                                 ICapacityCalculateStrategy capacityCalculate) {
        // 续作策略不处理新增规格排产，空实现
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 在新增SKU列表中查找可直接续作的同产品结构SKU。
     */
    private SkuScheduleDTO findSameStructureContinuousSku(LhScheduleContext context, MachineScheduleDTO machine) {
        if (CollectionUtils.isEmpty(context.getNewSpecSkuList())) {
            return null;
        }
        for (SkuScheduleDTO sku : context.getNewSpecSkuList()) {
            if (isSameStructureContinuousCandidate(context, machine, sku)) {
                return sku;
            }
        }
        return null;
    }

    /**
     * 在新增SKU列表中查找可以换活字块的SKU。
     */
    private SkuScheduleDTO findTypeBlockChangeSku(LhScheduleContext context, MachineScheduleDTO machine) {
        if (CollectionUtils.isEmpty(context.getNewSpecSkuList())) {
            return null;
        }

        for (SkuScheduleDTO sku : context.getNewSpecSkuList()) {
            if (endingJudgmentStrategy.isEnding(context, sku)
                    && isTypeBlockCandidate(context, machine, sku)) {
                return sku;
            }
        }
        for (SkuScheduleDTO sku : context.getNewSpecSkuList()) {
            if (isTypeBlockCandidate(context, machine, sku)) {
                return sku;
            }
        }
        return null;
    }

    /**
     * 判断SKU是否满足同产品结构直接续作。
     */
    private boolean isSameStructureContinuousCandidate(LhScheduleContext context,
                                                       MachineScheduleDTO machine,
                                                       SkuScheduleDTO sku) {
        return isSameEmbryo(context, machine, sku)
                && StringUtils.isNotEmpty(resolveMachineStructureKey(context, machine))
                && StringUtils.isNotEmpty(resolveStructureKey(sku))
                && StringUtils.equals(resolveMachineStructureKey(context, machine), resolveStructureKey(sku));
    }

    /**
     * 判断SKU是否满足换活字块条件：同胎胚、同规格、不同花纹。
     */
    private boolean isTypeBlockCandidate(LhScheduleContext context,
                                         MachineScheduleDTO machine,
                                         SkuScheduleDTO sku) {
        if (!isSameEmbryo(context, machine, sku)) {
            return false;
        }
        String machineSpecCode = resolveMachineSpecCode(context, machine);
        String machinePatternKey = resolveMachinePatternKey(context, machine);
        String skuPatternKey = resolvePatternKey(sku.getMainPattern(), sku.getPattern());
        if (StringUtils.isEmpty(machineSpecCode)
                || StringUtils.isEmpty(machinePatternKey)
                || StringUtils.isEmpty(sku.getSpecCode())
                || StringUtils.isEmpty(skuPatternKey)) {
            return false;
        }
        return StringUtils.equals(machineSpecCode, sku.getSpecCode())
                && !StringUtils.equals(machinePatternKey, skuPatternKey);
    }

    /**
     * 判断机台当前物料与候选SKU是否为相同胎胚。
     */
    private boolean isSameEmbryo(LhScheduleContext context, MachineScheduleDTO machine, SkuScheduleDTO sku) {
        String machineEmbryoCode = resolveMachineEmbryoCode(context, machine);
        return StringUtils.isNotEmpty(machineEmbryoCode)
                && StringUtils.isNotEmpty(sku.getEmbryoCode())
                && StringUtils.equals(machineEmbryoCode, sku.getEmbryoCode());
    }

    /**
     * 计算换活字块开产时间（无需换模、无需首检，仅消耗换活字块时间）
     */
    private Date calcTypeBlockStartTime(LhScheduleContext context, MachineScheduleDTO machine) {
        if (machine.getEstimatedEndTime() == null) {
            return null;
        }
        // 换活字块：收尾时间 + 换活字块总耗时
        return LhScheduleTimeUtil.addHours(machine.getEstimatedEndTime(),
                LhScheduleTimeUtil.getTypeBlockChangeTotalHours(context));
    }

    /**
     * 追加续作衔接结果（同产品结构直续或换活字块）。
     */
    private boolean appendFollowUpResult(LhScheduleContext context,
                                         MachineScheduleDTO machine,
                                         SkuScheduleDTO sku,
                                         Date startTime,
                                         List<LhShiftConfigVO> shifts,
                                         boolean typeBlock) {
        if (startTime == null) {
            return false;
        }
        boolean isEnding = endingJudgmentStrategy.isEnding(context, sku);
        int machineMouldQty = ShiftCapacityResolverUtil.resolveMachineMouldQty(machine);
        sku.setMouldQty(machineMouldQty);
        LhScheduleResult result = buildScheduleResult(
                context, machine, sku, startTime, shifts, machineMouldQty, isEnding);
        if (result == null || result.getDailyPlanQty() == null || result.getDailyPlanQty() <= 0) {
            return false;
        }
        result.setScheduleType("01");
        result.setIsEnd(isEnding ? "1" : "0");
        if (typeBlock) {
            result.setIsChangeMould("1");
            result.setMouldCode(resolveMouldCode(context, sku.getMaterialCode(), machine.getCurrentMaterialCode()));
        } else {
            result.setIsChangeMould("0");
        }
        // 续作衔接结果即便非收尾，也必须补齐可计算完工时刻，避免结果校验失败。
        Date actualCompletionTime = resolveActualCompletionTime(result);
        if (actualCompletionTime == null) {
            return false;
        }
        result.setSpecEndTime(actualCompletionTime);
        result.setTdaySpecEndTime(actualCompletionTime);

        context.getScheduleResultList().add(result);
        registerMachineAssignment(context, machine.getMachineCode(), result);
        updateMachineState(machine, sku, result);
        context.getNewSpecSkuList().remove(sku);
        log.debug("{}排产完成, 机台: {}, SKU: {}",
                typeBlock ? "换活字块" : "同产品结构续作",
                machine.getMachineCode(), sku.getMaterialCode());
        return true;
    }

    /**
     * 构建排程结果，分配各班次计划量
     */
    private LhScheduleResult buildScheduleResult(LhScheduleContext context,
                                                  MachineScheduleDTO machine,
                                                  SkuScheduleDTO sku,
                                                  Date startTime,
                                                  List<LhShiftConfigVO> shifts,
                                                  int mouldQty,
                                                  boolean isEnding) {
        LhScheduleResult result = new LhScheduleResult();
        result.setFactoryCode(context.getFactoryCode());
        result.setBatchNo(context.getBatchNo());
        result.setLhMachineCode(machine.getMachineCode());
        result.setLhMachineName(machine.getMachineName());
        result.setLeftRightMould(LeftRightMouldUtil.resolveLeftRightMould(result.getLeftRightMould(), machine.getMachineCode()));
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
        result.setIsEnd(isEnding ? "1" : "0");
        result.setIsDelivery(sku.isDeliveryLocked() ? "1" : "0");
        result.setIsRelease("0");
        result.setDataSource("0");
        result.setIsDelete(0);
        result.setScheduleType(sku.getScheduleType() != null ? sku.getScheduleType() : "01");
        result.setConstructionStage(sku.getConstructionStage());
        result.setEmbryoNo(sku.getEmbryoNo());
        result.setTextNo(sku.getTextNo());
        result.setLhNo(sku.getLhNo());
        result.setMonthPlanVersion(sku.getMonthPlanVersion());
        result.setProductionVersion(sku.getProductionVersion());
        result.setIsTrial(sku.isTrial() ? "1" : "0");
        result.setMachineOrder(machine.getMachineOrder());

        // 生成工单号
        String orderNo = generateOrderNo(context);
        result.setOrderNo(orderNo);

        // 按班次分配计划量
        int remaining = sku.getPendingQty() > 0 ? sku.getPendingQty() : sku.getWindowPlanQty();
        distributeToShifts(context, result, shifts, startTime,
                sku.getShiftCapacity(), sku.getLhTimeSeconds(), mouldQty, remaining);

        refreshResultSummary(result, shifts);
        result.setRealScheduleDate(context.getScheduleDate());
        result.setProductionStatus("0");

        return result;
    }

    /**
     * 向各班次分配计划量（从startTime所在班次开始，按夜->早->中次序填满）
     *
     * @return 未能排产的剩余量
     */
    private int distributeToShifts(LhScheduleContext context,
                                   LhScheduleResult result,
                                   List<LhShiftConfigVO> shifts,
                                   Date startTime,
                                   int shiftCapacity,
                                   int lhTimeSeconds,
                                   int mouldQty,
                                   int remaining) {
        if (lhTimeSeconds <= 0 || mouldQty <= 0 || remaining <= 0) {
            return remaining;
        }
        Map<Integer, ShiftRuntimeState> stateMap = context.getShiftRuntimeStateMap();

        boolean started = false;
        for (LhShiftConfigVO shift : shifts) {
            if (remaining <= 0) {
                break;
            }
            if (!started) {
                if (startTime != null && !startTime.before(shift.getShiftEndDateTime()) && shift != shifts.get(shifts.size() - 1)) {
                    continue;
                }
                started = true;
            }

            Date effectiveStart = (startTime != null && startTime.after(shift.getShiftStartDateTime()))
                    ? startTime : shift.getShiftStartDateTime();
            if (effectiveStart.after(shift.getShiftEndDateTime())) {
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

            setShiftPlanQty(result, shift.getShiftIndex(), shiftQty, effectiveStart, shift.getShiftEndDateTime());
            remaining -= shiftQty;
            startTime = null;

            if (!CollectionUtils.isEmpty(stateMap)) {
                ShiftRuntimeState st = stateMap.get(shift.getShiftIndex());
                if (st != null) {
                    st.setRemainingCapacity(Math.max(0, shiftMaxQty - shiftQty));
                }
            }
        }
        return remaining;
    }

    /**
     * 按班次索引设置计划量和开始/结束时间（Hutool BeanUtil）
     */
    private void setShiftPlanQty(LhScheduleResult result, int shiftIndex, int qty, Date startTime, Date endTime) {
        ShiftFieldUtil.setShiftPlanQty(result, shiftIndex, qty, startTime, endTime);
    }

    /**
     * 计算规格收尾时间（最后一个有计划量班次中，完成剩余量所需的时间点）
     */
    private Date calcSpecEndTime(LhScheduleResult result,
                                 List<LhShiftConfigVO> shifts,
                                 int lhTimeSeconds,
                                 int mouldQty,
                                 boolean isEnding) {
        if (!isEnding) {
            return null;
        }
        // 找到最后一个有计划量的班次
        for (int i = shifts.size() - 1; i >= 0; i--) {
            LhShiftConfigVO shift = shifts.get(i);
            Integer planQty = ShiftFieldUtil.getShiftPlanQty(result, shift.getShiftIndex());
            if (planQty != null && planQty > 0 && lhTimeSeconds > 0 && mouldQty > 0) {
                // 收尾时间 = 该班次开始时间 + (计划量/模数) * 硫化时间
                long secondsNeeded = (long) Math.ceil((double) planQty / mouldQty) * lhTimeSeconds;
                Date shiftStart = ShiftFieldUtil.getShiftStartTime(result, shift.getShiftIndex());
                if (shiftStart != null) {
                    return new Date(shiftStart.getTime() + secondsNeeded * 1000L);
                }
                return shift.getShiftEndDateTime();
            }
        }
        return null;
    }

    private int calcTotalPlanQty(LhScheduleResult result, List<LhShiftConfigVO> shifts) {
        int total = 0;
        for (LhShiftConfigVO s : shifts) {
            Integer qty = ShiftFieldUtil.getShiftPlanQty(result, s.getShiftIndex());
            total += (qty != null ? qty : 0);
        }
        return total;
    }

    /**
     * 重新在班次间均衡分配计划量（用于allocateShiftPlanQty后续调整）
     */
    private void redistributeShiftQty(LhScheduleResult result, List<LhShiftConfigVO> shifts) {
        redistributeShiftQty(result, shifts, ShiftFieldUtil.resolveScheduledQty(result));
    }

    /**
     * 按指定目标量重新在班次间均衡分配计划量。
     *
     * @param result 排程结果
     * @param shifts 班次列表
     * @param targetQty 目标计划量
     */
    private void redistributeShiftQty(LhScheduleResult result, List<LhShiftConfigVO> shifts, int targetQty) {
        if (CollectionUtils.isEmpty(shifts)
                || result.getLhTime() == null
                || result.getLhTime() <= 0) {
            return;
        }

        if (targetQty <= 0) {
            clearShiftPlanQty(result, shifts);
            refreshResultSummary(result, shifts);
            return;
        }

        int mouldQty = ShiftCapacityResolverUtil.resolveMachineMouldQty(
                result.getMouldQty() != null ? result.getMouldQty() : 0);
        int shiftCapacity = result.getSingleMouldShiftQty() != null ? result.getSingleMouldShiftQty() : 0;
        int remaining = targetQty;
        Date cursorStartTime = resolveRedistributeStartTime(result, shifts);

        for (LhShiftConfigVO shift : shifts) {
            if (remaining <= 0) {
                setShiftPlanQty(result, shift.getShiftIndex(), 0, null, null);
                continue;
            }
            if (cursorStartTime != null
                    && !cursorStartTime.before(shift.getShiftEndDateTime())
                    && shift != shifts.get(shifts.size() - 1)) {
                setShiftPlanQty(result, shift.getShiftIndex(), 0, null, null);
                continue;
            }
            Date shiftStartTime = shift.getShiftStartDateTime();
            Date effectiveStartTime = cursorStartTime != null && cursorStartTime.after(shiftStartTime)
                    ? cursorStartTime : shiftStartTime;
            int shiftMaxQty = ShiftCapacityResolverUtil.resolveShiftCapacity(
                    shift, effectiveStartTime, shiftCapacity, result.getLhTime(), mouldQty);
            if (shiftMaxQty <= 0) {
                setShiftPlanQty(result, shift.getShiftIndex(), 0, null, null);
                continue;
            }
            int shiftQty = Math.min(remaining, shiftMaxQty);
            setShiftPlanQty(result, shift.getShiftIndex(), shiftQty, effectiveStartTime, shift.getShiftEndDateTime());
            remaining -= shiftQty;
            cursorStartTime = shift.getShiftEndDateTime();
        }
        refreshResultSummary(result, shifts);
    }

    /**
     * 获取结果当前的首个开产时间，供续作班次重分配时保留残班起点。
     *
     * @param result 排程结果
     * @param shifts 班次列表
     * @return 首个有效开产时间
     */
    private Date resolveRedistributeStartTime(LhScheduleResult result, List<LhShiftConfigVO> shifts) {
        for (LhShiftConfigVO shift : shifts) {
            Date shiftStartTime = ShiftFieldUtil.getShiftStartTime(result, shift.getShiftIndex());
            if (shiftStartTime != null) {
                return shiftStartTime;
            }
        }
        return shifts.get(0).getShiftStartDateTime();
    }

    /**
     * 构建胎胚库存Map（基于context中现有的skuLhCapacityMap，用materialCode的embryoCode分组统计）
     */
    private Map<String, Integer> buildEmbryoStockMap(LhScheduleContext context) {
        Map<String, Integer> stockMap = new HashMap<>();
        for (SkuScheduleDTO sku : context.getContinuousSkuList()) {
            if (sku.getEmbryoCode() != null && sku.getEmbryoStock() >= 0) {
                stockMap.put(sku.getEmbryoCode(), sku.getEmbryoStock());
            }
        }
        for (SkuScheduleDTO sku : context.getNewSpecSkuList()) {
            if (sku.getEmbryoCode() != null
                    && sku.getEmbryoStock() >= 0
                    && !stockMap.containsKey(sku.getEmbryoCode())) {
                stockMap.put(sku.getEmbryoCode(), sku.getEmbryoStock());
            }
        }
        return stockMap;
    }

    /**
     * 根据胎胚库存调整排程结果的计划量
     */
    private void adjustResultByEmbryoStock(LhScheduleResult result,
                                           Map<String, Integer> embryoStockMap,
                                           List<LhShiftConfigVO> shifts) {
        String embryoCode = result.getEmbryoCode();
        if (embryoCode == null) {
            return;
        }
        Integer stock = embryoStockMap.get(embryoCode);
        if (stock == null || stock < 0) {
            return;
        }
        int totalPlan = ShiftFieldUtil.resolveScheduledQty(result);
        if (totalPlan <= stock) {
            embryoStockMap.put(embryoCode, stock - totalPlan);
        } else {
            // 库存不足，削减计划量
            redistributeShiftQty(result, shifts, stock);
            embryoStockMap.put(embryoCode, 0);
        }
    }

    /**
     * 清空结果行的班次计划量。
     *
     * @param result 排程结果
     * @param shifts 班次列表
     */
    private void clearShiftPlanQty(LhScheduleResult result, List<LhShiftConfigVO> shifts) {
        for (LhShiftConfigVO shift : shifts) {
            setShiftPlanQty(result, shift.getShiftIndex(), 0, null, null);
        }
    }

    /**
     * 刷新结果行的汇总计划量和收尾时间。
     *
     * @param result 排程结果
     * @param shifts 班次列表
     */
    private void refreshResultSummary(LhScheduleResult result, List<LhShiftConfigVO> shifts) {
        if (result == null) {
            return;
        }
        ShiftFieldUtil.syncDailyPlanQty(result);
        int lhTimeSeconds = result.getLhTime() != null ? result.getLhTime() : 0;
        int mouldQty = ShiftCapacityResolverUtil.resolveMachineMouldQty(
                result.getMouldQty() != null ? result.getMouldQty() : 0);
        Date specEndTime = calcSpecEndTime(result, shifts, lhTimeSeconds, mouldQty, "1".equals(result.getIsEnd()));
        if (specEndTime == null) {
            // 非收尾结果也要保留可推导完工时刻，避免后续校验出现 specEndTime 缺失。
            specEndTime = resolveActualCompletionTime(result);
        }
        result.setSpecEndTime(specEndTime);
        result.setTdaySpecEndTime(specEndTime);
    }

    private void updateMachineState(MachineScheduleDTO machine, SkuScheduleDTO sku, LhScheduleResult result) {
        machine.setCurrentMaterialCode(sku.getMaterialCode());
        machine.setCurrentMaterialDesc(sku.getMaterialDesc());
        machine.setPreviousSpecCode(sku.getSpecCode());
        machine.setPreviousProSize(sku.getProSize());
        // 机台预计结束时间严格回写为实际完工时间，避免被整班结束时间放大。
        machine.setEstimatedEndTime(resolveActualCompletionTime(result));
        machine.setEnding("1".equals(result.getIsEnd()) && result.getSpecEndTime() != null);
    }

    private Date resolveActualCompletionTime(LhScheduleResult result) {
        if (result == null) {
            return null;
        }
        int lhTimeSeconds = result.getLhTime() != null ? result.getLhTime() : 0;
        int mouldQty = ShiftCapacityResolverUtil.resolveMachineMouldQty(
                result.getMouldQty() != null ? result.getMouldQty() : 0);
        if (lhTimeSeconds > 0 && mouldQty > 0) {
            Date actualCompletionTime = null;
            for (int shiftIndex = 1; shiftIndex <= 8; shiftIndex++) {
                Integer shiftPlanQty = ShiftFieldUtil.getShiftPlanQty(result, shiftIndex);
                Date shiftStartTime = ShiftFieldUtil.getShiftStartTime(result, shiftIndex);
                if (shiftPlanQty == null || shiftPlanQty <= 0 || shiftStartTime == null) {
                    continue;
                }
                long secondsNeeded = (long) Math.ceil((double) shiftPlanQty / mouldQty) * lhTimeSeconds;
                Date shiftCompletionTime = new Date(shiftStartTime.getTime() + secondsNeeded * 1000L);
                if (actualCompletionTime == null || shiftCompletionTime.after(actualCompletionTime)) {
                    actualCompletionTime = shiftCompletionTime;
                }
            }
            if (actualCompletionTime != null) {
                return actualCompletionTime;
            }
        }
        return result.getSpecEndTime();
    }

    private String resolveMachineEmbryoCode(LhScheduleContext context, MachineScheduleDTO machine) {
        MdmMaterialInfo materialInfo = resolveMachineMaterialInfo(context, machine);
        if (materialInfo != null && StringUtils.isNotEmpty(materialInfo.getEmbryoCode())) {
            return materialInfo.getEmbryoCode();
        }
        SkuScheduleDTO currentSku = findSkuByMaterialCode(context.getContinuousSkuList(), machine.getCurrentMaterialCode());
        return currentSku != null ? currentSku.getEmbryoCode() : null;
    }

    private String resolveMachineSpecCode(LhScheduleContext context, MachineScheduleDTO machine) {
        if (StringUtils.isNotEmpty(machine.getPreviousSpecCode())) {
            return machine.getPreviousSpecCode();
        }
        MdmMaterialInfo materialInfo = resolveMachineMaterialInfo(context, machine);
        if (materialInfo != null && StringUtils.isNotEmpty(materialInfo.getSpecifications())) {
            return materialInfo.getSpecifications();
        }
        SkuScheduleDTO currentSku = findSkuByMaterialCode(context.getContinuousSkuList(), machine.getCurrentMaterialCode());
        return currentSku != null ? currentSku.getSpecCode() : null;
    }

    private String resolveMachinePatternKey(LhScheduleContext context, MachineScheduleDTO machine) {
        MdmMaterialInfo materialInfo = resolveMachineMaterialInfo(context, machine);
        if (materialInfo != null) {
            return resolvePatternKey(materialInfo.getMainPattern(), materialInfo.getPattern());
        }
        SkuScheduleDTO currentSku = findSkuByMaterialCode(context.getContinuousSkuList(), machine.getCurrentMaterialCode());
        if (currentSku == null) {
            return null;
        }
        return resolvePatternKey(currentSku.getMainPattern(), currentSku.getPattern());
    }

    private String resolveMachineStructureKey(LhScheduleContext context, MachineScheduleDTO machine) {
        MdmMaterialInfo materialInfo = resolveMachineMaterialInfo(context, machine);
        if (materialInfo != null) {
            String structureKey = resolveStructureKey(materialInfo.getStructureName(),
                    materialInfo.getSpecifications(),
                    materialInfo.getMainPattern(),
                    materialInfo.getPattern());
            if (StringUtils.isNotEmpty(structureKey)) {
                return structureKey;
            }
        }
        SkuScheduleDTO currentSku = findSkuByMaterialCode(context.getContinuousSkuList(), machine.getCurrentMaterialCode());
        return currentSku != null ? resolveStructureKey(currentSku) : null;
    }

    private MdmMaterialInfo resolveMachineMaterialInfo(LhScheduleContext context, MachineScheduleDTO machine) {
        if (context == null || machine == null || StringUtils.isEmpty(machine.getCurrentMaterialCode())) {
            return null;
        }
        return context.getMaterialInfoMap().get(machine.getCurrentMaterialCode());
    }

    private SkuScheduleDTO findSkuByMaterialCode(List<SkuScheduleDTO> skuList, String materialCode) {
        if (CollectionUtils.isEmpty(skuList) || StringUtils.isEmpty(materialCode)) {
            return null;
        }
        for (SkuScheduleDTO sku : skuList) {
            if (StringUtils.equals(materialCode, sku.getMaterialCode())) {
                return sku;
            }
        }
        return null;
    }

    private String resolveStructureKey(SkuScheduleDTO sku) {
        if (sku == null) {
            return null;
        }
        return resolveStructureKey(sku.getStructureName(), sku.getSpecCode(), sku.getMainPattern(), sku.getPattern());
    }

    private String resolveStructureKey(String structureName, String specCode, String mainPattern, String pattern) {
        if (StringUtils.isNotEmpty(structureName)) {
            return structureName;
        }
        String patternKey = resolvePatternKey(mainPattern, pattern);
        if (StringUtils.isEmpty(specCode) || StringUtils.isEmpty(patternKey)) {
            return null;
        }
        return specCode + "#" + patternKey;
    }

    private String resolvePatternKey(String mainPattern, String pattern) {
        if (StringUtils.isNotEmpty(mainPattern)) {
            return mainPattern;
        }
        return StringUtils.isNotEmpty(pattern) ? pattern : null;
    }

    private String resolveMouldCode(LhScheduleContext context, String... materialCodes) {
        if (context == null || materialCodes == null) {
            return null;
        }
        for (String materialCode : materialCodes) {
            if (StringUtils.isEmpty(materialCode) || !context.getSkuMouldRelMap().containsKey(materialCode)) {
                continue;
            }
            String mouldCode = context.getSkuMouldRelMap().get(materialCode).stream()
                    .map(MdmSkuMouldRel::getMouldCode)
                    .filter(StringUtils::isNotEmpty)
                    .distinct()
                    .collect(Collectors.joining(","));
            if (StringUtils.isNotEmpty(mouldCode)) {
                return mouldCode;
            }
        }
        return null;
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
     * 在所有SKU列表中查找指定materialCode的SKU
     */
    private SkuScheduleDTO findSkuDto(LhScheduleContext context, String materialCode) {
        for (SkuScheduleDTO sku : context.getContinuousSkuList()) {
            if (materialCode.equals(sku.getMaterialCode())) {
                return sku;
            }
        }
        for (SkuScheduleDTO sku : context.getNewSpecSkuList()) {
            if (materialCode.equals(sku.getMaterialCode())) {
                return sku;
            }
        }
        return null;
    }

    /**
     * 生成工单号（使用线程安全的OrderNoGenerator）
     */
    private String generateOrderNo(LhScheduleContext context) {
        return orderNoGenerator.generateOrderNo(context.getScheduleTargetDate());
    }
}
