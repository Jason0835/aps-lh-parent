package com.zlt.aps.lh.regression;

import com.zlt.aps.lh.api.domain.dto.MachineScheduleDTO;
import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.api.domain.entity.LhScheduleResult;
import com.zlt.aps.lh.component.OrderNoGenerator;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.engine.strategy.IEndingJudgmentStrategy;
import com.zlt.aps.lh.engine.strategy.impl.ContinuousProductionStrategy;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 续作结果字段口径回归：月总量与本次实际排产量需分离保存。
 */
@ExtendWith(MockitoExtension.class)
class ContinuousProductionResultQtyRegressionTest {

    @Mock
    private OrderNoGenerator orderNoGenerator;

    @Mock
    private IEndingJudgmentStrategy endingJudgmentStrategy;

    @InjectMocks
    private ContinuousProductionStrategy strategy;

    @Test
    void scheduleContinuousEnding_shouldStoreMonthQtyAndScheduledQtySeparately() {
        LhScheduleContext context = newContext();
        MachineScheduleDTO machine = new MachineScheduleDTO();
        machine.setMachineCode("M1");
        machine.setMachineName("FC-M1");
        machine.setMaxMoldNum(2);
        context.setMachineScheduleMap(new LinkedHashMap<>());
        context.getMachineScheduleMap().put("M1", machine);

        SkuScheduleDTO sku = new SkuScheduleDTO();
        sku.setMaterialCode("MAT-C1");
        sku.setMaterialDesc("MAT-C1-DESC");
        sku.setStructureName("S1");
        sku.setSpecCode("SPEC-C1");
        sku.setEmbryoCode("EMB-1");
        sku.setContinuousMachineCode("M1");
        sku.setMonthPlanQty(50);
        sku.setWindowPlanQty(30);
        sku.setPendingQty(1000);
        sku.setTargetScheduleQty(30);
        sku.setShiftCapacity(16);
        sku.setLhTimeSeconds(3060);
        sku.setScheduleType("01");
        context.getContinuousSkuList().add(sku);

        when(orderNoGenerator.generateOrderNo(any())).thenReturn("LHGD20260411011");
        when(endingJudgmentStrategy.isEnding(any(), any())).thenReturn(false);

        strategy.scheduleContinuousEnding(context);

        LhScheduleResult result = context.getScheduleResultList().get(0);
        assertEquals("LR", result.getLeftRightMould());
        assertEquals(50, result.getTotalDailyPlanQty());
        assertEquals(30, result.getDailyPlanQty());
        assertEquals(16, result.getClass1PlanQty());
        assertEquals(14, result.getClass2PlanQty());
    }

    @Test
    void scheduleContinuousEnding_shouldCapScheduledQtyByEightShiftCapacityWhenTargetExceedsCapacity() {
        LhScheduleContext context = newContext();
        MachineScheduleDTO machine = new MachineScheduleDTO();
        machine.setMachineCode("M2");
        machine.setMachineName("FC-M2");
        machine.setMaxMoldNum(2);
        context.setMachineScheduleMap(new LinkedHashMap<>());
        context.getMachineScheduleMap().put("M2", machine);

        SkuScheduleDTO sku = new SkuScheduleDTO();
        sku.setMaterialCode("MAT-C2");
        sku.setMaterialDesc("MAT-C2-DESC");
        sku.setStructureName("S2");
        sku.setSpecCode("SPEC-C2");
        sku.setEmbryoCode("EMB-2");
        sku.setContinuousMachineCode("M2");
        sku.setMonthPlanQty(300);
        sku.setWindowPlanQty(160);
        sku.setPendingQty(160);
        sku.setTargetScheduleQty(160);
        sku.setShiftCapacity(16);
        sku.setLhTimeSeconds(3060);
        sku.setScheduleType("01");
        context.getContinuousSkuList().add(sku);

        when(orderNoGenerator.generateOrderNo(any())).thenReturn("LHGD20260411012");
        when(endingJudgmentStrategy.isEnding(any(), any())).thenReturn(false);

        strategy.scheduleContinuousEnding(context);

        LhScheduleResult result = context.getScheduleResultList().get(0);
        assertEquals(300, result.getTotalDailyPlanQty());
        assertEquals(128, result.getDailyPlanQty(), "目标量超过8班总产能时，应按8班最大产能排满");
        assertEquals(16, result.getClass1PlanQty());
        assertEquals(16, result.getClass8PlanQty());
    }

