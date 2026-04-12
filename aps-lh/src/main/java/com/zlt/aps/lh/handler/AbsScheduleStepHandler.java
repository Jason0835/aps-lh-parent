package com.zlt.aps.lh.handler;

import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.exception.ScheduleException;
import lombok.extern.slf4j.Slf4j;

/**
 * 排程步骤处理器抽象基类
 * <p>所有S4.x步骤处理器的基类，提供通用的前置/后置处理</p>
 *
 * @author APS
 */
@Slf4j
public abstract class AbsScheduleStepHandler {

    /**
     * 执行步骤处理
     *
     * @param context 排程上下文
     */
    public void handle(LhScheduleContext context) {
        if (context.isInterrupted()) {
            log.warn("排程已中断, 跳过步骤: {}", getStepName());
            return;
        }
        log.info("[{}] 开始执行", getStepName());
        long startTime = System.currentTimeMillis();
        try {
            doHandle(context);
        } catch (ScheduleException e) {
            if (shouldPropagateException()) {
                throw e;
            }
            log.warn("[{}] 排程业务异常: {}", getStepName(), e.getMessage(), e);
            context.interruptSchedule(e.getMessage());
        } catch (Exception e) {
            if (shouldPropagateException()) {
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new RuntimeException(e);
            }
            log.error("[{}] 执行异常", getStepName(), e);
            context.interruptSchedule(getStepName() + "执行异常: " + e.getMessage());
        } finally {
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[{}] 执行完成, 耗时: {}ms", getStepName(), elapsed);
        }
    }

    /**
     * 是否允许当前处理器直接向上抛出异常。
     *
     * @return true 表示不再写入 interrupt 状态，而是让异常继续传播
     */
    protected boolean shouldPropagateException() {
        return false;
    }

    /**
     * 子类实现具体的步骤处理逻辑
     *
     * @param context 排程上下文
     */
    protected abstract void doHandle(LhScheduleContext context);

    /**
     * 获取步骤名称
     *
     * @return 步骤名称
     */
    protected abstract String getStepName();
}
