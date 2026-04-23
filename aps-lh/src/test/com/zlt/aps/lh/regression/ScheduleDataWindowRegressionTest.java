package com.zlt.aps.lh.regression;

import com.zlt.aps.lh.api.constant.LhScheduleConstant;
import com.zlt.aps.lh.api.constant.LhScheduleParamConstant;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.entity.LhDayFinishQty;
import com.zlt.aps.lh.api.domain.entity.LhMachineInfo;
import com.zlt.aps.lh.api.domain.entity.LhMachineOnlineInfo;
import com.zlt.aps.lh.api.enums.DeleteFlagEnum;
import com.zlt.aps.lh.mapper.FactoryMonthPlanProductionFinalResultMapper;
import com.zlt.aps.lh.mapper.LhDayFinishQtyMapper;
import com.zlt.aps.lh.mapper.LhMachineInfoMapper;
import com.zlt.aps.lh.mapper.LhMouldCleanPlanMapper;
import com.zlt.aps.lh.mapper.LhScheduleResultMapper;
import com.zlt.aps.lh.mapper.LhSpecifyMachineMapper;
import com.zlt.aps.lh.mapper.MdmDevMaintenancePlanMapper;
import com.zlt.aps.lh.mapper.MdmDevicePlanShutMapper;
import com.zlt.aps.lh.mapper.MdmCapsuleChuckMapper;
import com.zlt.aps.lh.mapper.LhMachineOnlineInfoMapper;
import com.zlt.aps.lh.mapper.LhRepairCapsuleMapper;
import com.zlt.aps.lh.mapper.MdmMaterialInfoMapper;
import com.zlt.aps.lh.mapper.MdmModelInfoMapper;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
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
    private MdmModelInfoMapper mdmModelInfoMapper;
    @Mock
    private LhMachineInfoMapper lhMachineInfoMapper;
    @Mock
    private LhMouldCleanPlanMapper lhMouldCleanPlanMapper;
    @Mock
    private MdmMonthSurplusMapper monthSurplusMapper;
    @Mock
    private LhDayFinishQtyMapper lhDayFinishQtyMapper;
    @Mock
    private MdmMaterialInfoMapper mdmMaterialInfoMapper;
    @Mock
    private MdmCapsuleChuckMapper mdmCapsuleChuckMapper;
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
        verify(lhMouldCleanPlanMapper).selectList(any());
        verify(lhScheduleResultMapper).selectList(any());
    }

    @Test
    void loadAllBaseData_shouldUseNearestOnlineInfoPerMachineInLookbackWindow() {
        Date target = LhScheduleTimeUtil.clearTime(date(2026, 4, 17));
        Date scheduleDate = LhScheduleTimeUtil.addDays(target, -2);
        prepareRequiredBaseMocks();

        // 模拟查询结果已按 onlineDate/updateTime 倒序；同机台保留第一条即最近记录。
        LhMachineOnlineInfo machineARecent = new LhMachineOnlineInfo();
        machineARecent.setOnlineDate(date(2026, 4, 14));
        machineARecent.setUpdateTime(dateTime(2026, 4, 14, 8, 30, 0));
        machineARecent.setLhCode("K1501");
        machineARecent.setMaterialCode("MAT-A-NEW");

        LhMachineOnlineInfo machineAOld = new LhMachineOnlineInfo();
        machineAOld.setOnlineDate(date(2026, 4, 13));
        machineAOld.setUpdateTime(dateTime(2026, 4, 13, 9, 0, 0));
        machineAOld.setLhCode("K1501");
        machineAOld.setMaterialCode("MAT-A-OLD");

        // 同机台同日记录通过 updateTime 决定优先级（ONLINE_DATE 在数据库是 date 类型）。
        LhMachineOnlineInfo machineBRecentByUpdateTime = new LhMachineOnlineInfo();
        machineBRecentByUpdateTime.setOnlineDate(date(2026, 4, 12));
        machineBRecentByUpdateTime.setUpdateTime(dateTime(2026, 4, 12, 10, 0, 0));
        machineBRecentByUpdateTime.setLhCode("K1502");
        machineBRecentByUpdateTime.setMaterialCode("MAT-B-LATE");

        LhMachineOnlineInfo machineBOldByUpdateTime = new LhMachineOnlineInfo();
        machineBOldByUpdateTime.setOnlineDate(date(2026, 4, 12));
        machineBOldByUpdateTime.setUpdateTime(dateTime(2026, 4, 12, 9, 0, 0));
        machineBOldByUpdateTime.setLhCode("K1502");
        machineBOldByUpdateTime.setMaterialCode("MAT-B-EARLY");

        when(lhMachineOnlineInfoMapper.selectList(any())).thenReturn(
                Arrays.asList(machineARecent, machineAOld, machineBRecentByUpdateTime, machineBOldByUpdateTime));

        LhScheduleContext context = new LhScheduleContext();
        context.setFactoryCode("FC01");
        context.setScheduleTargetDate(target);
        context.setScheduleDate(scheduleDate);
        context.getLhParamsMap().put(LhScheduleParamConstant.MACHINE_ONLINE_LOOKBACK_DAYS, "3");

        lhBaseDataService.loadAllBaseData(context);

        assertEquals(2, context.getMachineOnlineInfoMap().size());
        assertEquals("MAT-A-NEW", context.getMachineOnlineInfoMap().get("K1501").getMaterialCode());
        assertEquals("MAT-B-LATE", context.getMachineOnlineInfoMap().get("K1502").getMaterialCode());
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

    @Test
    void loadAllBaseData_shouldLoadDayFinishQtyAndAggregateMonthFinishedQtyUntilTargetDate() {
        Date target = LhScheduleTimeUtil.clearTime(date(2026, 4, 17));
        Date scheduleDate = LhScheduleTimeUtil.addDays(target, -2);
        prepareRequiredBaseMocks();
        when(lhMachineOnlineInfoMapper.selectList(any())).thenReturn(Collections.emptyList());

        LhDayFinishQty previousDayFinishQty = new LhDayFinishQty();
        previousDayFinishQty.setFinishDate(dateTime(2026, 4, 16, 8, 30, 0));
        previousDayFinishQty.setMaterialCode("MAT-TODAY");
        previousDayFinishQty.setDayFinishQty(BigDecimal.valueOf(20));

        LhDayFinishQty monthFinishQtyA = new LhDayFinishQty();
        monthFinishQtyA.setFinishDate(dateTime(2026, 4, 2, 9, 0, 0));
        monthFinishQtyA.setMaterialCode("MAT-MONTH");
        monthFinishQtyA.setDayFinishQty(BigDecimal.valueOf(60));

        LhDayFinishQty monthFinishQtyB = new LhDayFinishQty();
        monthFinishQtyB.setFinishDate(dateTime(2026, 4, 17, 13, 15, 0));
        monthFinishQtyB.setMaterialCode("MAT-MONTH");
        monthFinishQtyB.setDayFinishQty(BigDecimal.valueOf(20));

        LhDayFinishQty otherMaterialMonthFinishQty = new LhDayFinishQty();
        otherMaterialMonthFinishQty.setFinishDate(dateTime(2026, 4, 15, 10, 0, 0));
        otherMaterialMonthFinishQty.setMaterialCode("MAT-OTHER");
        otherMaterialMonthFinishQty.setDayFinishQty(BigDecimal.valueOf(9));

        when(lhDayFinishQtyMapper.selectList(any())).thenReturn(
                Collections.singletonList(previousDayFinishQty),
                Arrays.asList(monthFinishQtyA, monthFinishQtyB, otherMaterialMonthFinishQty));

        LhScheduleContext context = new LhScheduleContext();
        context.setFactoryCode("FC01");
        context.setScheduleTargetDate(target);
        context.setScheduleDate(scheduleDate);

        lhBaseDataService.loadAllBaseData(context);

        assertEquals(1, context.getMaterialDayFinishedQtyMap().size());
        assertEquals(20, context.getMaterialDayFinishedQtyMap().get("MAT-TODAY_2026-04-16").intValue());
        assertEquals(80, context.getMaterialMonthFinishedQtyMap().get("MAT-MONTH").intValue());
        assertEquals(9, context.getMaterialMonthFinishedQtyMap().get("MAT-OTHER").intValue());
    }

    @Test
    void loadAllBaseData_shouldAggregateMonthFinishedQtyUsingTargetMonthWhenWindowCrossesMonth() {
        Date target = LhScheduleTimeUtil.clearTime(date(2026, 5, 2));
        Date scheduleDate = LhScheduleTimeUtil.addDays(target, -2);
        prepareRequiredBaseMocks();
        when(lhMachineOnlineInfoMapper.selectList(any())).thenReturn(Collections.emptyList());

        LhDayFinishQty previousDayFinishQty = new LhDayFinishQty();
        previousDayFinishQty.setFinishDate(dateTime(2026, 5, 1, 9, 0, 0));
        previousDayFinishQty.setMaterialCode("MAT-TODAY");
        previousDayFinishQty.setDayFinishQty(BigDecimal.valueOf(6));

        LhDayFinishQty targetMonthFinishQtyA = new LhDayFinishQty();
        targetMonthFinishQtyA.setFinishDate(dateTime(2026, 5, 1, 10, 0, 0));
        targetMonthFinishQtyA.setMaterialCode("MAT-CROSS");
        targetMonthFinishQtyA.setDayFinishQty(BigDecimal.valueOf(15));

        LhDayFinishQty targetMonthFinishQtyB = new LhDayFinishQty();
        targetMonthFinishQtyB.setFinishDate(dateTime(2026, 5, 2, 15, 0, 0));
        targetMonthFinishQtyB.setMaterialCode("MAT-CROSS");
        targetMonthFinishQtyB.setDayFinishQty(BigDecimal.valueOf(7));

        when(lhDayFinishQtyMapper.selectList(any())).thenReturn(
                Collections.singletonList(previousDayFinishQty),
                Arrays.asList(targetMonthFinishQtyA, targetMonthFinishQtyB));

        LhScheduleContext context = new LhScheduleContext();
        context.setFactoryCode("FC01");
        context.setScheduleTargetDate(target);
        context.setScheduleDate(scheduleDate);

        lhBaseDataService.loadAllBaseData(context);

        assertEquals(22, context.getMaterialMonthFinishedQtyMap().get("MAT-CROSS").intValue());
    }

    @Test
    void resolveDayFinishedQty_shouldTreatNullAsZero() {
        LhDayFinishQty finishQty = new LhDayFinishQty();
        finishQty.setDayFinishQty(BigDecimal.valueOf(11));
        Integer finishedQty = ReflectionTestUtils.invokeMethod(lhBaseDataService,
                "resolveDayFinishedQty", finishQty);
        assertEquals(11, finishedQty.intValue());

        finishQty.setDayFinishQty(null);
        Integer nullFinishedQty = ReflectionTestUtils.invokeMethod(lhBaseDataService,
                "resolveDayFinishedQty", finishQty);
        assertEquals(0, nullFinishedQty.intValue());
    }

    private void prepareRequiredBaseMocks() {
        MpFactoryProductionVersion finalVersion = new MpFactoryProductionVersion();
        finalVersion.setProductionVersion("PV_REGRESSION_01");
        when(mpFactoryProductionVersionMapper.selectList(any())).thenReturn(Collections.singletonList(finalVersion));

        when(monthPlanMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(workCalendarMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(skuLhCapacityMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(devicePlanShutMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(skuMouldRelMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(mdmModelInfoMapper.selectList(any())).thenReturn(Collections.emptyList());

        LhMachineInfo machine = new LhMachineInfo();
        machine.setMachineCode("M1");
        machine.setStatus("0");
        machine.setIsDelete(DeleteFlagEnum.NORMAL.getCode());
        when(lhMachineInfoMapper.selectList(any())).thenReturn(Collections.singletonList(machine));

        when(lhMouldCleanPlanMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(monthSurplusMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(lhDayFinishQtyMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(mdmMaterialInfoMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(mdmCapsuleChuckMapper.selectList(any())).thenReturn(Collections.emptyList());
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
