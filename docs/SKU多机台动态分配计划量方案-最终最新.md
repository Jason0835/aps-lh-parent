# SKU多机台动态分配计划量方案-最终最新

## 1. 明确结论

本次按最小改动量在 S4.5 新增排产 `NewSpecProductionStrategy` 内完成动态拆量，不改 S4.4 续作主流程，不新增表，不新增 XML。

新增 SKU 在非严格目标场景下，会基于 day1/day2/day3 日计划账本、当前机台逐班次可排产能、窗口剩余目标量，提前判断是否需要扩到多台机台；收尾和试制等严格目标场景继续保持“不补满、不超排”的原语义。

## 2. 改造目标

解决新增排产 SKU 多机台场景下“先排满一台，再补下一台”导致 dayN 节奏失真的问题。文档案例已通过回归测试锁定：

- `K1105 = 80`
- `K1110 = 78`
- 窗口合计 `158`
- 第三天 `C6` 晚班分配为 `K1105=16、K1110=14`
- 目标已闭合后不再强行挪到 `K1105 C7`

## 3. 现有逻辑影响点

- 新增排产入口：`NewProductionHandler#doHandle`
- 核心策略：`NewSpecProductionStrategy#scheduleNewSpecs`
- 机台生产段：`MachineProductionSegment`
- 数量策略：`ProductionQuantityPolicy`
- 日计划账本：`SkuDailyPlanQuotaDTO`、`SkuDailyPlanQuotaUtil`
- 配置快照：`LhScheduleParamConstant`、`LhScheduleConstant`、`LhScheduleConfigResolver`、`LhScheduleConfig`
- SQL 初始化：`rule_engine_init.sql`
- 迁移脚本：`migrate_new_spec_shortage_look_ahead_days_param.sql`

## 4. 设计思路

保留现有候选机台排序、换模分配、首检分配、清洗/维保扣产、日计划账本回裁、机台状态更新和结果来源绑定。

在单台候选机台生成结果前，先计算该机台从开产到窗口结束的逐班次最大可排产能，形成 `shiftCapacityMap`。对于非严格目标且存在多候选机台的新增 SKU，再结合日计划账本判断：

- 当前机台窗口总能力不足时，提前扩机台；
- 当前机台总能力足够但 lookAhead 范围内 dayN 欠产无法消化时，提前扩机台；
- 需要扩机台时，按预计机台数给当前机台分配一段计划量，给后续机台保留尾量；
- 最后一台只闭合剩余目标量，不再为了补满班次把 `78` 扩成 `80`。

## 5. 参数说明

新增硫化参数：

- 参数编码：`SYS0304015`
- 参数名称：`新增排产欠产追补判断天数`
- 默认值：`2`
- 作用：当前天发生欠产后，额外向后观察 `2` 天，判断后续产能能否消化当前欠产，用于决定当前天是否需要提前扩机台。

该参数已接入 Java 常量、配置解析、配置快照 getter、初始化 SQL 和迁移 SQL。

## 6. 数据处理说明

- 日计划额度仍使用 `SkuDailyPlanQuotaDTO.dailyPlanQuotaMap`。
- 预估阶段只读取日计划账本和机台班次能力，不提前扣减账本。
- 只有生成 `LhScheduleResult` 后，才通过 `applyBlockToDailyQuota(...)` 消费账本。
- 换模、首检、同胎胚换模占用仍沿用现有失败回滚链路。
- 结果仍通过 `scheduleResultSourceSkuMap` 绑定来源 SKU，保证 S4.6 后置校验可见。

## 7. 边界场景

- 单机台窗口和追补能力都满足时，不扩机台。
- 单机台窗口总能力不足时，扩到后续候选机台。
- 单机台总能力足够但 lookAhead 内欠产无法消化时，提前扩机台。
- 收尾 SKU 继续严格按 `max(余量, 胎胚库存)` 截断。
- 试制 SKU 继续严格按日计划目标截断。
- 正式/量试非收尾最后一台只闭合剩余目标量，不额外跨班次放大目标。

## 8. 验证结果

已通过定向回归：

```bash
/Users/Jason/Software/apache-maven-3.9.1/bin/mvn -pl aps-lh -am -Dtest=NewSpecProductionStrategyRegressionTest#scheduleNewSpecs_shouldAllocateDocumentCaseByDailyCapacityAcrossMachines,LhScheduleConfigTest#shouldReadNewSpecShortageLookAheadDaysConfig -DfailIfNoTests=false test
```

结果：`Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`。

## 9. 后续验证建议

建议启动应用后对排程日期 `2026-05-03` 发起真实排程，再按最新 `batchNo` 查询 `T_LH_SCHEDULE_RESULT` 和过程日志，重点核对 `3302001724` 的机台、`CLASS1_PLAN_QTY ~ CLASS8_PLAN_QTY`、`DAILY_PLAN_QTY`、`SPEC_END_TIME`、`IS_END`。
