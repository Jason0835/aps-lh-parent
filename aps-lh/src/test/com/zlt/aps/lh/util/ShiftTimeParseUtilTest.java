package com.zlt.aps.lh.util;

import com.zlt.aps.lh.api.util.ShiftTimeParseUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ShiftTimeParseUtil} 单测
 */
class ShiftTimeParseUtilTest {

    @Test
    void parseToHms_严格HHmmss() {
        assertArrayEquals(new int[]{9, 5, 3}, ShiftTimeParseUtil.parseToHms("09:05:03"));
    }

    @Test
    void parseToHms_小时分未补零() {
        assertArrayEquals(new int[]{9, 5, 0}, ShiftTimeParseUtil.parseToHms("9:05"));
    }

    @Test
    void parseToHms_十四点整() {
        assertArrayEquals(new int[]{14, 0, 0}, ShiftTimeParseUtil.parseToHms("14:00:00"));
    }

    @Test
    void parseToHms_七点整() {
        assertArrayEquals(new int[]{7, 0, 0}, ShiftTimeParseUtil.parseToHms("07:00:00"));
    }

    @Test
    void parseToHms_空串抛错() {
        assertThrows(IllegalArgumentException.class, () -> ShiftTimeParseUtil.parseToHms(""));
    }
}
