package com.zlt.aps.lh.regression;

import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.engine.strategy.impl.DefaultMouldChangeBalanceStrategy;
import com.zlt.aps.mdm.api.domain.entity.MdmDevicePlanShut;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 换模均衡策略回归：设备停机期间不得发起换模。
 */
class DefaultMouldChangeBalanceStrategyRegressionTest {

    @Test
    void allocateMouldChange_shouldDelayToNextMorningWhenDowntimeEndsAtNight() {
        DefaultMouldChangeBalanceStrategy strategy = new DefaultMouldChangeBalanceStrategy();
        LhScheduleContext context = new LhScheduleContext();
        context.getDevicePlanShutList().add(planShut("K2024",
                dateTime(2026, 4, 21, 6, 0, 0),
                dateTime(2026, 4, 21, 23, 59, 59)));

        Date allocatedTime = strategy.allocateMouldChange(
                context,
                "K2024",
                dateTime(2026, 4, 21, 6, 0, 0));

        assertEquals(dateTime(2026, 4, 22, 6, 0, 0), allocatedTime,
                "停机结束落在夜班禁换模时段时，应顺延到次日早班再换模");
        assertNull(context.getDailyMouldChangeCountMap().get("2026-04-21"),
                "停机日不应占用当日换模配额");
        assertArrayEquals(new int[]{1, 0}, context.getDailyMouldChangeCountMap().get("2026-04-22"));
    }

    @Test
    void allocateMouldChange_shouldIgnoreDowntimeOfOtherMachine() {
        DefaultMouldChangeBalanceStrategy strategy = new DefaultMouldChangeBalanceStrategy();
        LhScheduleContext context = new LhScheduleContext();
        context.getDevicePlanShutList().add(planShut("K2024",
                dateTime(2026, 4, 21, 6, 0, 0),
                dateTime(2026, 4, 21, 23, 59, 59)));

        Date allocatedTime = strategy.allocateMouldChange(
                context,
                "K2025",
                dateTime(2026, 4, 21, 6, 0, 0));

        assertEquals(dateTime(2026, 4, 21, 6, 0, 0), allocatedTime,
                "其它机台的停机记录不应影响当前机台换模");
        assertArrayEquals(new int[]{1, 0}, context.getDailyMouldChangeCountMap().get("2026-04-21"));
    }

    private MdmDevicePlanShut planShut(String machineCode, Date beginDate, Date endDate) {
        MdmDevicePlanShut planShut = new MdmDevicePlanShut();
        planShut.setMachineCode(machineCode);
        planShut.setBeginDate(beginDate);
        planShut.setEndDate(endDate);
        return planShut;
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
