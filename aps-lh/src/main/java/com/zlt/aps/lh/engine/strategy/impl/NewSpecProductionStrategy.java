/**
 * Copyright (c) 2008, 智立通（厦门）科技有限公司 All rights reserved。
 */
package com.zlt.aps.lh.engine.strategy.impl;

import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.dto.MachineScheduleDTO;
import com.zlt.aps.lh.api.domain.dto.ShiftInfo;
import com.zlt.aps.lh.api.domain.dto.ShiftRuntimeState;
import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.api.domain.entity.LhScheduleResult;
import com.zlt.aps.lh.api.domain.entity.LhUnscheduledResult;
import com.zlt.aps.lh.api.enums.ScheduleTypeEnum;
import com.zlt.aps.lh.engine.strategy.ICapacityCalculateStrategy;
import com.zlt.aps.lh.engine.strategy.IFirstInspectionBalanceStrategy;
import com.zlt.aps.lh.engine.strategy.IMachineMatchStrategy;
import com.zlt.aps.lh.engine.strategy.IMouldChangeBalanceStrategy;
import com.zlt.aps.lh.engine.strategy.IProductionStrategy;
import com.zlt.aps.lh.util.ShiftFieldUtil;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import com.zlt.aps.lh.component.OrderNoGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
            if (sku == null || sku.getEmbryoStock() <= 0) {
                continue;
            }
            int totalPlan = result.getTotalDailyPlanQty() != null ? result.getTotalDailyPlanQty() : 0;
            if (totalPlan > sku.getEmbryoStock()) {
                // 库存不足，按比例削减各班次计划量
                double ratio = (double) sku.getEmbryoStock() / totalPlan;
                scaleShiftPlanQty(context, result, ratio);
                result.setTotalDailyPlanQty(sku.getEmbryoStock());
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

        List<ShiftInfo> shifts = LhScheduleTimeUtil.getScheduleShifts(context, context.getScheduleDate());

        Iterator<SkuScheduleDTO> iterator = context.getNewSpecSkuList().iterator();
        while (iterator.hasNext()) {
            SkuScheduleDTO sku = iterator.next();

            // 1. 匹配候选机台
            List<MachineScheduleDTO> candidates = machineMatch.matchMachines(context, sku);
            if (candidates.isEmpty()) {
                addUnscheduledResult(context, sku, "无可用硫化机台");
                iterator.remove();
                continue;
            }

            // 2. 选择最优机台
            MachineScheduleDTO bestMachine = machineMatch.selectBestMachine(candidates, sku);
            if (bestMachine == null) {
                addUnscheduledResult(context, sku, "机台选择失败");
                iterator.remove();
                continue;
            }

            // 3. 检查换模能力
            Date endingTime = bestMachine.getEstimatedEndTime() != null ? bestMachine.getEstimatedEndTime() : new Date();
            if (!mouldChangeBalance.hasCapacity(context, endingTime)) {
                addUnscheduledResult(context, sku, "换模能力不足，已达当日换模上限");
                iterator.remove();
                continue;
            }

            // 4. 分配换模班次（返回换模完成时间）
            Date mouldChangeCompleteTime = mouldChangeBalance.allocateMouldChange(context, endingTime);
            if (mouldChangeCompleteTime == null) {
                addUnscheduledResult(context, sku, "换模班次分配失败");
                iterator.remove();
                continue;
            }

            // 5. 分配首检时间
            Date inspectionTime = inspectionBalance.allocateInspection(context, bestMachine.getMachineCode(), mouldChangeCompleteTime);
            if (inspectionTime == null) {
                addUnscheduledResult(context, sku, "首检班次分配失败");
                iterator.remove();
                continue;
            }

            // 6. 计算开产时间（换模结束时间 + 首检时间）
            Date productionStartTime = capacityCalculate.calculateStartTime(context, bestMachine.getMachineCode(), endingTime);

            // 7. 构建排程结果，按班次分配计划量
            LhScheduleResult result = buildNewSpecScheduleResult(context, bestMachine, sku, productionStartTime, mouldChangeCompleteTime, shifts, capacityCalculate);
            if (result != null && result.getTotalDailyPlanQty() != null && result.getTotalDailyPlanQty() > 0) {
                context.getScheduleResultList().add(result);
                registerMachineAssignment(context, bestMachine.getMachineCode(), result);
                iterator.remove();
                log.debug("新增排产完成, SKU: {}, 机台: {}, 开产时间: {}", sku.getMaterialCode(), bestMachine.getMachineCode(), productionStartTime);
            } else {
                addUnscheduledResult(context, sku, "排程窗口内无可用产能");
                iterator.remove();
            }
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 构建新增规格排程结果，并按班次分配计划量
     */
    private LhScheduleResult buildNewSpecScheduleResult(LhScheduleContext context,
                                                         MachineScheduleDTO machine,
                                                         SkuScheduleDTO sku,
                                                         Date startTime,
                                                         Date mouldChangeTime,
                                                         List<ShiftInfo> shifts,
                                                         ICapacityCalculateStrategy capacityCalculate) {
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
        result.setMouldQty(sku.getMouldQty());
        result.setDailyPlanQty(sku.getDailyPlanQty());
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

        // 按班次分配计划量
        int pendingQty = sku.getPendingQty() > 0 ? sku.getPendingQty() : sku.getDailyPlanQty();
        int remaining = distributeToShifts(context, result, shifts, startTime, sku.getLhTimeSeconds(), sku.getMouldQty(), pendingQty, capacityCalculate);

        int totalQty = calcTotalPlanQty(result, shifts);
        result.setTotalDailyPlanQty(totalQty);
        return result;
    }

    /**
     * 将计划量分配到各班次（从开产时间开始）
     *
     * @return 未排产的剩余量
     */
    private int distributeToShifts(LhScheduleContext context,
                                   LhScheduleResult result,
                                   List<ShiftInfo> shifts,
                                   Date startTime,
                                   int lhTimeSeconds,
                                   int mouldQty,
                                   int remaining,
                                   ICapacityCalculateStrategy capacityCalculate) {
        if (lhTimeSeconds <= 0 || mouldQty <= 0 || remaining <= 0 || startTime == null) {
            return remaining;
        }
        Map<Integer, ShiftRuntimeState> stateMap = context.getShiftRuntimeStateMap();

        boolean started = false;
        for (ShiftInfo shift : shifts) {
            if (remaining <= 0) {
                break;
            }
            if (!started) {
                if (startTime.before(shift.getEndTime())) {
                    started = true;
                } else {
                    continue;
                }
            }

            Date effectiveStart = startTime.after(shift.getStartTime()) ? startTime : shift.getStartTime();
            if (!effectiveStart.before(shift.getEndTime())) {
                continue;
            }

            long availableSeconds = (shift.getEndTime().getTime() - effectiveStart.getTime()) / 1000L;
            if (availableSeconds <= 0) {
                continue;
            }

            int shiftMaxQty;
            if (startTime.equals(effectiveStart)) {
                shiftMaxQty = capacityCalculate.calculateFirstShiftQty(effectiveStart, shift.getEndTime(), lhTimeSeconds, mouldQty);
            } else {
                shiftMaxQty = capacityCalculate.calculateShiftCapacity(lhTimeSeconds, mouldQty);
            }

            int shiftQty = Math.min(remaining, shiftMaxQty);
            if (shiftQty > 0) {
                setShiftPlanQty(result, shift.getShiftIndex(), shiftQty, effectiveStart, shift.getEndTime());
                remaining -= shiftQty;
                startTime = shift.getEndTime();

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

    private int calcTotalPlanQty(LhScheduleResult result, List<ShiftInfo> shifts) {
        int total = 0;
        for (ShiftInfo s : shifts) {
            Integer q = ShiftFieldUtil.getShiftPlanQty(result, s.getShiftIndex());
            total += (q != null ? q : 0);
        }
        return total;
    }

    private void scaleShiftPlanQty(LhScheduleContext context, LhScheduleResult result, double ratio) {
        List<ShiftInfo> shifts = LhScheduleTimeUtil.getScheduleShifts(context, context.getScheduleDate());
        for (ShiftInfo s : shifts) {
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
