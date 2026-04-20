package com.zlt.aps.lh.regression;

import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.api.domain.entity.LhMachineOnlineInfo;
import com.zlt.aps.lh.api.domain.entity.LhScheduleResult;
import com.zlt.aps.lh.api.domain.entity.LhShiftFinishQty;
import com.zlt.aps.lh.api.enums.ScheduleTypeEnum;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.engine.strategy.impl.DefaultEndingJudgmentStrategy;
import com.zlt.aps.lh.handler.ScheduleAdjustHandler;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import com.zlt.aps.mdm.api.domain.entity.MdmSkuLhCapacity;
import com.zlt.aps.mp.api.domain.entity.FactoryMonthPlanProductionFinalResult;
import com.zlt.aps.mp.api.domain.entity.MpAdjustResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 欠产传导：前一日净欠产应进入当天需求对象，而不是回写昨天计划。
 */
class ScheduleAdjustCarryForwardRegressionTest {

    private final ScheduleAdjustHandler handler = new ScheduleAdjustHandler();

    @Test
    void doHandle_carriesForwardDeficitIntoTodayPendingQty() {
        ReflectionTestUtils.setField(handler, "endingJudgmentStrategy", new DefaultEndingJudgmentStrategy());

        LhScheduleContext context = new LhScheduleContext();
        context.setScheduleDate(date(2026, 4, 11));
        context.setScheduleTargetDate(date(2026, 4, 13));
        context.setScheduleWindowShifts(LhScheduleTimeUtil.buildDefaultScheduleShifts(context, context.getScheduleDate()));

        FactoryMonthPlanProductionFinalResult plan = new FactoryMonthPlanProductionFinalResult();
        plan.setMaterialCode("MAT-1");
        plan.setMaterialDesc("MAT-1-DESC");
        plan.setStructureName("S1");
        plan.setSpecifications("SPEC-1");
        plan.setProSize("18");
        plan.setTotalQty(1000);
        plan.setDayVulcanizationQty(120);
        plan.setDay11(40);
        plan.setDay12(40);
        plan.setDay13(20);
        context.setMonthPlanList(Collections.singletonList(plan));

        MdmSkuLhCapacity capacity = new MdmSkuLhCapacity();
        capacity.setMaterialCode("MAT-1");
        capacity.setClassCapacity(30);
        capacity.setApsCapacity(90);
        context.getSkuLhCapacityMap().put("MAT-1", capacity);

        LhScheduleResult previous = new LhScheduleResult();
        previous.setLhMachineCode("M1");
        previous.setMaterialCode("MAT-1");
        previous.setClass1PlanQty(80);
        context.setPreviousScheduleResultList(Collections.singletonList(previous));

        LhShiftFinishQty finishQty = new LhShiftFinishQty();
        finishQty.setLhMachineCode("M1");
        finishQty.setMaterialCode("MAT-1");
        finishQty.setClass1FinishQty(60);
        context.getShiftFinishQtyMap().put("M1_MAT-1", finishQty);

        ReflectionTestUtils.invokeMethod(handler, "doHandle", context);

        SkuScheduleDTO sku = context.getStructureSkuMap().get("S1").get(0);
        assertEquals(20, context.getCarryForwardQtyMap().get("MAT-1").intValue());
        assertEquals(100, sku.getWindowPlanQty());
        assertEquals(120, sku.getPendingQty());
        assertEquals(940, sku.getSurplusQty());
        assertEquals(120, sku.getTargetScheduleQty().intValue());
    }

