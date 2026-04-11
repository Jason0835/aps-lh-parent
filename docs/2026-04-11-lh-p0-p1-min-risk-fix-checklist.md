# 硫化排程 `P0/P1` 最小风险修复清单

> 基于你提供的深度分析报告，以及当前仓库中的实际实现整理。目标不是“顺手重构一遍”，而是先把会直接造成错排、重叠、清空数据、坏数据落库的高风险点收住。

## 1. 本轮范围

**只做 `P0/P1`，不做 `P2`：**

- 做：执行互斥、原子落库、新增规格时间轴闭环、机台状态补齐、欠产传导、收尾/优先级修正、结果校验加严。
- 不做：规则引擎统一、性能优化、`MachineIndexManager` 接入、反射热点治理、API 契约重构。

**最小风险原则：**

- 保留现有 `Controller -> Service -> Executor -> Template -> Handler` 主链，不做大拆。
- 优先把“危险动作后移”。尤其是删旧数据，必须从 `S4.1` 挪到最终原子持久化阶段。
- 优先补状态闭环，不先追求算法“更聪明”。
- 每个修复都要绑定回归用例，先锁住行为，再改逻辑。

## 2. 实施顺序

| 批次 | 优先级 | 目标 | 为什么先做 |
|------|--------|------|------------|
| A | `P0` | 执行互斥 + 原子替换落库 | 这是当前最容易把线上数据清空或写半套的风险 |
| B | `P0` | 新增规格时间轴闭环 + 机台占用推进 | 这是当前最容易产出重叠排程和错时序计划的风险 |
| C | `P1` | 机台状态初始化补齐 | 很多规则“看起来有，实际上没生效” |
| D | `P1` | 欠产/收尾/优先级/结果校验修正 | 收敛结果可信度，避免脏数据继续落库 |
| T | 配套 | 回归测试补齐 | 没测试的话，后面每一项都会反复回踩 |

## 3. 批次 A：执行互斥与原子替换落库

### A1. 用真实执行令牌替换 `checkScheduleInProgress`

**现状问题**

- `PreValidationHandler#checkScheduleInProgress` 判断的是新建 `context` 里的 `batchNo`，天然为空，无法挡住同工厂同目标日的重复提交。

**最小改法**

- 新增 `ScheduleExecutionGuard` 组件。
- 锁键建议使用 `factoryCode + scheduleTargetDate`，例如 `APS:LH:SCHEDULE:LOCK:{factory}:{yyyyMMdd}`。
- 在 `LhScheduleServiceImpl#executeSchedule` 进入执行器之前尝试加锁，失败直接返回“排程执行中”。
- 用 `try/finally` 释放锁，不依赖 Handler 内部状态。

**建议改动点**

- `aps-lh/src/main/java/com/zlt/aps/lh/service/impl/LhScheduleServiceImpl.java`
- 新增 `aps-lh/src/main/java/com/zlt/aps/lh/component/ScheduleExecutionGuard.java`
- `aps-lh/src/main/java/com/zlt/aps/lh/handler/PreValidationHandler.java`

**验收标准**

- 同工厂、同目标日的并发执行只能成功一个。
- 同工厂、不同目标日互不影响。
- 失败或异常后锁可以正常释放。

### A2. 把删旧数据从 `S4.1` 挪到最终持久化事务里

**现状问题**

- 现在 `PreValidationHandler#cleanHistoryData` 在排程前就删旧数据。
- 后续任何一步失败，目标日会变成“无数据”或“半套数据”。
- `AbsScheduleStepHandler` 目前会吞掉异常并 `interruptSchedule`，这也不利于事务回滚。

**最小改法**

- `S4.1` 只做校验和批次号生成，不再删库。
- 新增 `SchedulePersistenceService#replaceScheduleAtomically(...)`。
- 在一个明确的 `@Transactional` 方法中完成：
  1. 再次校验 MES 未发布。
  2. 删除目标日旧 `schedule_result / unscheduled_result / mould_change_plan / process_log`。
  3. 批量写入本次新结果。
