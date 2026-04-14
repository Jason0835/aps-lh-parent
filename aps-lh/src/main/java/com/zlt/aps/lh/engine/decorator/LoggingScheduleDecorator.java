package com.zlt.aps.lh.engine.decorator;

import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.dto.LhScheduleResponseDTO;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 日志增强装饰器
 * <p>在排程执行前后添加详细的日志记录</p>
 *
 * @author APS
 */
@Slf4j
public class LoggingScheduleDecorator extends AbsScheduleExecutorDecorator {

    public LoggingScheduleDecorator(IScheduleExecutor delegate) {
        super(delegate);
    }

    @Override
    public LhScheduleResponseDTO execute(LhScheduleContext context) {
        log.info("====== [日志装饰器] 排程执行开始 ======");
        log.info("工厂编号: {}", context.getFactoryCode());
        log.info("排程目标日: {}, T日: {}",
                LhScheduleTimeUtil.formatDate(context.getScheduleTargetDate()),
                LhScheduleTimeUtil.formatDate(context.getScheduleDate()));
        log.info("批次号: {}", context.getBatchNo());

        LhScheduleResponseDTO response = super.execute(context);

        log.info("====== [日志装饰器] 排程执行结束 ======");
        log.info("执行结果: {}", response.isSuccess() ? "成功" : "失败");
        log.info("排程结果数: {}", response.getScheduleResultCount());
        log.info("未排产数: {}", response.getUnscheduledCount());
        log.info("模具交替计划数: {}", response.getMouldChangePlanCount());
        if (!response.isSuccess()) {
            log.warn("失败原因: {}", response.getMessage());
        }
        return response;
    }
}
