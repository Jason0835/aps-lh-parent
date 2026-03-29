package com.zlt.aps.lh.engine.observer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * 排程事件发布器(被观察者/主题)
 * <p>负责将排程事件分发给所有注册的监听器</p>
 *
 * @author APS
 */
@Slf4j
@Component
public class ScheduleEventPublisher {

    @Autowired(required = false)
    private List<IScheduleEventListener> listeners;

    /**
     * 发布事件给所有支持该事件类型的监听器
     *
     * @param event 排程事件
     */
    public void publish(ScheduleEvent event) {
        if (listeners == null || listeners.isEmpty()) {
            log.debug("无注册的事件监听器");
            return;
        }
        log.info("发布排程事件: {}, 批次号: {}", event.getEventType(), event.getBatchNo());
        for (IScheduleEventListener listener : listeners) {
            try {
                if (listener.supports(event.getEventType())) {
                    listener.onEvent(event);
                }
            } catch (Exception e) {
                log.error("事件监听器[{}]处理异常", listener.getClass().getSimpleName(), e);
            }
        }
    }
}
