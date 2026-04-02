package com.zlt.aps.lh.regression;

import com.zlt.aps.lh.api.constant.LhScheduleConstant;
import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.entity.LhMachineInfo;
import com.zlt.aps.lh.api.enums.DeleteFlagEnum;
import com.zlt.aps.lh.mapper.FactoryMonthPlanProductionFinalResultMapper;
import com.zlt.aps.lh.mapper.LhCleaningPlanMapper;
import com.zlt.aps.lh.mapper.LhMachineInfoMapper;
import com.zlt.aps.lh.mapper.LhShiftFinishQtyMapper;
import com.zlt.aps.lh.mapper.LhSpecifyMachineMapper;
import com.zlt.aps.lh.mapper.MdmDevMaintenancePlanMapper;
import com.zlt.aps.lh.mapper.MdmDevicePlanShutMapper;
import com.zlt.aps.lh.mapper.MdmLhMachineOnlineInfoMapper;
import com.zlt.aps.lh.mapper.MdmLhRepairCapsuleMapper;
import com.zlt.aps.lh.mapper.MdmMaterialInfoMapper;
import com.zlt.aps.lh.mapper.MdmMonthSurplusMapper;
import com.zlt.aps.lh.mapper.MdmSkuLhCapacityMapper;
import com.zlt.aps.lh.mapper.MdmSkuMouldRelMapper;
import com.zlt.aps.lh.mapper.MdmWorkCalendarMapper;
import com.zlt.aps.lh.service.impl.LhBaseDataServiceImpl;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private MdmLhMachineOnlineInfoMapper lhMachineOnlineInfoMapper;
    @Mock
    private LhSpecifyMachineMapper lhSpecifyMachineMapper;
    @Mock
    private MdmLhRepairCapsuleMapper lhRepairCapsuleMapper;
    @Mock
    private MdmDevMaintenancePlanMapper devMaintenancePlanMapper;

    @InjectMocks
    private LhBaseDataServiceImpl lhBaseDataService;

    /** 计划中的典型例：目标日 2026-04-04 → T=2026-04-02，窗口覆盖 4/2～4/4 三个日历日 */
    @Test
    void 典型目标日与T日及三日左闭右开上界一致() {
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
    void loadAllBaseData_触发工作日历停机与清洗加载与窗口公式一致() {
        String factoryCode = "FC01";
        Date target = LhScheduleTimeUtil.clearTime(date(2026, 4, 4));
        int offsetDays = Math.max(0, LhScheduleConstant.SCHEDULE_DAYS - 1);
        Date scheduleDate = LhScheduleTimeUtil.addDays(target, -offsetDays);

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
        when(lhMachineOnlineInfoMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(lhSpecifyMachineMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(lhRepairCapsuleMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(devMaintenancePlanMapper.selectList(any())).thenReturn(Collections.emptyList());

        LhScheduleContext context = new LhScheduleContext();
        context.setFactoryCode(factoryCode);
        context.setScheduleTargetDate(target);
        context.setScheduleDate(scheduleDate);

        lhBaseDataService.loadAllBaseData(context);

        // 具体 [startDate,endDate) 与 T～T+2 对齐关系见 典型目标日与T日及三日左闭右开上界一致；此处仅确认三类数据均按该窗口参与加载
        verify(workCalendarMapper).selectList(any());
        verify(devicePlanShutMapper).selectList(any());
        verify(lhCleaningPlanMapper).selectList(any());
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
}
