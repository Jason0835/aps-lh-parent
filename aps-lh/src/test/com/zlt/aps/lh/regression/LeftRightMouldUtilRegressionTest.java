package com.zlt.aps.lh.regression;

import com.zlt.aps.lh.util.LeftRightMouldUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 左右模字段回归测试。
 */
class LeftRightMouldUtilRegressionTest {

    @Test
    void resolveLeftRightMould_shouldDefaultToLrWhenMachineCodeIsNormal() {
        assertEquals("LR", LeftRightMouldUtil.resolveLeftRightMould(null, "K1501"));
    }

    @Test
    void resolveLeftRightMould_shouldResolveLWhenMachineCodeEndsWithL() {
        assertEquals("L", LeftRightMouldUtil.resolveLeftRightMould(null, "K1501L"));
    }

    @Test
    void resolveLeftRightMould_shouldResolveRWhenMachineCodeEndsWithR() {
        assertEquals("R", LeftRightMouldUtil.resolveLeftRightMould(null, "K1502R"));
    }

    @Test
    void resolveLeftRightMould_shouldKeepCurrentValueWhenAlreadyPresent() {
        assertEquals("LR", LeftRightMouldUtil.resolveLeftRightMould("LR", "K1501L"));
    }
}
