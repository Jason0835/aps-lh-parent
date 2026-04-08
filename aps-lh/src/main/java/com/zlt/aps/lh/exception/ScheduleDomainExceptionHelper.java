package com.zlt.aps.lh.exception;

import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.api.enums.ScheduleStepEnum;

/**
 * 领域异常与排程中断的桥接：将 {@link ScheduleException} 的标准消息写入上下文中断原因。
 *
 * @author APS
 */
public final class ScheduleDomainExceptionHelper {

    private ScheduleDomainExceptionHelper() {
    }

    /**
     * 使用领域错误码中断排程（消息格式与 {@link ScheduleException#getMessage()} 一致）。
     *
     * @param context       排程上下文
     * @param step          当前步骤
     * @param errorCode     错误码
     * @param detailMessage 业务详情（不含步骤与错误码前缀）
     */
    public static void interrupt(LhScheduleContext context, ScheduleStepEnum step,
                                 ScheduleErrorCode errorCode, String detailMessage) {
        String msg = new ScheduleException(step, errorCode,
                context.getFactoryCode(), context.getBatchNo(), detailMessage).getMessage();
        context.interruptSchedule(msg);
    }
}
