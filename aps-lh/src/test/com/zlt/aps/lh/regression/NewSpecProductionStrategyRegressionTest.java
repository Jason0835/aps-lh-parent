package com.zlt.aps.lh.regression;

import com.zlt.aps.lh.api.domain.dto.MachineScheduleDTO;
import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.component.OrderNoGenerator;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.engine.strategy.ICapacityCalculateStrategy;
import com.zlt.aps.lh.engine.strategy.IFirstInspectionBalanceStrategy;
import com.zlt.aps.lh.engine.strategy.IMachineMatchStrategy;
import com.zlt.aps.lh.engine.strategy.IMouldChangeBalanceStrategy;
import com.zlt.aps.lh.engine.strategy.impl.NewSpecProductionStrategy;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 新增排产回归：首选机台窗口内无产能时，应继续尝试后续候选机台，避免直接误判未排产。
 */
class NewSpecProductionStrategyRegressionTest {

    @Test
    void scheduleNewSpecs_shouldFallbackToNextCandidateWhenFirstMachineHasNoCapacity() throws Exception {
        NewSpecProductionStrategy strategy = new NewSpecProductionStrategy();
        injectOrderNoGenerator(strategy);

        LhScheduleContext context = buildContext();
        SkuScheduleDTO sku = buildSku();
        context.getNewSpecSkuList().add(sku);

        MachineScheduleDTO noCapacityMachine = new MachineScheduleDTO();
        noCapacityMachine.setMachineCode("M-NO-CAP");
        noCapacityMachine.setMachineName("无产能机台");
        noCapacityMachine.setEstimatedEndTime(dateTime(2026, 4, 19, 21, 30));

        MachineScheduleDTO availableMachine = new MachineScheduleDTO();
        availableMachine.setMachineCode("M-OK");
        availableMachine.setMachineName("可排机台");
        availableMachine.setEstimatedEndTime(dateTime(2026, 4, 17, 6, 0));

        IMachineMatchStrategy machineMatchStrategy = new IMachineMatchStrategy() {
            @Override
            public java.util.List<MachineScheduleDTO> matchMachines(LhScheduleContext ctx, SkuScheduleDTO scheduleSku) {
                return Arrays.asList(noCapacityMachine, availableMachine);
            }

            @Override
            public MachineScheduleDTO selectBestMachine(java.util.List<MachineScheduleDTO> candidates, SkuScheduleDTO scheduleSku) {
                return candidates.get(0);
            }
        };

        IMouldChangeBalanceStrategy mouldChangeBalanceStrategy = new IMouldChangeBalanceStrategy() {
            @Override
            public boolean hasCapacity(LhScheduleContext ctx, Date targetDate) {
                return true;
            }

            @Override
            public Date allocateMouldChange(LhScheduleContext ctx, Date endingTime) {
                return endingTime;
            }

            @Override
            public int getRemainingCapacity(LhScheduleContext ctx, Date targetDate) {
                return 99;
            }
        };

        IFirstInspectionBalanceStrategy inspectionBalanceStrategy =
                (ctx, machineCode, mouldChangeTime) -> mouldChangeTime;

        ICapacityCalculateStrategy capacityCalculateStrategy = new ICapacityCalculateStrategy() {
            @Override
            public int calculateShiftCapacity(LhScheduleContext ctx, int lhTimeSeconds, int mouldQty) {
                return 1;
            }

            @Override
            public Date calculateStartTime(LhScheduleContext ctx, String machineCode, Date endingTime) {
                return endingTime;
            }

            @Override
            public int calculateFirstShiftQty(Date startTime, Date shiftEndTime, int lhTimeSeconds, int mouldQty) {
                return startTime.before(shiftEndTime) ? 1 : 0;
            }

            @Override
            public int calculateDailyCapacity(int lhTimeSeconds, int mouldQty) {
                return 1;
            }
        };

        strategy.scheduleNewSpecs(context, machineMatchStrategy, mouldChangeBalanceStrategy,
                inspectionBalanceStrategy, capacityCalculateStrategy);

        assertEquals(1, context.getScheduleResultList().size(), "第一候选机台无产能时，应继续尝试后续候选机台");
        assertEquals(0, context.getUnscheduledResultList().size(), "存在后续可排机台时，不应生成未排产记录");
        assertEquals("M-OK", context.getScheduleResultList().get(0).getLhMachineCode());
    }

    private LhScheduleContext buildContext() {
        LhScheduleContext context = new LhScheduleContext();
        Date scheduleDate = dateTime(2026, 4, 17, 0, 0);
        context.setFactoryCode("116");
        context.setBatchNo("TEST-BATCH");
        context.setScheduleDate(scheduleDate);
        context.setScheduleTargetDate(scheduleDate);
        context.setScheduleWindowShifts(LhScheduleTimeUtil.buildDefaultScheduleShifts(context, scheduleDate));
        return context;
    }

    private SkuScheduleDTO buildSku() {
        SkuScheduleDTO sku = new SkuScheduleDTO();
        sku.setMaterialCode("MAT-1");
        sku.setMaterialDesc("测试物料");
        sku.setSpecCode("11R22.5");
        sku.setSpecDesc("11R22.5");
        sku.setProSize("22.5");
        sku.setLhTimeSeconds(3600);
        sku.setMouldQty(1);
        sku.setPendingQty(1);
        sku.setDailyPlanQty(1);
        return sku;
    }

    private void injectOrderNoGenerator(NewSpecProductionStrategy strategy) throws Exception {
        OrderNoGenerator orderNoGenerator = new OrderNoGenerator();

        Field useRedisField = OrderNoGenerator.class.getDeclaredField("useRedis");
        useRedisField.setAccessible(true);
        useRedisField.set(orderNoGenerator, false);

        Field generatorField = NewSpecProductionStrategy.class.getDeclaredField("orderNoGenerator");
        generatorField.setAccessible(true);
        generatorField.set(strategy, orderNoGenerator);
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
