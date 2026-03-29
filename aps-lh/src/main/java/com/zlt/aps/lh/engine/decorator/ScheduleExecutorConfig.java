package com.zlt.aps.lh.engine.decorator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.annotation.Resource;

/**
 * 排程执行器装饰器配置
 * <p>通过配置类组装装饰器链</p>
 *
 * @author APS
 */
@Slf4j
@Configuration
public class ScheduleExecutorConfig {

    @Resource
    private DefaultScheduleExecutor defaultScheduleExecutor;

    /**
     * 构建带装饰器的排程执行器
     * <p>装饰器链: PerformanceMonitor -> Logging -> Default</p>
     *
     * @return 装饰后的排程执行器
     */
    @Bean
    @Primary
    public IScheduleExecutor decoratedScheduleExecutor() {
        IScheduleExecutor executor = defaultScheduleExecutor;
        executor = new LoggingScheduleDecorator(executor);
        executor = new PerformanceMonitorDecorator(executor);
        log.info("排程执行器装饰器链构建完成");
        return executor;
    }
}
