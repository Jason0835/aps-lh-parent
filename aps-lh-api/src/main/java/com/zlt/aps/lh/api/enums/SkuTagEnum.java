/**
 * Copyright (c) 2008, 智立通（厦门）科技有限公司 All rights reserved。
 */
package com.zlt.aps.lh.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * SKU状态标记枚举
 *
 * @author zlt
 */
@Getter
@AllArgsConstructor
public enum SkuTagEnum {

    NORMAL("01", "常规"),
    ENDING("02", "收尾");

    /** 标记编码 */
    private final String code;

    /** 标记描述 */
    private final String description;

    /**
     * 根据编码获取枚举
     *
     * @param code 标记编码
     * @return SKU状态标记枚举，未找到返回null
     */
    public static SkuTagEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (SkuTagEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