    @Test
    void buildScheduleResult_shouldRefineTargetQtyByMachineActualStartTime() {
        LhScheduleContext context = newContext();
        MachineScheduleDTO machine = new MachineScheduleDTO();
        machine.setMachineCode("M3");
        machine.setMachineName("FC-M3");
        machine.setMaxMoldNum(1);
        context.setMachineScheduleMap(new LinkedHashMap<>());
        context.getMachineScheduleMap().put("M3", machine);

        SkuScheduleDTO sku = new SkuScheduleDTO();
        sku.setMaterialCode("MAT-C3");
        sku.setMaterialDesc("MAT-C3-DESC");
        sku.setStructureName("S3");
        sku.setSpecCode("SPEC-C3");
        sku.setEmbryoCode("EMB-3");
        sku.setContinuousMachineCode("M3");
        sku.setMonthPlanQty(300);
        sku.setWindowPlanQty(8);
        sku.setPendingQty(8);
        sku.setTargetScheduleQty(128);
        sku.setShiftCapacity(16);
        sku.setLhTimeSeconds(3060);
        sku.setScheduleType("01");

        when(orderNoGenerator.generateOrderNo(any())).thenReturn("LHGD20260411013");
        Date startTime = context.getScheduleWindowShifts().get(1).getShiftStartDateTime();
        LhScheduleResult result = ReflectionTestUtils.invokeMethod(
                strategy,
                "buildScheduleResult",
                context,
                machine,
                sku,
                startTime,
                context.getScheduleWindowShifts(),
                1,
                false);

        assertEquals(112, result.getDailyPlanQty().intValue(), "续作应按机台实际开产后的剩余窗口产能收敛目标量");
        assertEquals(128, sku.getTargetScheduleQty().intValue(), "机台局部收敛值不应覆盖物料级全局目标量");
    }

    @Test
    void scheduleReduceMould_shouldKeepGlobalTargetWhenSingleMachineResultIsRefined() {
        LhScheduleContext context = newContext();
        MachineScheduleDTO machine1 = new MachineScheduleDTO();
        machine1.setMachineCode("M4");
        machine1.setMachineName("FC-M4");
        machine1.setMaxMoldNum(1);
        MachineScheduleDTO machine2 = new MachineScheduleDTO();
        machine2.setMachineCode("M5");
        machine2.setMachineName("FC-M5");
        machine2.setMaxMoldNum(1);
        context.setMachineScheduleMap(new LinkedHashMap<>());
        context.getMachineScheduleMap().put("M4", machine1);
        context.getMachineScheduleMap().put("M5", machine2);

        SkuScheduleDTO sku = new SkuScheduleDTO();
        sku.setMaterialCode("MAT-C4");
        sku.setMaterialDesc("MAT-C4-DESC");
        sku.setStructureName("S4");
        sku.setSpecCode("SPEC-C4");
        sku.setEmbryoCode("EMB-4");
        sku.setContinuousMachineCode("M4");
        sku.setMonthPlanQty(300);
        sku.setWindowPlanQty(8);
        sku.setPendingQty(8);
        sku.setTargetScheduleQty(128);
        sku.setShiftCapacity(16);
        sku.setLhTimeSeconds(3060);
        sku.setScheduleType("01");
        context.getContinuousSkuList().add(sku);

        when(orderNoGenerator.generateOrderNo(any())).thenReturn("LHGD20260411014");
        Date secondShiftStart = context.getScheduleWindowShifts().get(1).getShiftStartDateTime();
        LhScheduleResult refinedResult = ReflectionTestUtils.invokeMethod(
                strategy,
                "buildScheduleResult",
                context,
                machine1,
                sku,
                secondShiftStart,
                context.getScheduleWindowShifts(),
                1,
                false);
        context.getScheduleResultList().add(refinedResult);

        LhScheduleResult extraResult = new LhScheduleResult();
        extraResult.setLhMachineCode("M5");
        extraResult.setMaterialCode("MAT-C4");
        extraResult.setScheduleType("01");
        extraResult.setLhTime(sku.getLhTimeSeconds());
        extraResult.setMouldQty(1);
        extraResult.setSingleMouldShiftQty(16);
        extraResult.setDailyPlanQty(8);
        extraResult.setClass1PlanQty(8);
        extraResult.setClass1StartTime(context.getScheduleWindowShifts().get(0).getShiftStartDateTime());
        extraResult.setClass1EndTime(context.getScheduleWindowShifts().get(0).getShiftEndDateTime());
        context.getScheduleResultList().add(extraResult);

        strategy.scheduleReduceMould(context);

        List<LhScheduleResult> materialResults = context.getScheduleResultList();
        int totalScheduledQty = materialResults.stream()
                .filter(result -> "MAT-C4".equals(result.getMaterialCode()))
                .mapToInt(LhScheduleResult::getDailyPlanQty)
                .sum();
        assertEquals(120, totalScheduledQty, "降模应按物料级全局目标量判断，不能被单机收敛值误缩到112");
        assertEquals(128, sku.getTargetScheduleQty().intValue(), "续作降模前后都应保留物料级全局目标量");
    }

    private LhScheduleContext newContext() {
        LhScheduleContext context = new LhScheduleContext();
        context.setFactoryCode("116");
        context.setBatchNo("LHPC20260411011");
        context.setScheduleDate(date(2026, 4, 11));
        context.setScheduleTargetDate(date(2026, 4, 13));
        context.setScheduleWindowShifts(LhScheduleTimeUtil.buildDefaultScheduleShifts(context, context.getScheduleDate()));
        return context;
    }

    private static Date date(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month - 1);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        return calendar.getTime();
    }
}
