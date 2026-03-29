/**
 * Copyright (c) 2008, 智立通（厦门）科技有限公司 All rights reserved。
 */
package com.zlt.aps.lh.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 发布状态枚举
 *
 * @author zlt
 */
@Getter
@AllArgsConstructor
public enum ReleaseStatusEnum {

    NOT_RELEASED("0", "未发布"),
    RELEASED("1", "已发布"),
    RELEASE_FAILED("2", "发布失败"),
    TIMEOUT_FAILED("3", "超时失败"),
    PENDING_RELEASE("4", "待发布");

    /** 状态编码 */
    private final String code;

    /** 状态描述 */
    private final String description;

    /**
     * 根据编码获取枚举
     *
     * @param code 状态编码
     * @return 发布状态枚举，未找到返回null
     */
    public static ReleaseStatusEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (ReleaseStatusEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
