package com.zlt.aps.lh.regression;

import com.zlt.aps.lh.api.constant.LhScheduleParamConstant;
import com.zlt.aps.lh.api.domain.dto.MachineCleaningWindowDTO;
import com.zlt.aps.lh.api.domain.dto.MachineScheduleDTO;
import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.api.domain.entity.LhScheduleProcessLog;
import com.zlt.aps.lh.api.domain.entity.LhScheduleResult;
import com.zlt.aps.lh.component.OrderNoGenerator;
import com.zlt.aps.lh.component.TargetScheduleQtyResolver;
import com.zlt.aps.lh.context.LhScheduleConfig;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.engine.strategy.ICapacityCalculateStrategy;
import com.zlt.aps.lh.engine.strategy.IEndingJudgmentStrategy;
import com.zlt.aps.lh.engine.strategy.IFirstInspectionBalanceStrategy;
import com.zlt.aps.lh.engine.strategy.IMachineMatchStrategy;
import com.zlt.aps.lh.engine.strategy.IMouldChangeBalanceStrategy;
import com.zlt.aps.lh.engine.strategy.impl.DefaultMachineMatchStrategy;
import com.zlt.aps.lh.engine.strategy.impl.LocalSearchMachineAllocatorStrategy;
import com.zlt.aps.lh.engine.strategy.impl.NewSpecProductionStrategy;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import com.zlt.aps.mdm.api.domain.entity.MdmMaterialInfo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 新增排产回归：首选机台窗口内无产能时，应继续尝试后续候选机台，避免直接误判未排产。
 */
class NewSpecProductionStrategyRegressionTest {

    @Test
    void scheduleNewSpecs_shouldFallbackToNextCandidateWhenFirstMachineHasNoCapacity() throws Exception {
        NewSpecProductionStrategy strategy = new NewSpecProductionStrategy();
        injectDependencies(strategy, false);

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
            public MachineScheduleDTO selectBestMachine(LhScheduleContext ctx, SkuScheduleDTO scheduleSku,
                                                        java.util.List<MachineScheduleDTO> candidates,
                                                        Set<String> excludedMachineCodes) {
                if (candidates == null || candidates.isEmpty()) {
                    return null;
                }
                for (MachineScheduleDTO candidate : candidates) {
                    if (candidate == null || excludedMachineCodes.contains(candidate.getMachineCode())) {
                        continue;
                    }
                    return candidate;
                }
                return null;
            }
        };

