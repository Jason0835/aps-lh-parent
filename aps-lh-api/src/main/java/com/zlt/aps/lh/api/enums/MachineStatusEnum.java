/**
 * Copyright (c) 2008, 智立通（厦门）科技有限公司 All rights reserved。
 */
package com.zlt.aps.lh.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 机台状态枚举
 *
 * @author zlt
 */
@Getter
@AllArgsConstructor
public enum MachineStatusEnum {

    ENABLED("1", "启用"),
    DISABLED("0", "停用");

    /** 状态编码 */
    private final String code;

    /** 状态描述 */
    private final String description;

    /**
     * 根据编码获取枚举
     *
     * @param code 状态编码
     * @return 机台状态枚举，未找到返回null
     */
    public static MachineStatusEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (MachineStatusEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
