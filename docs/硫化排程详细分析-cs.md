# 硫化排程系统详细分析（架构 / 业务 / 算法）

> 基于仓库源码梳理，入口：`com.zlt.aps.lh.controller.LhScheduleResultController#executeSchedule`（经 `ILhScheduleService` 进入引擎）。  
> 若 `DESIGN.md` 等文档仍写 `LhScheduleController` 或 `/lh/schedule/execute`，以当前代码为准。

---

## 一、代码架构层面

### 1.1 整体结构是否合理

**分层与主链路**清晰，符合常见 APS 服务形态：

- **Controller**：仅日志 + 委托服务，职责薄。

```java
// LhScheduleResultController#executeSchedule
@PostMapping("/execute")
public LhScheduleResponseDTO executeSchedule(@RequestBody LhScheduleRequestDTO request) {
    log.info("收到排程请求, 工厂: {}, 日期: {}",
            request.getFactoryCode(), LhScheduleTimeUtil.formatDate(request.getScheduleDate()));
    return lhScheduleService.executeSchedule(request);
}
```

- **Service（`LhScheduleServiceImpl`）**：构建 `LhScheduleContext`、**分布式执行锁**（`ScheduleExecutionGuard`）、捕获异常并统一 `LhScheduleResponseDTO`。

- **引擎**：
  - `IScheduleExecutor` **装饰器链**（`ScheduleExecutorConfig`：`PerformanceMonitorDecorator` → `LoggingScheduleDecorator` → `DefaultScheduleExecutor`）；
  - **模板方法** `AbsLhScheduleTemplate#execute`（S4.1～S4.6）；
  - `LhScheduleTemplateImpl` 将各步委托给六个 `*Handler`。

该组合（**模板方法 + 分步 Handler + 策略工厂 + 原子持久化**）与硫化「多数据源加载 → 归集 → 续作/新增两阶段排产 → 校验落库」的形态匹配，**架构方向正确**。

### 1.2 耦合度与内聚性

| 方面 | 评价 |
|------|------|
| **Handler 与步骤** | 六步 Handler 内聚在单一流程上，依赖通过 Spring 注入，**内聚较好**。 |
| **`LhScheduleContext`** | 聚合大量 Map/List/DTO，全链路读写，是典型的 **排程总线 / God Context**：实现快、状态跟踪方便，但 **模块间隐式耦合高**（改字段易牵一发而动全身），单测需造大块上下文。 |
| **`ScheduleStrategyFactory`** | `IProductionStrategy` 多实现按 `getStrategyType()` 自注册，**扩展续作/新增实现较好**；机台匹配、换模均衡、首检、产能等仍为 **单 Bean 注入**，换实现需改配置或工厂，**可插拔性弱于排产策略**。 |

### 1.3 架构层面的风险与改进空间

1. **超长策略类**：`ContinuousProductionStrategy` 等单类体量极大（两千行量级），**内聚被「所有续作细节」稀释**，评审与修改成本高。  
   - **建议**：按子域拆包（收尾、换活字块、班次分配、降模、追溯日志等），或引入小型 **领域服务**，策略类只负责编排。

2. **文档与代码不一致**：`DESIGN.md` 中 HTTP 路径、`LhScheduleController` 等与当前 `LhScheduleResultController`、`/lhScheduleResult/execute` 可能不一致，**增加联调与运维成本**。  
   - **建议**：统一文档与 Swagger，必要时旧路径做兼容转发。

3. **`LhScheduleContext` 使用 `@Data`**：对所有字段生成 setter，**不利于不可变边界**（如配置快照、只读基础数据）。长期可考虑只读视图或分阶段上下文。

4. **规范一致性**：`ResultValidationHandler` 等处若仍使用 `SimpleDateFormat`，与「统一 `java.time`/hutool」的常见规范不一致，建议逐步替换。

### 1.4 可扩展性、可配置化、异常处理

- **可配置化**：`LhScheduleConfigResolver` 将 DB 参数 `LhParams` 与 `LhScheduleConstant` 默认合并为 **`LhScheduleConfig` 快照**，并驱动 `scheduleDays`、换模/首检、局部搜索等，**可配置化较好**。

