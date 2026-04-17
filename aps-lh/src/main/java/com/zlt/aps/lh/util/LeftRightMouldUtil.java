package com.zlt.aps.lh.util;

import com.zlt.aps.lh.api.constant.LhScheduleConstant;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;

/**
 * 左右模字段计算工具。
 *
 * @author APS
 */
public final class LeftRightMouldUtil {

    private LeftRightMouldUtil() {
    }

    /**
     * 解析左右模标识。
     * <p>规则：已有值优先；机台编码后缀 L/R 分别映射 L/R；其他默认 LR。</p>
     *
     * @param currentValue 当前左右模值
     * @param machineCode  机台编码
     * @return 左右模标识（L/R/LR）
     */
    public static String resolveLeftRightMould(String currentValue, String machineCode) {
        if (StringUtils.isNotEmpty(currentValue)) {
            return currentValue;
        }
        if (StringUtils.isEmpty(machineCode)) {
            return LhScheduleConstant.LEFT_RIGHT_MOULD;
        }
        String normalizedMachineCode = machineCode.trim().toUpperCase(Locale.ROOT);
        if (normalizedMachineCode.endsWith(LhScheduleConstant.LEFT_MOULD)) {
            return LhScheduleConstant.LEFT_MOULD;
        }
        if (normalizedMachineCode.endsWith(LhScheduleConstant.RIGHT_MOULD)) {
            return LhScheduleConstant.RIGHT_MOULD;
        }
        return LhScheduleConstant.LEFT_RIGHT_MOULD;
    }
}
