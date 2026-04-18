package com.zlt.aps.lh.regression;

import com.zlt.aps.lh.api.domain.dto.MachineScheduleDTO;
import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.api.domain.entity.LhScheduleResult;
import com.zlt.aps.lh.component.OrderNoGenerator;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.engine.strategy.IEndingJudgmentStrategy;
import com.zlt.aps.lh.engine.strategy.impl.ContinuousProductionStrategy;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import com.zlt.aps.mdm.api.domain.entity.MdmSkuMouldRel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 续作收尾与换活字块衔接回归测试。
 */
@ExtendWith(MockitoExtension.class)
class ContinuousProductionTypeBlockRegressionTest {

    @Mock
    private OrderNoGenerator orderNoGenerator;

    @Mock
    private IEndingJudgmentStrategy endingJudgmentStrategy;

    @InjectMocks
    private ContinuousProductionStrategy strategy;

    @Test
    void scheduleTypeBlockChange_shouldUseContinuousSpecEndTimeAfterEndingPass() {
        LhScheduleContext context = newContext();
        MachineScheduleDTO machine = buildMachine("M1", "MAT-C1");
        context.getMachineScheduleMap().put(machine.getMachineCode(), machine);
        context.getContinuousSkuList().add(buildContinuousSku("MAT-C1", "M1", 2, 8));
        context.getNewSpecSkuList().add(buildTypeBlockSku("MAT-N1", "MAT-C1", 8));
        putMouldRel(context, "MAT-C1", "MOULD-1");
        putMouldRel(context, "MAT-N1", "MOULD-1");

        when(orderNoGenerator.generateOrderNo(any())).thenReturn("ORD-1", "ORD-2");
        when(endingJudgmentStrategy.isEnding(any(), any())).thenAnswer(invocation -> {
            SkuScheduleDTO sku = invocation.getArgument(1);
            return "MAT-C1".equals(sku.getMaterialCode());
        });

        strategy.scheduleContinuousEnding(context);
        strategy.scheduleTypeBlockChange(context);

        assertEquals(2, context.getScheduleResultList().size());
        LhScheduleResult continuousResult = context.getScheduleResultList().get(0);
        LhScheduleResult typeBlockResult = context.getScheduleResultList().get(1);
        assertEquals("MAT-C1", continuousResult.getMaterialCode());
        assertEquals("MAT-N1", typeBlockResult.getMaterialCode());
        assertNotNull(continuousResult.getSpecEndTime());
        assertEquals(LhScheduleTimeUtil.addHours(continuousResult.getSpecEndTime(),
                        LhScheduleTimeUtil.getFirstInspectionHours(context)),
                resolveFirstStartTime(typeBlockResult));
        assertFalse(context.getNewSpecSkuList().stream()
                .anyMatch(sku -> "MAT-N1".equals(sku.getMaterialCode())));
    }

    @Test
    void scheduleTypeBlockChange_shouldSkipMachinesThatDoNotEndInWindow() {
        LhScheduleContext context = newContext();
        MachineScheduleDTO machine = buildMachine("M1", "MAT-C1");
        context.getMachineScheduleMap().put(machine.getMachineCode(), machine);
        context.getContinuousSkuList().add(buildContinuousSku("MAT-C1", "M1", 6, 8));
        context.getNewSpecSkuList().add(buildTypeBlockSku("MAT-N1", "MAT-C1", 8));
        putMouldRel(context, "MAT-C1", "MOULD-1");
        putMouldRel(context, "MAT-N1", "MOULD-1");

        when(orderNoGenerator.generateOrderNo(any())).thenReturn("ORD-1");
        when(endingJudgmentStrategy.isEnding(any(), any())).thenReturn(false);

        strategy.scheduleContinuousEnding(context);
        strategy.scheduleTypeBlockChange(context);

        assertEquals(1, context.getScheduleResultList().size());
        assertEquals("MAT-C1", context.getScheduleResultList().get(0).getMaterialCode());
        assertEquals(1, context.getNewSpecSkuList().size());
        assertEquals("MAT-N1", context.getNewSpecSkuList().get(0).getMaterialCode());
    }

