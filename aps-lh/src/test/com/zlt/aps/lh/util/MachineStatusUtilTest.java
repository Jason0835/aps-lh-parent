package com.zlt.aps.lh.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 机台状态工具回归测试。
 */
class MachineStatusUtilTest {

    @Test
    void isEnabled_returnsTrueWhenStatusIsZero() {
        assertTrue(MachineStatusUtil.isEnabled("0"));
        assertTrue(MachineStatusUtil.isEnabled(" 0 "));
    }

    @Test
    void isEnabled_returnsFalseWhenStatusIsOneOrBlank() {
        assertFalse(MachineStatusUtil.isEnabled("1"));
        assertFalse(MachineStatusUtil.isEnabled(""));
        assertFalse(MachineStatusUtil.isEnabled(null));
    }
}
