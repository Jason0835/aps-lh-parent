/**
 * Copyright (c) 2008, 智立通（厦门）科技有限公司 All rights reserved。
 */
package com.zlt.aps.lh.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 模具交替类型枚举
 *
 * @author zlt
 */
@Getter
@AllArgsConstructor
public enum MouldChangeTypeEnum {

    REGULAR("01", "正规"),
    TYPE_BLOCK("02", "更换活字块"),
    SAND_BLAST("03", "模具喷砂清洗"),
    DRY_ICE("04", "模具干冰清洗");

    /** 类型编码 */
    private final String code;

    /** 类型描述 */
    private final String description;

    /**
     * 根据编码获取枚举
     *
     * @param code 类型编码
     * @return 模具交替类型枚举，未找到返回null
     */
    public static MouldChangeTypeEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (MouldChangeTypeEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
