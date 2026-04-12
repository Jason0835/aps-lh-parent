基于入口 [LhScheduleResultController.java#L39](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/controller/LhScheduleResultController.java#L39) 沿主链路走读后，我的结论是：

这个项目“外层架构意图是对的”，但“排程内核还没有真正闭环”。它现在更像一个带模板、策略、装饰器外壳的贪心排程脚本，而不是一个可验证、可回放、可扩展的硫化排程引擎。

**关键问题**

1. `P0` 重排流程没有事务，也没有真正的并发互斥。`checkScheduleInProgress` 只判断新建 `context` 里的 `batchNo`，天然永远为空，所以挡不住重复提交；同时旧数据在 S4.1 就被删了，后续任一步失败都会留下“目标日数据被清空或部分写入”的状态。证据见 [PreValidationHandler.java#L51](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/handler/PreValidationHandler.java#L51)、[PreValidationHandler.java#L91](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/handler/PreValidationHandler.java#L91)、[PreValidationHandler.java#L107](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/handler/PreValidationHandler.java#L107)、[ResultValidationHandler.java#L207](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/handler/ResultValidationHandler.java#L207)、[AbsScheduleStepHandler.java#L21](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/handler/AbsScheduleStepHandler.java#L21)。

2. `P0` 新增规格排产没有把“换模 -> 首检 -> 开产 -> 机台占用”串成同一条时间轴。代码先算了 `mouldChangeCompleteTime` 和 `inspectionTime`，但真正开产时间却又拿旧的 `endingTime` 去算，`inspectionTime` 被直接丢掉；排完后也没有推进机台状态，所以同一台机有机会被连续选中并产生重叠计划。证据见 [NewSpecProductionStrategy.java#L136](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/NewSpecProductionStrategy.java#L136)、[NewSpecProductionStrategy.java#L152](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/NewSpecProductionStrategy.java#L152)、[NewSpecProductionStrategy.java#L160](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/NewSpecProductionStrategy.java#L160)、[NewSpecProductionStrategy.java#L165](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/NewSpecProductionStrategy.java#L165)、[DefaultCapacityCalculateStrategy.java#L44](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/DefaultCapacityCalculateStrategy.java#L44)、[DefaultMachineMatchStrategy.java#L61](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/DefaultMachineMatchStrategy.java#L61)。

3. `P1` 机台状态对象定义得很完整，但初始化只填了一小部分，导致很多规则名义上存在、实际上不生效。`previousSpecCode`、`previousProSize`、`currentMaterialDesc`、`maintenancePlanTime`、`repairPlanTime`、`cleaningPlanTime` 等关键字段都没被灌进去，直接让“同规格优先”“相近英寸优先”“保养/维修顺延”“交替计划前规格信息”失真。证据见 [MachineScheduleDTO.java#L36](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh-api/src/main/java/com/zlt/aps/lh/api/domain/dto/MachineScheduleDTO.java#L36)、[MachineScheduleDTO.java#L68](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh-api/src/main/java/com/zlt/aps/lh/api/domain/dto/MachineScheduleDTO.java#L68)、[DataInitHandler.java#L150](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/handler/DataInitHandler.java#L150)、[DefaultMachineMatchStrategy.java#L225](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/DefaultMachineMatchStrategy.java#L225)、[DefaultCapacityCalculateStrategy.java#L100](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/DefaultCapacityCalculateStrategy.java#L100)、[ResultValidationHandler.java#L145](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/handler/ResultValidationHandler.java#L145)。

4. `P1` S4.3 的“欠产调整”基本没有业务效果，而且“超产冲抵”只写在注释里没有实现。代码改的是前日结果的 `planQty`，后面算余量时却只累计 `finishQty`，所以欠产补差不会传导到当前排程；同时专门加载的 `shiftFinishQtyMap` 也完全没被用上。证据见 [ScheduleAdjustHandler.java#L72](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/handler/ScheduleAdjustHandler.java#L72)、[ScheduleAdjustHandler.java#L161](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/handler/ScheduleAdjustHandler.java#L161)、[LhBaseDataServiceImpl.java#L462](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/service/impl/LhBaseDataServiceImpl.java#L462)。

5. `P1` 收尾和优先级里有直接的逻辑错误或死分支。`calculateEndingDays` 存在明显 `+1` 偏移，3 个班会被算成 2 天；优先级排序依赖 `delayDays`，但 DTO 构建时从未赋值；胎胚库存调整依赖 `embryoStock`，同样没赋值。证据见 [DefaultEndingJudgmentStrategy.java#L71](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/DefaultEndingJudgmentStrategy.java#L71)、[DefaultSkuPriorityStrategy.java#L78](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/DefaultSkuPriorityStrategy.java#L78)、[ScheduleAdjustHandler.java#L210](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/handler/ScheduleAdjustHandler.java#L210)、[NewSpecProductionStrategy.java#L75](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/NewSpecProductionStrategy.java#L75)。

6. `P1` 结果校验太弱，坏数据会被带着落库。缺少关键字段只打日志不阻断；而 `mouldCode` 在生成交替计划时被依赖，但主排程路径里我没有看到任何赋值；新增规格结果也没有补 `specEndTime`，后续无法可靠做串行机台排产。证据见 [ResultValidationHandler.java#L77](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/handler/ResultValidationHandler.java#L77)、[ResultValidationHandler.java#L129](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/handler/ResultValidationHandler.java#L129)、[NewSpecProductionStrategy.java#L182](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/NewSpecProductionStrategy.java#L182)、[ContinuousProductionStrategy.java#L285](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/ContinuousProductionStrategy.java#L285)。

7. `P2` 规则配置体系是分裂的，不是真正的“可配置化”。入口先用一个已标 `@Deprecated` 的规则引擎算 T 日，到了 S4.2 又重新按 `LhParams` 算一次；而多个策略仍直接读取常量。这样需求一变，往往要同时改规则引擎、工具类、策略实现。证据见 [LhScheduleServiceImpl.java#L82](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/service/impl/LhScheduleServiceImpl.java#L82)、[DataInitHandler.java#L120](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/handler/DataInitHandler.java#L120)、[DatabaseScheduleRuleEngine.java#L24](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/engine/rule/impl/DatabaseScheduleRuleEngine.java#L24)、[DefaultCapacityCalculateStrategy.java#L34](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/DefaultCapacityCalculateStrategy.java#L34)、[DefaultProductionShutdownStrategy.java#L50](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/DefaultProductionShutdownStrategy.java#L50)。

8. `P2` API 契约和实际行为也有漂移。请求里有 `monthPlanVersion`、`productionVersion`、`operator`，但入口并不把它们放进上下文，`productionVersion` 还会被 S4.2 强制替换成“当前定稿版本”，这会让调用方以为自己在控版本，实际没有。证据见 [LhScheduleRequestDTO.java#L19](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh-api/src/main/java/com/zlt/aps/lh/api/domain/dto/LhScheduleRequestDTO.java#L19)、[LhScheduleServiceImpl.java#L82](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/service/impl/LhScheduleServiceImpl.java#L82)、[LhBaseDataServiceImpl.java#L277](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/service/impl/LhBaseDataServiceImpl.java#L277)。

**架构层面**
1. 优点是明显的。`Controller -> Service -> Executor -> Template -> Handler` 这条主链很清楚，控制层也足够薄，[AbsLhScheduleTemplate.java#L33](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/engine/template/AbsLhScheduleTemplate.java#L33) 的六步法适合表达硫化排程这种强流程型业务。

2. 真正的问题是耦合点不在“调用链”，而在 [LhScheduleContext.java#L37](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/context/LhScheduleContext.java#L37)。这个 `Context` 已经变成了巨型共享可变对象，基础数据、中间态、结果、控制位全部放一起，导致每个 Handler/Strategy 都能偷偷影响后续步骤，测试和变更成本都会越来越高。

3. `BaseDataService` 负责 16+ 张表的装载，`ScheduleStrategyFactory` 又注册了不少主链路没真正接通的策略，这说明项目已经开始出现“模式很多，但有效边界不清晰”的迹象。后续需求频繁变更时，很容易变成改一条规则要摸 5 个类。

4. 异常处理不够工程化。当前主要是“吞异常 -> 写 context 中断 -> 返回失败 DTO”，没有统一事务边界、没有全局异常语义、没有补偿机制，这对排程这种高一致性业务是不够的。

**业务逻辑层面**
1. 业务主流程我理解为：目标日请求 -> 回推 T 日 -> 加载版本/计划/日历/在机/模具/停机等基础数据 -> 做余量与收尾判断 -> 续作优先 -> 新增补位 -> 生成排程结果与换模计划 -> 发布。这个流程方向没问题。

2. 但和真实硫化场景相比，当前代码最大的偏差在“机台生命周期没建起来”。真实场景里，一台机在某个时点只能处于“继续当前规格”“换模中”“首检中”“新规格生产中”“保养/维修/清洗中”之一；现在代码更多是按 SKU 贪心地找一台“看起来能上”的机，而不是维护一条严谨的机台时间线。

3. 欠产/超产处理、胎胚库存、试制量试、停开产比例这些都是典型硫化业务规则，但当前很多规则只是定义了接口或注释，没真正接入主调度链。业务人员会以为“系统考虑了”，实际结果里并没有体现。

4. 交替计划的业务完整性不足。前规格、模具号、更换时间这些字段理论上是交替计划的核心，但当前来源不稳定，意味着 MES 或执行层拿到计划后，仍可能需要人工补充。

**排程算法层面**
1. 当前算法本质上是“规则驱动的顺序贪心”。优点是实现简单、容易追日志。缺点是局部选择过强、全局最优能力弱，而且对状态正确性极度敏感。

2. 准确性问题比性能问题更严重。现在最影响排程可信度的，不是 O(n*m) 的候选机台匹配，而是时间链断裂、机台状态不闭环、结果字段不完整。

3. 性能上也有明显改进空间。`DefaultMachineMatchStrategy` 对每个 SKU 线性扫所有机台，而 [MachineIndexManager.java#L33](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/component/MachineIndexManager.java#L33) 实际没接入；[ShiftFieldUtil.java#L30](/Users/Jason/IdeaProjects/test/aps-lh-parent/aps-lh/src/main/java/com/zlt/aps/lh/util/ShiftFieldUtil.java#L30) 在热路径里大量用反射读写 `class1~class8`，规模一上来会拖慢排程。

4. 可扩展性目前偏弱。因为算法状态散在 `Context`、`MachineScheduleDTO`、`LhScheduleResult` 和多个策略里，新增一个规则常常不是“加一个评分器/约束器”，而是“在好几个地方补 if”。

**建议的重构方向**
1. 先做稳态修复。给 `executeSchedule` 和 `publishSchedule` 建立明确的事务边界；用 `factoryCode + targetDate` 做分布式锁或唯一执行令牌；把“删旧数据”和“写新数据”放进同一事务。

2. 重建领域内核。把当前 `LhScheduleContext` 拆成 `ScheduleInput`、`BaseDataSnapshot`、`MachinePlanState`、`ScheduleResultAggregate` 四类对象，减少跨步骤隐式耦合。

3. 统一规则入口。保留一个 `RuleProvider` 即可，不要再出现“有规则引擎、又有 context 参数、又有硬编码常量”三套来源。

4. 重写新增规格排产的时间轴。必须让 `换模完成时间 -> 首检完成时间 -> 开产时间 -> 规格结束时间 -> 机台下一可用时间` 成为一条连续链，并在每次分配后回写机台状态。

5. 把业务规则从“注释和预留接口”收束成“主链上可见的约束组件”。优先补齐欠产/超产、胎胚库存、保养/维修、试制量试四块。

6. 增强测试。至少补 6 类回归：重复提交、删除后失败回滚、同机重叠排程、欠产补差生效、收尾天数边界、交替计划关键字段完整性。

**验证**
1. 我跑了 `mvn -pl aps-lh test -q`，当前测试是通过的。
2. 但现有测试主要覆盖 T 日窗口和少量回归，没覆盖上面这些高风险主逻辑，所以“测试通过”不代表排程内核已经可靠。

如果你愿意，我下一步可以直接继续做两件事里的一个：
1. 先出一版“最小风险修复清单”，只修 `P0/P1`。
2. 直接给你画一版新的硫化排程内核设计，把 `Context/Handler/Strategy` 重构成真正可扩展的引擎。