        IMouldChangeBalanceStrategy mouldChangeBalanceStrategy = new IMouldChangeBalanceStrategy() {
            @Override
            public boolean hasCapacity(LhScheduleContext ctx, Date targetDate) {
                return true;
            }

            @Override
            public Date allocateMouldChange(LhScheduleContext ctx, String machineCode, Date endingTime) {
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
        assertEquals("0", context.getScheduleResultList().get(0).getIsEnd(), "非收尾SKU应写入is_end=0");
    }

    @Test
    void scheduleNewSpecs_shouldSetIsEndOneWhenEndingJudgmentTrue() throws Exception {
        NewSpecProductionStrategy strategy = new NewSpecProductionStrategy();
        injectDependencies(strategy, true);

        LhScheduleContext context = buildContext();
        SkuScheduleDTO sku = buildSku();
        context.getNewSpecSkuList().add(sku);

        MachineScheduleDTO machine = new MachineScheduleDTO();
        machine.setMachineCode("M-END");
        machine.setMachineName("收尾机台");
        machine.setEstimatedEndTime(dateTime(2026, 4, 17, 6, 0));

        strategy.scheduleNewSpecs(context, singletonMachineMatch(machine), defaultMouldChangeBalance(),
                defaultInspectionBalance(), defaultCapacityCalculate());

        assertEquals(1, context.getScheduleResultList().size(), "应生成新增排产结果");
        assertEquals("1", context.getScheduleResultList().get(0).getIsEnd(), "收尾SKU应写入is_end=1");
    }

    @Test
    void scheduleNewSpecs_shouldSetIsEndZeroWhenEndingJudgmentFalse() throws Exception {
        NewSpecProductionStrategy strategy = new NewSpecProductionStrategy();
        injectDependencies(strategy, false);

        LhScheduleContext context = buildContext();
        SkuScheduleDTO sku = buildSku();
        context.getNewSpecSkuList().add(sku);

        MachineScheduleDTO machine = new MachineScheduleDTO();
        machine.setMachineCode("M-NORMAL");
        machine.setMachineName("常规机台");
        machine.setEstimatedEndTime(dateTime(2026, 4, 17, 6, 0));

        strategy.scheduleNewSpecs(context, singletonMachineMatch(machine), defaultMouldChangeBalance(),
                defaultInspectionBalance(), defaultCapacityCalculate());

        assertEquals(1, context.getScheduleResultList().size(), "应生成新增排产结果");
        assertEquals("0", context.getScheduleResultList().get(0).getIsEnd(), "非收尾SKU应写入is_end=0");
    }

    @Test
    void scheduleNewSpecs_shouldAllowContinuousCandidateFallbackIntoNewSpec() throws Exception {
        NewSpecProductionStrategy strategy = new NewSpecProductionStrategy();
        injectDependencies(strategy, false);

        LhScheduleContext context = buildContext();
        MachineScheduleDTO machine = new MachineScheduleDTO();
        machine.setMachineCode("M-CONT");
        machine.setMachineName("续作机台");
        machine.setCurrentMaterialCode("MAT-BASE");
        machine.setPreviousSpecCode("SPEC-A");
        context.getMachineScheduleMap().put(machine.getMachineCode(), machine);

        SkuScheduleDTO sku = buildSku();
        sku.setEmbryoCode("EMB-1");
        sku.setStructureName("STRUCT-A");
        sku.setSpecCode("SPEC-A");
        sku.setMainPattern("PAT-A");
        sku.setPattern("PAT-A");
        context.getNewSpecSkuList().add(sku);

        strategy.scheduleNewSpecs(context, singletonMachineMatch(machine), defaultMouldChangeBalance(),
                defaultInspectionBalance(), defaultCapacityCalculate());

        assertEquals(1, context.getScheduleResultList().size(), "续作阶段未命中的候选SKU应继续参与新增排产");
        assertEquals(0, context.getUnscheduledResultList().size(), "进入新增排产后命中机台时不应生成未排记录");
        assertEquals("02", context.getScheduleResultList().get(0).getScheduleType());
        assertEquals("1", context.getScheduleResultList().get(0).getIsChangeMould());
        assertEquals("0", context.getScheduleResultList().get(0).getIsTypeBlock());
    }

    @Test
    void scheduleNewSpecs_shouldRefineTargetQtyByActualWindowCapacity() throws Exception {
        NewSpecProductionStrategy strategy = new NewSpecProductionStrategy();
        injectDependencies(strategy, false);

        LhScheduleContext context = buildContext();
        SkuScheduleDTO sku = buildSku();
        sku.setPendingQty(8);
        sku.setTargetScheduleQty(128);
        sku.setShiftCapacity(16);
        context.getNewSpecSkuList().add(sku);

        MachineScheduleDTO machine = new MachineScheduleDTO();
        machine.setMachineCode("M-REFINE");
        machine.setMachineName("收敛机台");
        machine.setMaxMoldNum(1);
        machine.setEstimatedEndTime(dateTime(2026, 4, 17, 6, 0));

        IMouldChangeBalanceStrategy mouldChangeBalanceStrategy = new IMouldChangeBalanceStrategy() {
            @Override
            public boolean hasCapacity(LhScheduleContext ctx, Date targetDate) {
                return true;
            }

            @Override
            public Date allocateMouldChange(LhScheduleContext ctx, String machineCode, Date endingTime) {
                return dateTime(2026, 4, 17, 6, 0);
            }

            @Override
            public int getRemainingCapacity(LhScheduleContext ctx, Date targetDate) {
                return 99;
            }
        };

        strategy.scheduleNewSpecs(context, singletonMachineMatch(machine), mouldChangeBalanceStrategy,
                defaultInspectionBalance(), defaultCapacityCalculate());

        assertEquals(1, context.getScheduleResultList().size(), "应生成新增排产结果");
        assertEquals(112, context.getScheduleResultList().get(0).getDailyPlanQty().intValue(),
                "新增规格应按实际开产后的窗口剩余产能收敛目标量");
        assertEquals(112, sku.getTargetScheduleQty().intValue(),
                "收敛后的目标量应回写到SKU，供后续未排与收尾口径复用");
    }

    @Test
    void scheduleNewSpecs_shouldRestoreTargetQtyAfterFailedCandidateBuild() throws Exception {
        NewSpecProductionStrategy strategy = new NewSpecProductionStrategy();
        injectDependencies(strategy, false);

        LhScheduleContext context = buildContext();
        context.setScheduleWindowShifts(LhScheduleTimeUtil.buildDefaultScheduleShifts(context, context.getScheduleDate()));
        SkuScheduleDTO sku = buildSku();
        sku.setPendingQty(8);
        sku.setTargetScheduleQty(128);
        sku.setShiftCapacity(16);
        context.getNewSpecSkuList().add(sku);

        List<com.zlt.aps.lh.api.domain.vo.LhShiftConfigVO> shifts = context.getScheduleWindowShifts();
        Date firstShiftStart = shifts.get(0).getShiftStartDateTime();
        Date secondLastShiftStart = shifts.get(shifts.size() - 2).getShiftStartDateTime();
        Date lastShiftStart = shifts.get(shifts.size() - 1).getShiftStartDateTime();
        Date lastShiftEnd = shifts.get(shifts.size() - 1).getShiftEndDateTime();

        MachineScheduleDTO failedMachineCandidate = new MachineScheduleDTO();
        failedMachineCandidate.setMachineCode("M-ROLLBACK-FAIL");
        failedMachineCandidate.setMachineName("回滚失败机台");
        failedMachineCandidate.setMaxMoldNum(1);
        failedMachineCandidate.setEstimatedEndTime(secondLastShiftStart);

        MachineScheduleDTO successMachineCandidate = new MachineScheduleDTO();
        successMachineCandidate.setMachineCode("M-ROLLBACK-OK");
        successMachineCandidate.setMachineName("回滚成功机台");
        successMachineCandidate.setMaxMoldNum(1);
        successMachineCandidate.setEstimatedEndTime(firstShiftStart);

        MachineScheduleDTO failedMachineInContext = new MachineScheduleDTO();
        failedMachineInContext.setMachineCode("M-ROLLBACK-FAIL");
        failedMachineInContext.setMachineName("回滚失败机台");
        failedMachineInContext.setMaxMoldNum(1);
        MachineCleaningWindowDTO fullBlockWindow = new MachineCleaningWindowDTO();
        fullBlockWindow.setCleanType("01");
        fullBlockWindow.setCleanStartTime(lastShiftStart);
        fullBlockWindow.setCleanEndTime(lastShiftEnd);
        fullBlockWindow.setReadyTime(lastShiftEnd);
        failedMachineInContext.setCleaningWindowList(Arrays.asList(fullBlockWindow));
        context.getMachineScheduleMap().put(failedMachineInContext.getMachineCode(), failedMachineInContext);

        MachineScheduleDTO successMachineInContext = new MachineScheduleDTO();
        successMachineInContext.setMachineCode("M-ROLLBACK-OK");
        successMachineInContext.setMachineName("回滚成功机台");
        successMachineInContext.setMaxMoldNum(1);
        context.getMachineScheduleMap().put(successMachineInContext.getMachineCode(), successMachineInContext);

        IMachineMatchStrategy machineMatchStrategy = new IMachineMatchStrategy() {
            @Override
            public List<MachineScheduleDTO> matchMachines(LhScheduleContext ctx, SkuScheduleDTO scheduleSku) {
                return Arrays.asList(failedMachineCandidate, successMachineCandidate);
            }

            @Override
            public MachineScheduleDTO selectBestMachine(LhScheduleContext ctx, SkuScheduleDTO scheduleSku,
                                                        List<MachineScheduleDTO> candidates,
                                                        Set<String> excludedMachineCodes) {
                if (candidates == null || candidates.isEmpty()) {
                    return null;
                }
                for (MachineScheduleDTO candidate : candidates) {
                    if (candidate == null || excludedMachineCodes.contains(candidate.getMachineCode())) {
                        continue;
                    }
                    return candidate;
                }
                return null;
            }
        };

        strategy.scheduleNewSpecs(context, machineMatchStrategy, defaultMouldChangeBalance(),
                defaultInspectionBalance(), defaultCapacityCalculate());

        assertEquals(1, context.getScheduleResultList().size(), "候选机台失败回退后应继续尝试后续机台");
        assertEquals("M-ROLLBACK-OK", context.getScheduleResultList().get(0).getLhMachineCode());
        assertEquals(112, context.getScheduleResultList().get(0).getDailyPlanQty().intValue(),
                "失败候选机台的收敛目标量不应泄漏到后续成功机台");
        assertEquals(112, sku.getTargetScheduleQty().intValue(),
                "最终成功机台应按自身能力重新收敛目标量");
    }

    @Test
    void adjustEmbryoStock_shouldRemoveZeroPlanResultAndRestoreMachineState() throws Exception {
        NewSpecProductionStrategy strategy = new NewSpecProductionStrategy();
        injectDependencies(strategy, false);

        LhScheduleContext context = buildContext();
        MachineScheduleDTO machine = new MachineScheduleDTO();
        machine.setMachineCode("M-ZERO");
        machine.setMachineName("裁剪机台");
        machine.setCurrentMaterialCode("MAT-BASE");
        machine.setCurrentMaterialDesc("基础物料");
        machine.setPreviousSpecCode("BASE-SPEC");
        machine.setPreviousProSize("22.5");
        machine.setEstimatedEndTime(dateTime(2026, 4, 17, 6, 0));
        context.getMachineScheduleMap().put(machine.getMachineCode(), machine);

        SkuScheduleDTO sku = buildSku();
        sku.setMaterialCode("MAT-ZERO");
        sku.setEmbryoCode("EMB-1");
        sku.setStructureName("STRUCT-ZERO");
        sku.setEmbryoStock(0);
        context.getNewSpecSkuList().add(sku);
        context.getStructureSkuMap().put("STRUCT-ZERO", Arrays.asList(sku));

        strategy.scheduleNewSpecs(context, singletonMachineMatch(machine), defaultMouldChangeBalance(),
                defaultInspectionBalance(), defaultCapacityCalculate());

        assertEquals(1, context.getScheduleResultList().size(), "库存裁剪前应先生成新增排产结果");

        strategy.adjustEmbryoStock(context);

        assertEquals(0, context.getScheduleResultList().size(), "裁剪为0的新增结果应从排程结果列表移除");
        assertEquals(1, context.getUnscheduledResultList().size(), "裁剪为0的新增结果应转入未排");
        assertEquals(1, context.getUnscheduledResultList().get(0).getUnscheduledQty());
        assertEquals("新增结果裁剪为0", context.getUnscheduledResultList().get(0).getUnscheduledReason());
        assertEquals("MAT-BASE", machine.getCurrentMaterialCode(), "移除零计划结果后应回滚机台当前物料");
        assertEquals(dateTime(2026, 4, 17, 6, 0), machine.getEstimatedEndTime(), "机台完工时刻应回滚到初始状态");
    }

    @Test
    void adjustEmbryoStock_shouldResetIsEndWhenFinalPlanQtyLessThanSurplus() throws Exception {
        NewSpecProductionStrategy strategy = new NewSpecProductionStrategy();
        injectDependencies(strategy, true);

        LhScheduleContext context = buildContext();
        LhScheduleResult result = new LhScheduleResult();
        result.setScheduleType("02");
        result.setMaterialCode("MAT-ENDING-CHECK");
        result.setDailyPlanQty(88);
        result.setMouldSurplusQty(140);
        result.setIsEnd("1");
        context.getScheduleResultList().add(result);

        strategy.adjustEmbryoStock(context);

        assertEquals("0", context.getScheduleResultList().get(0).getIsEnd(),
                "新增结果最终计划量小于硫化余量时，应回写为正常");
    }

    @Test
    void adjustEmbryoStock_shouldKeepIsEndWhenFinalPlanQtyReachSurplus() throws Exception {
        NewSpecProductionStrategy strategy = new NewSpecProductionStrategy();
        injectDependencies(strategy, false);

        LhScheduleContext context = buildContext();
        LhScheduleResult result = new LhScheduleResult();
        result.setScheduleType("02");
        result.setMaterialCode("MAT-ENDING-CHECK");
        result.setDailyPlanQty(140);
        result.setMouldSurplusQty(140);
        result.setIsEnd("0");
        context.getScheduleResultList().add(result);

        strategy.adjustEmbryoStock(context);

        assertEquals("1", context.getScheduleResultList().get(0).getIsEnd(),
                "新增结果最终计划量达到硫化余量时，应回写为收尾");
    }

    @Test
    void scheduleNewSpecs_shouldUseUpdatedMachinePriorityRules() throws Exception {
        NewSpecProductionStrategy strategy = new NewSpecProductionStrategy();
        injectDependencies(strategy, false);

        LhScheduleContext context = buildContext();
        SkuScheduleDTO sku = buildSku();
        context.getNewSpecSkuList().add(sku);
        context.setEmbryoDescMaterialCountMap(new java.util.HashMap<String, Integer>() {{
            put("胎胚-早机", 1);
            put("胎胚-晚机", 9);
        }});

        MachineScheduleDTO earlierMachine = new MachineScheduleDTO();
        earlierMachine.setMachineCode("M-EARLY");
        earlierMachine.setMachineName("更早收尾机台");
        earlierMachine.setStatus("1");
        earlierMachine.setMaxMoldNum(1);
        earlierMachine.setEstimatedEndTime(dateTime(2026, 4, 17, 6, 0));
        earlierMachine.setPreviousSpecCode("SPEC-X");
        earlierMachine.setPreviousProSize("22.5");
        earlierMachine.setPreviousMaterialCode("MAT-EARLY");

        MachineScheduleDTO matchedSpecLateMachine = new MachineScheduleDTO();
        matchedSpecLateMachine.setMachineCode("M-LATE");
        matchedSpecLateMachine.setMachineName("更晚但同规格机台");
        matchedSpecLateMachine.setStatus("1");
        matchedSpecLateMachine.setMaxMoldNum(1);
        matchedSpecLateMachine.setEstimatedEndTime(dateTime(2026, 4, 17, 6, 30));
        matchedSpecLateMachine.setPreviousSpecCode("11R22.5");
        matchedSpecLateMachine.setPreviousProSize("22.5");
        matchedSpecLateMachine.setPreviousMaterialCode("MAT-LATE");

        context.getMachineScheduleMap().put(earlierMachine.getMachineCode(), earlierMachine);
        context.getMachineScheduleMap().put(matchedSpecLateMachine.getMachineCode(), matchedSpecLateMachine);

        MdmMaterialInfo earlyMaterial = new MdmMaterialInfo();
        earlyMaterial.setMaterialCode("MAT-EARLY");
        earlyMaterial.setEmbryoDesc("胎胚-早机");
        context.getMaterialInfoMap().put(earlyMaterial.getMaterialCode(), earlyMaterial);

        MdmMaterialInfo lateMaterial = new MdmMaterialInfo();
        lateMaterial.setMaterialCode("MAT-LATE");
        lateMaterial.setEmbryoDesc("胎胚-晚机");
        context.getMaterialInfoMap().put(lateMaterial.getMaterialCode(), lateMaterial);

        strategy.scheduleNewSpecs(context, new DefaultMachineMatchStrategy(), defaultMouldChangeBalance(),
                defaultInspectionBalance(), defaultCapacityCalculate());

        assertEquals(1, context.getScheduleResultList().size(), "应生成新增排产结果");
        assertEquals("M-EARLY", context.getScheduleResultList().get(0).getLhMachineCode(),
                "新增排产应复用更新后的选机优先级，先按收尾时间比较");
    }

    @Test
    void scheduleNewSpecs_shouldWriteMachineDecisionTraceLogWhenEnabled() throws Exception {
        NewSpecProductionStrategy strategy = new NewSpecProductionStrategy();
        injectDependencies(strategy, false);

        LhScheduleContext context = buildContext();
        Map<String, String> scheduleParamMap = new HashMap<>(4);
        scheduleParamMap.put(LhScheduleParamConstant.ENABLE_PRIORITY_TRACE_LOG, "1");
        scheduleParamMap.put(LhScheduleParamConstant.ENABLE_LOCAL_SEARCH, "0");
        context.setScheduleConfig(new LhScheduleConfig(scheduleParamMap));
        SkuScheduleDTO sku = buildSku();
        context.getNewSpecSkuList().add(sku);

        MachineScheduleDTO machine = new MachineScheduleDTO();
        machine.setMachineCode("M-TRACE");
        machine.setMachineName("跟踪机台");
        machine.setEstimatedEndTime(dateTime(2026, 4, 17, 6, 0));

        strategy.scheduleNewSpecs(context, singletonMachineMatch(machine), defaultMouldChangeBalance(),
                defaultInspectionBalance(), defaultCapacityCalculate());

        assertEquals(1, context.getScheduleResultList().size());
        assertEquals(1, context.getScheduleLogList().size());
        LhScheduleProcessLog processLog = context.getScheduleLogList().get(0);
        assertEquals("新增排产机台决策", processLog.getTitle());
        assertTrue(processLog.getLogDetail().contains("MAT-1"));
        assertTrue(processLog.getLogDetail().contains("M-TRACE"));
        assertTrue(processLog.getLogDetail().contains("决策结果=成功"));
    }

    @Test
    void scheduleNewSpecs_shouldControlTraceLogVolumeWhenLocalSearchAndTraceEnabled() throws Exception {
        NewSpecProductionStrategy strategy = new NewSpecProductionStrategy();
        injectDependencies(strategy, false);

        LhScheduleContext context = buildContext();
        Map<String, String> scheduleParamMap = new HashMap<>(8);
        scheduleParamMap.put(LhScheduleParamConstant.ENABLE_PRIORITY_TRACE_LOG, "1");
        scheduleParamMap.put(LhScheduleParamConstant.ENABLE_LOCAL_SEARCH, "1");
        scheduleParamMap.put(LhScheduleParamConstant.LOCAL_SEARCH_MACHINE_THRESHOLD, "10");
        scheduleParamMap.put(LhScheduleParamConstant.LOCAL_SEARCH_DEPTH, "2");
        scheduleParamMap.put(LhScheduleParamConstant.LOCAL_SEARCH_TIME_BUDGET_MS, "200");
        context.setScheduleConfig(new LhScheduleConfig(scheduleParamMap));

        SkuScheduleDTO firstSku = buildSku();
        SkuScheduleDTO secondSku = buildSku();
        secondSku.setMaterialCode("MAT-2");
        secondSku.setMaterialDesc("测试物料2");
        context.getNewSpecSkuList().add(firstSku);
        context.getNewSpecSkuList().add(secondSku);

        MachineScheduleDTO firstMachine = new MachineScheduleDTO();
        firstMachine.setMachineCode("M-LS-1");
        firstMachine.setMachineName("局部搜索机台1");
        firstMachine.setStatus("1");
        firstMachine.setMaxMoldNum(1);
        firstMachine.setEstimatedEndTime(dateTime(2026, 4, 17, 6, 0));
        firstMachine.setPreviousSpecCode("11R22.5");
        firstMachine.setPreviousProSize("22.5");
        firstMachine.setPreviousMaterialCode("MAT-PREV-1");

        MachineScheduleDTO secondMachine = new MachineScheduleDTO();
        secondMachine.setMachineCode("M-LS-2");
        secondMachine.setMachineName("局部搜索机台2");
        secondMachine.setStatus("1");
        secondMachine.setMaxMoldNum(1);
        secondMachine.setEstimatedEndTime(dateTime(2026, 4, 17, 6, 30));
        secondMachine.setPreviousSpecCode("11R22.5");
        secondMachine.setPreviousProSize("22.5");
        secondMachine.setPreviousMaterialCode("MAT-PREV-2");

        context.getMachineScheduleMap().put(firstMachine.getMachineCode(), firstMachine);
        context.getMachineScheduleMap().put(secondMachine.getMachineCode(), secondMachine);

        MdmMaterialInfo prevMaterial1 = new MdmMaterialInfo();
        prevMaterial1.setMaterialCode("MAT-PREV-1");
        prevMaterial1.setEmbryoDesc("胎胚-A");
        context.getMaterialInfoMap().put(prevMaterial1.getMaterialCode(), prevMaterial1);

        MdmMaterialInfo prevMaterial2 = new MdmMaterialInfo();
        prevMaterial2.setMaterialCode("MAT-PREV-2");
        prevMaterial2.setEmbryoDesc("胎胚-B");
        context.getMaterialInfoMap().put(prevMaterial2.getMaterialCode(), prevMaterial2);

        strategy.scheduleNewSpecs(context, new DefaultMachineMatchStrategy(), defaultMouldChangeBalance(),
                defaultInspectionBalance(), defaultCapacityCalculate());

        assertEquals(2, context.getScheduleResultList().size());
        int candidateTraceLogCount = 0;
        int decisionTraceLogCount = 0;
        for (LhScheduleProcessLog processLog : context.getScheduleLogList()) {
            if ("新增排产候选机台排序明细".equals(processLog.getTitle())) {
                candidateTraceLogCount++;
            }
            if ("新增排产机台决策".equals(processLog.getTitle())) {
                decisionTraceLogCount++;
            }
        }
        assertEquals(2, candidateTraceLogCount,
                "启用局部搜索后，候选机台日志应按真实SKU决策输出，不应写入DFS模拟分支日志");
        assertEquals(2, decisionTraceLogCount, "应为每个真实决策SKU输出一条机台决策日志");
        assertEquals(4, context.getScheduleLogList().size(), "日志数量应受控在真实决策口径内");
    }

    private LhScheduleContext buildContext() {
        LhScheduleContext context = new LhScheduleContext();
        Date scheduleDate = dateTime(2026, 4, 17, 0, 0);
        context.setFactoryCode("116");
        context.setBatchNo("TEST-BATCH");
        context.setScheduleDate(scheduleDate);
        context.setScheduleTargetDate(scheduleDate);
        context.setScheduleWindowShifts(LhScheduleTimeUtil.buildDefaultScheduleShifts(context, scheduleDate));
        context.setMachineScheduleMap(new java.util.LinkedHashMap<String, MachineScheduleDTO>());
        context.setMachineAssignmentMap(new java.util.LinkedHashMap<String, List<com.zlt.aps.lh.api.domain.entity.LhScheduleResult>>());
        context.setMaterialInfoMap(new java.util.HashMap<String, MdmMaterialInfo>());
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

    private IMachineMatchStrategy singletonMachineMatch(MachineScheduleDTO machine) {
        return new IMachineMatchStrategy() {
            @Override
            public java.util.List<MachineScheduleDTO> matchMachines(LhScheduleContext ctx, SkuScheduleDTO scheduleSku) {
                return Arrays.asList(machine);
            }

            @Override
            public MachineScheduleDTO selectBestMachine(LhScheduleContext ctx, SkuScheduleDTO scheduleSku,
                                                        java.util.List<MachineScheduleDTO> candidates,
                                                        Set<String> excludedMachineCodes) {
                if (excludedMachineCodes.contains(machine.getMachineCode())) {
                    return null;
                }
                return machine;
            }
        };
    }

    private IMouldChangeBalanceStrategy defaultMouldChangeBalance() {
        return new IMouldChangeBalanceStrategy() {
            @Override
            public boolean hasCapacity(LhScheduleContext ctx, Date targetDate) {
                return true;
            }

            @Override
            public Date allocateMouldChange(LhScheduleContext ctx, String machineCode, Date endingTime) {
                return endingTime;
            }

            @Override
            public int getRemainingCapacity(LhScheduleContext ctx, Date targetDate) {
                return 99;
            }
        };
    }

    private IFirstInspectionBalanceStrategy defaultInspectionBalance() {
        return (ctx, machineCode, mouldChangeTime) -> mouldChangeTime;
    }

    private ICapacityCalculateStrategy defaultCapacityCalculate() {
        return new ICapacityCalculateStrategy() {
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
    }

    private void injectDependencies(NewSpecProductionStrategy strategy, boolean isEnding) throws Exception {
        OrderNoGenerator orderNoGenerator = new OrderNoGenerator();

        Field useRedisField = OrderNoGenerator.class.getDeclaredField("useRedis");
        useRedisField.setAccessible(true);
        useRedisField.set(orderNoGenerator, false);

        Field generatorField = NewSpecProductionStrategy.class.getDeclaredField("orderNoGenerator");
        generatorField.setAccessible(true);
        generatorField.set(strategy, orderNoGenerator);

        IEndingJudgmentStrategy endingJudgmentStrategy = new IEndingJudgmentStrategy() {
            @Override
            public boolean isEnding(LhScheduleContext context, SkuScheduleDTO sku) {
                return isEnding;
            }

            @Override
            public int calculateEndingShifts(LhScheduleContext context, SkuScheduleDTO sku) {
                return isEnding ? 1 : 0;
            }

            @Override
            public int calculateEndingDays(LhScheduleContext context, SkuScheduleDTO sku) {
                return isEnding ? 1 : 0;
            }
        };
        Field endingField = NewSpecProductionStrategy.class.getDeclaredField("endingJudgmentStrategy");
        endingField.setAccessible(true);
        endingField.set(strategy, endingJudgmentStrategy);

        Field targetResolverField = NewSpecProductionStrategy.class.getDeclaredField("targetScheduleQtyResolver");
        targetResolverField.setAccessible(true);
        targetResolverField.set(strategy, new TargetScheduleQtyResolver());

        Field localSearchField = NewSpecProductionStrategy.class.getDeclaredField("localSearchMachineAllocator");
        localSearchField.setAccessible(true);
        localSearchField.set(strategy, new LocalSearchMachineAllocatorStrategy());
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