    @Test
    void doHandle_shouldPreferMonthAccumulatedFinishedQtyForSurplus() {
        ReflectionTestUtils.setField(handler, "endingJudgmentStrategy", new DefaultEndingJudgmentStrategy());

        LhScheduleContext context = new LhScheduleContext();
        context.setScheduleDate(date(2026, 4, 11));
        context.setScheduleTargetDate(date(2026, 4, 13));
        context.setScheduleWindowShifts(LhScheduleTimeUtil.buildDefaultScheduleShifts(context, context.getScheduleDate()));

        FactoryMonthPlanProductionFinalResult plan = new FactoryMonthPlanProductionFinalResult();
        plan.setMaterialCode("MAT-MONTH");
        plan.setMaterialDesc("MAT-MONTH-DESC");
        plan.setStructureName("SM");
        plan.setSpecifications("SPEC-M");
        plan.setTotalQty(1000);
        plan.setDay11(40);
        plan.setDay12(40);
        plan.setDay13(20);
        context.setMonthPlanList(Collections.singletonList(plan));

        // 月累计完成量应优先于前日结果兜底值
        context.getMaterialMonthFinishedQtyMap().put("MAT-MONTH", 880);

        ReflectionTestUtils.invokeMethod(handler, "doHandle", context);

        SkuScheduleDTO sku = context.getStructureSkuMap().get("SM").get(0);
        assertEquals(120, sku.getSurplusQty());
        assertEquals(100, sku.getWindowPlanQty());
        assertEquals(100, sku.getTargetScheduleQty().intValue());
    }

    @Test
    void doHandle_skipsSkuWhenWindowPlanQtyAndCarryForwardAreBothZero() {
        ReflectionTestUtils.setField(handler, "endingJudgmentStrategy", new DefaultEndingJudgmentStrategy());

        LhScheduleContext context = new LhScheduleContext();
        context.setScheduleDate(date(2026, 4, 11));
        context.setScheduleTargetDate(date(2026, 4, 13));
        context.setScheduleWindowShifts(LhScheduleTimeUtil.buildDefaultScheduleShifts(context, context.getScheduleDate()));

        FactoryMonthPlanProductionFinalResult plan = new FactoryMonthPlanProductionFinalResult();
        plan.setMaterialCode("MAT-2");
        plan.setMaterialDesc("MAT-2-DESC");
        plan.setStructureName("S2");
        plan.setSpecifications("SPEC-2");
        plan.setTotalQty(0);
        context.setMonthPlanList(Collections.singletonList(plan));

        ReflectionTestUtils.invokeMethod(handler, "doHandle", context);

        assertEquals(0, context.getStructureSkuMap().size());
        assertEquals(1, context.getUnscheduledResultList().size());
        assertEquals("物料：MAT-2 没有排产目标量，不进行排产",
                context.getUnscheduledResultList().get(0).getUnscheduledReason());
    }

    @Test
    void doHandle_keepsSkuWhenOnlyCarryForwardNeedsScheduling() {
        ReflectionTestUtils.setField(handler, "endingJudgmentStrategy", new DefaultEndingJudgmentStrategy());

        LhScheduleContext context = new LhScheduleContext();
        context.setScheduleDate(date(2026, 4, 11));
        context.setScheduleTargetDate(date(2026, 4, 13));
        context.setScheduleWindowShifts(LhScheduleTimeUtil.buildDefaultScheduleShifts(context, context.getScheduleDate()));

        FactoryMonthPlanProductionFinalResult plan = new FactoryMonthPlanProductionFinalResult();
        plan.setMaterialCode("MAT-3");
        plan.setMaterialDesc("MAT-3-DESC");
        plan.setStructureName("S3");
        plan.setSpecifications("SPEC-3");
        plan.setTotalQty(200);
        context.setMonthPlanList(Collections.singletonList(plan));

        LhScheduleResult previous = new LhScheduleResult();
        previous.setLhMachineCode("M3");
        previous.setMaterialCode("MAT-3");
        previous.setClass1PlanQty(50);
        context.setPreviousScheduleResultList(Collections.singletonList(previous));

        LhShiftFinishQty finishQty = new LhShiftFinishQty();
        finishQty.setLhMachineCode("M3");
        finishQty.setMaterialCode("MAT-3");
        finishQty.setClass1FinishQty(20);
        context.getShiftFinishQtyMap().put("M3_MAT-3", finishQty);

        ReflectionTestUtils.invokeMethod(handler, "doHandle", context);

        SkuScheduleDTO sku = context.getStructureSkuMap().get("S3").get(0);
        assertEquals(0, sku.getWindowPlanQty());
        assertEquals(30, sku.getPendingQty());
        assertEquals(30, sku.getTargetScheduleQty().intValue());
        assertEquals(0, context.getUnscheduledResultList().size());
    }

