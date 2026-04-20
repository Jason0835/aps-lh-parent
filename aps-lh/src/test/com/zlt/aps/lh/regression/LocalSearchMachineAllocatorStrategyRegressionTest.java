package com.zlt.aps.lh.regression;

import com.zlt.aps.lh.api.constant.LhScheduleParamConstant;
import com.zlt.aps.lh.api.domain.dto.MachineScheduleDTO;
import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.api.domain.vo.LhShiftConfigVO;
import com.zlt.aps.lh.context.LhScheduleConfig;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.engine.strategy.ICapacityCalculateStrategy;
import com.zlt.aps.lh.engine.strategy.IFirstInspectionBalanceStrategy;
import com.zlt.aps.lh.engine.strategy.IMachineMatchStrategy;
import com.zlt.aps.lh.engine.strategy.IMouldChangeBalanceStrategy;
import com.zlt.aps.lh.engine.strategy.impl.LocalSearchMachineAllocatorStrategy;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 局部搜索选机回归：开产时间应与首检分配时间一致，不再额外叠加首检时长。
 */
class LocalSearchMachineAllocatorStrategyRegressionTest {

    @Test
    void selectBestMachine_shouldUseInspectionTimeAsProductionStart() {
        LocalSearchMachineAllocatorStrategy strategy = new LocalSearchMachineAllocatorStrategy();
        LhScheduleContext context = newContext();

        MachineScheduleDTO machine = new MachineScheduleDTO();
        machine.setMachineCode("M1");
        machine.setMachineName("M1");
        machine.setMaxMoldNum(1);
        machine.setEstimatedEndTime(dateTime(2026, 4, 11, 12, 0));

        SkuScheduleDTO sku = new SkuScheduleDTO();
        sku.setMaterialCode("MAT-LOCAL");
        sku.setPendingQty(1);
        sku.setWindowPlanQty(1);
        sku.setShiftCapacity(0);
        sku.setLhTimeSeconds(1800);

        List<SkuScheduleDTO> windowSkuList = Collections.singletonList(sku);
        List<MachineScheduleDTO> candidates = Collections.singletonList(machine);
        List<LhShiftConfigVO> shifts = context.getScheduleWindowShifts();

        IMachineMatchStrategy machineMatchStrategy = new IMachineMatchStrategy() {
            @Override
            public List<MachineScheduleDTO> matchMachines(LhScheduleContext ctx, SkuScheduleDTO scheduleSku) {
                return candidates;
            }

            @Override
            public MachineScheduleDTO selectBestMachine(LhScheduleContext ctx,
                                                        SkuScheduleDTO scheduleSku,
                                                        List<MachineScheduleDTO> candidateMachines,
                                                        Set<String> excludedMachineCodes) {
                return machine;
            }
        };

        IMouldChangeBalanceStrategy mouldChangeBalanceStrategy = new IMouldChangeBalanceStrategy() {
            @Override
            public boolean hasCapacity(LhScheduleContext ctx, Date targetDate) {
                return true;
            }

            @Override
            public Date allocateMouldChange(LhScheduleContext ctx, Date endingTime) {
                return dateTime(2026, 4, 11, 15, 0);
            }

            @Override
            public int getRemainingCapacity(LhScheduleContext ctx, Date targetDate) {
                return 99;
            }
        };

        IFirstInspectionBalanceStrategy inspectionBalanceStrategy =
                (ctx, machineCode, mouldChangeTime) -> dateTime(2026, 4, 11, 21, 30);

        ICapacityCalculateStrategy capacityCalculateStrategy = new ICapacityCalculateStrategy() {
            @Override
            public int calculateShiftCapacity(LhScheduleContext ctx, int lhTimeSeconds, int mouldQty) {
                return 0;
            }

            @Override
            public Date calculateStartTime(LhScheduleContext ctx, String machineCode, Date endingTime) {
                return endingTime;
            }

            @Override
            public int calculateFirstShiftQty(Date startTime, Date shiftEndTime, int lhTimeSeconds, int mouldQty) {
                return 0;
            }

            @Override
            public int calculateDailyCapacity(int lhTimeSeconds, int mouldQty) {
                return 0;
            }
        };

        MachineScheduleDTO selected = strategy.selectBestMachine(
                context,
                windowSkuList,
                candidates,
                shifts,
                machineMatchStrategy,
                mouldChangeBalanceStrategy,
                inspectionBalanceStrategy,
                capacityCalculateStrategy);

        assertNotNull(selected, "局部搜索应在首检时间可用时给出候选机台");
        assertEquals("M1", selected.getMachineCode());
    }

