# SKU支持多机台排产 — 详细设计方案与实施计划

---

## 一、明确结论

**推荐方案**：在现有排程流程基础上，以最小改动量支持一个SKU拆分到多台硫化机台排产。核心思路是：

1. **待排产量口径**：复用 `MonthPlanDayQtyUtil.resolveWindowPlanQty()` 按窗口汇总日计划量，**当前逻辑已满足**，无需修改。
2. **多机台拆量排产**：在 `NewSpecProductionStrategy.scheduleNewSpecs()` 中，将"排产成功即break"改为"排产后扣减剩余待排量，若仍有余量则继续尝试下一台候选机台"。
3. **收尾判断调整**：在 `DefaultEndingJudgmentStrategy.isEnding()` 中，将单机台产能计算改为基于SKU所有候选机台的合计可用产能。
4. **胎胚库存上调限制**：在 `ScheduleAdjustHandler.buildSkuScheduleDTO()` 中，将 `max(硫化余量, 胎胚库存)` 基线调整为"仅在收尾场景下取胎胚库存上限"。
5. **月计划扣减保障**：多机台拆量时，每台机台排产后立即扣减 `sku.pendingQty`，避免重复排产。

---

## 二、改造目标

> 当某个SKU待排产量较大，一台硫化机台在排程窗口内产能无法排完时，允许同一个SKU拆分到多台可用硫化机台上排产。

同时确保：
- 收尾判断基于多机台合计产能
- 非收尾场景不允许因胎胚库存上调计划量
- 月计划余量按实际排产量准确扣减，不重复、不遗漏

---

## 三、现有逻辑影响点

### 3.1 需要检查和修改的类

| 序号 | 类名 | 路径 | 修改内容 |
|------|------|------|----------|
| 1 | `NewSpecProductionStrategy` | `aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/` | **核心改动**：排产成功后不再break，改为扣减待排量后继续尝试下一台机台 |
| 2 | `DefaultEndingJudgmentStrategy` | `aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/` | 收尾判断产能计算改为多机台合计产能 |
| 3 | `ScheduleAdjustHandler` | `aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/` | 胎胚库存上调基线逻辑调整 |
| 4 | `TargetScheduleQtyResolver` | `aps-lh/src/main/java/com/zlt/aps/lh/component/` | `refineTargetQtyByMachineCapacity` 方法需支持剩余待排量传入 |
| 5 | `ContinuousProductionStrategy` | `aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/` | 续作场景也需考虑多机台拆量（次要优先级） |
| 6 | `TypeBlockProductionStrategy` | `aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/` | 换活字块场景需同步适配（次要优先级） |

### 3.2 需要检查但不一定修改的类

| 序号 | 类名 | 检查原因 |
|------|------|----------|
| 1 | `MonthPlanDayQtyUtil` | 确认 `resolveWindowPlanQty()` 已按窗口汇总，无需修改 |
| 2 | `DefaultMachineMatchStrategy` | `matchMachines()` 已返回多台候选机台，无需修改 |
| 3 | `LhScheduleTemplateImpl` | 排程流程模板无需修改，多机台拆量在策略内部完成 |
| 4 | `LocalSearchMachineAllocatorStrategy` | 局部搜索选机策略需确认与多机台拆量不冲突 |
| 5 | `ShiftCapacityResolverUtil` | 班次产能计算无需修改，但需确认同一班次不会被重复占用 |
| 6 | `SkuScheduleDTO` | 可能需要新增字段追踪多机台排产状态 |

### 3.3 需要检查的数据表和Mapper

| 序号 | 表/Mapper | 检查原因 |
|------|-----------|----------|
| 1 | `lh_schedule_result` | 排程结果表，确认同一SKU多机台排产结果可分别保存 |
| 2 | `T_MP_MONTH_PLAN_PROD_FINAL` | 月计划表，确认月计划扣减不会重复 |
| 3 | `SchedulePersistenceService` | 保存逻辑，确认批量插入支持同一SKU多条记录 |

---

## 四、设计思路

### 4.1 核心设计：多机台拆量排产循环

**当前逻辑**（单机台排产）：

```
for each SKU:
    candidates = matchMachines(sku)
    while true:
        machine = selectCandidateMachine(candidates, excluded)
        if machine == null: break
        targetQty = refineTargetQty(machine, sku)
        result = buildScheduleResult(machine, sku, targetQty)
        if success:
            save(result)
            break    ← 排产成功立即退出，不再尝试其他机台
        else:
            rollback + excluded.add(machine)
    if !scheduled:
        addUnscheduledResult(sku)
```

**改造后逻辑**（多机台拆量排产）：

