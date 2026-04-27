package com.zlt.aps.lh.regression;

import com.zlt.aps.lh.api.constant.LhScheduleParamConstant;
import com.zlt.aps.lh.api.domain.dto.MachineCleaningWindowDTO;
import com.zlt.aps.lh.api.domain.dto.MachineScheduleDTO;
import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.api.domain.entity.LhMachineOnlineInfo;
import com.zlt.aps.lh.api.domain.entity.LhScheduleProcessLog;
import com.zlt.aps.lh.api.domain.entity.LhScheduleResult;
import com.zlt.aps.lh.component.OrderNoGenerator;
import com.zlt.aps.lh.context.LhScheduleConfig;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.engine.strategy.IEndingJudgmentStrategy;
import com.zlt.aps.lh.engine.strategy.impl.ContinuousProductionStrategy;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import com.zlt.aps.lh.util.ShiftFieldUtil;
import com.zlt.aps.mdm.api.domain.entity.MdmMaterialInfo;
import com.zlt.aps.mdm.api.domain.entity.MdmDevicePlanShut;
import com.zlt.aps.mdm.api.domain.entity.MdmSkuMouldRel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 续作阶段同产品结构直续/换活字块回归测试。
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
    void scheduleTypeBlockChange_shouldSkipSameStructureDirectContinuousAndUsePriorityOneCandidate() {
        LhScheduleContext context = newContext();
        context.getMachineScheduleMap().put("M1", buildMachine("M1", "MAT-C1"));
        context.getContinuousSkuList().add(buildContinuousSku("MAT-C1", "M1", "EMB-1", "STRUCT-A", "SPEC-A", "PAT-A", 1));
        context.getNewSpecSkuList().add(buildNewSku("MAT-S1", "EMB-1", "STRUCT-A", "SPEC-X", "PAT-X", 4));
        context.getNewSpecSkuList().add(buildNewSku("MAT-P1", "EMB-9", "STRUCT-B", "SPEC-B", "PAT-A", 4));
        putMaterialInfo(context, "MAT-C1", "胎胚描述-A", "SPEC-A", "PAT-A", "PAT-A");
        putMaterialInfo(context, "MAT-S1", "胎胚描述-X", "SPEC-X", "PAT-X", "PAT-X");
        putMaterialInfo(context, "MAT-P1", "胎胚描述-A", "SPEC-B", "PAT-A", "PAT-A");
        putMouldRel(context, "MAT-C1", "MOULD-1");
        putMouldRel(context, "MAT-S1", "MOULD-1");
        putMouldRel(context, "MAT-P1", "MOULD-1");

        when(orderNoGenerator.generateOrderNo(any())).thenReturn("ORD-1", "ORD-2");
        when(endingJudgmentStrategy.isEnding(any(), any())).thenAnswer(invocation -> {
            SkuScheduleDTO sku = invocation.getArgument(1);
            return "MAT-C1".equals(sku.getMaterialCode()) || "MAT-P1".equals(sku.getMaterialCode());
        });

        strategy.scheduleContinuousEnding(context);
        strategy.scheduleTypeBlockChange(context);

        assertEquals(2, context.getScheduleResultList().size());
        LhScheduleResult continuousResult = context.getScheduleResultList().get(0);
        LhScheduleResult followUpResult = context.getScheduleResultList().get(1);
        assertEquals("MAT-P1", followUpResult.getMaterialCode());
        assertEquals("02", followUpResult.getScheduleType());  // 换活字块现在显示为新增类型
        assertEquals("0", continuousResult.getIsTypeBlock());
        assertEquals("1", followUpResult.getIsEnd());
        assertEquals("1", followUpResult.getIsChangeMould());
        assertEquals("1", followUpResult.getIsTypeBlock());
        assertNotNull(followUpResult.getSpecEndTime());
        assertNotNull(followUpResult.getTdaySpecEndTime());
        assertEquals(LhScheduleTimeUtil.addHours(continuousResult.getSpecEndTime(),
                        LhScheduleTimeUtil.getTypeBlockChangeTotalHours(context)),
                resolveFirstStartTime(followUpResult));
        assertEquals("MAT-P1", context.getMachineScheduleMap().get("M1").getCurrentMaterialCode());
        assertEquals(1, context.getNewSpecSkuList().size());
        assertEquals("MAT-S1", context.getNewSpecSkuList().get(0).getMaterialCode());
    }

    @Test
    void scheduleTypeBlockChange_shouldUpdateMachineEstimatedEndTimeWithActualCompletion() {
        LhScheduleContext context = newContext();
        context.getMachineScheduleMap().put("M1", buildMachine("M1", "MAT-C1"));
        context.getContinuousSkuList().add(buildContinuousSku("MAT-C1", "M1", "EMB-1", "STRUCT-A", "SPEC-A", "PAT-A", 1));
        context.getNewSpecSkuList().add(buildNewSku("MAT-S1", "EMB-1", "STRUCT-A", "SPEC-A", "PAT-A", 1));
        putMouldRel(context, "MAT-C1", "MOULD-1");
        putMouldRel(context, "MAT-S1", "MOULD-1");

        when(orderNoGenerator.generateOrderNo(any())).thenReturn("ORD-1", "ORD-2");
        when(endingJudgmentStrategy.isEnding(any(), any())).thenAnswer(invocation -> {
            SkuScheduleDTO sku = invocation.getArgument(1);
            return "MAT-C1".equals(sku.getMaterialCode());
        });

        strategy.scheduleContinuousEnding(context);
        strategy.scheduleTypeBlockChange(context);

        LhScheduleResult followUpResult = context.getScheduleResultList().get(1);
        MachineScheduleDTO machine = context.getMachineScheduleMap().get("M1");
        Date expectedCompletionTime = resolveActualCompletionTime(followUpResult);
        Date lastShiftEndTime = resolveLastShiftEndTime(followUpResult);
        assertEquals("0", followUpResult.getIsEnd());
        assertEquals(expectedCompletionTime, followUpResult.getSpecEndTime());
        assertEquals(expectedCompletionTime, followUpResult.getTdaySpecEndTime());
        assertEquals(expectedCompletionTime, machine.getEstimatedEndTime());
        assertFalse(lastShiftEndTime.equals(machine.getEstimatedEndTime()));
    }

    @Test
    void scheduleTypeBlockChange_shouldUseTypeBlockHoursInsteadOfInspectionHours() {
        LhScheduleContext context = newContext();
        context.setScheduleConfig(createConfig(3, 9));
        context.getMachineScheduleMap().put("M1", buildMachine("M1", "MAT-C1"));
        context.getContinuousSkuList().add(buildContinuousSku("MAT-C1", "M1", "EMB-1", "STRUCT-A", "SPEC-A", "PAT-A", 1));
        context.getNewSpecSkuList().add(buildNewSku("MAT-T1", "EMB-1", "STRUCT-B", "SPEC-A", "PAT-B", 4));
        putMouldRel(context, "MAT-C1", "MOULD-1");
        putMouldRel(context, "MAT-T1", "MOULD-1");

        when(orderNoGenerator.generateOrderNo(any())).thenReturn("ORD-1", "ORD-2");
        when(endingJudgmentStrategy.isEnding(any(), any())).thenAnswer(invocation -> {
            SkuScheduleDTO sku = invocation.getArgument(1);
            return "MAT-C1".equals(sku.getMaterialCode());
        });

        strategy.scheduleContinuousEnding(context);
        strategy.scheduleTypeBlockChange(context);

        LhScheduleResult continuousResult = context.getScheduleResultList().get(0);
        LhScheduleResult typeBlockResult = context.getScheduleResultList().get(1);
        Date expectedStartTime = LhScheduleTimeUtil.addHours(continuousResult.getSpecEndTime(), 3);
        assertEquals("MAT-T1", typeBlockResult.getMaterialCode());
        assertEquals(expectedStartTime, resolveFirstStartTime(typeBlockResult));
        assertEquals(continuousResult.getSpecEndTime(),
                ReflectionTestUtils.getField(typeBlockResult, "mouldChangeStartTime"));
        assertEquals("1", typeBlockResult.getIsChangeMould());
        assertNotNull(typeBlockResult.getMouldCode());
        assertFalse(expectedStartTime.equals(LhScheduleTimeUtil.addHours(continuousResult.getSpecEndTime(), 9)));
    }

    @Test
    void scheduleTypeBlockChange_shouldWriteCleaningTypeBlockAnalysisWhenWindowHitsShift() {
        LhScheduleContext context = newContext();
        MachineScheduleDTO machine = buildMachine("M1", "MAT-C1");
        MachineCleaningWindowDTO cleaningWindow = new MachineCleaningWindowDTO();
        cleaningWindow.setCleanType("01");
        // 清洗窗口覆盖换活字块衔接班次，且不影响续作收尾时刻计算。
        cleaningWindow.setCleanStartTime(dateTime(2026, 4, 18, 15, 0, 0));
        cleaningWindow.setCleanEndTime(dateTime(2026, 4, 18, 17, 0, 0));
        cleaningWindow.setReadyTime(dateTime(2026, 4, 18, 17, 0, 0));
        List<MachineCleaningWindowDTO> cleaningWindowList = new ArrayList<>();
        cleaningWindowList.add(cleaningWindow);
        machine.setCleaningWindowList(cleaningWindowList);
        context.getMachineScheduleMap().put("M1", machine);
        context.getContinuousSkuList().add(buildContinuousSku("MAT-C1", "M1", "EMB-1", "STRUCT-A", "SPEC-A", "PAT-A", 1));
        context.getNewSpecSkuList().add(buildNewSku("MAT-T1", "EMB-1", "STRUCT-B", "SPEC-A", "PAT-B", 1));
        putMouldRel(context, "MAT-C1", "MOULD-1");
        putMouldRel(context, "MAT-T1", "MOULD-1");

        when(orderNoGenerator.generateOrderNo(any())).thenReturn("ORD-1", "ORD-2");
        when(endingJudgmentStrategy.isEnding(any(), any())).thenAnswer(invocation -> {
            SkuScheduleDTO sku = invocation.getArgument(1);
            return "MAT-C1".equals(sku.getMaterialCode());
        });

        strategy.scheduleContinuousEnding(context);
        strategy.scheduleTypeBlockChange(context);

        LhScheduleResult typeBlockResult = context.getScheduleResultList().get(1);
        int firstPlannedShift = resolveFirstPlannedShiftIndex(typeBlockResult);
        assertEquals("模具清洗+换活字块", ShiftFieldUtil.getShiftAnalysis(typeBlockResult, firstPlannedShift));
    }

    @Test
    void scheduleTypeBlockChange_shouldNotWriteCleaningTypeBlockAnalysisWithoutCleaningWindow() {
        LhScheduleContext context = newContext();
        context.getMachineScheduleMap().put("M1", buildMachine("M1", "MAT-C1"));
        context.getContinuousSkuList().add(buildContinuousSku("MAT-C1", "M1", "EMB-1", "STRUCT-A", "SPEC-A", "PAT-A", 1));
        context.getNewSpecSkuList().add(buildNewSku("MAT-T1", "EMB-1", "STRUCT-B", "SPEC-A", "PAT-B", 1));
        putMouldRel(context, "MAT-C1", "MOULD-1");
        putMouldRel(context, "MAT-T1", "MOULD-1");

        when(orderNoGenerator.generateOrderNo(any())).thenReturn("ORD-1", "ORD-2");
        when(endingJudgmentStrategy.isEnding(any(), any())).thenAnswer(invocation -> {
            SkuScheduleDTO sku = invocation.getArgument(1);
            return "MAT-C1".equals(sku.getMaterialCode());
        });

        strategy.scheduleContinuousEnding(context);
        strategy.scheduleTypeBlockChange(context);

        LhScheduleResult typeBlockResult = context.getScheduleResultList().get(1);
        int firstPlannedShift = resolveFirstPlannedShiftIndex(typeBlockResult);
        assertNull(ShiftFieldUtil.getShiftAnalysis(typeBlockResult, firstPlannedShift));
    }

    @Test
    void scheduleTypeBlockChange_shouldNotWriteCleaningTypeBlockAnalysisWhenOnlyTouchBoundary() {
        LhScheduleContext context = newContext();
        context.setScheduleConfig(createConfig(3, 9));
        MachineScheduleDTO machine = buildMachine("M1", "MAT-C1");
        context.getMachineScheduleMap().put("M1", machine);
        context.getContinuousSkuList().add(buildContinuousSku("MAT-C1", "M1", "EMB-1", "STRUCT-A", "SPEC-A", "PAT-A", 1));
        context.getNewSpecSkuList().add(buildNewSku("MAT-T1", "EMB-1", "STRUCT-B", "SPEC-A", "PAT-B", 1));
        putMouldRel(context, "MAT-C1", "MOULD-1");
        putMouldRel(context, "MAT-T1", "MOULD-1");

        when(orderNoGenerator.generateOrderNo(any())).thenReturn("ORD-1", "ORD-2");
        when(endingJudgmentStrategy.isEnding(any(), any())).thenAnswer(invocation -> {
            SkuScheduleDTO sku = invocation.getArgument(1);
            return "MAT-C1".equals(sku.getMaterialCode());
        });

        strategy.scheduleContinuousEnding(context);
        LhScheduleResult continuousResult = context.getScheduleResultList().get(0);
        Date typeBlockStartBoundary = LhScheduleTimeUtil.addHours(
                continuousResult.getSpecEndTime(),
                LhScheduleTimeUtil.getTypeBlockChangeTotalHours(context));

        MachineCleaningWindowDTO cleaningWindow = new MachineCleaningWindowDTO();
        cleaningWindow.setCleanType("01");
        cleaningWindow.setCleanStartTime(LhScheduleTimeUtil.addHours(typeBlockStartBoundary, -1));
        cleaningWindow.setCleanEndTime(typeBlockStartBoundary);
        cleaningWindow.setReadyTime(typeBlockStartBoundary);
        List<MachineCleaningWindowDTO> cleaningWindowList = new ArrayList<>();
        cleaningWindowList.add(cleaningWindow);
        machine.setCleaningWindowList(cleaningWindowList);

        strategy.scheduleTypeBlockChange(context);

        LhScheduleResult typeBlockResult = context.getScheduleResultList().get(1);
        int firstPlannedShift = resolveFirstPlannedShiftIndex(typeBlockResult);
        assertNull(ShiftFieldUtil.getShiftAnalysis(typeBlockResult, firstPlannedShift));
    }

    @Test
    void scheduleTypeBlockChange_shouldStartAfterSandBlastEndThenAddTypeBlockHours() {
        LhScheduleContext context = newContext();
        MachineScheduleDTO machine = buildMachine("M1", "MAT-C1");
        machine.setEstimatedEndTime(dateTime(2026, 4, 18, 11, 0, 0));
        MachineCleaningWindowDTO cleaningWindow = new MachineCleaningWindowDTO();
        cleaningWindow.setCleanType("02");
        cleaningWindow.setCleanStartTime(dateTime(2026, 4, 18, 8, 0, 0));
        cleaningWindow.setCleanEndTime(dateTime(2026, 4, 18, 18, 0, 0));
        cleaningWindow.setReadyTime(dateTime(2026, 4, 18, 18, 0, 0));
        List<MachineCleaningWindowDTO> cleaningWindowList = new ArrayList<>();
        cleaningWindowList.add(cleaningWindow);
        machine.setCleaningWindowList(cleaningWindowList);
        context.getMachineScheduleMap().put("M1", machine);
        context.getContinuousSkuList().add(buildContinuousSku("MAT-C1", "M1", "EMB-1", "STRUCT-A", "SPEC-A", "PAT-A", 1));
        context.getNewSpecSkuList().add(buildNewSku("MAT-T1", "EMB-1", "STRUCT-B", "SPEC-A", "PAT-B", 1));
        putMouldRel(context, "MAT-C1", "MOULD-1");
        putMouldRel(context, "MAT-T1", "MOULD-1");

        when(orderNoGenerator.generateOrderNo(any())).thenReturn("ORD-1", "ORD-2");
        when(endingJudgmentStrategy.isEnding(any(), any())).thenAnswer(invocation -> {
            SkuScheduleDTO sku = invocation.getArgument(1);
            return "MAT-C1".equals(sku.getMaterialCode());
        });

        strategy.scheduleContinuousEnding(context);
        strategy.scheduleTypeBlockChange(context);

        LhScheduleResult typeBlockResult = context.getScheduleResultList().get(1);
        assertEquals(dateTime(2026, 4, 19, 2, 0, 0), resolveFirstStartTime(typeBlockResult));
        assertEquals(dateTime(2026, 4, 18, 18, 0, 0),
                ReflectionTestUtils.getField(typeBlockResult, "mouldChangeStartTime"));
    }

    @Test
    void scheduleTypeBlockChange_shouldSelectFirstSkuWithinPriorityTwoCandidates() {
        LhScheduleContext context = newContext();
        context.getMachineScheduleMap().put("M1", buildMachine("M1", "MAT-C1"));
        context.getContinuousSkuList().add(buildContinuousSku("MAT-C1", "M1", "EMB-1", "STRUCT-A", "SPEC-A", "PAT-A", 1));
        context.getNewSpecSkuList().add(buildNewSku("MAT-T1", "EMB-1", "STRUCT-B", "SPEC-A", "PAT-B", 4));
        context.getNewSpecSkuList().add(buildNewSku("MAT-T2", "EMB-1", "STRUCT-C", "SPEC-A", "PAT-C", 4));
        putMaterialInfo(context, "MAT-C1", "胎胚描述-A", "SPEC-A", "PAT-A", "PAT-A");
        putMaterialInfo(context, "MAT-T1", "胎胚描述-X", "SPEC-A", "PAT-B", "PAT-B");
        putMaterialInfo(context, "MAT-T2", "胎胚描述-Y", "SPEC-A", "PAT-C", "PAT-C");
        putMouldRel(context, "MAT-C1", "MOULD-1");
        putMouldRel(context, "MAT-T1", "MOULD-1");
        putMouldRel(context, "MAT-T2", "MOULD-1");

        when(orderNoGenerator.generateOrderNo(any())).thenReturn("ORD-1", "ORD-2");
        when(endingJudgmentStrategy.isEnding(any(), any())).thenAnswer(invocation -> {
            SkuScheduleDTO sku = invocation.getArgument(1);
            return "MAT-C1".equals(sku.getMaterialCode()) || "MAT-T2".equals(sku.getMaterialCode());
        });

        strategy.scheduleContinuousEnding(context);
        strategy.scheduleTypeBlockChange(context);

        LhScheduleResult typeBlockResult = context.getScheduleResultList().get(1);
        assertEquals("MAT-T1", typeBlockResult.getMaterialCode());
        assertEquals(1, context.getNewSpecSkuList().size());
        assertEquals("MAT-T2", context.getNewSpecSkuList().get(0).getMaterialCode());
    }

    @Test
    void scheduleTypeBlockChange_shouldStopWhenPriorityOneAndTwoBothMiss() {
        LhScheduleContext context = newContext();
        context.getMachineScheduleMap().put("M1", buildMachine("M1", "MAT-C1"));
        context.getContinuousSkuList().add(buildContinuousSku("MAT-C1", "M1", "EMB-1", "STRUCT-A", "SPEC-A", "PAT-A", 1));
        context.getNewSpecSkuList().add(buildNewSku("MAT-T1", "EMB-2", "STRUCT-B", "SPEC-B", "PAT-B", 4));
        putMaterialInfo(context, "MAT-C1", "胎胚描述-A", "SPEC-A", "PAT-A", "PAT-A");
        putMaterialInfo(context, "MAT-T1", "胎胚描述-B", "SPEC-B", "PAT-B", "PAT-B");
        putMouldRel(context, "MAT-C1", "MOULD-1");
        putMouldRel(context, "MAT-T1", "MOULD-1");

        when(orderNoGenerator.generateOrderNo(any())).thenReturn("ORD-1");
        when(endingJudgmentStrategy.isEnding(any(), any())).thenAnswer(invocation -> {
            SkuScheduleDTO sku = invocation.getArgument(1);
            return "MAT-C1".equals(sku.getMaterialCode());
        });

        strategy.scheduleContinuousEnding(context);
        strategy.scheduleTypeBlockChange(context);

        assertEquals(1, context.getScheduleResultList().size());
        assertEquals("MAT-C1", context.getScheduleResultList().get(0).getMaterialCode());
        assertEquals(1, context.getNewSpecSkuList().size());
        assertEquals("MAT-T1", context.getNewSpecSkuList().get(0).getMaterialCode());
    }

    @Test
    void scheduleTypeBlockChange_shouldRequireStrictMainPatternForPriorityOne() {
        LhScheduleContext context = newContext();
        context.getMachineScheduleMap().put("M1", buildMachine("M1", "MAT-C1"));
        context.getContinuousSkuList().add(buildContinuousSku("MAT-C1", "M1", "EMB-1", "STRUCT-A", "SPEC-A", "PAT-A", 1));
        SkuScheduleDTO sku = buildNewSku("MAT-T1", "EMB-9", "STRUCT-B", "SPEC-B", "PAT-A", 4);
        sku.setMainPattern(null);
        context.getNewSpecSkuList().add(sku);
        putMaterialInfo(context, "MAT-C1", "胎胚描述-A", "SPEC-A", "PAT-A", "PAT-A");
        putMaterialInfo(context, "MAT-T1", "胎胚描述-A", "SPEC-B", null, "PAT-A");
        putMouldRel(context, "MAT-C1", "MOULD-1");
        putMouldRel(context, "MAT-T1", "MOULD-1");

        when(orderNoGenerator.generateOrderNo(any())).thenReturn("ORD-1");
        when(endingJudgmentStrategy.isEnding(any(), any())).thenAnswer(invocation -> {
            SkuScheduleDTO targetSku = invocation.getArgument(1);
            return "MAT-C1".equals(targetSku.getMaterialCode());
        });

        strategy.scheduleContinuousEnding(context);
        strategy.scheduleTypeBlockChange(context);

        assertEquals(1, context.getScheduleResultList().size());
        assertEquals("MAT-C1", context.getScheduleResultList().get(0).getMaterialCode());
        assertEquals(1, context.getNewSpecSkuList().size());
        assertEquals("MAT-T1", context.getNewSpecSkuList().get(0).getMaterialCode());
    }

    @Test
    void scheduleTypeBlockChange_shouldSkipMachinesThatDoNotEndInWindow() {
        LhScheduleContext context = newContext();
        context.getMachineScheduleMap().put("M1", buildMachine("M1", "MAT-C1"));
        context.getContinuousSkuList().add(buildContinuousSku("MAT-C1", "M1", "EMB-1", "STRUCT-A", "SPEC-A", "PAT-A", 6));
        context.getNewSpecSkuList().add(buildNewSku("MAT-T1", "EMB-1", "STRUCT-B", "SPEC-A", "PAT-B", 4));
        putMouldRel(context, "MAT-C1", "MOULD-1");
        putMouldRel(context, "MAT-T1", "MOULD-1");

        when(orderNoGenerator.generateOrderNo(any())).thenReturn("ORD-1");
        when(endingJudgmentStrategy.isEnding(any(), any())).thenReturn(false);

        strategy.scheduleContinuousEnding(context);
        strategy.scheduleTypeBlockChange(context);

        assertEquals(1, context.getScheduleResultList().size());
        assertEquals("MAT-C1", context.getScheduleResultList().get(0).getMaterialCode());
        assertEquals(1, context.getNewSpecSkuList().size());
        assertEquals("MAT-T1", context.getNewSpecSkuList().get(0).getMaterialCode());
    }

    @Test
    void scheduleTypeBlockChange_shouldFallbackWhenPreviousDayHasNoSameMachineSku() {
        LhScheduleContext context = newContext();
        context.getMachineScheduleMap().put("M1", buildMachine("M1", "MAT-C1"));
        putMachineOnlineInfo(context, "M1", "MAT-C1");
        context.getNewSpecSkuList().add(buildNewSku("MAT-T1", "EMB-1", "STRUCT-B", "SPEC-A", "PAT-A", 2));
        putMaterialInfo(context, "MAT-C1", "胎胚描述-A", "SPEC-A", "PAT-A", "PAT-A");
        putMaterialInfo(context, "MAT-T1", "胎胚描述-A", "SPEC-B", "PAT-A", "PAT-A");
        putMouldRel(context, "MAT-C1", "MOULD-1");
        putMouldRel(context, "MAT-T1", "MOULD-1");

        strategy.scheduleTypeBlockChange(context);

        assertEquals(1, context.getScheduleResultList().size());
        assertEquals("MAT-T1", context.getScheduleResultList().get(0).getMaterialCode());
    }

    @Test
    void scheduleTypeBlockChange_shouldFallbackWhenPreviousDayLatestSameMachineSkuEnded() {
        LhScheduleContext context = newContext();
        context.getMachineScheduleMap().put("M1", buildMachine("M1", "MAT-C1"));
        putMachineOnlineInfo(context, "M1", "MAT-C1");
        context.getNewSpecSkuList().add(buildNewSku("MAT-T1", "EMB-1", "STRUCT-B", "SPEC-A", "PAT-A", 2));
        putMaterialInfo(context, "MAT-C1", "胎胚描述-A", "SPEC-A", "PAT-A", "PAT-A");
        putMaterialInfo(context, "MAT-T1", "胎胚描述-A", "SPEC-B", "PAT-A", "PAT-A");
        putMouldRel(context, "MAT-C1", "MOULD-1");
        putMouldRel(context, "MAT-T1", "MOULD-1");
        context.getPreviousScheduleResultList().add(
                buildPreviousScheduleResult("M1", "MAT-C1", "1",
                        dateTime(2026, 4, 17, 8, 0, 0), dateTime(2026, 4, 17, 8, 5, 0)));

        strategy.scheduleTypeBlockChange(context);

        assertEquals(1, context.getScheduleResultList().size());
        assertEquals("MAT-T1", context.getScheduleResultList().get(0).getMaterialCode());
    }

    @Test
    void scheduleTypeBlockChange_shouldSkipFallbackWhenPreviousDayLatestSameMachineSkuNotEnd() {
        LhScheduleContext context = newTraceContext();
        context.getMachineScheduleMap().put("M1", buildMachine("M1", "MAT-C1"));
        putMachineOnlineInfo(context, "M1", "MAT-C1");
        context.getNewSpecSkuList().add(buildNewSku("MAT-T1", "EMB-1", "STRUCT-B", "SPEC-A", "PAT-A", 2));
        putMaterialInfo(context, "MAT-C1", "胎胚描述-A", "SPEC-A", "PAT-A", "PAT-A");
        putMaterialInfo(context, "MAT-T1", "胎胚描述-A", "SPEC-B", "PAT-A", "PAT-A");
        putMouldRel(context, "MAT-C1", "MOULD-1");
        putMouldRel(context, "MAT-T1", "MOULD-1");
        context.getPreviousScheduleResultList().add(
                buildPreviousScheduleResult("M1", "MAT-C1", "0",
                        dateTime(2026, 4, 17, 9, 0, 0), dateTime(2026, 4, 17, 9, 5, 0)));

        strategy.scheduleTypeBlockChange(context);

        assertEquals(0, context.getScheduleResultList().size());
        assertEquals(1, context.getNewSpecSkuList().size());
        StringBuilder allLogText = new StringBuilder();
        for (LhScheduleProcessLog scheduleLog : context.getScheduleLogList()) {
            allLogText.append(scheduleLog.getLogDetail()).append('\n');
        }
        assertTrue(allLogText.toString().contains("T-1 最新记录未收尾，跳过兜底反查"));
    }

    @Test
    void scheduleTypeBlockChange_shouldUseLatestPreviousDayRecordWhenMixedEndFlags() {
        LhScheduleContext context = newContext();
        context.getMachineScheduleMap().put("M1", buildMachine("M1", "MAT-C1"));
        putMachineOnlineInfo(context, "M1", "MAT-C1");
        context.getNewSpecSkuList().add(buildNewSku("MAT-T1", "EMB-1", "STRUCT-B", "SPEC-A", "PAT-A", 2));
        putMaterialInfo(context, "MAT-C1", "胎胚描述-A", "SPEC-A", "PAT-A", "PAT-A");
        putMaterialInfo(context, "MAT-T1", "胎胚描述-A", "SPEC-B", "PAT-A", "PAT-A");
        putMouldRel(context, "MAT-C1", "MOULD-1");
        putMouldRel(context, "MAT-T1", "MOULD-1");
        context.getPreviousScheduleResultList().add(
                buildPreviousScheduleResult("M1", "MAT-C1", "1",
                        dateTime(2026, 4, 17, 7, 0, 0), dateTime(2026, 4, 17, 10, 0, 0)));
        context.getPreviousScheduleResultList().add(
                buildPreviousScheduleResult("M1", "MAT-C1", "0",
                        dateTime(2026, 4, 17, 9, 0, 0), dateTime(2026, 4, 17, 9, 30, 0)));

        strategy.scheduleTypeBlockChange(context);

        assertEquals(0, context.getScheduleResultList().size());
        assertEquals(1, context.getNewSpecSkuList().size());
    }

    @Test
    void scheduleTypeBlockChange_shouldPrioritizeEndingMachineBeforeFallbackMachine() {
        LhScheduleContext context = newContext();
        context.getMachineScheduleMap().put("M1", buildMachine("M1", "MAT-C1"));
        context.getMachineScheduleMap().put("M2", buildMachine("M2", "MAT-C2"));
        context.getContinuousSkuList().add(buildContinuousSku("MAT-C1", "M1", "EMB-1", "STRUCT-A", "SPEC-A", "PAT-A", 2));
        context.getNewSpecSkuList().add(buildNewSku("MAT-T1", "EMB-9", "STRUCT-B", "SPEC-B", "PAT-A", 2));
        putMachineOnlineInfo(context, "M2", "MAT-C2");
        putMaterialInfo(context, "MAT-C1", "胎胚描述-A", "SPEC-A", "PAT-A", "PAT-A");
        putMaterialInfo(context, "MAT-C2", "胎胚描述-A", "SPEC-C", "PAT-A", "PAT-A");
        putMaterialInfo(context, "MAT-T1", "胎胚描述-A", "SPEC-B", "PAT-A", "PAT-A");
        putMouldRel(context, "MAT-C1", "MOULD-1");
        putMouldRel(context, "MAT-C2", "MOULD-1");
        putMouldRel(context, "MAT-T1", "MOULD-1");
        context.getPreviousScheduleResultList().add(
                buildPreviousScheduleResult("M2", "MAT-C2", "1",
                        dateTime(2026, 4, 17, 8, 0, 0), dateTime(2026, 4, 17, 8, 5, 0)));

        when(orderNoGenerator.generateOrderNo(any())).thenReturn("ORD-1", "ORD-2");
        when(endingJudgmentStrategy.isEnding(any(), any())).thenAnswer(invocation -> {
            SkuScheduleDTO sku = invocation.getArgument(1);
            return "MAT-C1".equals(sku.getMaterialCode());
        });

        strategy.scheduleContinuousEnding(context);
        strategy.scheduleTypeBlockChange(context);

        assertEquals(2, context.getScheduleResultList().size());
        assertEquals(Arrays.asList("MAT-C1", "MAT-T1"),
                Arrays.asList(
                        context.getScheduleResultList().get(0).getMaterialCode(),
                        context.getScheduleResultList().get(1).getMaterialCode()));
        assertEquals("M1", context.getScheduleResultList().get(1).getLhMachineCode());
        assertEquals("MAT-C2", context.getMachineScheduleMap().get("M2").getCurrentMaterialCode());
        assertEquals(0, context.getNewSpecSkuList().size());
    }

    @Test
    void scheduleTypeBlockChange_shouldFollowUpdatedMachineEndTimeOrder() {
        LhScheduleContext context = newContext();
        context.getMachineScheduleMap().put("M1", buildMachine("M1", "MAT-C1"));
        context.getMachineScheduleMap().put("M2", buildMachine("M2", "MAT-C2"));
        context.getContinuousSkuList().add(buildContinuousSku("MAT-C1", "M1", "EMB-1", "STRUCT-A", "SPEC-A", "PAT-A", 1));
        context.getContinuousSkuList().add(buildContinuousSku("MAT-C2", "M2", "EMB-2", "STRUCT-D", "SPEC-D", "PAT-D", 3));
        context.getNewSpecSkuList().add(buildNewSku("MAT-T1", "EMB-1", "STRUCT-B", "SPEC-A", "PAT-B", 4));
        context.getNewSpecSkuList().add(buildNewSku("MAT-T2", "EMB-2", "STRUCT-E", "SPEC-D", "PAT-E", 4));
        putMouldRel(context, "MAT-C1", "MOULD-1");
        putMouldRel(context, "MAT-C2", "MOULD-2");
        putMouldRel(context, "MAT-T1", "MOULD-1");
        putMouldRel(context, "MAT-T2", "MOULD-2");

        when(orderNoGenerator.generateOrderNo(any())).thenReturn("ORD-1", "ORD-2", "ORD-3", "ORD-4");
        when(endingJudgmentStrategy.isEnding(any(), any())).thenAnswer(invocation -> {
            SkuScheduleDTO sku = invocation.getArgument(1);
            return sku.getMaterialCode().startsWith("MAT-C");
        });

        strategy.scheduleContinuousEnding(context);
        strategy.scheduleTypeBlockChange(context);

        List<LhScheduleResult> results = context.getScheduleResultList();
        assertEquals(Arrays.asList("MAT-C1", "MAT-C2", "MAT-T1", "MAT-T2"),
                Arrays.asList(
                        results.get(0).getMaterialCode(),
                        results.get(1).getMaterialCode(),
                        results.get(2).getMaterialCode(),
                        results.get(3).getMaterialCode()));
        assertEquals(LhScheduleTimeUtil.addHours(results.get(0).getSpecEndTime(),
                        LhScheduleTimeUtil.getTypeBlockChangeTotalHours(context)),
                resolveFirstStartTime(results.get(2)));
        assertEquals(LhScheduleTimeUtil.addHours(results.get(1).getSpecEndTime(),
                        LhScheduleTimeUtil.getTypeBlockChangeTotalHours(context)),
                resolveFirstStartTime(results.get(3)));
    }

    @Test
    void scheduleTypeBlockChange_shouldContinueAtNightWhenEndingBefore20() {
        LhScheduleContext context = newContext();
        context.getMachineScheduleMap().put("M1", buildMachine("M1", "MAT-C1"));
        context.getContinuousSkuList().add(buildContinuousSku("MAT-C1", "M1", "EMB-1", "STRUCT-A", "SPEC-A", "PAT-A", 13));
        context.getNewSpecSkuList().add(buildNewSku("MAT-T1", "EMB-1", "STRUCT-B", "SPEC-A", "PAT-B", 1));
        putMouldRel(context, "MAT-C1", "MOULD-1");
        putMouldRel(context, "MAT-T1", "MOULD-1");

        when(orderNoGenerator.generateOrderNo(any())).thenReturn("ORD-1", "ORD-2");
        when(endingJudgmentStrategy.isEnding(any(), any())).thenAnswer(invocation -> {
            SkuScheduleDTO sku = invocation.getArgument(1);
            return "MAT-C1".equals(sku.getMaterialCode());
        });

        strategy.scheduleContinuousEnding(context);
        LhScheduleResult continuousResult = context.getScheduleResultList().get(0);
        assertEquals(dateTime(2026, 4, 18, 19, 0, 0), continuousResult.getSpecEndTime(),
                "收尾时间应按真实完工时刻回写，不能放大到班次结束");

        strategy.scheduleTypeBlockChange(context);
        LhScheduleResult typeBlockResult = context.getScheduleResultList().get(1);
        assertEquals(dateTime(2026, 4, 19, 3, 0, 0), resolveFirstStartTime(typeBlockResult),
                "20:00前收尾应允许连续切换，夜班继续生产");
    }

    @Test
    void scheduleTypeBlockChange_shouldDelayToNextMorningWhenEndingAtOrAfter20() {
        LhScheduleContext context = newContext();
        context.getMachineScheduleMap().put("M1", buildMachine("M1", "MAT-C1"));
        context.getContinuousSkuList().add(buildContinuousSku("MAT-C1", "M1", "EMB-1", "STRUCT-A", "SPEC-A", "PAT-A", 14));
        context.getNewSpecSkuList().add(buildNewSku("MAT-T1", "EMB-1", "STRUCT-B", "SPEC-A", "PAT-B", 1));
        putMouldRel(context, "MAT-C1", "MOULD-1");
        putMouldRel(context, "MAT-T1", "MOULD-1");

        when(orderNoGenerator.generateOrderNo(any())).thenReturn("ORD-1", "ORD-2");
        when(endingJudgmentStrategy.isEnding(any(), any())).thenAnswer(invocation -> {
            SkuScheduleDTO sku = invocation.getArgument(1);
            return "MAT-C1".equals(sku.getMaterialCode());
        });

        strategy.scheduleContinuousEnding(context);
        LhScheduleResult continuousResult = context.getScheduleResultList().get(0);
        assertEquals(dateTime(2026, 4, 18, 20, 0, 0), continuousResult.getSpecEndTime(),
                "边界20:00应视为禁止发起切换");

        strategy.scheduleTypeBlockChange(context);
        LhScheduleResult typeBlockResult = context.getScheduleResultList().get(1);
        assertEquals(dateTime(2026, 4, 19, 14, 0, 0), resolveFirstStartTime(typeBlockResult),
                "20:00及之后收尾应顺延到次日早班发起，再叠加换活字块总耗时");
    }

    @Test
    void scheduleTypeBlockChange_shouldDelayTypeBlockToNextMorningWhenDowntimeOverlapsAndNightForbidden() {
        LhScheduleContext context = newContext();
        context.setScheduleDate(date(2026, 4, 21));
        context.setScheduleTargetDate(date(2026, 4, 23));
        context.setScheduleWindowShifts(LhScheduleTimeUtil.buildDefaultScheduleShifts(context, context.getScheduleDate()));
        MachineScheduleDTO machine = buildMachine("K2024", "3302001556");
        machine.setEnding(true);
        machine.setEstimatedEndTime(dateTime(2026, 4, 21, 6, 0, 0));
        context.getMachineScheduleMap().put("K2024", machine);
        context.getNewSpecSkuList().add(buildNewSku("3302002174", "EMB-1", "STRUCT-A", "SPEC-A", "PAT-A", 50));
        putMaterialInfo(context, "3302001556", "胎胚描述-A", "SPEC-A", "PAT-A", "PAT-A");
        putMaterialInfo(context, "3302002174", "胎胚描述-A", "SPEC-A", "PAT-A", "PAT-A");
        context.getDevicePlanShutList().add(buildDevicePlanShut(
                "K2024", dateTime(2026, 4, 21, 6, 0, 0), dateTime(2026, 4, 21, 23, 59, 59)));
        putMouldRel(context, "3302001556", "MOULD-1");
        putMouldRel(context, "3302002174", "MOULD-1");

        when(orderNoGenerator.generateOrderNo(any())).thenReturn("ORD-1");
        when(endingJudgmentStrategy.isEnding(any(), any())).thenReturn(false);

        strategy.scheduleTypeBlockChange(context);

        assertEquals(1, context.getScheduleResultList().size());
        LhScheduleResult typeBlockResult = context.getScheduleResultList().get(0);
        assertEquals("3302002174", typeBlockResult.getMaterialCode());
        assertEquals(dateTime(2026, 4, 22, 14, 0, 0), resolveFirstStartTime(typeBlockResult),
                "停机重叠且夜班禁换时，应顺延到次日早班发起换活字块，次日中班开始生产");
        Integer class3PlanQty = ShiftFieldUtil.getShiftPlanQty(typeBlockResult, 3);
        assertTrue(class3PlanQty == null || class3PlanQty <= 0, "停机日夜班不应出现换活字块后的生产计划量");
    }

    @Test
    void scheduleTypeBlockChange_shouldChainPriorityOneCandidatesBeforeSpecFallbackOnSameMachine() {
        LhScheduleContext context = newContext();
        context.setScheduleDate(date(2026, 4, 24));
        context.setScheduleTargetDate(date(2026, 4, 24));
        context.setScheduleWindowShifts(LhScheduleTimeUtil.buildDefaultScheduleShifts(context, context.getScheduleDate()));
        MachineScheduleDTO machine = buildMachine("K2024", "3302001556");
        machine.setEnding(true);
        machine.setEstimatedEndTime(dateTime(2026, 4, 24, 6, 0, 0));
        context.getMachineScheduleMap().put("K2024", machine);

        context.getNewSpecSkuList().add(buildNewSku("3302002174", "EMB-1", "STRUCT-A", "SPEC-A", "PAT-A", 1));
        context.getNewSpecSkuList().add(buildNewSku("3302001647", "EMB-1", "STRUCT-B", "SPEC-B", "PAT-A", 1));
        context.getNewSpecSkuList().add(buildNewSku("3302001002", "EMB-9", "STRUCT-C", "SPEC-B", "PAT-Z", 1));

        putMaterialInfo(context, "3302001556", "胎胚描述-A", "SPEC-A", "PAT-A", "PAT-A");
        putMaterialInfo(context, "3302002174", "胎胚描述-A", "SPEC-A", "PAT-A", "PAT-A");
        putMaterialInfo(context, "3302001647", "胎胚描述-A", "SPEC-B", "PAT-A", "PAT-A");
        putMaterialInfo(context, "3302001002", "胎胚描述-B", "SPEC-B", "PAT-Z", "PAT-Z");

        putMouldRel(context, "3302001556", "MOULD-1");
        putMouldRel(context, "3302002174", "MOULD-1");
        putMouldRel(context, "3302001647", "MOULD-1");
        putMouldRel(context, "3302001002", "MOULD-2");

        when(orderNoGenerator.generateOrderNo(any())).thenReturn("ORD-1", "ORD-2", "ORD-3");
        when(endingJudgmentStrategy.isEnding(any(), any())).thenAnswer(invocation -> {
            SkuScheduleDTO sku = invocation.getArgument(1);
            return "3302002174".equals(sku.getMaterialCode());
        });

        strategy.scheduleTypeBlockChange(context);

        assertTrue(context.getScheduleResultList().size() >= 2);
        List<String> scheduledMaterials = new ArrayList<>(context.getScheduleResultList().size());
        for (LhScheduleResult result : context.getScheduleResultList()) {
            scheduledMaterials.add(result.getMaterialCode());
        }
        assertEquals("3302002174", scheduledMaterials.get(0));
        assertEquals("3302001647", scheduledMaterials.get(1));
    }

    @Test
    void scheduleTypeBlockChange_shouldResortAcrossMachinesAfterEachSuccess() {
        LhScheduleContext context = newContext();
        context.getMachineScheduleMap().put("M1", buildMachine("M1", "MAT-C1"));
        context.getMachineScheduleMap().put("M2", buildMachine("M2", "MAT-C2"));
        context.getContinuousSkuList().add(buildContinuousSku("MAT-C1", "M1", "EMB-1", "STRUCT-A", "SPEC-A", "PAT-A", 1));
        context.getContinuousSkuList().add(buildContinuousSku("MAT-C2", "M2", "EMB-2", "STRUCT-B", "SPEC-B", "PAT-B", 3));
        context.getNewSpecSkuList().add(buildNewSku("MAT-T1", "EMB-1", "STRUCT-A1", "SPEC-A1", "PAT-A", 4));
        context.getNewSpecSkuList().add(buildNewSku("MAT-T2", "EMB-2", "STRUCT-B1", "SPEC-B1", "PAT-B", 1));
        context.getNewSpecSkuList().add(buildNewSku("MAT-T3", "EMB-1", "STRUCT-A2", "SPEC-A2", "PAT-A", 1));
        putMaterialInfo(context, "MAT-C1", "胎胚描述-A", "SPEC-A", "PAT-A", "PAT-A");
        putMaterialInfo(context, "MAT-C2", "胎胚描述-B", "SPEC-B", "PAT-B", "PAT-B");
        putMaterialInfo(context, "MAT-T1", "胎胚描述-A", "SPEC-A1", "PAT-A", "PAT-A");
        putMaterialInfo(context, "MAT-T2", "胎胚描述-B", "SPEC-B1", "PAT-B", "PAT-B");
        putMaterialInfo(context, "MAT-T3", "胎胚描述-A", "SPEC-A2", "PAT-A", "PAT-A");
        putMouldRel(context, "MAT-C1", "MOULD-1");
        putMouldRel(context, "MAT-C2", "MOULD-2");
        putMouldRel(context, "MAT-T1", "MOULD-1");
        putMouldRel(context, "MAT-T2", "MOULD-2");
        putMouldRel(context, "MAT-T3", "MOULD-1");

        when(orderNoGenerator.generateOrderNo(any())).thenReturn("ORD-1", "ORD-2", "ORD-3", "ORD-4", "ORD-5");
        when(endingJudgmentStrategy.isEnding(any(), any())).thenAnswer(invocation -> {
            SkuScheduleDTO sku = invocation.getArgument(1);
            return "MAT-C1".equals(sku.getMaterialCode())
                    || "MAT-C2".equals(sku.getMaterialCode())
                    || "MAT-T1".equals(sku.getMaterialCode());
        });

        strategy.scheduleContinuousEnding(context);
        strategy.scheduleTypeBlockChange(context);

        List<String> scheduledMaterials = new ArrayList<>(context.getScheduleResultList().size());
        for (LhScheduleResult result : context.getScheduleResultList()) {
            scheduledMaterials.add(result.getMaterialCode());
        }
        assertEquals(Arrays.asList("MAT-C1", "MAT-C2", "MAT-T1", "MAT-T2", "MAT-T3"), scheduledMaterials);
    }

    @Test
    void scheduleTypeBlockChange_shouldStopChainWhenMachineBecomesNonEnding() {
        LhScheduleContext context = newContext();
        context.setScheduleDate(date(2026, 4, 24));
        context.setScheduleTargetDate(date(2026, 4, 24));
        context.setScheduleWindowShifts(LhScheduleTimeUtil.buildDefaultScheduleShifts(context, context.getScheduleDate()));
        MachineScheduleDTO machine = buildMachine("K2024", "3302001556");
        machine.setEnding(true);
        machine.setEstimatedEndTime(dateTime(2026, 4, 24, 6, 0, 0));
        context.getMachineScheduleMap().put("K2024", machine);

        context.getNewSpecSkuList().add(buildNewSku("3302002174", "EMB-1", "STRUCT-A", "SPEC-A", "PAT-A", 1));
        context.getNewSpecSkuList().add(buildNewSku("3302001647", "EMB-1", "STRUCT-B", "SPEC-B", "PAT-A", 1));
        context.getNewSpecSkuList().add(buildNewSku("3302001648", "EMB-1", "STRUCT-C", "SPEC-C", "PAT-A", 1));

        putMaterialInfo(context, "3302001556", "胎胚描述-A", "SPEC-A", "PAT-A", "PAT-A");
        putMaterialInfo(context, "3302002174", "胎胚描述-A", "SPEC-A", "PAT-A", "PAT-A");
        putMaterialInfo(context, "3302001647", "胎胚描述-A", "SPEC-B", "PAT-A", "PAT-A");
        putMaterialInfo(context, "3302001648", "胎胚描述-A", "SPEC-C", "PAT-A", "PAT-A");

        putMouldRel(context, "3302001556", "MOULD-1");
        putMouldRel(context, "3302002174", "MOULD-1");
        putMouldRel(context, "3302001647", "MOULD-1");
        putMouldRel(context, "3302001648", "MOULD-1");

        when(orderNoGenerator.generateOrderNo(any())).thenReturn("ORD-1", "ORD-2", "ORD-3");
        when(endingJudgmentStrategy.isEnding(any(), any())).thenAnswer(invocation -> {
            SkuScheduleDTO sku = invocation.getArgument(1);
            return "3302002174".equals(sku.getMaterialCode());
        });

        strategy.scheduleTypeBlockChange(context);

        List<String> scheduledMaterials = new ArrayList<>(context.getScheduleResultList().size());
        for (LhScheduleResult result : context.getScheduleResultList()) {
            scheduledMaterials.add(result.getMaterialCode());
        }
        assertEquals(Arrays.asList("3302002174", "3302001647"), scheduledMaterials);
        assertEquals(1, context.getNewSpecSkuList().size());
        assertEquals("3302001648", context.getNewSpecSkuList().get(0).getMaterialCode());
    }

    @Test
    void scheduleTypeBlockChange_shouldWriteTraceLogsAfterActualEndTimeUpdate() {
        LhScheduleContext context = newTraceContext();
        context.getMachineScheduleMap().put("M1", buildMachine("M1", "MAT-C1"));
        context.getMachineScheduleMap().put("M2", buildMachine("M2", "MAT-C2"));
        context.getContinuousSkuList().add(buildContinuousSku("MAT-C1", "M1", "EMB-1", "STRUCT-A", "SPEC-A", "PAT-A", 1));
        context.getContinuousSkuList().add(buildContinuousSku("MAT-C2", "M2", "EMB-2", "STRUCT-D", "SPEC-D", "PAT-D", 3));
        context.getNewSpecSkuList().add(buildNewSku("MAT-T1", "EMB-1", "STRUCT-B", "SPEC-A", "PAT-B", 4));
        context.getNewSpecSkuList().add(buildNewSku("MAT-T2", "EMB-2", "STRUCT-E", "SPEC-D", "PAT-E", 4));
        putMaterialInfo(context, "MAT-C1", "胎胚描述-A", "SPEC-A", "PAT-A", "PAT-A");
        putMaterialInfo(context, "MAT-C2", "胎胚描述-B", "SPEC-D", "PAT-D", "PAT-D");
        putMaterialInfo(context, "MAT-T1", "胎胚描述-A", "SPEC-A", "PAT-B", "PAT-B");
        putMaterialInfo(context, "MAT-T2", "胎胚描述-B", "SPEC-D", "PAT-E", "PAT-E");
        putMouldRel(context, "MAT-C1", "MOULD-1");
        putMouldRel(context, "MAT-C2", "MOULD-2");
        putMouldRel(context, "MAT-T1", "MOULD-1");
        putMouldRel(context, "MAT-T2", "MOULD-2");

        when(orderNoGenerator.generateOrderNo(any())).thenReturn("ORD-1", "ORD-2", "ORD-3", "ORD-4");
        when(endingJudgmentStrategy.isEnding(any(), any())).thenAnswer(invocation -> {
            SkuScheduleDTO sku = invocation.getArgument(1);
            return sku.getMaterialCode().startsWith("MAT-C");
        });

        strategy.scheduleContinuousEnding(context);
        strategy.scheduleTypeBlockChange(context);

        assertTrue(context.getScheduleLogList().size() >= 4);
        StringBuilder allLogText = new StringBuilder();
        for (LhScheduleProcessLog scheduleLog : context.getScheduleLogList()) {
            allLogText.append(scheduleLog.getTitle()).append('\n')
                    .append(scheduleLog.getLogDetail()).append('\n');
        }
        String logText = allLogText.toString();
        assertTrue(logText.contains("续作收尾真实时间回写"));
        assertTrue(logText.contains("衔接机台排序总览"));
        assertTrue(logText.contains("收尾机台衔接决策"));
        assertTrue(logText.contains("M1"));
        assertTrue(logText.contains("M2"));
        assertTrue(logText.contains("MAT-T1"));
        assertTrue(logText.contains("MAT-T2"));
        assertTrue(logText.contains("第一层"));
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

    private LhScheduleContext newTraceContext() {
        LhScheduleContext context = newContext();
        Map<String, String> paramMap = new HashMap<>(4);
        paramMap.put(LhScheduleParamConstant.ENABLE_PRIORITY_TRACE_LOG, "1");
        context.setScheduleConfig(new LhScheduleConfig(paramMap));
        return context;
    }

    private LhScheduleConfig createConfig(int typeBlockHours, int inspectionHours) {
        Map<String, String> paramMap = new HashMap<>(4);
        paramMap.put(LhScheduleParamConstant.TYPE_BLOCK_CHANGE_TOTAL_HOURS, String.valueOf(typeBlockHours));
        paramMap.put(LhScheduleParamConstant.FIRST_INSPECTION_HOURS, String.valueOf(inspectionHours));
        return new LhScheduleConfig(paramMap);
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

    private SkuScheduleDTO buildContinuousSku(String materialCode,
                                              String machineCode,
                                              String embryoCode,
                                              String structureName,
                                              String specCode,
                                              String pattern,
                                              int pendingQty) {
        SkuScheduleDTO sku = new SkuScheduleDTO();
        sku.setMaterialCode(materialCode);
        sku.setMaterialDesc(materialCode);
        sku.setStructureName(structureName);
        sku.setEmbryoCode(embryoCode);
        sku.setSpecCode(specCode);
        sku.setMainPattern(pattern);
        sku.setPattern(pattern);
        sku.setContinuousMachineCode(machineCode);
        sku.setMonthPlanQty(pendingQty);
        sku.setWindowPlanQty(pendingQty);
        sku.setPendingQty(pendingQty);
        sku.setShiftCapacity(8);
        sku.setLhTimeSeconds(3600);
        sku.setScheduleType("01");
        return sku;
    }

    private SkuScheduleDTO buildNewSku(String materialCode,
                                       String embryoCode,
                                       String structureName,
                                       String specCode,
                                       String pattern,
                                       int pendingQty) {
        SkuScheduleDTO sku = new SkuScheduleDTO();
        sku.setMaterialCode(materialCode);
        sku.setMaterialDesc(materialCode);
        sku.setStructureName(structureName);
        sku.setEmbryoCode(embryoCode);
        sku.setSpecCode(specCode);
        sku.setMainPattern(pattern);
        sku.setPattern(pattern);
        sku.setMonthPlanQty(pendingQty);
        sku.setWindowPlanQty(pendingQty);
        sku.setPendingQty(pendingQty);
        sku.setShiftCapacity(8);
        sku.setLhTimeSeconds(3600);
        sku.setScheduleType("02");
        return sku;
    }

    private void putMachineOnlineInfo(LhScheduleContext context, String machineCode, String materialCode) {
        LhMachineOnlineInfo machineOnlineInfo = new LhMachineOnlineInfo();
        machineOnlineInfo.setLhCode(machineCode);
        machineOnlineInfo.setMaterialCode(materialCode);
        context.getMachineOnlineInfoMap().put(machineCode, machineOnlineInfo);
    }

    private LhScheduleResult buildPreviousScheduleResult(String machineCode,
                                                         String materialCode,
                                                         String isEnd,
                                                         Date specEndTime,
                                                         Date createTime) {
        LhScheduleResult previousResult = new LhScheduleResult();
        previousResult.setLhMachineCode(machineCode);
        previousResult.setMaterialCode(materialCode);
        previousResult.setIsEnd(isEnd);
        previousResult.setSpecEndTime(specEndTime);
        previousResult.setCreateTime(createTime);
        return previousResult;
    }

    private void putMouldRel(LhScheduleContext context, String materialCode, String mouldCode) {
        MdmSkuMouldRel relation = new MdmSkuMouldRel();
        relation.setMaterialCode(materialCode);
        relation.setMouldCode(mouldCode);
        context.getSkuMouldRelMap().put(materialCode, Arrays.asList(relation));
    }

    private void putMaterialInfo(LhScheduleContext context,
                                 String materialCode,
                                 String embryoDesc,
                                 String specCode,
                                 String mainPattern,
                                 String pattern) {
        MdmMaterialInfo materialInfo = new MdmMaterialInfo();
        materialInfo.setMaterialCode(materialCode);
        materialInfo.setEmbryoDesc(embryoDesc);
        materialInfo.setSpecifications(specCode);
        materialInfo.setMainPattern(mainPattern);
        materialInfo.setPattern(pattern);
        context.getMaterialInfoMap().put(materialCode, materialInfo);
    }

    private MdmDevicePlanShut buildDevicePlanShut(String machineCode, Date beginDate, Date endDate) {
        MdmDevicePlanShut planShut = new MdmDevicePlanShut();
        planShut.setMachineCode(machineCode);
        planShut.setBeginDate(beginDate);
        planShut.setEndDate(endDate);
        return planShut;
    }

    private Date resolveFirstStartTime(LhScheduleResult result) {
        for (int shiftIndex = 1; shiftIndex <= 8; shiftIndex++) {
            Date shiftStartTime = ShiftFieldUtil.getShiftStartTime(result, shiftIndex);
            if (shiftStartTime != null) {
                return shiftStartTime;
            }
        }
        return null;
    }

    private int resolveFirstPlannedShiftIndex(LhScheduleResult result) {
        for (int shiftIndex = 1; shiftIndex <= 8; shiftIndex++) {
            Integer shiftPlanQty = ShiftFieldUtil.getShiftPlanQty(result, shiftIndex);
            if (shiftPlanQty != null && shiftPlanQty > 0) {
                return shiftIndex;
            }
        }
        return 1;
    }

    private Date resolveActualCompletionTime(LhScheduleResult result) {
        int lhTimeSeconds = result.getLhTime() != null ? result.getLhTime() : 0;
        int mouldQty = result.getMouldQty() != null && result.getMouldQty() > 0 ? result.getMouldQty() : 1;
        Date completionTime = null;
        for (int shiftIndex = 1; shiftIndex <= 8; shiftIndex++) {
            Integer shiftPlanQty = ShiftFieldUtil.getShiftPlanQty(result, shiftIndex);
            Date shiftStartTime = ShiftFieldUtil.getShiftStartTime(result, shiftIndex);
            if (shiftPlanQty == null || shiftPlanQty <= 0 || shiftStartTime == null) {
                continue;
            }
            long secondsNeeded = (long) Math.ceil((double) shiftPlanQty / mouldQty) * lhTimeSeconds;
            Date shiftCompletionTime = new Date(shiftStartTime.getTime() + secondsNeeded * 1000L);
            if (completionTime == null || shiftCompletionTime.after(completionTime)) {
                completionTime = shiftCompletionTime;
            }
        }
        return completionTime;
    }

    private Date resolveLastShiftEndTime(LhScheduleResult result) {
        Date lastShiftEndTime = null;
        for (int shiftIndex = 1; shiftIndex <= 8; shiftIndex++) {
            Date shiftEndTime = ShiftFieldUtil.getShiftEndTime(result, shiftIndex);
            if (shiftEndTime != null && (lastShiftEndTime == null || shiftEndTime.after(lastShiftEndTime))) {
                lastShiftEndTime = shiftEndTime;
            }
        }
        return lastShiftEndTime;
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
