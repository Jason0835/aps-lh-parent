/**
 * Copyright (c) 2008, 智立通（厦门）科技有限公司 All rights reserved。
 */
package com.zlt.aps.lh.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 未排产原因枚举
 *
 * @author zlt
 */
@Getter
@AllArgsConstructor
public enum UnscheduledReasonEnum {

    NO_AVAILABLE_MOULD("01", "无可用模具"),
    MOULD_CHANGE_EXCEEDED("02", "换模次数超限"),
    NO_AVAILABLE_MACHINE("03", "无可用机台"),
    CAPACITY_INSUFFICIENT("04", "产能不足"),
    EMBRYO_STOCK_INSUFFICIENT("05", "胎胚库存不足"),
    CONSTRUCTION_NOT_READY("06", "施工信息未就绪");

    /** 原因编码 */
    private final String code;

    /** 原因描述 */
    private final String description;

    /**
     * 根据编码获取枚举
     *
     * @param code 原因编码
     * @return 未排产原因枚举，未找到返回null
     */
    public static UnscheduledReasonEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (UnscheduledReasonEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
