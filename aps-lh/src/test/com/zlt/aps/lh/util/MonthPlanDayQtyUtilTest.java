package com.zlt.aps.lh.util;

import com.zlt.aps.mp.api.domain.entity.FactoryMonthPlanProductionFinalResult;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link MonthPlanDayQtyUtil} 月计划窗口取量回归测试。
 */
class MonthPlanDayQtyUtilTest {

    @Test
    void resolveWindowPlanQty_shouldSumCurrentMonthDayColumns() {
        FactoryMonthPlanProductionFinalResult plan = new FactoryMonthPlanProductionFinalResult();
        plan.setDay15(10);
        plan.setDay16(8);
        plan.setDay17(12);

        int windowPlanQty = MonthPlanDayQtyUtil.resolveWindowPlanQty(
                plan, date(2026, 4, 15), date(2026, 4, 17));

        assertEquals(30, windowPlanQty);
    }

    @Test
    void resolveWindowPlanQty_shouldTreatNullAsZero() {
        FactoryMonthPlanProductionFinalResult plan = new FactoryMonthPlanProductionFinalResult();
        plan.setDay15(10);
        plan.setDay17(12);

        int windowPlanQty = MonthPlanDayQtyUtil.resolveWindowPlanQty(
                plan, date(2026, 4, 15), date(2026, 4, 17));

        assertEquals(22, windowPlanQty);
    }

    @Test
    void resolveWindowPlanQty_shouldRejectCrossMonthWindow() {
        FactoryMonthPlanProductionFinalResult plan = new FactoryMonthPlanProductionFinalResult();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> MonthPlanDayQtyUtil.resolveWindowPlanQty(plan, date(2026, 4, 30), date(2026, 5, 1)));

        assertEquals(MonthPlanDayQtyUtil.CROSS_MONTH_UNSUPPORTED_MESSAGE, exception.getMessage());
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
