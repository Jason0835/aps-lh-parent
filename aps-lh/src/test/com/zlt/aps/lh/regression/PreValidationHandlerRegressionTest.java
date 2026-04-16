package com.zlt.aps.lh.regression;

import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.exception.ScheduleErrorCode;
import com.zlt.aps.lh.exception.ScheduleException;
import com.zlt.aps.lh.handler.PreValidationHandler;
import com.zlt.aps.lh.service.ILhScheduleResultService;
import com.zlt.aps.lh.util.MonthPlanDayQtyUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 前置校验回归：跨月排程需在 S4.1 直接拦截。
 */
@ExtendWith(MockitoExtension.class)
class PreValidationHandlerRegressionTest {

    @Mock
    private ILhScheduleResultService scheduleResultService;

    @InjectMocks
    private PreValidationHandler handler;

    @Test
    void doHandle_shouldRejectCrossMonthScheduleWindow() {
        LhScheduleContext context = new LhScheduleContext();
        context.setFactoryCode("116");
        context.setScheduleDate(date(2026, 4, 30));
        context.setScheduleTargetDate(date(2026, 5, 2));
        when(scheduleResultService.countReleasedByDate(context.getScheduleTargetDate(), "116")).thenReturn(0);

        ScheduleException exception = assertThrows(ScheduleException.class,
                () -> ReflectionTestUtils.invokeMethod(handler, "doHandle", context));

        assertEquals(ScheduleErrorCode.CROSS_MONTH_SCHEDULE_UNSUPPORTED, exception.getErrorCode());
        assertTrue(exception.getMessage().contains(MonthPlanDayQtyUtil.CROSS_MONTH_UNSUPPORTED_MESSAGE));
    }

    private static Date date(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month - 1);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        return calendar.getTime();
    }
}
