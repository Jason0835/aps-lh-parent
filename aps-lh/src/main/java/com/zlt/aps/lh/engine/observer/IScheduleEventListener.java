package com.zlt.aps.lh.engine.observer;

/**
 * 排程事件监听器接口(观察者)
 *
 * @author APS
 */
public interface IScheduleEventListener {

    /**
     * 处理排程事件
     *
     * @param event 排程事件
     */
    void onEvent(ScheduleEvent event);

    /**
     * 是否关注该事件类型
     *
     * @param eventType 事件类型
     * @return true-关注, false-不关注
     */
    boolean supports(ScheduleEvent.EventType eventType);
}
