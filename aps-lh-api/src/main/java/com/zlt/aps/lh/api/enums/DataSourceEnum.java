/**
 * Copyright (c) 2008, 智立通（厦门）科技有限公司 All rights reserved。
 */
package com.zlt.aps.lh.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 数据来源枚举
 *
 * @author zlt
 */
@Getter
@AllArgsConstructor
public enum DataSourceEnum {

    AUTO_SCHEDULE("0", "自动排程"),
    INSERT_ORDER("1", "插单"),
    IMPORT("2", "导入");

    /** 来源编码 */
    private final String code;

    /** 来源描述 */
    private final String description;

    /**
     * 根据编码获取枚举
     *
     * @param code 来源编码
     * @return 数据来源枚举，未找到返回null
     */
    public static DataSourceEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (DataSourceEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