- `ResultValidationHandler` 不直接操作多个 Mapper，而是委托给持久化服务。

**建议改动点**

- `aps-lh/src/main/java/com/zlt/aps/lh/handler/PreValidationHandler.java`
- `aps-lh/src/main/java/com/zlt/aps/lh/handler/ResultValidationHandler.java`
- 新增 `aps-lh/src/main/java/com/zlt/aps/lh/service/impl/SchedulePersistenceService.java`

**验收标准**

- 任意保存失败都不会影响旧批次数据。
- 只有当整批新结果成功写入后，目标日数据才被替换。
- `publishSchedule` 的读取口径不受影响。

### A3. 保留“业务中断”语义，但不要再让持久化异常静默降级

**现状问题**

- `AbsScheduleStepHandler` 抓住异常后只改 `context`，会掩盖真正的持久化失败。

**最小改法**

- 计算阶段仍可用 `interruptSchedule` 表达可恢复中断。
- 持久化阶段出现异常时直接抛出，让模板返回失败，并触发事务回滚。
- 简单说：算法规则失败可以“中断”，数据库替换失败必须“报错”。

**建议改动点**

- `aps-lh/src/main/java/com/zlt/aps/lh/handler/AbsScheduleStepHandler.java`
- `aps-lh/src/main/java/com/zlt/aps/lh/handler/ResultValidationHandler.java`

**验收标准**

- 模拟 `insertBatch` 异常时，最终响应为失败，且旧数据完整保留。

## 4. 批次 B：新增规格时间轴闭环

### B1. 把“机台准备就绪时间”从“开产时间”里拆出来

**现状问题**

- `NewSpecProductionStrategy` 先算了 `mouldChangeCompleteTime` 和 `inspectionTime`，但 `calculateStartTime(...)` 又从旧的 `endingTime` 重新推了一遍，导致首检时间被丢掉，时间链断开。

**最小改法**

- 把时间轴拆成 5 个明确节点：
  1. `machineReadyTime`
  2. `mouldChangeStartTime / mouldChangeEndTime`
  3. `inspectionStartTime / inspectionEndTime`
  4. `productionStartTime`
  5. `specEndTime`
- `machineReadyTime` 只负责表达“机台从什么时候开始可以排换模/首检”，不再混入首检完成语义。
- `productionStartTime` 必须等于 `inspectionEndTime`，不能再回退到旧 `endingTime`。

**建议改动点**

- `aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/NewSpecProductionStrategy.java`
- `aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/DefaultCapacityCalculateStrategy.java`

**验收标准**

- 同一条新增规格计划上，换模、首检、开产时间单调递增。
- 若首检被顺延到次日早班，开产时间必须跟着顺延。

### B2. 新增规格排产后必须回写机台状态

**现状问题**

- 新增规格排完后只把结果塞进 `machineAssignmentMap`，没有推进 `MachineScheduleDTO` 的主状态。
- 下一轮候选机台排序仍可能把同一台机当成“空闲且旧状态”再次选中。

**最小改法**

- 新增规格成功落到机台后，至少同步更新：
  - `currentMaterialCode`
  - `currentMaterialDesc`
  - `previousSpecCode`
  - `previousProSize`
  - `estimatedEndTime`
- `estimatedEndTime` 以本次 `result.specEndTime` 为准。
- `ResultValidationHandler` 生成换模计划时，读取的也应该是“更新后的机台状态”，不是初始化时的旧状态。

**建议改动点**

- `aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/NewSpecProductionStrategy.java`
- `aps-lh/src/main/java/com/zlt/aps/lh/handler/ResultValidationHandler.java`

**验收标准**

- 同一台机连续分配两个新增规格时，不会出现时间重叠。
- 后一个 SKU 的候选排序能看到前一个 SKU 回写后的前规格信息。

### B3. 补齐新增规格结果里的关键时间/模具字段

**现状问题**

