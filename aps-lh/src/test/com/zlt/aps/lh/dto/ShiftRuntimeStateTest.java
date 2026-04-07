package com.zlt.aps.lh.dto;

import com.zlt.aps.lh.api.domain.dto.ShiftRuntimeState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * {@link ShiftRuntimeState} 属性读写测试
 */
class ShiftRuntimeStateTest {

    @Test
    void getters_与setter写入的值一致() {
        ShiftRuntimeState state = new ShiftRuntimeState();
        state.setShiftIndex(3);
        state.setRemainingCapacity(100);
        state.setAvailable(false);
        state.setUnavailableReason("停机");

        assertEquals(3, state.getShiftIndex());
        assertEquals(100, state.getRemainingCapacity());
        assertFalse(state.isAvailable());
        assertEquals("停机", state.getUnavailableReason());
    }
}
