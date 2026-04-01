/**
 * Copyright (c) 2008, 智立通（厦门）科技有限公司 All rights reserved。
 */
package com.zlt.aps.lh.engine.strategy.impl;

import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.dto.MachineScheduleDTO;
import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.api.domain.entity.LhScheduleResult;
import com.zlt.aps.lh.api.domain.entity.LhUnscheduledResult;
import com.zlt.aps.lh.engine.strategy.ICapacityCalculateStrategy;
import com.zlt.aps.lh.engine.strategy.IFirstInspectionBalanceStrategy;
import com.zlt.aps.lh.engine.strategy.IMachineMatchStrategy;
import com.zlt.aps.lh.engine.strategy.IMouldChangeBalanceStrategy;
import com.zlt.aps.lh.engine.strategy.IProductionStrategy;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * 新增规格排产策略实现
 * <p>处理新增规格上机的排产逻辑, 包括机台匹配、换模均衡、首检分配、产能计算等</p>
 *
 * @author APS
 */
@Slf4j
@Component("newSpecProductionStrategy")
public class NewSpecProductionStrategy implements IProductionStrategy {

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
                scaleShiftPlanQty(result, ratio);
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

        List<LhScheduleTimeUtil.ShiftInfo> shifts = LhScheduleTimeUtil.getScheduleShifts(context, context.getScheduleDate());

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
                                                         List<LhScheduleTimeUtil.ShiftInfo> shifts,
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
        int remaining = distributeToShifts(result, shifts, startTime, sku.getLhTimeSeconds(), sku.getMouldQty(), pendingQty, capacityCalculate);

        int totalQty = calcTotalPlanQty(result);
        result.setTotalDailyPlanQty(totalQty);
        return result;
    }

    /**
     * 将计划量分配到各班次（从开产时间开始）
     *
     * @return 未排产的剩余量
     */
    private int distributeToShifts(LhScheduleResult result,
                                   List<LhScheduleTimeUtil.ShiftInfo> shifts,
                                   Date startTime,
                                   int lhTimeSeconds,
                                   int mouldQty,
                                   int remaining,
                                   ICapacityCalculateStrategy capacityCalculate) {
        if (lhTimeSeconds <= 0 || mouldQty <= 0 || remaining <= 0 || startTime == null) {
            return remaining;
        }

        boolean started = false;
        for (LhScheduleTimeUtil.ShiftInfo shift : shifts) {
            if (remaining <= 0) {
                break;
            }
            // 找到开产时间所在班次或之后的班次
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
                // 首班：使用首班计划量计算
                shiftMaxQty = capacityCalculate.calculateFirstShiftQty(effectiveStart, shift.getEndTime(), lhTimeSeconds, mouldQty);
            } else {
                // 后续班次：使用标准班产
                shiftMaxQty = capacityCalculate.calculateShiftCapacity(lhTimeSeconds, mouldQty);
            }

            int shiftQty = Math.min(remaining, shiftMaxQty);
            if (shiftQty > 0) {
                setShiftPlanQty(result, shift.getShiftIndex(), shiftQty, effectiveStart, shift.getEndTime());
                remaining -= shiftQty;
                startTime = shift.getEndTime();
            }
        }
        return remaining;
    }

    private void setShiftPlanQty(LhScheduleResult result, int shiftIndex, int qty, Date startTime, Date endTime) {
        switch (shiftIndex) {
            case 1: result.setClass1PlanQty(qty); result.setClass1StartTime(startTime); result.setClass1EndTime(endTime); break;
            case 2: result.setClass2PlanQty(qty); result.setClass2StartTime(startTime); result.setClass2EndTime(endTime); break;
            case 3: result.setClass3PlanQty(qty); result.setClass3StartTime(startTime); result.setClass3EndTime(endTime); break;
            case 4: result.setClass4PlanQty(qty); result.setClass4StartTime(startTime); result.setClass4EndTime(endTime); break;
            case 5: result.setClass5PlanQty(qty); result.setClass5StartTime(startTime); result.setClass5EndTime(endTime); break;
            case 6: result.setClass6PlanQty(qty); result.setClass6StartTime(startTime); result.setClass6EndTime(endTime); break;
            case 7: result.setClass7PlanQty(qty); result.setClass7StartTime(startTime); result.setClass7EndTime(endTime); break;
            case 8: result.setClass8PlanQty(qty); result.setClass8StartTime(startTime); result.setClass8EndTime(endTime); break;
            default: log.warn("未知班次索引: {}", shiftIndex); break;
        }
    }

    private int calcTotalPlanQty(LhScheduleResult result) {
        int total = 0;
        total += (result.getClass1PlanQty() != null ? result.getClass1PlanQty() : 0);
        total += (result.getClass2PlanQty() != null ? result.getClass2PlanQty() : 0);
        total += (result.getClass3PlanQty() != null ? result.getClass3PlanQty() : 0);
        total += (result.getClass4PlanQty() != null ? result.getClass4PlanQty() : 0);
        total += (result.getClass5PlanQty() != null ? result.getClass5PlanQty() : 0);
        total += (result.getClass6PlanQty() != null ? result.getClass6PlanQty() : 0);
        total += (result.getClass7PlanQty() != null ? result.getClass7PlanQty() : 0);
        total += (result.getClass8PlanQty() != null ? result.getClass8PlanQty() : 0);
        return total;
    }

    private void scaleShiftPlanQty(LhScheduleResult result, double ratio) {
        for (int i = 1; i <= 8; i++) {
            Integer qty = getShiftPlanQty(result, i);
            if (qty != null && qty > 0) {
                setShiftPlanQty(result, i, (int) (qty * ratio), getShiftStartTime(result, i), getShiftEndTime(result, i));
            }
        }
    }

    private Integer getShiftPlanQty(LhScheduleResult result, int shiftIndex) {
        switch (shiftIndex) {
            case 1: return result.getClass1PlanQty();
            case 2: return result.getClass2PlanQty();
            case 3: return result.getClass3PlanQty();
            case 4: return result.getClass4PlanQty();
            case 5: return result.getClass5PlanQty();
            case 6: return result.getClass6PlanQty();
            case 7: return result.getClass7PlanQty();
            case 8: return result.getClass8PlanQty();
            default: return null;
        }
    }

    private Date getShiftStartTime(LhScheduleResult result, int shiftIndex) {
        switch (shiftIndex) {
            case 1: return result.getClass1StartTime();
            case 2: return result.getClass2StartTime();
            case 3: return result.getClass3StartTime();
            case 4: return result.getClass4StartTime();
            case 5: return result.getClass5StartTime();
            case 6: return result.getClass6StartTime();
            case 7: return result.getClass7StartTime();
            case 8: return result.getClass8StartTime();
            default: return null;
        }
    }

    private Date getShiftEndTime(LhScheduleResult result, int shiftIndex) {
        switch (shiftIndex) {
            case 1: return result.getClass1EndTime();
            case 2: return result.getClass2EndTime();
            case 3: return result.getClass3EndTime();
            case 4: return result.getClass4EndTime();
            case 5: return result.getClass5EndTime();
            case 6: return result.getClass6EndTime();
            case 7: return result.getClass7EndTime();
            case 8: return result.getClass8EndTime();
            default: return null;
        }
    }

    /**
     * 生成工单号
     */
    private static int orderSeq = 0;

    private String generateOrderNo(LhScheduleContext context) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd");
        String dateStr = sdf.format(context.getScheduleTargetDate());
        int seq = (++orderSeq) % 1000;
        return String.format("%s%s%03d", "LHGD", dateStr, seq);
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