    @Test
    void doHandle_shouldFillDeliveryLockDelayDaysAndSupplyPendingQty() {
        ReflectionTestUtils.setField(handler, "endingJudgmentStrategy", new DefaultEndingJudgmentStrategy());

        LhScheduleContext context = new LhScheduleContext();
        context.setScheduleDate(date(2026, 4, 11));
        context.setScheduleTargetDate(date(2026, 4, 13));
        context.setScheduleWindowShifts(LhScheduleTimeUtil.buildDefaultScheduleShifts(context, context.getScheduleDate()));

        FactoryMonthPlanProductionFinalResult plan = new FactoryMonthPlanProductionFinalResult();
        plan.setMaterialCode("MAT-LOCK");
        plan.setMaterialDesc("MAT-LOCK-DESC");
        plan.setStructureName("S-LOCK");
        plan.setSpecifications("SPEC-LOCK");
        plan.setTotalQty(200);
        plan.setDay9(20);
        plan.setDay11(40);
        plan.setHeightProductionQty(11);
        plan.setCycleProductionQty(9);
        plan.setMidProductionQty(7);
        plan.setConventionProductionQty(5);
        context.setMonthPlanList(Collections.singletonList(plan));

        MpAdjustResult adjustResult = new MpAdjustResult();
        adjustResult.setMaterialCode("MAT-LOCK");
        adjustResult.setIsLockSchedule("1");
        context.getMpAdjustResultMap().put("MAT-LOCK", Collections.singletonList(adjustResult));

        ReflectionTestUtils.invokeMethod(handler, "doHandle", context);

        SkuScheduleDTO sku = context.getStructureSkuMap().get("S-LOCK").get(0);
        assertEquals(true, sku.isDeliveryLocked());
        assertEquals(2, sku.getDelayDays());
        assertEquals(11, sku.getHighPriorityPendingQty());
        assertEquals(9, sku.getCycleProductionPendingQty());
        assertEquals(7, sku.getMidPriorityPendingQty());
        assertEquals(5, sku.getConventionProductionPendingQty());
    }

    @Test
    void doHandle_skipsSkuWhenWindowPlanQtyZeroAndNoCarryForwardEvenIfSurplusPositive() {
        ReflectionTestUtils.setField(handler, "endingJudgmentStrategy", new DefaultEndingJudgmentStrategy());

        LhScheduleContext context = new LhScheduleContext();
        context.setScheduleDate(date(2026, 4, 11));
        context.setScheduleTargetDate(date(2026, 4, 13));
        context.setScheduleWindowShifts(LhScheduleTimeUtil.buildDefaultScheduleShifts(context, context.getScheduleDate()));

        FactoryMonthPlanProductionFinalResult plan = new FactoryMonthPlanProductionFinalResult();
        plan.setMaterialCode("MAT-4");
        plan.setMaterialDesc("MAT-4-DESC");
        plan.setStructureName("S4");
        plan.setSpecifications("SPEC-4");
        plan.setTotalQty(300);
        context.setMonthPlanList(Collections.singletonList(plan));

        ReflectionTestUtils.invokeMethod(handler, "doHandle", context);

        assertEquals(0, context.getStructureSkuMap().size());
        assertEquals(1, context.getUnscheduledResultList().size());
        assertEquals("物料：MAT-4 没有排产目标量，不进行排产",
                context.getUnscheduledResultList().get(0).getUnscheduledReason());
    }

