package com.zlt.aps.lh.engine.decorator;

import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.dto.LhScheduleResponseDTO;

/**
 * 排程执行器接口(装饰器模式核心接口)
 *
 * @author APS
 */
public interface IScheduleExecutor {

    /**
     * 执行排程
     *
     * @param context 排程上下文
     * @return 排程响应结果
     */
    LhScheduleResponseDTO execute(LhScheduleContext context);
}
