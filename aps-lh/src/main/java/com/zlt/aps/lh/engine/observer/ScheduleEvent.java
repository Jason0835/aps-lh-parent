package com.zlt.aps.lh.engine.observer;

import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.api.enums.EventTypeEnum;
import lombok.Data;

import java.util.Date;

/**
 * 排程事件对象
 *
 * @author APS
 */
@Data
public class ScheduleEvent {

    /** 事件类型 */
    private EventTypeEnum eventType;
    /** 批次号 */
    private String batchNo;
    /** 工厂编号 */
    private String factoryCode;
    /** 排程日期 */
    private Date scheduleDate;
    /** 事件发生时间 */
    private Date eventTime;
    /** 事件消息 */
    private String message;
    /** 排程上下文(携带结果数据) */
    private LhScheduleContext context;

    /**
     * 事件中的业务排程日：优先目标日；发布场景无目标日时从结果首条取 scheduleDate
     */
    private static Date resolveBusinessScheduleDate(LhScheduleContext context) {
        if (context.getScheduleTargetDate() != null) {
            return context.getScheduleTargetDate();
        }
        if (context.getScheduleResultList() != null && !context.getScheduleResultList().isEmpty()) {
            return context.getScheduleResultList().get(0).getScheduleDate();
        }
        return context.getScheduleDate();
    }

    /**
     * 构建排程完成事件
     *
     * @param context 排程上下文
     * @return 排程完成事件
     */
    public static ScheduleEvent completed(LhScheduleContext context) {
        ScheduleEvent event = new ScheduleEvent();
        event.setEventType(EventTypeEnum.SCHEDULE_COMPLETED);
        event.setBatchNo(context.getBatchNo());
        event.setFactoryCode(context.getFactoryCode());
        event.setScheduleDate(resolveBusinessScheduleDate(context));
        event.setEventTime(new Date());
        event.setMessage("硫化排程完成");
        event.setContext(context);
        return event;
    }

    /**
     * 构建排程失败事件
     *
     * @param context 排程上下文
     * @param reason  失败原因
     * @return 排程失败事件
     */
    public static ScheduleEvent failed(LhScheduleContext context, String reason) {
        ScheduleEvent event = new ScheduleEvent();
        event.setEventType(EventTypeEnum.SCHEDULE_FAILED);
        event.setBatchNo(context.getBatchNo());
        event.setFactoryCode(context.getFactoryCode());
        event.setScheduleDate(resolveBusinessScheduleDate(context));
        event.setEventTime(new Date());
        event.setMessage(reason);
        event.setContext(context);
        return event;
    }

    /**
     * 构建排程结果发布事件
     *
     * @param context 排程上下文（含批次号）
     * @return 排程结果发布事件
     */
    public static ScheduleEvent published(LhScheduleContext context) {
        ScheduleEvent event = new ScheduleEvent();
        event.setEventType(EventTypeEnum.RESULT_PUBLISHED);
        event.setBatchNo(context.getBatchNo());
        event.setFactoryCode(context.getFactoryCode());
        event.setScheduleDate(resolveBusinessScheduleDate(context));
        event.setEventTime(new Date());
        event.setMessage("排程结果已发布");
        event.setContext(context);
        return event;
    }
}
