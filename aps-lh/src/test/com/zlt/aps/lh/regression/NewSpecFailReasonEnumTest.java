package com.zlt.aps.lh.regression;

import com.zlt.aps.lh.api.enums.NewSpecFailReasonEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 新增规格失败原因枚举回归：校验优先级与描述定义正确。
 */
class NewSpecFailReasonEnumTest {

    @Test
    void priorities_shouldFollowExpectedOrder() {
        assertTrue(NewSpecFailReasonEnum.NO_CAPACITY_IN_SCHEDULE_WINDOW.getPriority()
                > NewSpecFailReasonEnum.FIRST_INSPECTION_SHIFT_ALLOCATE_FAILED.getPriority());
        assertTrue(NewSpecFailReasonEnum.FIRST_INSPECTION_SHIFT_ALLOCATE_FAILED.getPriority()
                > NewSpecFailReasonEnum.MOULD_CHANGE_SHIFT_ALLOCATE_FAILED.getPriority());
        assertTrue(NewSpecFailReasonEnum.MOULD_CHANGE_SHIFT_ALLOCATE_FAILED.getPriority()
                > NewSpecFailReasonEnum.MACHINE_SELECTION_FAILED.getPriority());
    }

    @Test
    void descriptions_shouldMatchDomainSemantics() {
        assertEquals("机台选择失败", NewSpecFailReasonEnum.MACHINE_SELECTION_FAILED.getDescription());
        assertEquals("换模班次分配失败", NewSpecFailReasonEnum.MOULD_CHANGE_SHIFT_ALLOCATE_FAILED.getDescription());
        assertEquals("首检班次分配失败", NewSpecFailReasonEnum.FIRST_INSPECTION_SHIFT_ALLOCATE_FAILED.getDescription());
        assertEquals("排程窗口内无可用产能", NewSpecFailReasonEnum.NO_CAPACITY_IN_SCHEDULE_WINDOW.getDescription());
    }
}
