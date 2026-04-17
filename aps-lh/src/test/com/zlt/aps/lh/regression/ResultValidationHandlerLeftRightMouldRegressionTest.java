package com.zlt.aps.lh.regression;

import com.zlt.aps.lh.api.domain.entity.LhMouldChangePlan;
import com.zlt.aps.lh.api.domain.entity.LhScheduleResult;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.handler.ResultValidationHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 结果校验处理器左右模回归测试。
 */
class ResultValidationHandlerLeftRightMouldRegressionTest {

    @Test
    void generateMouldChangePlan_shouldKeepResultLeftRightMould() {
        ResultValidationHandler handler = new ResultValidationHandler();
        LhScheduleContext context = newContext();
        LhScheduleResult result = new LhScheduleResult();
        result.setFactoryCode("116");
        result.setBatchNo("LHPC20260417003");
        result.setLhMachineCode("K1501");
        result.setLhMachineName("华澳");
        result.setScheduleType("02");
        result.setIsChangeMould("1");
        result.setLeftRightMould("R");
        result.setMaterialCode("3302001690");
        result.setMaterialDesc("11R24.5 149/146L 16PR JD727 BL4HJY");
        result.setMouldCode("HM20231203902");
        result.setClass1StartTime(dateTime(2026, 4, 17, 7, 0));
        result.setSpecEndTime(dateTime(2026, 4, 17, 14, 0));
        context.getScheduleResultList().add(result);

        ReflectionTestUtils.invokeMethod(handler, "generateMouldChangePlan", context);
        assertEquals(1, context.getMouldChangePlanList().size());
        LhMouldChangePlan plan = context.getMouldChangePlanList().get(0);
        assertEquals("R", plan.getLeftRightMould());
    }

    private LhScheduleContext newContext() {
        LhScheduleContext context = new LhScheduleContext();
        context.setFactoryCode("116");
        context.setBatchNo("LHPC20260417003");
        context.setScheduleTargetDate(date(2026, 4, 17));
        return context;
    }

    private static Date date(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month - 1);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        return calendar.getTime();
    }

    private static Date dateTime(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month - 1);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        return calendar.getTime();
    }
}