```
for each SKU:
    candidates = matchMachines(sku)
    remainingQty = sku.pendingQty    ← SKU剩余待排量
    while remainingQty > 0:
        machine = selectCandidateMachine(candidates, excluded)
        if machine == null: break
        // 按剩余待排量重新计算目标量
        sku.setTargetScheduleQty(remainingQty)
        targetQty = refineTargetQty(machine, sku)
        if targetQty <= 0:
            excluded.add(machine)
            continue
        result = buildScheduleResult(machine, sku, targetQty)
        if success:
            save(result)
            remainingQty -= actualScheduleQty    ← 扣减已排量
            // 如果还有剩余量，不break，继续尝试下一台机台
            if remainingQty <= 0:
                break
        else:
            rollback + excluded.add(machine)
    if remainingQty > 0:
        addUnscheduledResult(sku, remainingQty)    ← 记录未排量
```

### 4.2 核心设计：收尾判断改为多机台合计产能

**当前逻辑**：

```java
// 规则2：排产目标量 <= 单机台班产 * 总班次数
int totalCapacity = shiftCapacity * totalScheduleShifts;
boolean isEnding = candidateQty <= totalCapacity;
```

问题：`shiftCapacity` 是单台机台的班产能力，只看一台机台。

**改造后逻辑**：

```java
// 规则2：排产目标量 <= SKU所有可用机台在窗口内的合计可生产总量
List<MachineScheduleDTO> candidateMachines = machineMatchStrategy.matchMachines(context, sku);
int totalCapacity = calcSkuTotalAvailableCapacity(context, sku, candidateMachines, scheduleShifts);
boolean isEnding = candidateQty <= totalCapacity;
```

其中 `calcSkuTotalAvailableCapacity` 需要新增，计算逻辑：
- 遍历SKU的所有候选机台
- 对每台机台计算其在排程窗口内可用于该SKU的产能
- 汇总所有机台的可用产能

### 4.3 核心设计：胎胚库存上调限制

**当前逻辑**（`ScheduleAdjustHandler.buildSkuScheduleDTO()`）：

```java
// 待排量以"余量/库存取大"为基线
int basePendingQty = Math.max(surplus.getSurplusQty(), Math.max(0, dto.getEmbryoStock()));
```

问题：无论是否收尾，都将胎胚库存作为待排量基线，非收尾场景也可能上调。

**改造后逻辑**：

```java
int basePendingQty;
if (isSkuCanFinishInWindow(context, plan, dto)) {
    // 收尾场景：允许取大
    basePendingQty = Math.max(surplus.getSurplusQty(), Math.max(0, dto.getEmbryoStock()));
} else {
    // 非收尾场景：不允许胎胚库存上调
    basePendingQty = surplus.getSurplusQty();
}
```

**关键问题**：收尾判断依赖多机台合计产能，而多机台合计产能计算需要候选机台列表，候选机台匹配需要SKU的排程上下文。在 `ScheduleAdjustHandler` 阶段（排程调整与SKU归集阶段），机台匹配信息可能尚未完全就绪。

**解决方案**：在 `ScheduleAdjustHandler` 中引入轻量级收尾预判方法，仅计算SKU月计划余量是否 <= 多机台合计产能，不执行实际排产。如果预判结果为收尾，则允许胎胚库存上调；否则不允许。

### 4.4 核心设计：月计划扣减保障

多机台拆量排产时，月计划扣减的关键保障：

1. **排产过程中**：每台机台排产成功后，立即扣减 `sku.pendingQty`，下一个机台使用的目标量是扣减后的剩余量
2. **排产结果保存**：每台机台的排产结果分别保存，`mouldSurplusQty` 按实际排产量写入
3. **避免重复扣减**：排产循环中 `sku.pendingQty` 是同一个对象引用，扣减后后续机台自动感知

---

## 五、详细实施步骤

### 步骤1：SkuScheduleDTO 新增字段

**修改文件**：`aps-lh-api/src/main/java/com/zlt/aps/lh/api/domain/dto/SkuScheduleDTO.java`

**新增字段**：

```java
/** 本SKU已成功排产的机台数量（多机台拆量场景） */
private int scheduledMachineCount;

/** 本SKU多机台排产的剩余未排量 */
private int remainingUnscheduledQty;
```

**说明**：这两个字段用于追踪多机台拆量的进度状态，不影响现有字段语义。

---

### 步骤2：DefaultEndingJudgmentStrategy 收尾判断改造

**修改文件**：`aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/DefaultEndingJudgmentStrategy.java`

**修改点2.1**：新增依赖注入

```java
@Autowired
private IMachineMatchStrategy machineMatchStrategy;
```

**修改点2.2**：新增多机台合计产能计算方法

