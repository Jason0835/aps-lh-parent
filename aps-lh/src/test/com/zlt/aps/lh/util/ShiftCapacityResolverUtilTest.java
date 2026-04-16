package com.zlt.aps.lh.util;

import com.zlt.aps.lh.api.domain.vo.LhShiftConfigVO;
import com.zlt.aps.lh.context.LhScheduleContext;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ShiftCapacityResolverUtil} 班产与残班折算回归测试。
 */
class ShiftCapacityResolverUtilTest {

    @Test
    void resolveShiftCapacity_shouldDeductClassCapacityByAvailableTime() {
        LhShiftConfigVO morningShift = findMorningShift(date(2026, 4, 17));

        int shiftQty = ShiftCapacityResolverUtil.resolveShiftCapacity(
                morningShift, dateTime(2026, 4, 17, 7, 0), 16, 3060, 2);

        assertEquals(14, shiftQty, "有班产主数据时，残班应按有效时长比例向下折算");
    }

    @Test
    void resolveShiftCapacity_shouldFallbackToLhTimeAndMachineMouldQty() {
        LhShiftConfigVO morningShift = findMorningShift(date(2026, 4, 17));

        int partialShiftQty = ShiftCapacityResolverUtil.resolveShiftCapacity(
                morningShift, dateTime(2026, 4, 17, 7, 0), 0, 3060, 2);
        int fullShiftQty = ShiftCapacityResolverUtil.resolveShiftCapacity(
                morningShift, dateTime(2026, 4, 17, 6, 0), 0, 3060, 2);

        assertEquals(16, partialShiftQty, "无班产主数据时，应按有效时长、硫化时长和机台模台数回退计算");
        assertEquals(18, fullShiftQty, "无班产主数据时，满班应按整班时长和机台模台数回退计算");
    }

    private LhShiftConfigVO findMorningShift(Date scheduleDate) {
        LhScheduleContext context = new LhScheduleContext();
        List<LhShiftConfigVO> shifts = LhScheduleTimeUtil.buildDefaultScheduleShifts(context, scheduleDate);
        for (LhShiftConfigVO shift : shifts) {
            if (shift.getShiftStartDateTime() != null && getHour(shift.getShiftStartDateTime()) == 6) {
                return shift;
            }
        }
        throw new IllegalStateException("未找到 06:00 开始的早班");
    }

    private int getHour(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar.get(Calendar.HOUR_OF_DAY);
    }

    private static Date date(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month - 1);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        return calendar.getTime();
    }

    private static Date dateTime(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month - 1);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        return calendar.getTime();
    }
}
