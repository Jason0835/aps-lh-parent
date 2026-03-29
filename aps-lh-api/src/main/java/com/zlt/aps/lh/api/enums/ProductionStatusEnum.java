/**
 * Copyright (c) 2008, 智立通（厦门）科技有限公司 All rights reserved。
 */
package com.zlt.aps.lh.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 生产状态枚举
 *
 * @author zlt
 */
@Getter
@AllArgsConstructor
public enum ProductionStatusEnum {

    NOT_PRODUCED("0", "未生产"),
    PRODUCING("1", "生产中"),
    COMPLETED("2", "生产完成");

    /** 状态编码 */
    private final String code;

    /** 状态描述 */
    private final String description;

    /**
     * 根据编码获取枚举
     *
     * @param code 状态编码
     * @return 生产状态枚举，未找到返回null
     */
    public static ProductionStatusEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (ProductionStatusEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