```java
/**
 * 计算SKU在排程窗口内所有可用机台的合计可生产总量
 *
 * @param context 排程上下文
 * @param sku SKU排程数据
 * @param shifts 排程窗口班次列表
 * @return 多机台合计可用产能
 */
private int calcSkuTotalAvailableCapacityInWindow(LhScheduleContext context,
                                                   SkuScheduleDTO sku,
                                                   List<LhShiftConfigVO> shifts) {
    List<MachineScheduleDTO> candidates = machineMatchStrategy.matchMachines(context, sku);
    if (CollectionUtils.isEmpty(candidates)) {
        return sku.getShiftCapacity() * getTotalScheduleShifts(context);
    }

    int totalCapacity = 0;
    int totalShifts = getTotalScheduleShifts(context);
    for (MachineScheduleDTO machine : candidates) {
        // 每台机台的产能 = 班产 * 可用班次数（扣减已有占用）
        int machineShiftCapacity = resolveMachineShiftCapacity(context, sku, machine, shifts);
        if (machineShiftCapacity > 0) {
            int machineCapacity = machineShiftCapacity * totalShifts;
            // 扣减该机台已有排产占用的产能
            int occupiedQty = resolveMachineOccupiedQty(context, machine.getMachineCode(), sku);
            totalCapacity += Math.max(0, machineCapacity - occupiedQty);
        }
    }

    // 保底：如果候选机台产能为0，回退到原逻辑（单机台班产 * 总班次）
    if (totalCapacity <= 0) {
        totalCapacity = sku.getShiftCapacity() * totalShifts;
    }

    log.info("SKU多机台收尾产能计算: sku={}, candidateMachineCount={}, totalAvailableCapacity={}",
            sku.getMaterialCode(), candidates.size(), totalCapacity);
    return totalCapacity;
}
```

**修改点2.3**：新增辅助方法

```java
/**
 * 解析机台对该SKU的班产能力
 */
private int resolveMachineShiftCapacity(LhScheduleContext context,
                                         SkuScheduleDTO sku,
                                         MachineScheduleDTO machine,
                                         List<LhShiftConfigVO> shifts) {
    // 优先取SKU的标准班产
    return sku.getShiftCapacity();
}

/**
 * 解析机台已被其他SKU占用的产能
 */
private int resolveMachineOccupiedQty(LhScheduleContext context,
                                       String machineCode,
                                       SkuScheduleDTO currentSku) {
    int occupied = 0;
    for (LhScheduleResult result : context.getScheduleResultList()) {
        if (machineCode.equals(result.getLhMachineCode())
                && !currentSku.getMaterialCode().equals(result.getMaterialCode())) {
            occupied += ShiftFieldUtil.resolveScheduledQty(result);
        }
    }
    return occupied;
}
```

**修改点2.4**：修改 `isEnding` 方法规则2

将原来的：

```java
int totalCapacity = shiftCapacity * totalScheduleShifts;
if (rule2CandidateQty <= totalCapacity) {
    return true;
}
```

改为：

```java
int totalCapacity = calcSkuTotalAvailableCapacityInWindow(context, sku,
        LhScheduleTimeUtil.getScheduleShifts(context, context.getScheduleDate()));
if (rule2CandidateQty <= totalCapacity) {
    return true;
}
```

**修改点2.5**：规则3同理，日产能也应基于多机台合计

```java
// 规则3：待排量 < 多机台日产能合计
int multiMachineDailyCapacity = calcSkuMultiMachineDailyCapacity(context, sku);
if (multiMachineDailyCapacity > 0 && targetScheduleQty < multiMachineDailyCapacity && targetScheduleQty > 0) {
    return true;
}
```

新增辅助方法：

```java
/**
 * 计算SKU多机台日产能合计
 */
private int calcSkuMultiMachineDailyCapacity(LhScheduleContext context, SkuScheduleDTO sku) {
    return sku.getDailyCapacity();
    // 注：初始版本使用SKU的日产能（已考虑标准产能），
    // 后续如果需要按候选机台数量倍增，再进一步改造
}
```

**说明**：规则3暂不倍增日产能，因为规则3场景是"目标量小于日产能"的快速判定，多机台改造主要影响规则2。

---

### 步骤3：ScheduleAdjustHandler 胎胚库存上调限制

**修改文件**：`aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/ScheduleAdjustHandler.java`

**修改点3.1**：新增轻量级收尾预判方法

```java
/**
 * 轻量级预判SKU是否可在当前窗口收尾
 * <p>用于胎胚库存上调条件判断，不依赖完整机台匹配流程</p>
 *
 * @param context 排程上下文
 * @param plan 月计划结果
 * @param dto SKU排程DTO
 * @return true-可在窗口收尾
 */
private boolean isSkuCanFinishInWindow(LhScheduleContext context,
                                        FactoryMonthPlanProductionFinalResult plan,
                                        SkuScheduleDTO dto) {
    int surplusQty = dto.getSurplusQty();
    if (surplusQty <= 0) {
        return true;
    }

    // 已标记为收尾的SKU，直接返回true
    if (SkuTagEnum.ENDING.getCode().equals(dto.getSkuTag())) {
        return true;
    }

    // 轻量级预判：月计划余量 <= SKU日产能 * 窗口天数 * 候选机台数量
    // 候选机台数量取一个保守估值：该英寸段可用机台数
    int candidateMachineCount = estimateCandidateMachineCount(context, dto);
    int windowDays = LhScheduleTimeUtil.getScheduleWindowDays(context);
    int dailyCapacity = dto.getDailyCapacity();
    int totalCapacity = dailyCapacity * windowDays * Math.max(1, candidateMachineCount);

    boolean canFinish = surplusQty <= totalCapacity;
    log.debug("SKU收尾预判: sku={}, surplusQty={}, dailyCapacity={}, windowDays={}, "
            + "candidateMachineCount={}, totalCapacity={}, canFinish={}",
            dto.getMaterialCode(), surplusQty, dailyCapacity, windowDays,
            candidateMachineCount, totalCapacity, canFinish);
    return canFinish;
}

/**
 * 估算SKU可用的候选机台数量
 * <p>轻量级估算，不执行完整机台匹配</p>
 */
private int estimateCandidateMachineCount(LhScheduleContext context, SkuScheduleDTO dto) {
    String proSize = dto.getProSize();
    int count = 0;
    for (MachineScheduleDTO machine : context.getMachineList()) {
        if (machine.isUsable() && machine.isInchMatch(proSize)) {
            count++;
        }
    }
    return Math.max(1, count);
}
```

