package com.zlt.aps.lh.regression;

import com.zlt.aps.lh.api.domain.dto.MachineScheduleDTO;
import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.api.domain.entity.LhScheduleResult;
import com.zlt.aps.lh.component.OrderNoGenerator;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.engine.observer.ScheduleEventPublisher;
import com.zlt.aps.lh.engine.factory.ScheduleStrategyFactory;
import com.zlt.aps.lh.engine.strategy.IEndingJudgmentStrategy;
import com.zlt.aps.lh.engine.strategy.impl.ContinuousProductionStrategy;
import com.zlt.aps.lh.handler.ContinuousProductionHandler;
import com.zlt.aps.lh.handler.ResultValidationHandler;
import com.zlt.aps.lh.service.impl.SchedulePersistenceService;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 端到端回归：续作衔接非收尾结果进入 S4.6 时不应再触发 specEndTime 缺失。
 */
@ExtendWith(MockitoExtension.class)
class ContinuousFollowUpResultValidationRegressionTest {

    @Mock
    private OrderNoGenerator orderNoGenerator;

    @Mock
    private IEndingJudgmentStrategy endingJudgmentStrategy;

    @InjectMocks
    private ContinuousProductionStrategy continuousProductionStrategy;

    @Mock
    private ScheduleStrategyFactory strategyFactory;

    @InjectMocks
    private ContinuousProductionHandler continuousProductionHandler;

    @Mock
    private ScheduleEventPublisher scheduleEventPublisher;

    @Mock
    private SchedulePersistenceService schedulePersistenceService;

    @InjectMocks
    private ResultValidationHandler resultValidationHandler;

    @Test
    void handle_shouldPassWhenFollowUpResultIsNonEnding() {
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
        when(strategyFactory.getProductionStrategy("01")).thenReturn(continuousProductionStrategy);

        // 走真实 S4.4 全链路，覆盖收尾、衔接、班次重分配、胎胚库存调整与降模步骤。
        continuousProductionHandler.handle(context);

        LhScheduleResult followUpResult = context.getScheduleResultList().stream()
                .filter(result -> "MAT-S1".equals(result.getMaterialCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("缺少续作衔接结果"));
        assertEquals("0", followUpResult.getIsEnd());
        assertNotNull(followUpResult.getSpecEndTime());
        assertNotNull(followUpResult.getTdaySpecEndTime());

        assertDoesNotThrow(() -> resultValidationHandler.handle(context));
        verify(schedulePersistenceService).replaceScheduleAtomically(context);
        verify(scheduleEventPublisher).publish(any());
    }

    private LhScheduleContext newContext() {
        LhScheduleContext context = new LhScheduleContext();
        context.setFactoryCode("116");
        context.setBatchNo("LHPC20260418099");
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
        sku.setEmbryoStock(1000);
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
        sku.setEmbryoStock(1000);
        sku.setShiftCapacity(8);
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