- **异常**：领域错误用 `ScheduleException`，模板与 Service 转为 `fail` DTO；需注意对外信息是否暴露过多内部细节。

- **并发与事务**：`ScheduleExecutionGuard`（Redis SET NX + TTL）与 S4.1/S4.6「已发布不可覆盖」形成多层防护；`SchedulePersistenceService#replaceScheduleAtomically` 使用 **`@Transactional`** 做删旧插新，**事务边界在落库阶段清晰**（长计算在事务外，避免长事务锁表——合理取舍）。

### 1.5 架构优化与重构方案（落地优先级）

1. **P0（低风险）**：修正对外文档与路径；策略大类按包/文件拆分，**行为不变只搬家**。  
2. **P1**：将非 `IProductionStrategy` 的算法策略改为与工厂一致的 **接口 + 条件/命名装配**，便于工厂差异与 A/B。  
3. **P2**：步骤级 **可观测指标**（耗时、SKU 数、未排原因分布），不破坏现有 Handler。  
4. **P2**：对 `LhScheduleContext` 做 **分阶段冻结**（如 S4.2 后基础数据只读），降低误改风险。

---

## 二、业务逻辑层面

### 2.1 核心业务流程（与代码一致）

| 步骤 | 处理器 | 概要 |
|------|--------|------|
| S4.1 | `PreValidationHandler` | MES 已发布则禁重排；排程窗口跨月则拒绝；生成批次号（Redis）。 |
| S4.2 | `DataInitHandler` | 加载月计划、日历、产能、模具、在机、保养等进入 Context。 |
| S4.3 | `ScheduleAdjustHandler` | 前日欠超产调整 → 结构归集 → 收尾标记 → **续作/新增分流**。 |
| S4.4 | `ContinuousProductionHandler` | 续作策略：在机延续、换活字块衔接、班次分配、胎胚、降模等。 |
| S4.5 | `NewProductionHandler` | 新增策略 + 机台匹配 + 换模/首检均衡 + 开产时间 + 分配与降模。 |
| S4.6 | `ResultValidationHandler` | 后置校验、换模计划、工单号、顺序、日志；`replaceScheduleAtomically` 原子替换当日数据并发布完成事件。 |

业务上体现：**目标日 vs 窗口起点 T 日**、**已发布门禁**、**续作与新增分阶段**、**换模/首检资源约束** 等硫化常见关切点。

### 2.2 潜在问题与逻辑张力

1. **「同产品结构直续」在代码中停用**（`ContinuousProductionStrategy` 内相关逻辑被注释，并注明业务要求），与 Handler 注释或历史文档中的「直续优先」可能 **不一致**，需同步需求说明。

2. **跨月窗口直接拒绝**：若现场存在「跨自然月仍属同一计划版本」的场景，规则可能 **过刚**，需与计划域对齐。

3. **「排程结果可为空」**：`postValidation` 中可对空结果仅告警；若业务要求「无有效排程则整体失败或禁止覆盖」，应升级为 **可配置硬失败**。

4. **发布与排程闭环**：`publishSchedule` 与 `isRelease`、S4.1/S4.6 校验需与 MES **撤销发布** 流程严格一致，避免状态不同步。

### 2.3 与真实硫化场景的匹配度

- **匹配较好**：多班时间轴、换模时段、在机延续、月计划与余量、换模/首检均衡、定点机台、清洗/保养窗口等。  
- **需持续校准**：收尾天数、满排模式、试制上限、T-1 在机兜底等 **强依赖参数**；建议配套 **用例库 + 回归测试** 覆盖争议场景。

### 2.4 业务优化建议

1. 批次维度写入 **规则/参数版本号**，便于追溯。  
2. 明确「零结果落库」「部分未排」的 **产品语义**，API 返回结构化未排统计。  
3. 与 MP/MES 对齐 **跨月、版本切换日** 的官方规则。

---

## 三、排程算法层面