**修改点3.2**：修改 `buildSkuScheduleDTO` 中待排量基线计算

将原来的：

```java
int basePendingQty = Math.max(surplus.getSurplusQty(), Math.max(0, dto.getEmbryoStock()));
```

改为：

```java
int embryoStock = Math.max(0, dto.getEmbryoStock());
int basePendingQty;
if (isSkuCanFinishInWindow(context, plan, dto)) {
    // 收尾场景：允许胎胚库存上调
    basePendingQty = Math.max(surplus.getSurplusQty(), embryoStock);
} else {
    // 非收尾场景：不允许胎胚库存上调，仅取月计划余量
    basePendingQty = surplus.getSurplusQty();
    if (embryoStock > surplus.getSurplusQty()) {
        log.info("SKU非收尾场景跳过胎胚库存上调: sku={}, surplusQty={}, embryoStock={}",
                dto.getMaterialCode(), surplus.getSurplusQty(), embryoStock);
    }
}
```

**注意**：停产模式的特殊处理 `resolveStopProductionDemandQty` 保持不变，停产本身就是收尾场景。

---

### 步骤4：NewSpecProductionStrategy 多机台拆量排产改造

**修改文件**：`aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/NewSpecProductionStrategy.java`

这是本次改造的核心修改点。

**修改点4.1**：修改 `scheduleNewSpecs` 方法中SKU排产循环逻辑

将原来排产成功后 `break` 的逻辑，改为扣减剩余量后继续尝试其他机台。

**当前代码结构**（简化）：

```java
while (iterator.hasNext()) {
    SkuScheduleDTO sku = iterator.next();
    // ... 前置检查 ...
    List<MachineScheduleDTO> candidates = machineMatch.matchMachines(context, sku);
    // ...
    boolean scheduled = false;
    Set<String> excludedMachineCodes = new HashSet<>();
    Integer originalTargetScheduleQty = sku.getTargetScheduleQty();

    while (true) {
        MachineScheduleDTO candidateMachine = selectCandidateMachine(...);
        if (candidateMachine == null) break;
        // ... 换模、首检、产能计算 ...
        sku.setTargetScheduleQty(refinedTargetQty);
        LhScheduleResult result = buildNewSpecScheduleResult(...);
        if (result有效) {
            context.getScheduleResultList().add(result);
            updateMachineState(context, candidateMachine, sku, result);
            scheduled = true;
            iterator.remove();
            break;    ← 【修改点】排产成功立即退出
        } else {
            // 回滚 + excluded
        }
    }
    if (!scheduled) {
        addUnscheduledResult(context, sku, "原因", map);
        iterator.remove();
    }
}
```

**改造后代码结构**：

