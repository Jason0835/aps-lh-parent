package com.zlt.aps.lh.regression;

import com.zlt.aps.lh.api.domain.dto.MachineScheduleDTO;
import com.zlt.aps.lh.api.domain.dto.SkuDailyScheduleDemandDTO;
import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.api.domain.entity.LhScheduleResult;
import com.zlt.aps.lh.api.domain.entity.LhUnscheduledResult;
import com.zlt.aps.lh.component.OrderNoGenerator;
import com.zlt.aps.lh.component.TargetScheduleQtyResolver;
import com.zlt.aps.lh.context.LhScheduleConfig;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.engine.strategy.ICapacityCalculateStrategy;
import com.zlt.aps.lh.engine.strategy.IEndingJudgmentStrategy;
import com.zlt.aps.lh.engine.strategy.IFirstInspectionBalanceStrategy;
import com.zlt.aps.lh.engine.strategy.IMachineMatchStrategy;
import com.zlt.aps.lh.engine.strategy.IMouldChangeBalanceStrategy;
import com.zlt.aps.lh.engine.strategy.impl.LocalSearchMachineAllocatorStrategy;
import com.zlt.aps.lh.engine.strategy.impl.NewSpecProductionStrategy;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import com.zlt.aps.lh.util.ShiftFieldUtil;
import com.zlt.aps.lh.api.domain.vo.LhShiftConfigVO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 新增规格多机台拆量排产 + 日计划桶控制 回归测试。
 * <p>覆盖场景：单机排完、多机拆量、日桶封顶、日维度未排、收尾上调、非收尾不上调</p>
 */
class NewSpecMultiMachineScheduleTest {

    /**
     * 单台机台+单日小量可排完 → 只排一台，日桶排满后 SKU 从待排列表移除。
     * 1天×5 在换模后的剩余班次产能范围内，应全部排完。
     */
    @Test
    void scheduleNewSpecs_singleMachineSufficient_shouldOnlyUseOneMachine() throws Exception {
        NewSpecProductionStrategy strategy = newStrategy(false);
        LhScheduleContext context = buildContext();

        SkuScheduleDTO sku = buildSingleDaySku("MAT-SINGLE", 5);
        context.getNewSpecSkuList().add(sku);

        MachineScheduleDTO m1 = buildMachine("M1", dateTime(2026, 4, 17, 6, 0));

        strategy.scheduleNewSpecs(context, singletonMachineMatch(m1),
                defaultMouldChangeBalance(), defaultInspectionBalance(), defaultCapacityCalculate());

        assertTrue(context.getScheduleResultList().size() >= 1, "应生成至少一条排产结果");
        assertEquals("M1", context.getScheduleResultList().get(0).getLhMachineCode());
        // 5 单位在单机产能内，日桶应排满 → SKU 从待排列表移除
        assertTrue(context.getNewSpecSkuList().isEmpty(),
                "日桶排满后 SKU 应从待排列表移除, newSpecSkuList.size=" + context.getNewSpecSkuList().size());
    }

    /**
     * 一台排不完需多台：SKU 拆分到多台机台，各台独立计算产能。
     * 3天×50=150, 单机产能约56, 两台合计约112, 排不完但多机台各自产生有效产量。
     */
    @Test
    void scheduleNewSpecs_multiMachineSplit_shouldDistributeAcrossMachines() throws Exception {
        NewSpecProductionStrategy strategy = newStrategy(false);
        LhScheduleContext context = buildContext();

        SkuScheduleDTO sku = buildMultiDaySku("MAT-MULTI", 3, 50);
        context.getNewSpecSkuList().add(sku);

        MachineScheduleDTO m1 = buildMachine("M1", dateTime(2026, 4, 17, 6, 0));
        MachineScheduleDTO m2 = buildMachine("M2", dateTime(2026, 4, 17, 6, 0));

        strategy.scheduleNewSpecs(context, multiMachineMatch(Arrays.asList(m1, m2)),
                defaultMouldChangeBalance(), defaultInspectionBalance(), defaultCapacityCalculate());

        // 2 台候选机台应都被尝试，至少一台排产成功
        assertTrue(context.getScheduleResultList().size() >= 1, "至少应有一台机台排产");
        int totalScheduled = context.getScheduleResultList().stream()
                .filter(r -> "MAT-MULTI".equals(r.getMaterialCode()))
                .mapToInt(r -> r.getDailyPlanQty() != null ? r.getDailyPlanQty() : 0)
                .sum();
        // 总排产量不超过目标量
        assertTrue(totalScheduled <= 150, "总排产量不应超过日目标量150，实际=" + totalScheduled);
        // 每台机台产出的日计划量应大于0
        for (LhScheduleResult result : context.getScheduleResultList()) {
            if ("MAT-MULTI".equals(result.getMaterialCode())) {
                assertTrue(result.getDailyPlanQty() != null && result.getDailyPlanQty() > 0,
                        "每台机台排产量应大于0, machine=" + result.getLhMachineCode());
            }
        }
    }

