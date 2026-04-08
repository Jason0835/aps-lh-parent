package com.zlt.aps.lh.util;

import com.zlt.aps.lh.api.domain.entity.LhScheduleResult;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link ShiftFieldUtil} BeanUtil 读写与越界行为
 */
class ShiftFieldUtilTest {

    @Test
    void setAndGet_planQty_roundTrip() {
        LhScheduleResult r = new LhScheduleResult();
        Date s = new Date(1L);
        Date e = new Date(2L);
        ShiftFieldUtil.setShiftPlanQty(r, 1, 10, s, e);
        assertEquals(10, ShiftFieldUtil.getShiftPlanQty(r, 1));
        assertEquals(s, ShiftFieldUtil.getShiftStartTime(r, 1));
        assertEquals(e, ShiftFieldUtil.getShiftEndTime(r, 1));
    }

    @Test
    void setAndGet_slot8() {
        LhScheduleResult r = new LhScheduleResult();
        ShiftFieldUtil.setShiftPlanQty(r, 8, 3, null, null);
        assertEquals(3, ShiftFieldUtil.getShiftPlanQty(r, 8));
    }

    @Test
    void invalidIndex_returnsNull() {
        LhScheduleResult r = new LhScheduleResult();
        assertNull(ShiftFieldUtil.getShiftPlanQty(r, 0));
        assertNull(ShiftFieldUtil.getShiftPlanQty(r, 9));
    }
}
