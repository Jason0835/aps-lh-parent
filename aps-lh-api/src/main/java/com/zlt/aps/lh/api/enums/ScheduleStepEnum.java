/**
 * Copyright (c) 2008, 智立通（厦门）科技有限公司 All rights reserved。
 */
package com.zlt.aps.lh.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 排程步骤枚举
 *
 * @author zlt
 */
@Getter
@AllArgsConstructor
public enum ScheduleStepEnum {

    S4_1_PRE_VALIDATION("S4.1", "前置校验与数据清理"),
    S4_2_DATA_INIT("S4.2", "基础数据初始化"),
    S4_3_ADJUST_AND_GATHER("S4.3", "排程调整与SKU归集"),
    S4_4_CONTINUOUS_PRODUCTION("S4.4", "续作规格排产"),
    S4_5_NEW_PRODUCTION("S4.5", "新增规格排产"),
    S4_6_RESULT_VALIDATION("S4.6", "结果校验与发布保存");

    /** 步骤编码 */
    private final String code;

    /** 步骤描述 */
    private final String description;

    /**
     * 根据编码获取枚举
     *
     * @param code 步骤编码
     * @return 排程步骤枚举，未找到返回null
     */
    public static ScheduleStepEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (ScheduleStepEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
