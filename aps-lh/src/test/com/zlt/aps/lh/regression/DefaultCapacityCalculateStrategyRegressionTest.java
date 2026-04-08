package com.zlt.aps.lh.regression;

import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.dto.MachineScheduleDTO;
import com.zlt.aps.lh.engine.strategy.impl.DefaultCapacityCalculateStrategy;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import com.zlt.aps.mdm.api.domain.entity.MdmDevicePlanShut;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 产能系数相关：维修开产时间依赖上下文中的停机计划列表（由 loadDevicePlanShut 装入窗口内计划）。
 */
class DefaultCapacityCalculateStrategyRegressionTest {

    private final DefaultCapacityCalculateStrategy strategy = new DefaultCapacityCalculateStrategy();

    @Test
    void calculateStartTime_usesShutPlanDurationWithinScheduleWindow() {
        String machineCode = "M1";
        Date repairDay = date(2026, 4, 3);
        Date begin = LhScheduleTimeUtil.buildTime(repairDay, 8, 0, 0);
        Date end = LhScheduleTimeUtil.addHours(begin, 10);

        MdmDevicePlanShut shut = new MdmDevicePlanShut();
        shut.setMachineCode(machineCode);
        shut.setBeginDate(begin);
        shut.setEndDate(end);

        LhScheduleContext context = new LhScheduleContext();
        context.setDevicePlanShutList(Collections.singletonList(shut));

        MachineScheduleDTO dto = new MachineScheduleDTO();
        dto.setMachineCode(machineCode);
        dto.setHasRepairPlan(true);
        dto.setRepairPlanTime(repairDay);

        LinkedHashMap<String, MachineScheduleDTO> map = new LinkedHashMap<>();
        map.put(machineCode, dto);
        context.setMachineScheduleMap(map);

        Date ending = LhScheduleTimeUtil.buildTime(date(2026, 4, 2), 12, 0, 0);
        Date startTime = strategy.calculateStartTime(context, machineCode, ending);

        // 维修 10h + 换模含预热等（默认 8h）应从 repairStart 8:00 起算
        Date expectedMin = LhScheduleTimeUtil.addHours(begin, 10 + 8);
        assertTrue(!startTime.before(expectedMin), "开产时间应反映停机计划时长（10h）而非默认 8h");
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
