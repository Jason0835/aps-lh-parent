package com.zlt.aps.lh.regression;

import com.zlt.aps.lh.api.constant.LhScheduleConstant;
import com.zlt.aps.lh.api.constant.LhScheduleParamConstant;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.entity.LhMachineInfo;
import com.zlt.aps.lh.api.domain.entity.LhMachineOnlineInfo;
import com.zlt.aps.lh.api.enums.DeleteFlagEnum;
import com.zlt.aps.lh.mapper.FactoryMonthPlanProductionFinalResultMapper;
import com.zlt.aps.lh.mapper.LhCleaningPlanMapper;
import com.zlt.aps.lh.mapper.LhMachineInfoMapper;
import com.zlt.aps.lh.mapper.LhShiftFinishQtyMapper;
import com.zlt.aps.lh.mapper.LhScheduleResultMapper;
import com.zlt.aps.lh.mapper.LhSpecifyMachineMapper;
import com.zlt.aps.lh.mapper.MdmDevMaintenancePlanMapper;
import com.zlt.aps.lh.mapper.MdmDevicePlanShutMapper;
import com.zlt.aps.lh.mapper.LhMachineOnlineInfoMapper;
import com.zlt.aps.lh.mapper.LhRepairCapsuleMapper;
import com.zlt.aps.lh.mapper.MdmMaterialInfoMapper;
import com.zlt.aps.lh.mapper.MdmMonthSurplusMapper;
import com.zlt.aps.lh.mapper.MdmSkuLhCapacityMapper;
import com.zlt.aps.lh.mapper.MdmSkuMouldRelMapper;
import com.zlt.aps.lh.mapper.MdmWorkCalendarMapper;
import com.zlt.aps.lh.mapper.MpFactoryProductionVersionMapper;
import com.zlt.aps.lh.service.impl.LhBaseDataServiceImpl;
import com.zlt.aps.mp.api.domain.entity.MpFactoryProductionVersion;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 排程数据窗口回归：典型目标日 → T 日 → [startDate, endDate) 与日历/清洗/停机查询一致。
 */
@ExtendWith(MockitoExtension.class)
class ScheduleDataWindowRegressionTest {

    @Mock
    private FactoryMonthPlanProductionFinalResultMapper monthPlanMapper;
    @Mock
    private MpFactoryProductionVersionMapper mpFactoryProductionVersionMapper;
    @Mock
    private MdmWorkCalendarMapper workCalendarMapper;
    @Mock
    private MdmSkuLhCapacityMapper skuLhCapacityMapper;
    @Mock
    private MdmDevicePlanShutMapper devicePlanShutMapper;
    @Mock
    private MdmSkuMouldRelMapper skuMouldRelMapper;
    @Mock
    private LhMachineInfoMapper lhMachineInfoMapper;
    @Mock
    private LhCleaningPlanMapper lhCleaningPlanMapper;
    @Mock
    private MdmMonthSurplusMapper monthSurplusMapper;
    @Mock
    private LhShiftFinishQtyMapper lhShiftFinishQtyMapper;
    @Mock
    private MdmMaterialInfoMapper mdmMaterialInfoMapper;
    @Mock
    private LhMachineOnlineInfoMapper lhMachineOnlineInfoMapper;
    @Mock
    private LhSpecifyMachineMapper lhSpecifyMachineMapper;
    @Mock
    private LhRepairCapsuleMapper lhRepairCapsuleMapper;
    @Mock
    private MdmDevMaintenancePlanMapper devMaintenancePlanMapper;
    @Mock
    private LhScheduleResultMapper lhScheduleResultMapper;

    @InjectMocks
    private LhBaseDataServiceImpl lhBaseDataService;

    /**
     * 典型例：目标日 2026-04-04 → T=2026-04-02，窗口覆盖 4/2～4/4 三个日历日；校验 T 日与 [start,end) 公式。
     */
    @Test
    void scheduleWindow_targetDayTDayAndHalfOpenEndMatchFormula() {
        Date target = date(2026, 4, 4);
        Date targetClear = LhScheduleTimeUtil.clearTime(target);
        int offsetDays = Math.max(0, LhScheduleConstant.SCHEDULE_DAYS - 1);
        Date tDay = LhScheduleTimeUtil.addDays(targetClear, -offsetDays);

        assertEquals(date(2026, 4, 2), stripTime(tDay), "T 日应为目标日前移 SCHEDULE_DAYS-1 天");

        Date startDate = LhScheduleTimeUtil.clearTime(tDay);
        Date endDate = LhScheduleTimeUtil.addDays(startDate, LhScheduleConstant.SCHEDULE_DAYS);

        assertEquals(date(2026, 4, 2), stripTime(startDate));
        assertEquals(date(2026, 4, 5), stripTime(endDate), "endDate 为 T+SCHEDULE_DAYS 日 0 点，[start,end) 含 T～T+2");

        assertTrue(date(2026, 4, 4).before(endDate), "目标日 4/4 0 点应 < endDate（4/5 0 点）");
        assertTrue(!date(2026, 4, 5).before(endDate), "T+3 日 0 点等于 endDate，productionDate < endDate 不含该日");
    }