- 新增规格结果缺 `specEndTime`，换模计划依赖的 `mouldCode` 也没有稳定赋值。

**最小改法**

- `buildNewSpecScheduleResult(...)` 中显式计算并写入：
  - `specEndTime`
  - `tdaySpecEndTime`
  - `mouldCode`
- `mouldCode` 直接来源于 `skuMouldRelMap`，多模具用逗号拼接。

**建议改动点**

- `aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/NewSpecProductionStrategy.java`

**验收标准**

- 所有 `scheduleType = 02` 的结果都有 `specEndTime`。
- 所有 `isChangeMould = 1` 的结果都有 `mouldCode`。

## 5. 批次 C：机台状态初始化补齐

### C1. 用在机信息 + 物料主数据补齐前规格画像

**现状问题**

- `MachineScheduleDTO` 里定义了 `currentMaterialDesc / previousSpecCode / previousProSize`，但 `DataInitHandler` 只填了 `currentMaterialCode`。

**最小改法**

- 用 `machineOnlineInfoMap` 填 `currentMaterialCode / currentMaterialDesc`。
- 再用 `materialInfoMap` 反查当前在机物料，填：
  - `previousSpecCode`
  - `previousProSize`
- 这样 `DefaultMachineMatchStrategy` 里的“同规格优先 / 相近英寸优先”才能真正生效。

**建议改动点**

- `aps-lh/src/main/java/com/zlt/aps/lh/handler/DataInitHandler.java`

**验收标准**

- 同规格机台优先级排序不再依赖空字段。
- 寸口接近排序能真实生效。

### C2. 把清洗、保养、维修计划真正灌入机台状态

**现状问题**

- `cleaningPlanTime / maintenancePlanTime / repairPlanTime` 都在 DTO 里，但当前几乎没有初始化。
- `DefaultCapacityCalculateStrategy` 已经依赖这些字段，导致规则名义上存在、实际上不生效。

**最小改法**

- `cleaningPlanList` 写入：
  - `hasDryIceCleaning`
  - `hasSandBlastCleaning`
  - `cleaningPlanTime`
- `maintenancePlanMap` 解析 `operTime`，写入：
  - `hasMaintenancePlan`
  - `maintenancePlanTime`
- `devicePlanShutList` 区分维修类停机，写入：
  - `hasRepairPlan`
  - `repairPlanTime`

**建议改动点**

- `aps-lh/src/main/java/com/zlt/aps/lh/handler/DataInitHandler.java`
- 如需时间解析，补充 `aps-lh/src/main/java/com/zlt/aps/lh/util/LhScheduleTimeUtil.java`

**验收标准**

- 有保养/维修/清洗计划的机台，开产时间会被顺延。
- 无计划的机台不受影响。

### C3. 初始化 `estimatedEndTime`

**现状问题**

- `DefaultMachineMatchStrategy` 和 `NewSpecProductionStrategy` 都依赖 `estimatedEndTime`，但初始化阶段没有稳定赋值。

**最小改法**

- 先用前日排程结果中该机台最后一个有计划的 `specEndTime`。
- 若前日没有结果，再退化到在机默认基线时间。

**建议改动点**

- `aps-lh/src/main/java/com/zlt/aps/lh/handler/DataInitHandler.java`

**验收标准**

- 候选机台排序不再大量出现 `estimatedEndTime = null`。

## 6. 批次 D：欠产传导、收尾/优先级、结果校验

### D1. 欠产调整不要再改“昨天的 planQty”，而要改“今天的待排量”

**现状问题**

- 现在 `ScheduleAdjustHandler#adjustPreviousSchedule` 改的是前日结果上的班次计划量。
- 后续算余量时却只累计完成量，导致欠产补差根本不会传导到今天的待排量。
- `shiftFinishQtyMap` 已加载却未使用。

**最小改法**

- 保留前日结果只读。
- 新增 `carryForwardQty` 或直接调整 `SkuScheduleDTO.pendingQty / surplusQty`。
- 用 `shiftFinishQtyMap` 或前日结果的完成量，计算“欠产补差 / 超产冲抵”的净值，再回写到当天需求对象。

