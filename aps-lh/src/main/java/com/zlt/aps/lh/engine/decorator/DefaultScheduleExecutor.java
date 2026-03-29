package com.zlt.aps.lh.engine.decorator;

import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.dto.LhScheduleResponseDTO;
import com.zlt.aps.lh.engine.template.AbsLhScheduleTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 默认排程执行器
 * <p>直接委托给模板方法执行排程</p>
 *
 * @author APS
 */
@Slf4j
@Component
public class DefaultScheduleExecutor implements IScheduleExecutor {

    @Resource
    private AbsLhScheduleTemplate scheduleTemplate;

    @Override
    public LhScheduleResponseDTO execute(LhScheduleContext context) {
        return scheduleTemplate.execute(context);
    }
}
