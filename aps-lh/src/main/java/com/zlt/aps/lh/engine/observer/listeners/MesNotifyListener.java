package com.zlt.aps.lh.engine.observer.listeners;

import com.zlt.aps.lh.engine.observer.IScheduleEventListener;
import com.zlt.aps.lh.engine.observer.ScheduleEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MES系统通知监听器
 * <p>排程完成后通知MES系统</p>
 *
 * @author APS
 */
@Slf4j
@Component
public class MesNotifyListener implements IScheduleEventListener {

    @Override
    public void onEvent(ScheduleEvent event) {
        log.info("通知MES系统, 批次号: {}, 事件类型: {}, 工厂: {}",
                event.getBatchNo(), event.getEventType(), event.getFactoryCode());
        try {
            // 组装推送数据，实际调用由MES接口模块负责（当前记录日志，接口集成待MES侧对接）
            int resultCount = event.getContext() != null
                    ? event.getContext().getScheduleResultList().size() : 0;
            log.info("MES通知准备: 批次={}, 排程结果数={}", event.getBatchNo(), resultCount);
            // TODO(MES对接): 调用 MES 接口推送排程结果（待 MES 侧提供接口文档后实现）
        } catch (Exception e) {
            log.warn("MES通知异常，不影响主流程, 原因: {}", e.getMessage());
        }
    }

    @Override
    public boolean supports(ScheduleEvent.EventType eventType) {
        return eventType == ScheduleEvent.EventType.SCHEDULE_COMPLETED
                || eventType == ScheduleEvent.EventType.RESULT_PUBLISHED;
    }
}
