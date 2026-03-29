/**
 * Copyright (c) 2008, 智立通（厦门）科技有限公司 All rights reserved。
 */
package com.zlt.aps.lh.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 班次枚举
 *
 * @author zlt
 */
@Getter
@AllArgsConstructor
public enum ShiftEnum {

    NIGHT_SHIFT("01", "夜班", "22:00", "06:00"),
    MORNING_SHIFT("02", "早班", "06:00", "14:00"),
    AFTERNOON_SHIFT("03", "中班", "14:00", "22:00");

    /** 班次编码 */
    private final String code;

    /** 班次描述 */
    private final String description;

    /** 开始时间 */
    private final String startTime;

    /** 结束时间 */
    private final String endTime;

    /**
     * 根据编码获取枚举
     *
     * @param code 班次编码
     * @return 班次枚举，未找到返回null
     */
    public static ShiftEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (ShiftEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
