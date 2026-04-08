package com.zlt.aps.lh.api.util;

import com.zlt.aps.lh.api.enums.ShiftEnum;
import org.apache.commons.lang3.StringUtils;

/**
 * 班次类型配置值解析为 {@link ShiftEnum}
 *
 * @author APS
 */
public final class ShiftTypeParseUtil {

    private ShiftTypeParseUtil() {
    }

    /**
     * 解析班次类型：支持枚举编码 01/02/03 及中文 早班/中班/夜班
     *
     * @param raw 配置值
     * @return 枚举，无法识别返回 null
     */
    public static ShiftEnum parse(String raw) {
        if (StringUtils.isEmpty(raw)) {
            return null;
        }
        String t = raw.trim();
        ShiftEnum byCode = ShiftEnum.getByCode(t);
        if (byCode != null) {
            return byCode;
        }
        if (t.contains("夜")) {
            return ShiftEnum.NIGHT_SHIFT;
        }
        if (t.contains("早")) {
            return ShiftEnum.MORNING_SHIFT;
        }
        if (t.contains("中")) {
            return ShiftEnum.AFTERNOON_SHIFT;
        }
        return null;
    }
}
