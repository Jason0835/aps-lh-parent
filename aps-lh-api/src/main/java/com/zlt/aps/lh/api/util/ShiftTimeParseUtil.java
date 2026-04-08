package com.zlt.aps.lh.api.util;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import org.apache.commons.lang3.StringUtils;

/**
 * 班次配置时刻字符串解析（优先 Hutool {@link DateUtil}），格式与历史行为兼容
 *
 * @author APS
 */
public final class ShiftTimeParseUtil {

    /** 仅用于解析时刻片段的固定日期前缀（避开 1970 纪元边界在部分时区/解析实现下的歧义） */
    private static final String DATE_PREFIX = "2000-01-01 ";

    /** 依次尝试的完整日期时间模式（前缀 + 时刻） */
    private static final String[] TIME_PARSE_PATTERNS = {
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd H:mm:ss",
            "yyyy-MM-dd H:mm",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'H:mm:ss",
            "yyyy-MM-dd'T'H:mm"
    };

    private ShiftTimeParseUtil() {
    }

    /**
     * 解析时刻字符串为 时、分、秒（24 小时制）
     *
     * @param raw 原始字符串（如 09:05:03、9:05）
     * @return 长度为 3：时、分、秒
     * @throws IllegalArgumentException 空串或无法解析
     */
    public static int[] parseToHms(String raw) {
        if (StringUtils.isEmpty(raw)) {
            throw new IllegalArgumentException("班次时刻不能为空");
        }
        String t = raw.trim();
        String combined = t.contains("T") ? ("2000-01-01" + t.substring(t.indexOf('T'))) : (DATE_PREFIX + t);
        RuntimeException last = null;
        for (String pattern : TIME_PARSE_PATTERNS) {
            try {
                DateTime dt = DateUtil.parse(combined, pattern);
                return new int[]{dt.hour(true), dt.minute(), dt.second()};
            } catch (RuntimeException e) {
                last = e;
            }
        }
        throw new IllegalArgumentException("班次时刻格式无法识别，实际=" + t,
                last != null ? last : new IllegalArgumentException());
    }
}
