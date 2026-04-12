package com.zlt.aps.lh.regression;

import com.zlt.aps.lh.api.constant.LhScheduleConstant;
import com.zlt.aps.lh.api.domain.dto.LhScheduleRequestDTO;
import com.zlt.aps.lh.api.domain.dto.LhScheduleResponseDTO;
import com.zlt.aps.lh.api.enums.ScheduleStepEnum;
import com.zlt.aps.lh.component.ScheduleExecutionGuard;
import com.zlt.aps.lh.engine.decorator.IScheduleExecutor;
import com.zlt.aps.lh.engine.rule.IScheduleRuleEngine;
import com.zlt.aps.lh.exception.ScheduleErrorCode;
import com.zlt.aps.lh.exception.ScheduleException;
import com.zlt.aps.lh.service.impl.LhScheduleServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 执行互斥：同厂同日并发时入口层应直接拒绝后续执行。
 */
@ExtendWith(MockitoExtension.class)
class ExecuteScheduleConcurrencyGuardTest {

    @Mock
    private IScheduleExecutor scheduleExecutor;

    @Mock
    private IScheduleRuleEngine scheduleRuleEngine;

    @Mock
    private ScheduleExecutionGuard scheduleExecutionGuard;

    @InjectMocks
    private LhScheduleServiceImpl lhScheduleService;

    @Test
    void executeSchedule_returnsFailureWhenGuardRejectsConcurrentRequest() {
        when(scheduleRuleEngine.getScheduleDays(anyString())).thenReturn(LhScheduleConstant.SCHEDULE_DAYS);
        when(scheduleExecutionGuard.acquire(anyString(), any()))
                .thenThrow(new ScheduleException(ScheduleStepEnum.S4_1_PRE_VALIDATION,
                        ScheduleErrorCode.SCHEDULE_IN_PROGRESS,
                        "FC01", null, "当前工厂目标日已有排程执行中"));

        LhScheduleRequestDTO request = new LhScheduleRequestDTO();
        request.setFactoryCode("FC01");
        request.setScheduleDate(date(2026, 4, 13));

        LhScheduleResponseDTO response = lhScheduleService.executeSchedule(request);

        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().contains("排程执行中"));
        verify(scheduleExecutor, never()).execute(any());
    }

    private static java.util.Date date(int y, int month, int day) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(Calendar.YEAR, y);
        c.set(Calendar.MONTH, month - 1);
        c.set(Calendar.DAY_OF_MONTH, day);
        return c.getTime();
    }
}