```java
while (iterator.hasNext()) {
    SkuScheduleDTO sku = iterator.next();
    // ... 前置检查 ...
    List<MachineScheduleDTO> candidates = machineMatch.matchMachines(context, sku);
    // ...
    int remainingQty = sku.resolveTargetScheduleQty();  ← SKU剩余待排量
    int totalScheduledQty = 0;  ← 已排产量累计
    int scheduledMachineCount = 0;  ← 已排机台数量
    Set<String> excludedMachineCodes = new HashSet<>();
    Integer originalTargetScheduleQty = sku.getTargetScheduleQty();

    while (remainingQty > 0) {    ← 【修改点】按剩余量循环
        MachineScheduleDTO candidateMachine = selectCandidateMachine(...);
        if (candidateMachine == null) break;

        // ... 换模、首检计算（同原逻辑） ...

        // 【修改点】按剩余待排量重新设置目标量
        sku.setTargetScheduleQty(remainingQty);

        int refinedTargetQty = getTargetScheduleQtyResolver()
                .refineTargetQtyByMachineCapacity(...);
        if (refinedTargetQty <= 0) {
            // 回滚 + excluded
            excludedMachineCodes.add(machineCode);
            continue;
        }

        LhScheduleResult result = buildNewSpecScheduleResult(...);
        if (result有效) {
            int actualScheduleQty = ShiftFieldUtil.resolveScheduledQty(result);

            context.getScheduleResultList().add(result);
            updateMachineState(context, candidateMachine, sku, result);
            registerMachineAssignment(context, machineCode, result);

            totalScheduledQty += actualScheduleQty;
            remainingQty -= actualScheduleQty;
            scheduledMachineCount++;
            sku.setScheduledMachineCount(scheduledMachineCount);

            log.info("SKU多机台拆量排产: sku={}, machine={}, machineScheduleQty={}, "
                    + "totalScheduledQty={}, remainingQty={}",
                    sku.getMaterialCode(), machineCode, actualScheduleQty,
                    totalScheduledQty, remainingQty);

            // 【修改点】不break，如果还有剩余量，继续尝试下一台机台
            if (remainingQty <= 0) {
                break;
            }
            // 后续机台不需要再分配换模窗口（如果是同一SKU，可以续作排产）
            // 但需要注意：不同机台仍需分配各自的首检窗口
        } else {
            // 回滚 + excluded
            sku.setTargetScheduleQty(originalTargetScheduleQty);
            excludedMachineCodes.add(machineCode);
        }
    }

    // 恢复原始目标量（排产结果已各自保存）
    sku.setTargetScheduleQty(originalTargetScheduleQty);

    if (totalScheduledQty > 0) {
        iterator.remove();
        if (remainingQty > 0) {
            sku.setRemainingUnscheduledQty(remainingQty);
            log.info("SKU多机台排产部分完成: sku={}, totalScheduledQty={}, "
                    + "remainingUnscheduledQty={}",
                    sku.getMaterialCode(), totalScheduledQty, remainingQty);
        }
    } else {
        addUnscheduledResult(context, sku, failReason.getDescription(), map);
        iterator.remove();
    }
}
```

**修改点4.2**：多机台场景下后续机台的换模处理

当SKU在第一台机台排产后继续到第二台机台时，第二台机台需要：
- 如果第二台机台当前模具与SKU模具不同，仍需分配换模窗口
- 如果第二台机台当前模具恰好与SKU模具相同（续作），可以跳过换模

这个逻辑在现有代码中已通过 `mouldChangeBalance.allocateMouldChange()` 处理，无需额外修改。但需要注意：多机台排产时，每台机台都要独立走换模+首检流程。

**修改点4.3**：多机台排产后收尾标记刷新

新增私有方法：

```java
/**
 * 多机台排产后刷新SKU收尾标记
 * <p>基于多机台合计排产量与月计划余量比较</p>
 */
private void refreshMultiMachineEndingFlag(LhScheduleContext context, SkuScheduleDTO sku, int totalScheduledQty) {
    int surplusQty = sku.getSurplusQty();
    // 多机台合计排产量 >= 月计划余量，标记为收尾
    boolean isEnding = totalScheduledQty >= surplusQty;
    if (isEnding) {
        log.info("SKU多机台排产触发收尾标记: sku={}, totalScheduledQty={}, surplusQty={}",
                sku.getMaterialCode(), totalScheduledQty, surplusQty);
    }
    // 刷新排程结果中的收尾标记
    for (LhScheduleResult result : context.getScheduleResultList()) {
        if (sku.getMaterialCode().equals(result.getMaterialCode())) {
            result.setIsEnd(isEnding ? "1" : "0");
        }
    }
}
```

---

### 步骤5：TargetScheduleQtyResolver 适配多机台

**修改文件**：`aps-lh/src/main/java/com/zlt/aps/lh/component/TargetScheduleQtyResolver.java`

**修改点5.1**：`refineTargetQtyByMachineCapacity` 方法需支持剩余待排量

当前方法直接从 `sku.resolveTargetScheduleQty()` 读取目标量。在多机台拆量场景下，调用方会先设置 `sku.setTargetScheduleQty(remainingQty)`，所以方法签名无需修改。

但需要确认：`resolveActualWindowCapacity` 方法计算的是单台机台的实际可用产能，不会因为传入的 `targetScheduleQty` 不同而改变。即该方法是幂等的，只依赖机台和排程窗口状态。

**验证确认**：`resolveActualWindowCapacity` 计算的是机台在剩余窗口内的实际可排产量，与SKU的目标量无关。**无需修改**。

**修改点5.2**：新增多机台合计产能快捷计算方法（供收尾判断调用）

```java
/**
 * 计算SKU在排程窗口内所有候选机台的合计可用产能
 * <p>用于收尾判断和目标量上限计算</p>
 *
 * @param context 排程上下文
 * @param sku SKU排程数据
 * @param candidateMachines 候选机台列表
 * @return 多机台合计可用产能
 */
public int calcSkuTotalAvailableCapacity(LhScheduleContext context,
                                          SkuScheduleDTO sku,
                                          List<MachineScheduleDTO> candidateMachines) {
    if (CollectionUtils.isEmpty(candidateMachines)) {
        return 0;
    }
    int totalCapacity = 0;
    for (MachineScheduleDTO machine : candidateMachines) {
        int machineCapacity = resolveTheoreticalWindowCapacity(context, sku);
        totalCapacity += machineCapacity;
    }
    return totalCapacity;
}
```

