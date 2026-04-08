package com.zlt.aps.lh.util;

import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.vo.LhShiftConfigVO;
import com.zlt.aps.lh.api.enums.ShiftEnum;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LhScheduleTimeUtil#getScheduleShifts} 默认模板与 {@link LhShiftConfigVO} 跨日/跨年行为单测
 */
class LhScheduleTimeUtilShiftInfoTest {

    @Test
    void getScheduleShifts_第三班夜班跨自然日且偏移为1() {
        LhScheduleContext ctx = new LhScheduleContext();
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.JANUARY, 15, 10, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date scheduleDate = cal.getTime();

        List<LhShiftConfigVO> shifts = LhScheduleTimeUtil.getScheduleShifts(ctx, scheduleDate);
        assertEquals(8, shifts.size());
        LhShiftConfigVO class3 = shifts.get(2);
        assertEquals(3, class3.getShiftIndex().intValue());
        assertEquals(1, class3.getDateOffset().intValue());
        assertTrue(class3.isCrossesCalendarDay(), "夜班应跨自然日");
        assertEquals(class3.resolveShiftTypeEnum().getCode(), class3.getShiftCode());
        assertTrue(class3.getDurationMinutes() > 0);
        assertNotNull(class3.getShiftName());
        assertEquals(ShiftEnum.NIGHT_SHIFT, class3.resolveShiftTypeEnum());
    }

    @Test
    void getScheduleShifts_第一班为T日偏移0且不跨自然日() {
        LhScheduleContext ctx = new LhScheduleContext();
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.MARCH, 1, 8, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        List<LhShiftConfigVO> shifts = LhScheduleTimeUtil.getScheduleShifts(ctx, cal.getTime());
        LhShiftConfigVO class1 = shifts.get(0);
        assertEquals(0, class1.getDateOffset().intValue());
        assertFalse(class1.isCrossesCalendarDay());
    }

    @Test
    void getScheduleShifts_T为年末第三班夜班跨年() {
        LhScheduleContext ctx = new LhScheduleContext();
        Calendar cal = Calendar.getInstance();
        cal.set(2025, Calendar.DECEMBER, 31, 12, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        List<LhShiftConfigVO> shifts = LhScheduleTimeUtil.getScheduleShifts(ctx, cal.getTime());
        LhShiftConfigVO class3 = shifts.get(2);
        assertEquals(3, class3.getShiftIndex().intValue());
        assertTrue(class3.isCrossesYear(), "T 日夜班应跨入次年");
    }
}
