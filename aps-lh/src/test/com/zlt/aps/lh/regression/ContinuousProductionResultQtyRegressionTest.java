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

import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;

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
        sku.setPendingQty(30);
        sku.setShiftCapacity(16);
        sku.setLhTimeSeconds(3060);
        sku.setScheduleType("01");
        context.getContinuousSkuList().add(sku);

        when(orderNoGenerator.generateOrderNo(any())).thenReturn("LHGD20260411011");
        when(endingJudgmentStrategy.isEnding(any(), any())).thenReturn(false);

        strategy.scheduleContinuousEnding(context);

        LhScheduleResult result = context.getScheduleResultList().get(0);
        assertEquals(50, result.getTotalDailyPlanQty());
        assertEquals(30, result.getDailyPlanQty());
        assertEquals(16, result.getClass1PlanQty());
        assertEquals(14, result.getClass2PlanQty());
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