---

### 步骤6：ContinuousProductionStrategy 续作场景适配

**修改文件**：`aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/ContinuousProductionStrategy.java`

**修改点6.1**：续作收尾判断也需基于多机台合计产能

在续作排产中，续作机台是固定的（`continuousMachineCode`），但如果续作机台产能不足以完成收尾，理论上也可以拆量到其他机台。

**但考虑到改动量和风险**：续作场景暂时不做多机台拆量，仅调整收尾判断逻辑。

修改 `refreshContinuousEndingFlagByResult` 方法中的收尾判断，使判断考虑多机台合计产能而非单台机台。

**修改点6.2**：`adjustResultByEmbryoStock` 方法中胎胚库存调整

当前逻辑：对所有排程结果（包括非收尾SKU）按胎胚库存削减。

需要调整为：
- 收尾SKU：保持现有逻辑（按胎胚库存削减或上调）
- 非收尾SKU：不允许因胎胚库存不足而削减窗口计划量（但允许因胎胚库存不足而削减超过窗口计划量的部分）

---

### 步骤7：TypeBlockProductionStrategy 换活字块场景适配

**修改文件**：`aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/TypeBlockProductionStrategy.java`

**修改点7.1**：换活字块排产中，如果SKU在换活字块机台上的排产量不足，需要考虑是否拆量到其他可用机台。

**但考虑到改动量和风险**：换活字块场景暂时不做多机台拆量，仅确保换活字块排产结果中收尾标记正确。

---

### 步骤8：日志增强

在以下关键位置增加日志：

```java
// 1. SKU多机台排产计算总览
log.info("SKU多机台排产计算: sku={}, windowPlanQty={}, monthRemainQty={}, "
        + "embryoStockQty={}, totalAvailableCapacity={}, canFinishInWindow={}, targetQty={}",
        skuCode, windowPlanQty, monthRemainQty, embryoStockQty,
        totalAvailableCapacity, canFinishInWindow, targetQty);

// 2. 每台机台分配产量
log.info("SKU多机台拆量: sku={}, machine={}, machineAvailableCapacity={}, "
        + "scheduleQty={}, remainingQty={}",
        skuCode, machineCode, machineAvailableCapacity, scheduleQty, remainingQty);

// 3. 非收尾场景跳过胎胚库存上调
log.info("SKU非收尾场景跳过胎胚库存上调: sku={}, surplusQty={}, embryoStock={}",
        skuCode, surplusQty, embryoStock);

// 4. 多机台合计产能计算
log.info("SKU多机台收尾产能计算: sku={}, candidateMachineCount={}, totalAvailableCapacity={}",
        skuCode, candidateMachineCount, totalAvailableCapacity);
```

---

### 步骤9：单元测试

**新增测试类**：`NewSpecMultiMachineScheduleTest.java`

测试用例覆盖以下场景：

| 测试场景 | 输入 | 期望结果 |
|----------|------|----------|
| 一台机台可以排完 | SKU待排80，机台A产能100 | 只排A，排产量80 |
| 一台排不完需多台 | SKU待排180，A产能100，B产能80 | A排100，B排80 |
| 多台也排不完 | SKU待排300，A产能100，B产能80 | A排100，B排80，剩余120记入未排 |
| 非收尾胎胚库存不上调 | 窗口计划100，胎胚180，月余500 | 目标排产量100 |
| 收尾胎胚库存上调 | 窗口计划100，胎胚180，月余180 | 目标排产量180 |
| 收尾判断考虑多机台 | 月余180，A产能100，B产能100 | 判断为可收尾 |
| 月计划不重复扣减 | SKU排180（A排100+B排80） | 月计划扣减180 |
| 同机台同班次不重复占用 | A机台已被占用50 | A机台剩余产能正确扣减 |

---

## 六、数据处理说明

### 6.1 待排产量初始化

```
窗口计划量 = MonthPlanDayQtyUtil.resolveWindowPlanQty(plan, startDate, endDate)
月计划余量 = monthPlanQty - finishedQty
待排量基线 = 收尾 ? max(月计划余量, 胎胚库存) : 月计划余量
初始待排量 = max(0, 待排量基线 - 继承计划量) + 结转量
```

### 6.2 排产量扣减

```
每次排产成功后：
  remainingQty -= actualScheduleQty
  
最终：
  如果 remainingQty <= 0：SKU全部排完
  如果 remainingQty > 0：部分排完，记录未排量
```

### 6.3 月计划余量扣减

排产过程中的月计划余量通过 `sku.pendingQty` 隐含扣减：
- `sku.pendingQty` 初始值 = 待排量基线 - 继承计划量 + 结转量
- 每次排产成功后，`sku.pendingQty` 不直接扣减（因为 `pendingQty` 是初始待排量）
- 实际扣减通过 `remainingQty` 追踪