### 3.1 设计思路与机制

本系统 **不是** 单一 MILP/CP 求解器路径，而是 **多阶段启发式流水线**：

- **S4.3**：规则 + 数据驱动（欠超产、结构归集、收尾判定、续作/新增分类）。  
- **S4.4 / S4.5**：在机台时间轴、班次容量、模具/停机约束上，按优先级 **贪心放置**，辅以换模、首检等 **均衡策略**；配置中存在 **局部搜索** 相关参数，用于有限改进。

`ScheduleStrategyFactory` 对 **`IProductionStrategy` 多实现自注册** 有利于「续作 vs 新增」算法分叉演进。

### 3.2 效率、准确性与可扩展性

| 维度 | 说明 |
|------|------|
| **效率** | 成本主要在 **多表加载** 与 **SKU × 机台 × 班次** 的循环/排序；策略内层循环可能成为热点，需 profiling。 |
| **准确性（优化意义）** | 启发式 **不保证全局最优**；换模/首检偏 **可行与负载均衡**。若业务要「最小换模」等，需显式 **目标函数与评价指标**。 |
| **可扩展性** | 新规则易塞进策略类，但大类文件膨胀会降低维护性；排产策略扩展性好于部分单例策略 Bean。 |

### 3.3 性能与逻辑风险点

1. **大方法、深分支**：衔接 SKU、兜底机台、时间重叠等交织，边界情况难复现；建议关键路径 **结构化 trace**（与现有 trace 辅助类结合）。  
2. **魔法常量**：如 `TYPE_BLOCK_SWITCH_MAX_ATTEMPTS` 等若写死在策略内，现场调优困难，宜 **下沉 LhParams**。  
3. **数据质量**：在机、月计划、模具关系一处不准，启发式会稳定产出 **错误但自洽** 的方案，需上游质量监控。

### 3.4 算法优化与改进方案

1. **影子排程**：新旧规则双跑对比 KPI（换模次数、未排条数、延迟）。  
2. **两阶段**：先贪心得可行解，再对高代价子集做 **时限内局部搜索**。  
3. **算子配置化**：候选 SKU 排序键、机台遍历序等工厂差异 **少改代码**。  
4. **数据结构**：对热点过滤建立 **按结构/规格/机台时间索引**，减少全量扫描。

---

## 四、小结

| 维度 | 结论 |
|------|------|
| **架构** | 分层清晰，模板方法 + 装饰器 + 原子持久化 + Redis 锁与批次号 **整体合理**；主要技术债：**God Context、超大策略类、部分策略可插拔不一致、文档与路径漂移**。 |
| **业务** | 六步流程与硫化场景 **匹配度高**；关注 **直续停用与文档一致性**、**跨月/零结果语义**、与 MES 发布状态闭环。 |
| **算法** | **启发式多阶段** 适合需求多变现场，全局最优弱；建议加强 **可配置算子、影子对比、观测与回归资产**，并对最大类做 **无损拆分**。 |

---

## 五、主要源码锚点

| 模块 | 类 / 说明 |
|------|-----------|
| HTTP 入口 | `LhScheduleResultController` |
| 服务入口 | `LhScheduleServiceImpl#executeSchedule`、`buildContext` |
| 执行锁 | `ScheduleExecutionGuard` |
| 装饰器链 | `ScheduleExecutorConfig`、`DefaultScheduleExecutor` |
| 模板骨架 | `AbsLhScheduleTemplate`、`LhScheduleTemplateImpl` |
| 六步 Handler | `PreValidationHandler` … `ResultValidationHandler` |
| 原子落库 | `SchedulePersistenceService#replaceScheduleAtomically` |
| 配置解析 | `LhScheduleConfigResolver`、`LhScheduleConfig` |
| 策略工厂 | `ScheduleStrategyFactory` |
| 续作/新增策略 | `ContinuousProductionStrategy`、`NewSpecProductionStrategy`（及 `engine/strategy` 下其它实现） |

---

*文档版本：与仓库分析同步；可按迭代更新「五、锚点」与风险清单。*
