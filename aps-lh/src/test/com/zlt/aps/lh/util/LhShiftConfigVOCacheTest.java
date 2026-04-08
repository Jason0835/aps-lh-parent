package com.zlt.aps.lh.util;

import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.vo.LhShiftConfigVO;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link LhShiftConfigVO} 边界缓存与 setter 失效单测
 */
class LhShiftConfigVOCacheTest {

    @Test
    void 修改开始时刻后应重新合成绝对起止() {
        LhScheduleContext ctx = new LhScheduleContext();
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.JANUARY, 15, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date scheduleDate = cal.getTime();

        List<LhShiftConfigVO> shifts = LhScheduleTimeUtil.buildDefaultScheduleShifts(ctx, scheduleDate);
        LhShiftConfigVO first = shifts.get(0);
        Date start1 = first.getShiftStartDateTime();
        assertNotNull(start1);

        first.setStartTime("07:00:00");
        assertEquals("07:00:00", first.getStartTime(), "子类 setStartTime 应更新父类时刻字段");
        Date start2 = first.getShiftStartDateTime();
        assertNotNull(start2);
        assertNotEquals(start1.getTime(), start2.getTime());
    }

    @Test
    void 无法合成时短路重复计算_修正后可通过setter恢复() {
        LhShiftConfigVO vo = new LhShiftConfigVO();
        vo.setScheduleBaseDate(new Date());
        vo.setDateOffset(0);
        vo.setShiftType("02");
        vo.setStartTime("06:00:00");
        vo.setEndTime("14:00:00");
        assertNotNull(vo.getShiftStartDateTime());

        vo.setShiftType("invalid_type_xyz");
        assertNull(vo.getShiftStartDateTime());

        vo.setShiftType("02");
        assertNotNull(vo.getShiftStartDateTime());
    }
}