排程结果保存时：
- 每条排程结果的 `mouldSurplusQty` 记录当前硫化余量
- 多台机台的排产结果分别保存，各自记录对应的机台、班次、产量

### 6.4 机台产能扣减

```
每台机台排产成功后：
  updateMachineState(context, machine, sku, result)
  → 更新机台的预计结束时间
  → 更新班次产能占用状态
  → 排除其他SKU重复占用同一班次
```

---

## 七、边界场景

### 7.1 一台机台可以排完

SKU待排80，机台A产能100。

- **期望**：只排A，排产量80，不继续占用其他机台
- **验证**：`remainingQty = 80 - 80 = 0`，循环正常退出

### 7.2 一台机台排不完，需要多台

SKU待排180，机台A产能100，机台B产能80。

- **期望**：A排100，B排80，SKU剩余待排为0
- **验证**：第一轮 `remainingQty = 180 - 100 = 80`，第二轮 `remainingQty = 80 - 80 = 0`

### 7.3 多台机台也排不完

SKU待排300，机台A产能100，机台B产能80。

- **期望**：A排100，B排80，剩余120进入未满足量
- **验证**：`remainingQty = 300 - 100 - 80 = 120 > 0`，无更多候选机台，记录未排量

### 7.4 非收尾场景，胎胚库存大于窗口计划量

窗口计划量100，胎胚库存180，月计划余量500，多机台产能300。

- **期望**：目标排产量100，不允许上调到180
- **验证**：`isSkuCanFinishInWindow = false`，`basePendingQty = 500`（取余量），最终目标量受 `windowPlanQty = 100` 限制

### 7.5 收尾场景，胎胚库存大于窗口计划量

窗口计划量100，胎胚库存180，月计划余量180，多机台产能200。

- **期望**：目标排产量上调到180，多机台合计排完180
- **验证**：`isSkuCanFinishInWindow = true`，`basePendingQty = max(180, 180) = 180`

### 7.6 收尾判断必须考虑多机台

月计划余量180，单台机台最大只能排100，两台合计可排200。

- **期望**：判断为可以收尾
- **验证**：`totalAvailableCapacity = 200 >= 180`，`canFinish = true`

### 7.7 同一SKU多机台排产结果的收尾标记

SKU月计划余量180，A排100，B排80。

- **期望**：A和B的排产结果都标记为收尾（因为合计排产180 >= 余量180）
- **验证**：`refreshMultiMachineEndingFlag` 方法统一刷新

### 7.8 停产收尾场景

停产模式下，胎胚库存上调逻辑不受本次改造影响。

- **期望**：停产收尾仍按 `resolveStopProductionDemandQty` 计算待排量
- **验证**：停产模式分支保持原逻辑

### 7.9 续作+新增混合场景

SKU A 续作排产后仍有余量，进入新增排产继续拆量。

- **期望**：续作排产量正确扣减，新增排产从剩余量开始
- **验证**：续作和新增分别排产，`pendingQty` 正确传递

---

## 八、风险点

### 8.1 排产循环从break改为continue的风险

**风险**：原逻辑排产成功即break，改动后继续尝试下一台机台，可能导致：
- 同一SKU占用过多机台资源，影响其他SKU排产
- 机台产能状态在循环中可能不一致

**缓解措施**：
- `remainingQty <= 0` 时立即退出，不会多占机台
- 机台状态在每次排产成功后通过 `updateMachineState` 实时更新
- 排产顺序仍然遵循SKU优先级排序

### 8.2 收尾判断引入机台匹配的风险

**风险**：收尾判断中引入 `machineMatchStrategy.matchMachines()` 可能：
- 改变收尾判断的性能（增加了机台匹配计算）
- 在 `ScheduleAdjustHandler` 阶段机台信息不完整

**缓解措施**：
- `isEnding` 中的 `calcSkuTotalAvailableCapacityInWindow` 做了保底处理（回退到原逻辑）
- `ScheduleAdjustHandler` 中使用轻量级预判方法，不执行完整机台匹配
- 收尾判断结果有日志记录，方便排查

### 8.3 胎胚库存上调限制可能影响现有排产结果

**风险**：非收尾场景不再允许胎胚库存上调，可能导致某些SKU的排产量比原来少。

**缓解措施**：
- 该改动符合需求文档要求
- 影响范围仅限于"非收尾且胎胚库存 > 月计划余量"的SKU
- 增加日志记录跳过上调的场景

### 8.4 多机台排产后收尾标记可能不一致

**风险**：多个机台排产结果中的收尾标记需要统一刷新，否则可能部分标记收尾、部分未标记。

**缓解措施**：
- `refreshMultiMachineEndingFlag` 方法统一刷新同一SKU所有排程结果的收尾标记
- 刷新时机：所有候选机台排产完毕后

### 8.5 换活字块与多机台拆量的交互