**建议改动点**

- `aps-lh/src/main/java/com/zlt/aps/lh/handler/ScheduleAdjustHandler.java`

**验收标准**

- 夜班欠产会真实增加当天待排量。
- 超产会真实冲抵当天待排量。

### D2. 修正收尾天数和优先级输入字段

**现状问题**

- `calculateEndingDays` 公式有偏移，`3` 个班被算成 `2` 天。
- `delayDays`、`embryoStock` 当前没有稳定赋值，但排序/削减逻辑又依赖它们。

**最小改法**

- `calculateEndingDays` 改成真正的向上取整。
- 若当前轮拿不到可靠 `delayDays / embryoStock` 数据，就不要继续用默认 `0` 假装参与高优先级规则。
- 最小可行方案是：
  - 补齐可可靠推导的字段；
  - 对暂时无法可靠推导的字段降级为“不参与排序/调整”，而不是伪数据。

**建议改动点**

- `aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/DefaultEndingJudgmentStrategy.java`
- `aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/DefaultSkuPriorityStrategy.java`
- `aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/NewSpecProductionStrategy.java`
- `aps-lh/src/main/java/com/zlt/aps/lh/handler/ScheduleAdjustHandler.java`

**验收标准**

- `3` 个班次 = `1` 天收尾。
- 依赖缺失字段的排序分支不再悄悄误导结果。

### D3. 把结果校验从“记日志”升级为“阻断坏数据落库”

**现状问题**

- 当前缺字段多数只是 `warn`，不会阻止落库。

**最小改法**

- 对以下字段改成强校验：
  - `batchNo`
  - `factoryCode`
  - `lhMachineCode`
  - `materialCode`
  - `scheduleType`
  - `specEndTime`
  - `mouldCode`（当 `isChangeMould = 1` 时）
- 一旦缺失，直接中断持久化，不进入原子替换事务。

**建议改动点**

- `aps-lh/src/main/java/com/zlt/aps/lh/handler/ResultValidationHandler.java`

**验收标准**

- 构造缺 `specEndTime` 或 `mouldCode` 的结果时，最终响应失败，且数据库旧数据不受影响。

## 7. 配套测试清单

| 测试名称 | 覆盖目标 |
|----------|----------|
| `ExecuteScheduleConcurrencyGuardTest` | 同工厂同目标日并发只能执行一个 |
| `SchedulePersistenceRollbackTest` | 保存失败时旧数据不被清空 |
| `NewSpecTimelineRegressionTest` | 换模、首检、开产、结束时间链连续 |
| `MachineOccupationRegressionTest` | 同机连续分配不重叠 |
| `MachineStateInitRegressionTest` | 前规格/英寸/保养/维修/清洗字段被正确初始化 |
| `ScheduleAdjustCarryForwardRegressionTest` | 欠产补差、超产冲抵真实进入当天待排量 |
| `EndingDaysRegressionTest` | `1/2/3/4` 个班次的收尾天数边界正确 |
| `ResultValidationBlockingTest` | 关键字段缺失时阻断落库 |

## 8. 建议的落地节奏

| 天数 | 交付内容 |
|------|----------|
| Day 1 | 先补测试基线 + 执行互斥 |
| Day 2 | 原子替换落库 |
| Day 3 | 新增规格时间轴闭环 |
| Day 4 | 机台状态初始化补齐 |
| Day 5 | 欠产/收尾/优先级/结果校验修正 |

## 9. 本轮明确不碰的内容

- 不统一 `RuleEngine / LhParams / 常量` 三套配置来源。
- 不改 `MachineIndexManager`、不做候选机台索引优化。
- 不改 `ShiftFieldUtil` 反射模型。
- 不做 API 字段语义整改。

这样做的好处是：先把最危险的结果一致性和时间轴正确性收住，再为下一轮内核重构留出稳定基线。
