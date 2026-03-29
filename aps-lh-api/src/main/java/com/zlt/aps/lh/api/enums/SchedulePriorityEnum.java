/**
 * Copyright (c) 2008, 智立通（厦门）科技有限公司 All rights reserved。
 */
package com.zlt.aps.lh.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 排产优先级枚举
 *
 * @author zlt
 */
@Getter
@AllArgsConstructor
public enum SchedulePriorityEnum {

    DELIVERY_LOCKED("01", "有发货要求(锁定交期)"),
    DELAY_PRODUCTION("02", "延误上机"),
    STRUCTURE_ENDING("03", "结构收尾SKU优先"),
    HIGH_PRIORITY("04", "高优先级"),
    CYCLE_PRODUCTION("05", "周期排产"),
    MID_PRIORITY("06", "中优先级"),
    MATCH_PRODUCTION("07", "搭配排产");

    /** 优先级编码 */
    private final String code;

    /** 优先级描述 */
    private final String description;

    /**
     * 根据编码获取枚举
     *
     * @param code 优先级编码
     * @return 排产优先级枚举，未找到返回null
     */
    public static SchedulePriorityEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (SchedulePriorityEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