**风险**：换活字块排产基于"续作收尾后机台空闲"的前提，多机台拆量可能改变续作收尾时间。

**缓解措施**：
- 第一版暂不改造换活字块的多机台拆量
- 换活字块策略中的收尾标记刷新需适配多机台
- 后续版本再优化换活字块与多机台的交互

---

## 九、验证建议

### 9.1 单元测试验证

执行步骤9中的单元测试，覆盖所有边界场景。

### 9.2 对比验证

在相同排程输入条件下，对比改造前后的排程结果：

1. **不涉及多机台的SKU**：排产结果应完全一致
2. **涉及多机台的SKU**：
   - 改造前：只在一台机台排产，可能有未排量
   - 改造后：多台机台排产，未排量应减少或为0

### 9.3 日志验证

检查以下日志输出：

1. `SKU多机台排产计算` - 确认窗口计划量、月计划余量、胎胚库存、收尾判断正确
2. `SKU多机台拆量` - 确认每台机台分配量正确，剩余量递减
3. `SKU非收尾场景跳过胎胚库存上调` - 确认非收尾场景不触发上调
4. `SKU多机台收尾产能计算` - 确认多机台合计产能计算正确

### 9.4 数据验证

验证以下数据一致性：

1. SKU总排产量 = 各机台排产量之和
2. SKU总排产量 <= 月计划余量
3. SKU总排产量 <= 窗口计划量（非收尾场景）
4. SKU总排产量 <= 多机台可用总产能
5. 每台机台的排产量 <= 该机台可用产能
6. 同一机台同一班次不被多个SKU重复占用
7. 月计划扣减量 = SKU总排产量

---

## 十、实施优先级与排期建议

| 优先级 | 步骤 | 预估工时 | 说明 |
|--------|------|----------|------|
| P0 | 步骤4：NewSpecProductionStrategy 多机台拆量 | 2天 | 核心改动 |
| P0 | 步骤2：DefaultEndingJudgmentStrategy 收尾判断 | 1天 | 核心改动 |
| P0 | 步骤3：ScheduleAdjustHandler 胎胚库存限制 | 0.5天 | 核心改动 |
| P1 | 步骤1：SkuScheduleDTO 新增字段 | 0.5天 | 辅助字段 |
| P1 | 步骤5：TargetScheduleQtyResolver 适配 | 0.5天 | 适配改造 |
| P1 | 步骤8：日志增强 | 0.5天 | 贯穿所有步骤 |
| P1 | 步骤9：单元测试 | 1.5天 | 验证保障 |
| P2 | 步骤6：续作场景适配 | 1天 | 次要优先级 |
| P2 | 步骤7：换活字块场景适配 | 1天 | 次要优先级 |

**总预估工时**：P0 约3.5天，P1 约3天，P2 约2天。建议先完成P0+P1，验证通过后再做P2。

---

## 十一、改动文件清单汇总

| 文件 | 改动类型 | 改动说明 |
|------|----------|----------|
| `SkuScheduleDTO.java` | 新增字段 | 增加 `scheduledMachineCount`、`remainingUnscheduledQty` |
| `DefaultEndingJudgmentStrategy.java` | 修改 | 规则2产能计算改为多机台合计；新增 `calcSkuTotalAvailableCapacityInWindow` 等辅助方法 |
| `ScheduleAdjustHandler.java` | 修改 | 待排量基线计算增加收尾判断条件；新增 `isSkuCanFinishInWindow`、`estimateCandidateMachineCount` 方法 |
| `NewSpecProductionStrategy.java` | 修改 | **核心改动**：排产成功后不再break，改为扣减剩余量继续循环；新增 `refreshMultiMachineEndingFlag` 方法 |
| `TargetScheduleQtyResolver.java` | 新增方法 | 新增 `calcSkuTotalAvailableCapacity` 快捷计算方法 |
| `ContinuousProductionStrategy.java` | 修改 | 收尾标记刷新适配多机台；胎胚库存调整增加收尾条件 |
| `TypeBlockProductionStrategy.java` | 修改 | 收尾标记刷新适配多机台（P2） |
| 单元测试类 | 新增 | `NewSpecMultiMachineScheduleTest.java` |

---

## 十二、不改动清单

以下类/方法经分析确认无需修改：

| 类/方法 | 不改动原因 |
|---------|------------|
| `MonthPlanDayQtyUtil.resolveWindowPlanQty()` | 已按窗口日期范围汇总日计划量，满足需求 |
| `DefaultMachineMatchStrategy.matchMachines()` | 已返回多台候选机台列表，满足需求 |
| `LhScheduleTemplateImpl` | 排程模板流程不变，多机台拆量在策略内部完成 |
| `ShiftCapacityResolverUtil` | 班次产能计算逻辑不变 |
| `SchedulePersistenceService` | 排程结果保存已支持同一SKU多条记录（按机台编码区分） |
| `FactoryMonthPlanProductionFinalResult` | 月计划实体不变 |
