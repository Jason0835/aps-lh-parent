/**
 * Copyright (c) 2008, 智立通（厦门）科技有限公司 All rights reserved。
 */
package com.zlt.aps.lh.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 删除标识枚举
 *
 * @author zlt
 */
@Getter
@AllArgsConstructor
public enum DeleteFlagEnum {

    NORMAL(0, "正常"),
    DELETED(1, "已删除");

    /** 删除标识编码 */
    private final Integer code;

    /** 描述 */
    private final String description;

    /**
     * 根据编码获取枚举
     *
     * @param code 删除标识编码
     * @return 删除标识枚举，未找到返回null
     */
    public static DeleteFlagEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (DeleteFlagEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
