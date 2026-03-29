package com.zlt.aps.lh.engine.decorator;

import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.dto.LhScheduleResponseDTO;
import lombok.extern.slf4j.Slf4j;

/**
 * 性能监控装饰器
 * <p>监控排程执行的性能指标</p>
 *
 * @author APS
 */
@Slf4j
public class PerformanceMonitorDecorator extends AbsScheduleExecutorDecorator {

    public PerformanceMonitorDecorator(IScheduleExecutor delegate) {
        super(delegate);
    }

    @Override
    public LhScheduleResponseDTO execute(LhScheduleContext context) {
        long startTime = System.currentTimeMillis();
        long startMemory = Runtime.getRuntime().freeMemory();

        LhScheduleResponseDTO response = super.execute(context);

        long elapsed = System.currentTimeMillis() - startTime;
        long memoryUsed = startMemory - Runtime.getRuntime().freeMemory();

        log.info("[性能监控] 排程耗时: {}ms, 内存使用: {}KB", elapsed, memoryUsed / 1024);
        log.info("[性能监控] 排程结果数: {}, 未排产数: {}",
                response.getScheduleResultCount(), response.getUnscheduledCount());

        /** @todo 若耗时超过阈值, 记录性能告警 */
        return response;
    }
}
