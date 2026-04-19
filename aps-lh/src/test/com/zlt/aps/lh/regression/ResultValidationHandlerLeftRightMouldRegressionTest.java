package com.zlt.aps.lh.regression;

import com.zlt.aps.lh.api.domain.dto.MachineScheduleDTO;
import com.zlt.aps.lh.api.domain.entity.LhMouldChangePlan;
import com.zlt.aps.lh.api.domain.entity.LhScheduleResult;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.handler.ResultValidationHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 结果校验处理器左右模回归测试。
 */
class ResultValidationHandlerLeftRightMouldRegressionTest {

    @Test
    void generateMouldChangePlan_shouldKeepResultLeftRightMould() {
        ResultValidationHandler handler = new ResultValidationHandler();
        LhScheduleContext context = newContext();
        context.setMachineScheduleMap(new LinkedHashMap<>());
        context.setInitialMachineScheduleMap(new LinkedHashMap<>());
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
        result.setDailyPlanQty(10);
        result.setClass1StartTime(dateTime(2026, 4, 17, 7, 0));
        result.setSpecEndTime(dateTime(2026, 4, 17, 14, 0));
        context.getScheduleResultList().add(result);

        ReflectionTestUtils.invokeMethod(handler, "generateMouldChangePlan", context);
        assertEquals(1, context.getMouldChangePlanList().size());
        LhMouldChangePlan plan = context.getMouldChangePlanList().get(0);
        assertEquals("R", plan.getLeftRightMould());
    }

    @Test
    void generateMouldChangePlan_shouldUseSnapshotMaterialForFirstPlan() {
        ResultValidationHandler handler = new ResultValidationHandler();
        LhScheduleContext context = newContext();
        context.setMachineScheduleMap(new LinkedHashMap<>());
        context.setInitialMachineScheduleMap(new LinkedHashMap<>());
        MachineScheduleDTO machine = new MachineScheduleDTO();
        machine.setMachineCode("K1501");
        machine.setCurrentMaterialCode("MAT-ONLINE");
        machine.setCurrentMaterialDesc("当前在机物料");
        context.getMachineScheduleMap().put("K1501", machine);
        context.getInitialMachineScheduleMap().put("K1501", machine);

        LhScheduleResult result = buildChangeResult("K1501", "MAT-NEW", "排程物料", dateTime(2026, 4, 17, 7, 0),
                dateTime(2026, 4, 17, 14, 0));
        context.getScheduleResultList().add(result);

        ReflectionTestUtils.invokeMethod(handler, "generateMouldChangePlan", context);
        assertEquals(1, context.getMouldChangePlanList().size());
        LhMouldChangePlan plan = context.getMouldChangePlanList().get(0);
        assertNull(plan.getBeforeMaterialCode());
        assertNull(plan.getBeforeMaterialDesc());
        assertEquals("MAT-ONLINE", plan.getAfterMaterialCode());
        assertEquals("当前在机物料", plan.getAfterMaterialDesc());
    }

    @Test
    void generateMouldChangePlan_shouldRollBeforeAfterMaterialAcrossPlans() {
        ResultValidationHandler handler = new ResultValidationHandler();
        LhScheduleContext context = newContext();
        context.setMachineScheduleMap(new LinkedHashMap<>());
        context.setInitialMachineScheduleMap(new LinkedHashMap<>());
        MachineScheduleDTO machine = new MachineScheduleDTO();
        machine.setMachineCode("K1501");
        machine.setCurrentMaterialCode("MAT-ONLINE");
        machine.setCurrentMaterialDesc("当前在机物料");
        context.getMachineScheduleMap().put("K1501", machine);
        context.getInitialMachineScheduleMap().put("K1501", machine);

        LhScheduleResult first = buildChangeResult("K1501", "MAT-A", "物料A", dateTime(2026, 4, 17, 7, 0),
                dateTime(2026, 4, 17, 10, 0));
        LhScheduleResult second = buildChangeResult("K1501", "MAT-B", "物料B", dateTime(2026, 4, 17, 11, 0),
                dateTime(2026, 4, 17, 14, 0));
        context.getScheduleResultList().add(first);
        context.getScheduleResultList().add(second);

        ReflectionTestUtils.invokeMethod(handler, "generateMouldChangePlan", context);
        assertEquals(2, context.getMouldChangePlanList().size());

        LhMouldChangePlan firstPlan = context.getMouldChangePlanList().get(0);
        assertNull(firstPlan.getBeforeMaterialCode());
        assertNull(firstPlan.getBeforeMaterialDesc());
        assertEquals("MAT-ONLINE", firstPlan.getAfterMaterialCode());
        assertEquals("当前在机物料", firstPlan.getAfterMaterialDesc());

        LhMouldChangePlan secondPlan = context.getMouldChangePlanList().get(1);
        assertEquals("MAT-ONLINE", secondPlan.getBeforeMaterialCode());
        assertEquals("当前在机物料", secondPlan.getBeforeMaterialDesc());
        assertEquals("MAT-A", secondPlan.getAfterMaterialCode());
        assertEquals("物料A", secondPlan.getAfterMaterialDesc());
    }

    private LhScheduleContext newContext() {
        LhScheduleContext context = new LhScheduleContext();
        context.setFactoryCode("116");
        context.setBatchNo("LHPC20260417003");
        context.setScheduleTargetDate(date(2026, 4, 17));
        return context;
    }

    private LhScheduleResult buildChangeResult(String machineCode, String materialCode, String materialDesc,
                                               Date startTime, Date endTime) {
        LhScheduleResult result = new LhScheduleResult();
        result.setFactoryCode("116");
        result.setBatchNo("LHPC20260417003");
        result.setLhMachineCode(machineCode);
        result.setLhMachineName("华澳");
        result.setScheduleType("02");
        result.setIsChangeMould("1");
        result.setLeftRightMould("R");
        result.setMaterialCode(materialCode);
        result.setMaterialDesc(materialDesc);
        result.setMouldCode("HM20231203902");
        result.setClass1StartTime(startTime);
        result.setSpecEndTime(endTime);
        result.setDailyPlanQty(10);
        return result;
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
