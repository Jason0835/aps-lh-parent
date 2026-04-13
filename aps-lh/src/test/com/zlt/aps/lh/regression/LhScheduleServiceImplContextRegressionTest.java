package com.zlt.aps.lh.regression;

import com.zlt.aps.lh.api.constant.LhScheduleConstant;
import com.zlt.aps.lh.api.constant.LhScheduleParamConstant;
import com.zlt.aps.lh.component.LhScheduleConfigResolver;
import com.zlt.aps.lh.component.ScheduleExecutionGuard;
import com.zlt.aps.lh.context.LhScheduleConfig;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.dto.LhScheduleRequestDTO;
import com.zlt.aps.lh.api.domain.dto.LhScheduleResponseDTO;
import com.zlt.aps.lh.engine.decorator.IScheduleExecutor;
import com.zlt.aps.lh.service.impl.LhScheduleServiceImpl;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 排程入口上下文：请求目标日与引擎 T 日推算回归。
 */
@ExtendWith(MockitoExtension.class)
class LhScheduleServiceImplContextRegressionTest {

    @Mock
    private IScheduleExecutor scheduleExecutor;

    @Mock
    private LhScheduleConfigResolver scheduleConfigResolver;

    @Mock
    private ScheduleExecutionGuard scheduleExecutionGuard;

    @InjectMocks
    private LhScheduleServiceImpl lhScheduleService;

    @Test
    void executeSchedule_passesCorrectTargetDayAndTDayToExecutor() {
        doAnswer(invocation -> {
            LhScheduleContext context = invocation.getArgument(0);
            context.setScheduleConfig(createConfig(LhScheduleConstant.SCHEDULE_DAYS));
            return null;
        }).when(scheduleConfigResolver).resolveAndAttach(any());
        when(scheduleExecutionGuard.acquire(anyString(), any())).thenReturn("LOCK-1");
        when(scheduleExecutor.execute(any())).thenReturn(LhScheduleResponseDTO.success("B1", "ok"));

        Date target = date(2026, 4, 4);
        LhScheduleRequestDTO request = new LhScheduleRequestDTO();
        request.setFactoryCode("FC01");
        request.setScheduleDate(target);

        lhScheduleService.executeSchedule(request);

        ArgumentCaptor<LhScheduleContext> ctxCap = ArgumentCaptor.forClass(LhScheduleContext.class);
        verify(scheduleExecutor).execute(ctxCap.capture());
        LhScheduleContext ctx = ctxCap.getValue();

        Date targetClear = LhScheduleTimeUtil.clearTime(target);
        int offset = Math.max(0, LhScheduleConstant.SCHEDULE_DAYS - 1);
        Date expectedT = LhScheduleTimeUtil.addDays(targetClear, -offset);

        assertEquals(targetClear, LhScheduleTimeUtil.clearTime(ctx.getScheduleTargetDate()));
        assertEquals(LhScheduleTimeUtil.clearTime(expectedT), LhScheduleTimeUtil.clearTime(ctx.getScheduleDate()));
    }

    @Test
    void executeSchedule_prefersResolvedConfigScheduleDays() {
        doAnswer(invocation -> {
            LhScheduleContext context = invocation.getArgument(0);
            context.setScheduleConfig(createConfig(5));
            return null;
        }).when(scheduleConfigResolver).resolveAndAttach(any());
        when(scheduleExecutionGuard.acquire(anyString(), any())).thenReturn("LOCK-2");
        when(scheduleExecutor.execute(any())).thenReturn(LhScheduleResponseDTO.success("B2", "ok"));

        Date target = date(2026, 4, 8);
        LhScheduleRequestDTO request = new LhScheduleRequestDTO();
        request.setFactoryCode("FC01");
        request.setScheduleDate(target);

        lhScheduleService.executeSchedule(request);

        ArgumentCaptor<LhScheduleContext> ctxCap = ArgumentCaptor.forClass(LhScheduleContext.class);
        verify(scheduleExecutor).execute(ctxCap.capture());
        LhScheduleContext ctx = ctxCap.getValue();

        Date expectedT = LhScheduleTimeUtil.addDays(LhScheduleTimeUtil.clearTime(target), -4);
        assertEquals(LhScheduleTimeUtil.clearTime(expectedT), LhScheduleTimeUtil.clearTime(ctx.getScheduleDate()));
    }

    private static LhScheduleConfig createConfig(int scheduleDays) {
        Map<String, String> resolvedParamMap = new HashMap<>(4);
        resolvedParamMap.put(LhScheduleParamConstant.SCHEDULE_DAYS, String.valueOf(scheduleDays));
        return new LhScheduleConfig(resolvedParamMap);
    }

    private static Date date(int y, int month, int day) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(Calendar.YEAR, y);
        c.set(Calendar.MONTH, month - 1);
        c.set(Calendar.DAY_OF_MONTH, day);
        return c.getTime();
    }
}
