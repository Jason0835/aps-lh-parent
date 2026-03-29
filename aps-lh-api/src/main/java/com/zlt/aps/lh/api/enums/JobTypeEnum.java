/**
 * Copyright (c) 2008, 智立通（厦门）科技有限公司 All rights reserved。
 */
package com.zlt.aps.lh.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 作业类型枚举
 *
 * @author zlt
 */
@Getter
@AllArgsConstructor
public enum JobTypeEnum {

    RESTRICTED("0", "限制作业"),
    NOT_ALLOWED("1", "不可作业");

    /** 类型编码 */
    private final String code;

    /** 类型描述 */
    private final String description;

    /**
     * 根据编码获取枚举
     *
     * @param code 类型编码
     * @return 作业类型枚举，未找到返回null
     */
    public static JobTypeEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (JobTypeEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