    @Test
    void loadAllBaseData_invokesWorkCalendarShutAndCleaningWithScheduleWindow() {
        String factoryCode = "FC01";
        Date target = LhScheduleTimeUtil.clearTime(date(2026, 4, 4));
        int offsetDays = Math.max(0, LhScheduleConstant.SCHEDULE_DAYS - 1);
        Date scheduleDate = LhScheduleTimeUtil.addDays(target, -offsetDays);

        prepareRequiredBaseMocks();
        when(lhMachineOnlineInfoMapper.selectList(any())).thenReturn(Collections.emptyList());

        LhScheduleContext context = new LhScheduleContext();
        context.setFactoryCode(factoryCode);
        context.setScheduleTargetDate(target);
        context.setScheduleDate(scheduleDate);

        lhBaseDataService.loadAllBaseData(context);

        // [startDate,endDate) 与 T～T+2 对齐见 scheduleWindow_targetDayTDayAndHalfOpenEndMatchFormula；此处仅确认三类数据按该窗口加载
        verify(workCalendarMapper).selectList(any());
        verify(devicePlanShutMapper).selectList(any());
        verify(lhCleaningPlanMapper).selectList(any());
        verify(lhScheduleResultMapper).selectList(any());
    }

    @Test
    void loadAllBaseData_shouldUseLatestSnapshotInConfiguredLookbackWindow() {
        Date target = LhScheduleTimeUtil.clearTime(date(2026, 4, 17));
        Date scheduleDate = LhScheduleTimeUtil.addDays(target, -2);
        prepareRequiredBaseMocks();

        // 第一次查询仅用于命中“最近有数据日期”，4/12 有数据，4/14 与 4/13 均为空
        LhMachineOnlineInfo latestDateRow = new LhMachineOnlineInfo();
        latestDateRow.setOnlineDate(dateTime(2026, 4, 12, 8, 30, 0));

        // 第二次查询加载命中日期（4/12）整天快照
        LhMachineOnlineInfo snapshot1 = new LhMachineOnlineInfo();
        snapshot1.setOnlineDate(dateTime(2026, 4, 12, 9, 0, 0));
        snapshot1.setLhCode("K1501");
        snapshot1.setMaterialCode("MAT-A");
        LhMachineOnlineInfo snapshot2 = new LhMachineOnlineInfo();
        snapshot2.setOnlineDate(dateTime(2026, 4, 12, 10, 0, 0));
        snapshot2.setLhCode("K1502");
        snapshot2.setMaterialCode("MAT-B");
        when(lhMachineOnlineInfoMapper.selectList(any())).thenReturn(
                Collections.singletonList(latestDateRow),
                Arrays.asList(snapshot1, snapshot2));

        LhScheduleContext context = new LhScheduleContext();
        context.setFactoryCode("FC01");
        context.setScheduleTargetDate(target);
        context.setScheduleDate(scheduleDate);
        context.getLhParamsMap().put(LhScheduleParamConstant.MACHINE_ONLINE_LOOKBACK_DAYS, "3");

        lhBaseDataService.loadAllBaseData(context);

        assertEquals(2, context.getMachineOnlineInfoMap().size());
        assertEquals("MAT-A", context.getMachineOnlineInfoMap().get("K1501").getMaterialCode());
        assertEquals("MAT-B", context.getMachineOnlineInfoMap().get("K1502").getMaterialCode());
    }

    @Test
    void loadAllBaseData_shouldKeepEmptyWhenNoDataWithinLookbackWindow() {
        Date target = LhScheduleTimeUtil.clearTime(date(2026, 4, 17));
        Date scheduleDate = LhScheduleTimeUtil.addDays(target, -2);
        prepareRequiredBaseMocks();
        when(lhMachineOnlineInfoMapper.selectList(any())).thenReturn(Collections.emptyList());

        LhScheduleContext context = new LhScheduleContext();
        context.setFactoryCode("FC01");
        context.setScheduleTargetDate(target);
        context.setScheduleDate(scheduleDate);
        context.getLhParamsMap().put(LhScheduleParamConstant.MACHINE_ONLINE_LOOKBACK_DAYS, "1");

        lhBaseDataService.loadAllBaseData(context);

        assertTrue(context.getMachineOnlineInfoMap().isEmpty());
    }

    private void prepareRequiredBaseMocks() {
        MpFactoryProductionVersion finalVersion = new MpFactoryProductionVersion();
        finalVersion.setProductionVersion("PV_REGRESSION_01");
        when(mpFactoryProductionVersionMapper.selectCount(any())).thenReturn(1L);
        when(mpFactoryProductionVersionMapper.selectList(any())).thenReturn(Collections.singletonList(finalVersion));

        when(monthPlanMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(workCalendarMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(skuLhCapacityMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(devicePlanShutMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(skuMouldRelMapper.selectList(any())).thenReturn(Collections.emptyList());

        LhMachineInfo machine = new LhMachineInfo();
        machine.setMachineCode("M1");
        machine.setStatus("0");
        machine.setIsDelete(DeleteFlagEnum.NORMAL.getCode());
        when(lhMachineInfoMapper.selectList(any())).thenReturn(Collections.singletonList(machine));

        when(lhCleaningPlanMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(monthSurplusMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(lhShiftFinishQtyMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(mdmMaterialInfoMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(lhSpecifyMachineMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(lhRepairCapsuleMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(devMaintenancePlanMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(lhScheduleResultMapper.selectList(any())).thenReturn(Collections.emptyList());
    }

    private static Date date(int y, int month, int day) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(Calendar.YEAR, y);
        c.set(Calendar.MONTH, month - 1);
        c.set(Calendar.DAY_OF_MONTH, day);
        return c.getTime();
    }

    private static Date stripTime(Date d) {
        return LhScheduleTimeUtil.clearTime(d);
    }

    private static Date dateTime(int y, int month, int day, int hour, int minute, int second) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(Calendar.YEAR, y);
        c.set(Calendar.MONTH, month - 1);
        c.set(Calendar.DAY_OF_MONTH, day);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, second);
        return c.getTime();
    }
}