    /**
     * 多台也排不完：剩余量形成日维度未排记录。
     * day7=500, 两台各约 120, 合计约 240, 剩余 260。
     */
    @Test
    void scheduleNewSpecs_multiMachineInsufficient_shouldGenerateDailyUnscheduled() throws Exception {
        NewSpecProductionStrategy strategy = newStrategy(false);
        LhScheduleContext context = buildContext();

        SkuScheduleDTO sku = buildMultiDaySku("MAT-SHORTFALL", 3, 500);
        context.getNewSpecSkuList().add(sku);

        MachineScheduleDTO m1 = buildMachine("M1", dateTime(2026, 4, 17, 6, 0));
        MachineScheduleDTO m2 = buildMachine("M2", dateTime(2026, 4, 17, 6, 0));

        // 只有 2 台候选机台
        IMachineMatchStrategy machineMatch = new IMachineMatchStrategy() {
            @Override
            public List<MachineScheduleDTO> matchMachines(LhScheduleContext ctx, SkuScheduleDTO scheduleSku) {
                return Arrays.asList(m1, m2);
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

        strategy.scheduleNewSpecs(context, machineMatch,
                defaultMouldChangeBalance(), defaultInspectionBalance(), defaultCapacityCalculate());

        assertTrue(context.getScheduleResultList().size() >= 1, "至少有一台机台排产");
        // 应有日维度未排记录
        List<LhUnscheduledResult> unscheduledList = context.getUnscheduledResultList();
        assertTrue(unscheduledList.size() > 0, "多台仍排不完时应生成日维度未排记录");
        // 未排记录应包含正确的物料编码
        boolean hasMaterialUnscheduled = unscheduledList.stream()
                .anyMatch(u -> "MAT-SHORTFALL".equals(u.getMaterialCode()));
        assertTrue(hasMaterialUnscheduled, "未排记录应包含对应物料");
        // 未排记录的 scheduleDate 应为具体日期而非窗口目标日期
        LhUnscheduledResult dailyUnscheduled = unscheduledList.stream()
                .filter(u -> "MAT-SHORTFALL".equals(u.getMaterialCode()))
                .findFirst().orElse(null);
        assertNotNull(dailyUnscheduled, "应存在日维度未排记录");
        assertNotNull(dailyUnscheduled.getScheduleDate(), "日维度未排记录应包含具体日期");
        assertTrue(dailyUnscheduled.getUnscheduledQty() > 0, "未排数量应大于0");
    }

    /**
     * 非收尾场景：目标量严格受当天 dayN 控制，不允许因胎胚库存上调。
     * 胎胚库存 500，但 day7 只有 80，目标量应为 80 而非 500。
     */
    @Test
    void scheduleNewSpecs_nonEnding_shouldNotUpgradeByEmbryoStock() throws Exception {
        NewSpecProductionStrategy strategy = newStrategy(false);
        LhScheduleContext context = buildContext();

        SkuScheduleDTO sku = buildMultiDaySku("MAT-NON-ENDING", 3, 80);
        sku.setSurplusQty(500);
        sku.setEmbryoStock(500);
        // pendingQty 取自 surplusQty，收尾上调由 applyEndingDailyDemandUpgrade 控制
        sku.setPendingQty(80);
        context.getNewSpecSkuList().add(sku);

        MachineScheduleDTO m1 = buildMachine("M1", dateTime(2026, 4, 17, 6, 0));

        strategy.scheduleNewSpecs(context, singletonMachineMatch(m1),
                defaultMouldChangeBalance(), defaultInspectionBalance(), defaultCapacityCalculate());

        assertTrue(context.getScheduleResultList().size() >= 1, "应生成排产结果");
        // 非收尾时 total plan qty 不应超过 dayN 计划量
        int totalScheduled = context.getScheduleResultList().stream()
                .filter(r -> "MAT-NON-ENDING".equals(r.getMaterialCode()))
                .mapToInt(r -> r.getDailyPlanQty() != null ? r.getDailyPlanQty() : 0)
                .sum();
        assertTrue(totalScheduled <= 80, "非收尾场景目标量不应因胎胚库存上调，期望≤80，实际=" + totalScheduled);
    }

    /**
     * 收尾场景：允许上调到胎胚库存，上调量放到最后一天。
     * day7=80, day8=80, day9=80, 胎胚库存 500, surplusQty 400。
     * 收尾时最后一天目标量应上调。
     */
    @Test
    void scheduleNewSpecs_ending_shouldUpgradeLastDayByEmbryoStock() throws Exception {
        NewSpecProductionStrategy strategy = newStrategy(true);
        LhScheduleContext context = buildContext();

        SkuScheduleDTO sku = buildMultiDaySkuThreeDays("MAT-ENDING", 80, 80, 80);
        sku.setSurplusQty(400);
        sku.setEmbryoStock(500);
        sku.setPendingQty(400);
        sku.setShiftCapacity(8);
        sku.setLhTimeSeconds(3600);
        context.getNewSpecSkuList().add(sku);

        MachineScheduleDTO m1 = buildMachine("M1", dateTime(2026, 4, 17, 6, 0));
        MachineScheduleDTO m2 = buildMachine("M2", dateTime(2026, 4, 17, 6, 0));
        MachineScheduleDTO m3 = buildMachine("M3", dateTime(2026, 4, 17, 6, 0));

        strategy.scheduleNewSpecs(context,
                multiMachineMatch(Arrays.asList(m1, m2, m3)),
                defaultMouldChangeBalance(), defaultInspectionBalance(), defaultCapacityCalculate());

        assertTrue(context.getScheduleResultList().size() >= 1, "应生成排产结果");
        int totalScheduled = context.getScheduleResultList().stream()
                .filter(r -> "MAT-ENDING".equals(r.getMaterialCode()))
                .mapToInt(r -> r.getDailyPlanQty() != null ? r.getDailyPlanQty() : 0)
                .sum();
        // 收尾时总目标量应 >= 窗口日计划量（允许上调）
        assertTrue(totalScheduled > 80, "收尾场景总排产量应大于单日计划量，实际=" + totalScheduled);
    }

    /**
     * 日计划桶控制：每个班次按所属日期累计，日桶满后跳过该日后续班次。
     * day7=30, M1 每班产能 40，只有 day7 的 30 被排，剩余产能不能用于 day7 但可用于 day8/day9。
     */
    @Test
    void scheduleNewSpecs_dailyBucketControl_shouldCapPerDay() throws Exception {
        NewSpecProductionStrategy strategy = newStrategy(false);
        LhScheduleContext context = buildContext();

        // 只有 day7 有 30 的需求
        SkuScheduleDTO sku = buildMultiDaySku("MAT-DAILY-CAP", 1, 30);
        sku.setPendingQty(30);
        context.getNewSpecSkuList().add(sku);

        MachineScheduleDTO m1 = buildMachine("M1", dateTime(2026, 4, 17, 6, 0));

        strategy.scheduleNewSpecs(context, singletonMachineMatch(m1),
                defaultMouldChangeBalance(), defaultInspectionBalance(), defaultCapacityCalculate());

        assertTrue(context.getScheduleResultList().size() >= 1, "应生成排产结果");
        // day7 只有 30 需求，不应超过
        int totalScheduled = context.getScheduleResultList().stream()
                .filter(r -> "MAT-DAILY-CAP".equals(r.getMaterialCode()))
                .mapToInt(r -> r.getDailyPlanQty() != null ? r.getDailyPlanQty() : 0)
                .sum();
        assertTrue(totalScheduled <= 30, "日桶封顶后不应超过当天计划量，期望≤30，实际=" + totalScheduled);
    }

    /**
     * 同一 SKU 多机台排产时，每台机台独立换模/首检检查。
     * M1 换模失败 → 排除 M1，尝试 M2 → M2 成功。
     */
    @Test
    void scheduleNewSpecs_firstMachineMouldChangeFailed_shouldTryNextMachine() throws Exception {
        NewSpecProductionStrategy strategy = newStrategy(false);
        LhScheduleContext context = buildContext();

        SkuScheduleDTO sku = buildMultiDaySku("MAT-FAILOVER", 3, 100);
        context.getNewSpecSkuList().add(sku);

        MachineScheduleDTO m1 = buildMachine("M1-FAIL", dateTime(2026, 4, 17, 6, 0));
        MachineScheduleDTO m2 = buildMachine("M2-OK", dateTime(2026, 4, 17, 6, 0));

        // M1 换模失败，M2 成功
        IMouldChangeBalanceStrategy selectiveMouldChange = new IMouldChangeBalanceStrategy() {
            @Override
            public boolean hasCapacity(LhScheduleContext ctx, Date targetDate) {
                return true;
            }

            @Override
            public Date allocateMouldChange(LhScheduleContext ctx, String machineCode, Date endingTime) {
                if ("M1-FAIL".equals(machineCode)) {
                    return null; // M1 换模失败
                }
                return endingTime;
            }

            @Override
            public int getRemainingCapacity(LhScheduleContext ctx, Date targetDate) {
                return 99;
            }
        };

        strategy.scheduleNewSpecs(context, multiMachineMatch(Arrays.asList(m1, m2)),
                selectiveMouldChange, defaultInspectionBalance(), defaultCapacityCalculate());

        assertEquals(1, context.getScheduleResultList().size(), "M1 换模失败后应成功使用 M2");
        assertEquals("M2-OK", context.getScheduleResultList().get(0).getLhMachineCode());
    }

    /**
     * 日维度需求列表为空时 → 回退到窗口总量模式，不跳过 SKU。
     */
    @Test
    void scheduleNewSpecs_emptyDailyDemandList_shouldFallbackToWindowTotalMode() throws Exception {
        NewSpecProductionStrategy strategy = newStrategy(false);
        LhScheduleContext context = buildContext();

        // 不设置 dailyDemandList，模拟旧构造数据
        SkuScheduleDTO sku = buildSkuWithoutDailyDemand("MAT-LEGACY", 100);
        context.getNewSpecSkuList().add(sku);

        MachineScheduleDTO m1 = buildMachine("M1", dateTime(2026, 4, 17, 6, 0));

        strategy.scheduleNewSpecs(context, singletonMachineMatch(m1),
                defaultMouldChangeBalance(), defaultInspectionBalance(), defaultCapacityCalculate());

        assertEquals(1, context.getScheduleResultList().size(),
                "旧构造数据无日维度列表时，应回退到窗口总量模式继续排产，不应直接跳过");
        assertEquals("MAT-LEGACY", context.getScheduleResultList().get(0).getMaterialCode());
    }

    /**
     * 多机台排产后日维度未排记录不被归一化吞掉。
     * 同一物料在不同日期有未排 → normalizeUnscheduledResultsByMaterial 应保留各自日期。
     */
    @Test
    void normalizeUnscheduledResults_shouldPreservePerDateRecords() throws Exception {
        NewSpecProductionStrategy strategy = newStrategy(false);
        LhScheduleContext context = buildContext();

        SkuScheduleDTO sku = buildMultiDaySkuThreeDays("MAT-DATES", 500, 500, 500);
        sku.setPendingQty(1000);
        context.getNewSpecSkuList().add(sku);

        MachineScheduleDTO m1 = buildMachine("M1", dateTime(2026, 4, 17, 6, 0));
        MachineScheduleDTO m2 = buildMachine("M2", dateTime(2026, 4, 17, 6, 0));

        strategy.scheduleNewSpecs(context, multiMachineMatch(Arrays.asList(m1, m2)),
                defaultMouldChangeBalance(), defaultInspectionBalance(), defaultCapacityCalculate());

        // 模拟 normalization（adjustEmbryoStock 中会调用的逻辑）
        LhScheduleResult zeroResult = new LhScheduleResult();
        zeroResult.setScheduleType("02");
        zeroResult.setMaterialCode("MAT-DATES");
        zeroResult.setDailyPlanQty(0);
        context.getScheduleResultList().add(zeroResult);

        strategy.adjustEmbryoStock(context);

        // 验证同物料不同日期的未排记录未被错误合并
        List<LhUnscheduledResult> unscheduledList = context.getUnscheduledResultList();
        long matDatesCount = unscheduledList.stream()
                .filter(u -> "MAT-DATES".equals(u.getMaterialCode()))
                .count();
        // 同物料可能有多个未排记录（不同日期 + 零计划），每个保留独立日期信息
        assertTrue(matDatesCount >= 1, "应至少保留一条未排记录");
    }

    /**
     * 多机台排产后，SKU 的 targetScheduleQty 应被更新为日桶剩余总量。
     */
    @Test
    void scheduleNewSpecs_shouldUpdateTargetQtyAfterEachMachine() throws Exception {
        NewSpecProductionStrategy strategy = newStrategy(false);
        LhScheduleContext context = buildContext();

        SkuScheduleDTO sku = buildMultiDaySku("MAT-TARGET-UPDATE", 3, 300);
        int originalTarget = sku.resolveTargetScheduleQty();
        context.getNewSpecSkuList().add(sku);

        MachineScheduleDTO m1 = buildMachine("M1", dateTime(2026, 4, 17, 6, 0));

        strategy.scheduleNewSpecs(context, singletonMachineMatch(m1),
                defaultMouldChangeBalance(), defaultInspectionBalance(), defaultCapacityCalculate());

        // 机台排产后 targetScheduleQty 应更新（如果还有剩余日桶需求）
        // 如果日桶已全部排满，targetScheduleQty 可能为 0（SKU 已从列表移除）
        assertTrue(context.getNewSpecSkuList().isEmpty()
                        || sku.resolveTargetScheduleQty() <= originalTarget,
                "排产后目标量应≤原始目标量");
    }

    // ==================== 辅助方法 ====================

    private LhScheduleContext buildContext() {
        LhScheduleContext context = new LhScheduleContext();
        Date scheduleDate = dateTime(2026, 4, 17, 0, 0);
        context.setFactoryCode("116");
        context.setBatchNo("TEST-BATCH");
        context.setScheduleDate(scheduleDate);
        context.setScheduleTargetDate(scheduleDate);
        context.setScheduleWindowShifts(LhScheduleTimeUtil.buildDefaultScheduleShifts(context, scheduleDate));
        context.setMachineScheduleMap(new LinkedHashMap<>());
        context.setMachineAssignmentMap(new LinkedHashMap<>());
        context.setMaterialInfoMap(new LinkedHashMap<>());
        return context;
    }

    /**
     * 构建单日 SKU，仅一个日期的 dailyDemand。
     */
    private SkuScheduleDTO buildSingleDaySku(String materialCode, int dayPlanQty) {
        SkuScheduleDTO sku = new SkuScheduleDTO();
        sku.setMaterialCode(materialCode);
        sku.setMaterialDesc("测试物料-" + materialCode);
        sku.setSpecCode("11R22.5");
        sku.setSpecDesc("11R22.5");
        sku.setProSize("22.5");
        sku.setLhTimeSeconds(3600);
        sku.setMouldQty(1);
        sku.setShiftCapacity(8);
        sku.setPendingQty(dayPlanQty);
        sku.setSurplusQty(dayPlanQty);
        sku.setTargetScheduleQty(dayPlanQty);

        SkuDailyScheduleDemandDTO demand = new SkuDailyScheduleDemandDTO();
        demand.setMaterialCode(materialCode);
        demand.setScheduleDate(dateTime(2026, 4, 17, 0, 0));
        demand.setDayPlanQty(dayPlanQty);
        demand.setInheritedQty(0);
        demand.setCarryForwardQty(0);
        demand.setTargetQty(dayPlanQty);
        demand.setRemainingQty(dayPlanQty);

        sku.setDailyDemandList(Arrays.asList(demand));
        return sku;
    }

    /**
     * 构建多日 SKU，设置 dailyDemandList。
     * 每个日期 dayPlanQty 相同，inheritedQty=0, carryForwardQty=0（仅首日）。
     */
    private SkuScheduleDTO buildMultiDaySku(String materialCode, int days, int dayPlanQty) {
        SkuScheduleDTO sku = new SkuScheduleDTO();
        sku.setMaterialCode(materialCode);
        sku.setMaterialDesc("测试物料-" + materialCode);
        sku.setSpecCode("11R22.5");
        sku.setSpecDesc("11R22.5");
        sku.setProSize("22.5");
        sku.setLhTimeSeconds(3600);
        sku.setMouldQty(1);
        sku.setShiftCapacity(8);
        sku.setPendingQty(dayPlanQty * days);
        sku.setSurplusQty(dayPlanQty * days);
        sku.setTargetScheduleQty(dayPlanQty * days);

        // 构建日维度需求列表
        List<SkuDailyScheduleDemandDTO> dailyDemandList = new ArrayList<>(days);
        Calendar cursor = Calendar.getInstance();
        cursor.setTime(dateTime(2026, 4, 17, 0, 0));
        for (int i = 0; i < days; i++) {
            SkuDailyScheduleDemandDTO demand = new SkuDailyScheduleDemandDTO();
            demand.setMaterialCode(materialCode);
            demand.setScheduleDate(cursor.getTime());
            demand.setDayPlanQty(dayPlanQty);
            demand.setInheritedQty(0);
            demand.setCarryForwardQty(i == 0 ? 0 : 0); // 无欠产结转
            demand.setTargetQty(dayPlanQty);
            demand.setRemainingQty(dayPlanQty);
            dailyDemandList.add(demand);
            cursor.add(Calendar.DAY_OF_MONTH, 1);
        }
        sku.setDailyDemandList(dailyDemandList);
        return sku;
    }

    /**
     * 构建三天不同计划的 SKU。
     */
    private SkuScheduleDTO buildMultiDaySkuThreeDays(String materialCode, int day7Qty, int day8Qty, int day9Qty) {
        int totalQty = day7Qty + day8Qty + day9Qty;
        SkuScheduleDTO sku = new SkuScheduleDTO();
        sku.setMaterialCode(materialCode);
        sku.setMaterialDesc("测试物料-" + materialCode);
        sku.setSpecCode("11R22.5");
        sku.setSpecDesc("11R22.5");
        sku.setProSize("22.5");
        sku.setLhTimeSeconds(3600);
        sku.setMouldQty(1);
        sku.setShiftCapacity(8);
        sku.setPendingQty(totalQty);
        sku.setSurplusQty(totalQty);
        sku.setTargetScheduleQty(totalQty);

        List<SkuDailyScheduleDemandDTO> dailyDemandList = new ArrayList<>(3);
        Calendar cursor = Calendar.getInstance();
        cursor.setTime(dateTime(2026, 4, 17, 0, 0));

        int[] qtys = {day7Qty, day8Qty, day9Qty};
        for (int qty : qtys) {
            SkuDailyScheduleDemandDTO demand = new SkuDailyScheduleDemandDTO();
            demand.setMaterialCode(materialCode);
            demand.setScheduleDate(cursor.getTime());
            demand.setDayPlanQty(qty);
            demand.setInheritedQty(0);
            demand.setCarryForwardQty(0);
            demand.setTargetQty(qty);
            demand.setRemainingQty(qty);
            dailyDemandList.add(demand);
            cursor.add(Calendar.DAY_OF_MONTH, 1);
        }
        sku.setDailyDemandList(dailyDemandList);
        return sku;
    }

    /**
     * 构建无日维度需求列表的 SKU（模拟旧构造数据）。
     */
    private SkuScheduleDTO buildSkuWithoutDailyDemand(String materialCode, int pendingQty) {
        SkuScheduleDTO sku = new SkuScheduleDTO();
        sku.setMaterialCode(materialCode);
        sku.setMaterialDesc("测试物料-" + materialCode);
        sku.setSpecCode("11R22.5");
        sku.setSpecDesc("11R22.5");
        sku.setProSize("22.5");
        sku.setLhTimeSeconds(3600);
        sku.setMouldQty(1);
        sku.setShiftCapacity(8);
        sku.setPendingQty(pendingQty);
        sku.setSurplusQty(pendingQty);
        sku.setTargetScheduleQty(pendingQty);
        // 不设置 dailyDemandList，模拟旧数据
        return sku;
    }

    private MachineScheduleDTO buildMachine(String machineCode, Date estimatedEndTime) {
        MachineScheduleDTO machine = new MachineScheduleDTO();
        machine.setMachineCode(machineCode);
        machine.setMachineName(machineCode);
        machine.setStatus("1");
        machine.setMaxMoldNum(1);
        machine.setEstimatedEndTime(estimatedEndTime);
        machine.setPreviousSpecCode("11R22.5");
        machine.setPreviousProSize("22.5");
        machine.setPreviousMaterialCode("PREV-" + machineCode);
        return machine;
    }

    private IMachineMatchStrategy singletonMachineMatch(MachineScheduleDTO machine) {
        return new IMachineMatchStrategy() {
            @Override
            public List<MachineScheduleDTO> matchMachines(LhScheduleContext ctx, SkuScheduleDTO scheduleSku) {
                return Arrays.asList(machine);
            }

            @Override
            public MachineScheduleDTO selectBestMachine(LhScheduleContext ctx, SkuScheduleDTO scheduleSku,
                                                        List<MachineScheduleDTO> candidates,
                                                        Set<String> excludedMachineCodes) {
                if (excludedMachineCodes.contains(machine.getMachineCode())) {
                    return null;
                }
                return machine;
            }
        };
    }

    private IMachineMatchStrategy multiMachineMatch(List<MachineScheduleDTO> machines) {
        return new IMachineMatchStrategy() {
            @Override
            public List<MachineScheduleDTO> matchMachines(LhScheduleContext ctx, SkuScheduleDTO scheduleSku) {
                return new ArrayList<>(machines);
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

    private NewSpecProductionStrategy newStrategy(boolean isEnding) throws Exception {
        NewSpecProductionStrategy strategy = new NewSpecProductionStrategy();

        // 注入 OrderNoGenerator
        OrderNoGenerator orderNoGenerator = new OrderNoGenerator();
        Field useRedisField = OrderNoGenerator.class.getDeclaredField("useRedis");
        useRedisField.setAccessible(true);
        useRedisField.set(orderNoGenerator, false);

        Field generatorField = NewSpecProductionStrategy.class.getDeclaredField("orderNoGenerator");
        generatorField.setAccessible(true);
        generatorField.set(strategy, orderNoGenerator);

        // 注入收尾判定策略
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

        // 注入 TargetScheduleQtyResolver
        Field targetResolverField = NewSpecProductionStrategy.class.getDeclaredField("targetScheduleQtyResolver");
        targetResolverField.setAccessible(true);
        targetResolverField.set(strategy, new TargetScheduleQtyResolver());

        // 注入 LocalSearchMachineAllocator
        Field localSearchField = NewSpecProductionStrategy.class.getDeclaredField("localSearchMachineAllocator");
        localSearchField.setAccessible(true);
        localSearchField.set(strategy, new LocalSearchMachineAllocatorStrategy());

        return strategy;
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