    @Test
    void doHandle_shouldMatchContinuousSkuByMachineOnlineInfo() {
        ReflectionTestUtils.setField(handler, "endingJudgmentStrategy", new DefaultEndingJudgmentStrategy());

        LhScheduleContext context = new LhScheduleContext();
        context.setScheduleDate(date(2026, 4, 11));
        context.setScheduleTargetDate(date(2026, 4, 13));
        context.setScheduleWindowShifts(LhScheduleTimeUtil.buildDefaultScheduleShifts(context, context.getScheduleDate()));
        context.setMonthPlanList(Arrays.asList(
                buildPlan("MAT-A", "S1", 100, 10),
                buildPlan("MAT-B", "S2", 100, 10),
                buildPlan("MAT-C", "S3", 100, 10)));
        context.setMachineOnlineInfoMap(buildMachineOnlineInfoMap(
                buildOnlineInfo("K1501", "MAT-A"),
                buildOnlineInfo("K1502", "MAT-X")));

        ReflectionTestUtils.invokeMethod(handler, "doHandle", context);

        SkuScheduleDTO continuousSku = context.getStructureSkuMap().get("S1").get(0);
        SkuScheduleDTO newSku = context.getStructureSkuMap().get("S2").get(0);
        SkuScheduleDTO unmatchedSku = context.getStructureSkuMap().get("S3").get(0);
        assertEquals(1, context.getContinuousSkuList().size());
        assertEquals(2, context.getNewSpecSkuList().size());
        assertEquals(ScheduleTypeEnum.CONTINUOUS.getCode(), continuousSku.getScheduleType());
        assertEquals("K1501", continuousSku.getContinuousMachineCode());
        assertEquals(ScheduleTypeEnum.NEW_SPEC.getCode(), newSku.getScheduleType());
        assertNull(newSku.getContinuousMachineCode());
        assertEquals(ScheduleTypeEnum.NEW_SPEC.getCode(), unmatchedSku.getScheduleType());
        assertNull(unmatchedSku.getContinuousMachineCode());
    }

    @Test
    void doHandle_shouldConsumeDuplicatedMaterialSkusOncePerMachine() {
        ReflectionTestUtils.setField(handler, "endingJudgmentStrategy", new DefaultEndingJudgmentStrategy());

        LhScheduleContext context = new LhScheduleContext();
        context.setScheduleDate(date(2026, 4, 11));
        context.setScheduleTargetDate(date(2026, 4, 13));
        context.setScheduleWindowShifts(LhScheduleTimeUtil.buildDefaultScheduleShifts(context, context.getScheduleDate()));
        context.setMonthPlanList(Arrays.asList(
                buildPlan("MAT-DUP", "S1", 100, 10),
                buildPlan("MAT-DUP", "S2", 100, 10)));
        context.setMachineOnlineInfoMap(buildMachineOnlineInfoMap(
                buildOnlineInfo("K1501", "MAT-DUP"),
                buildOnlineInfo("K1502", "MAT-DUP"),
                buildOnlineInfo("K1503", "MAT-DUP")));

        ReflectionTestUtils.invokeMethod(handler, "doHandle", context);

        SkuScheduleDTO firstSku = context.getStructureSkuMap().get("S1").get(0);
        SkuScheduleDTO secondSku = context.getStructureSkuMap().get("S2").get(0);
        assertEquals(2, context.getContinuousSkuList().size());
        assertEquals(0, context.getNewSpecSkuList().size());
        assertEquals("K1501", firstSku.getContinuousMachineCode());
        assertEquals("K1502", secondSku.getContinuousMachineCode());
    }

    private FactoryMonthPlanProductionFinalResult buildPlan(String materialCode, String structureName,
                                                            int totalQty, int day11Qty) {
        FactoryMonthPlanProductionFinalResult plan = new FactoryMonthPlanProductionFinalResult();
        plan.setMaterialCode(materialCode);
        plan.setMaterialDesc(materialCode + "-DESC");
        plan.setStructureName(structureName);
        plan.setSpecifications(structureName + "-SPEC");
        plan.setTotalQty(totalQty);
        plan.setDay11(day11Qty);
        return plan;
    }

    private Map<String, LhMachineOnlineInfo> buildMachineOnlineInfoMap(LhMachineOnlineInfo... onlineInfos) {
        Map<String, LhMachineOnlineInfo> machineOnlineInfoMap = new LinkedHashMap<>();
        for (LhMachineOnlineInfo onlineInfo : onlineInfos) {
            machineOnlineInfoMap.put(onlineInfo.getLhCode(), onlineInfo);
        }
        return machineOnlineInfoMap;
    }

    private LhMachineOnlineInfo buildOnlineInfo(String machineCode, String materialCode) {
        LhMachineOnlineInfo onlineInfo = new LhMachineOnlineInfo();
        onlineInfo.setLhCode(machineCode);
        onlineInfo.setMaterialCode(materialCode);
        return onlineInfo;
    }

    private static java.util.Date date(int y, int month, int day) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(Calendar.YEAR, y);
        c.set(Calendar.MONTH, month - 1);
        c.set(Calendar.DAY_OF_MONTH, day);
        return c.getTime();
    }
}
