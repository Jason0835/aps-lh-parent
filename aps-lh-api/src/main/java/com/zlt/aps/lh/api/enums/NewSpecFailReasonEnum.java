/**
 * Copyright (c) 2008, 智立通（厦门）科技有限公司 All rights reserved。
 */
package com.zlt.aps.lh.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 新增规格排产失败原因优先级枚举
 *
 * @author APS
 */
@Getter
@AllArgsConstructor
public enum NewSpecFailReasonEnum {

    MACHINE_SELECTION_FAILED("01", "机台选择失败", 1),
    MOULD_CHANGE_SHIFT_ALLOCATE_FAILED("02", "换模班次分配失败", 2),
    FIRST_INSPECTION_SHIFT_ALLOCATE_FAILED("03", "首检班次分配失败", 3),
    NO_CAPACITY_IN_SCHEDULE_WINDOW("04", "排程窗口内无可用产能", 4);

    /** 原因编码 */
    private final String code;

    /** 原因描述 */
    private final String description;

    /** 优先级，数值越大优先级越高 */
    private final int priority;
}