    @Test
    void selectBestMachine_shouldUseWindowStartWhenEstimatedEndTimeMissing() {
        LocalSearchMachineAllocatorStrategy strategy = new LocalSearchMachineAllocatorStrategy();
        LhScheduleContext context = newContext();

        MachineScheduleDTO machine = new MachineScheduleDTO();
        machine.setMachineCode("M1");
        machine.setMachineName("M1");
        machine.setMaxMoldNum(1);

        SkuScheduleDTO sku = new SkuScheduleDTO();
        sku.setMaterialCode("MAT-LOCAL");
        sku.setPendingQty(1);
        sku.setWindowPlanQty(1);
        sku.setShiftCapacity(8);
        sku.setLhTimeSeconds(1800);

        List<SkuScheduleDTO> windowSkuList = Collections.singletonList(sku);
        List<MachineScheduleDTO> candidates = Collections.singletonList(machine);
        List<LhShiftConfigVO> shifts = context.getScheduleWindowShifts();
        Date expectedBaseTime = shifts.get(0).getShiftStartDateTime();
        final Date[] capturedEndingTime = new Date[1];

        IMachineMatchStrategy machineMatchStrategy = new IMachineMatchStrategy() {
            @Override
            public List<MachineScheduleDTO> matchMachines(LhScheduleContext ctx, SkuScheduleDTO scheduleSku) {
                return candidates;
            }

            @Override
            public MachineScheduleDTO selectBestMachine(LhScheduleContext ctx,
                                                        SkuScheduleDTO scheduleSku,
                                                        List<MachineScheduleDTO> candidateMachines,
                                                        Set<String> excludedMachineCodes) {
                return machine;
            }
        };

        IMouldChangeBalanceStrategy mouldChangeBalanceStrategy = new IMouldChangeBalanceStrategy() {
            @Override
            public boolean hasCapacity(LhScheduleContext ctx, Date targetDate) {
                return true;
            }

            @Override
            public Date allocateMouldChange(LhScheduleContext ctx, Date endingTime) {
                return dateTime(2026, 4, 11, 15, 0);
            }

            @Override
            public int getRemainingCapacity(LhScheduleContext ctx, Date targetDate) {
                return 99;
            }
        };

        IFirstInspectionBalanceStrategy inspectionBalanceStrategy =
                (ctx, machineCode, mouldChangeTime) -> dateTime(2026, 4, 11, 21, 30);

        ICapacityCalculateStrategy capacityCalculateStrategy = new ICapacityCalculateStrategy() {
            @Override
            public int calculateShiftCapacity(LhScheduleContext ctx, int lhTimeSeconds, int mouldQty) {
                return 0;
            }

            @Override
            public Date calculateStartTime(LhScheduleContext ctx, String machineCode, Date endingTime) {
                capturedEndingTime[0] = endingTime;
                return endingTime;
            }

            @Override
            public int calculateFirstShiftQty(Date startTime, Date shiftEndTime, int lhTimeSeconds, int mouldQty) {
                return 0;
            }

            @Override
            public int calculateDailyCapacity(int lhTimeSeconds, int mouldQty) {
                return 0;
            }
        };

        strategy.selectBestMachine(
                context,
                windowSkuList,
                candidates,
                shifts,
                machineMatchStrategy,
                mouldChangeBalanceStrategy,
                inspectionBalanceStrategy,
                capacityCalculateStrategy);

        assertNotNull(capturedEndingTime[0], "局部搜索应执行机台开工时间计算");
        assertEquals(expectedBaseTime, capturedEndingTime[0],
                "局部搜索兜底时间应与主流程一致，使用排程窗口基准时间");
    }

    private LhScheduleContext newContext() {
        LhScheduleContext context = new LhScheduleContext();
        Date scheduleDate = date(2026, 4, 11);
        context.setScheduleDate(scheduleDate);

        Map<String, String> paramMap = new HashMap<>(4);
        paramMap.put(LhScheduleParamConstant.LOCAL_SEARCH_TIME_BUDGET_MS, "5000");
        context.setScheduleConfig(new LhScheduleConfig(paramMap));

        List<LhShiftConfigVO> allShifts = LhScheduleTimeUtil.buildDefaultScheduleShifts(context, scheduleDate);
        context.setScheduleWindowShifts(new ArrayList<>(allShifts.subList(0, 2)));
        return context;
    }

    private static Date date(int year, int month, int day) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(Calendar.YEAR, year);
        c.set(Calendar.MONTH, month - 1);
        c.set(Calendar.DAY_OF_MONTH, day);
        return c.getTime();
    }

    private static Date dateTime(int year, int month, int day, int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(Calendar.YEAR, year);
        c.set(Calendar.MONTH, month - 1);
        c.set(Calendar.DAY_OF_MONTH, day);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        return c.getTime();
    }
}
