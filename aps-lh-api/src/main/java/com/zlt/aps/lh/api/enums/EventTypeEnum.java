package com.zlt.aps.lh.api.enums;

/**
 * 排程事件类型（原 {@code ScheduleEvent} 内部枚举抽取至 API 模块）
 *
 * @author APS
 */
public enum EventTypeEnum {

    /** 排程开始 */
    SCHEDULE_STARTED,
    /** 排程步骤完成 */
    STEP_COMPLETED,
    /** 排程完成 */
    SCHEDULE_COMPLETED,
    /** 排程失败 */
    SCHEDULE_FAILED,
    /** 排程结果发布 */
    RESULT_PUBLISHED
}
