package com.zlt.aps.lh.engine.observer.listeners;

import com.zlt.aps.lh.api.domain.entity.LhScheduleProcessLog;
import com.zlt.aps.lh.api.enums.EventTypeEnum;
import com.zlt.aps.lh.engine.observer.IScheduleEventListener;
import com.zlt.aps.lh.engine.observer.ScheduleEvent;
import com.zlt.aps.lh.mapper.LhScheduleProcessLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;

/**
 * 排程日志事件监听器
 * <p>记录排程过程的关键事件到日志表</p>
 *
 * @author APS
 */
@Slf4j
@Component
public class LogEventListener implements IScheduleEventListener {

    @Resource
    private LhScheduleProcessLogMapper processLogMapper;

    @Override
    public void onEvent(ScheduleEvent event) {
        log.info("记录排程事件日志, 类型: {}, 批次: {}, 消息: {}",
                event.getEventType(), event.getBatchNo(), event.getMessage());
        try {
            LhScheduleProcessLog processLog = new LhScheduleProcessLog();
            processLog.setBatchNo(event.getBatchNo());
            processLog.setTitle(event.getEventType().name());
            processLog.setBusiCode(event.getFactoryCode());
            processLog.setLogDetail(String.format("[%s] 批次:%s 工厂:%s 消息:%s",
                    event.getEventType().name(),
                    event.getBatchNo(),
                    event.getFactoryCode(),
                    event.getMessage()));
            processLog.setIsDelete(0);
            processLogMapper.insertBatch(Collections.singletonList(processLog));
        } catch (Exception e) {
            log.warn("排程日志写入失败, 不影响主流程, 原因: {}", e.getMessage());
        }
    }

    @Override
    public boolean supports(EventTypeEnum eventType) {
        return true;
    }
}