    @Test
    void scheduleTypeBlockChange_shouldFollowUpdatedMachineEndTimeOrder() {
        LhScheduleContext context = newContext();
        context.getMachineScheduleMap().put("M1", buildMachine("M1", "MAT-C1"));
        context.getMachineScheduleMap().put("M2", buildMachine("M2", "MAT-C2"));
        context.getContinuousSkuList().add(buildContinuousSku("MAT-C1", "M1", 1, 8));
        context.getContinuousSkuList().add(buildContinuousSku("MAT-C2", "M2", 3, 8));
        context.getNewSpecSkuList().add(buildTypeBlockSku("MAT-N1", "MAT-C1", 8));
        context.getNewSpecSkuList().add(buildTypeBlockSku("MAT-N2", "MAT-C2", 8));
        putMouldRel(context, "MAT-C1", "MOULD-1");
        putMouldRel(context, "MAT-N1", "MOULD-1");
        putMouldRel(context, "MAT-C2", "MOULD-2");
        putMouldRel(context, "MAT-N2", "MOULD-2");

        when(orderNoGenerator.generateOrderNo(any())).thenReturn("ORD-1", "ORD-2", "ORD-3", "ORD-4");
        when(endingJudgmentStrategy.isEnding(any(), any())).thenAnswer(invocation -> {
            SkuScheduleDTO sku = invocation.getArgument(1);
            return sku.getMaterialCode().startsWith("MAT-C");
        });

        strategy.scheduleContinuousEnding(context);
        strategy.scheduleTypeBlockChange(context);

        List<LhScheduleResult> results = context.getScheduleResultList();
        assertEquals(Arrays.asList("MAT-C1", "MAT-C2", "MAT-N1", "MAT-N2"),
                Arrays.asList(
                        results.get(0).getMaterialCode(),
                        results.get(1).getMaterialCode(),
                        results.get(2).getMaterialCode(),
                        results.get(3).getMaterialCode()));
        assertEquals(LhScheduleTimeUtil.addHours(results.get(0).getSpecEndTime(),
                        LhScheduleTimeUtil.getFirstInspectionHours(context)),
                resolveFirstStartTime(results.get(2)));
        assertEquals(LhScheduleTimeUtil.addHours(results.get(1).getSpecEndTime(),
                        LhScheduleTimeUtil.getFirstInspectionHours(context)),
                resolveFirstStartTime(results.get(3)));
    }

    private LhScheduleContext newContext() {
        LhScheduleContext context = new LhScheduleContext();
        context.setFactoryCode("116");
        context.setBatchNo("LHPC20260418001");
        context.setScheduleDate(date(2026, 4, 18));
        context.setScheduleTargetDate(date(2026, 4, 20));
        context.setScheduleWindowShifts(LhScheduleTimeUtil.buildDefaultScheduleShifts(context, context.getScheduleDate()));
        context.setMachineScheduleMap(new LinkedHashMap<>());
        return context;
    }

    private MachineScheduleDTO buildMachine(String machineCode, String currentMaterialCode) {
        MachineScheduleDTO machine = new MachineScheduleDTO();
        machine.setMachineCode(machineCode);
        machine.setMachineName(machineCode);
        machine.setCurrentMaterialCode(currentMaterialCode);
        machine.setMaxMoldNum(1);
        machine.setEstimatedEndTime(dateTime(2026, 4, 18, 6, 0, 0));
        return machine;
    }

    private SkuScheduleDTO buildContinuousSku(String materialCode, String machineCode, int pendingQty, int shiftCapacity) {
        SkuScheduleDTO sku = new SkuScheduleDTO();
        sku.setMaterialCode(materialCode);
        sku.setMaterialDesc(materialCode);
        sku.setStructureName("S1");
        sku.setEmbryoCode(materialCode + "-EMB");
        sku.setContinuousMachineCode(machineCode);
        sku.setMonthPlanQty(pendingQty);
        sku.setWindowPlanQty(pendingQty);
        sku.setPendingQty(pendingQty);
        sku.setShiftCapacity(shiftCapacity);
        sku.setLhTimeSeconds(3600);
        sku.setScheduleType("01");
        return sku;
    }

    private SkuScheduleDTO buildTypeBlockSku(String materialCode, String embryoCode, int shiftCapacity) {
        SkuScheduleDTO sku = new SkuScheduleDTO();
        sku.setMaterialCode(materialCode);
        sku.setMaterialDesc(materialCode);
        sku.setStructureName("S1");
        sku.setEmbryoCode(embryoCode);
        sku.setMonthPlanQty(4);
        sku.setWindowPlanQty(4);
        sku.setPendingQty(4);
        sku.setShiftCapacity(shiftCapacity);
        sku.setLhTimeSeconds(3600);
        sku.setScheduleType("02");
        return sku;
    }

    private void putMouldRel(LhScheduleContext context, String materialCode, String mouldCode) {
        MdmSkuMouldRel relation = new MdmSkuMouldRel();
        relation.setMaterialCode(materialCode);
        relation.setMouldCode(mouldCode);
        context.getSkuMouldRelMap().put(materialCode, Arrays.asList(relation));
    }

    private Date resolveFirstStartTime(LhScheduleResult result) {
        if (result.getClass1StartTime() != null) {
            return result.getClass1StartTime();
        }
        if (result.getClass2StartTime() != null) {
            return result.getClass2StartTime();
        }
        if (result.getClass3StartTime() != null) {
            return result.getClass3StartTime();
        }
        if (result.getClass4StartTime() != null) {
            return result.getClass4StartTime();
        }
        if (result.getClass5StartTime() != null) {
            return result.getClass5StartTime();
        }
        if (result.getClass6StartTime() != null) {
            return result.getClass6StartTime();
        }
        if (result.getClass7StartTime() != null) {
            return result.getClass7StartTime();
        }
        return result.getClass8StartTime();
    }

    private static Date date(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month - 1);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        return calendar.getTime();
    }

    private static Date dateTime(int year, int month, int day, int hour, int minute, int second) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month - 1);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, second);
        return calendar.getTime();
    }
}
