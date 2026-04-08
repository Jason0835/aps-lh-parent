package com.zlt.aps.lh.engine.decorator;

import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.dto.LhScheduleResponseDTO;

/**
 * 排程执行器装饰器抽象类
 * <p>所有装饰器的基类, 持有被装饰对象的引用</p>
 *
 * @author APS
 */
public abstract class AbsScheduleExecutorDecorator implements IScheduleExecutor {

    protected final IScheduleExecutor delegate;

    protected AbsScheduleExecutorDecorator(IScheduleExecutor delegate) {
        this.delegate = delegate;
    }

    @Override
    public LhScheduleResponseDTO execute(LhScheduleContext context) {
        return delegate.execute(context);
    }
}
