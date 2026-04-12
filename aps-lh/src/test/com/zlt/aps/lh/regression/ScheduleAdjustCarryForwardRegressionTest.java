package com.zlt.aps.lh.regression;

import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.api.domain.entity.LhScheduleResult;
import com.zlt.aps.lh.api.domain.entity.LhShiftFinishQty;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.engine.strategy.impl.DefaultEndingJudgmentStrategy;
import com.zlt.aps.lh.handler.ScheduleAdjustHandler;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import com.zlt.aps.mdm.api.domain.entity.MdmMonthSurplus;
import com.zlt.aps.mdm.api.domain.entity.MdmSkuLhCapacity;
import com.zlt.aps.mp.api.domain.entity.FactoryMonthPlanProductionFinalResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        context.setMonthPlanList(Collections.singletonList(plan));

        MdmMonthSurplus monthSurplus = new MdmMonthSurplus();
        monthSurplus.setPlanSurplusQty(BigDecimal.valueOf(100));
        context.getMonthSurplusMap().put("MAT-1", monthSurplus);

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
        assertEquals(120, sku.getPendingQty());
        assertEquals(120, sku.getSurplusQty());
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
