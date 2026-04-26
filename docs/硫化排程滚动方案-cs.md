# 硫化排程滚动方案（落地方案 · 小改动优先）

> 目标：在**不重写排程主链**（`Controller → Service → Template → Handlers`）的前提下，把**滚动多目标日排程**在「产品、数据、对账、可选轻量实现」上落到**可执行、可验收**的状态。  
> 范围：`aps-lh` 与现有 MES/MP 主数据消费方式；**默认 3 日窗（1+2，8 班）** 与 `SCHEDULE_DAYS` 配置一致。  
> 与前置分析关系：本文件不重复长代码解析，**操作项**可对照 [硫化排程详细分析-cs.md](./硫化排程详细分析-cs.md) 与仓库内《executeSchedule 调用链》类文档。

---

## 1. 现状与滚动语义（执行层共识）

在落地前，项目组需统一 **三条口径**（与 [LhScheduleServiceImpl](../aps-lh/src/main/java/com/zlt/aps/lh/service/impl/LhScheduleServiceImpl.java) / [SchedulePersistenceService](../aps-lh/src/main/java/com/zlt/aps/lh/service/impl/SchedulePersistenceService.java) 一致）：

| 概念 | 含义 | 对滚动的影响 |
|------|------|--------------|
| **排程目标日** | 请求体中的「排程日期」= `scheduleTargetDate` | 每次排程的**主键日**、落库**整批替换**的维度 |
| **T 日** | `T = 目标日 − (SCHEDULE_DAYS − 1)` | 8 个班次时间轴的**窗起点** |
| **前日**（欠产用） | `目标日 − 1` 的日历日 | 取 `T_LH_SCHEDULE_RESULT` 的 `SCHEDULE_DATE=目标日-1` + 同日的日完成量做 **carryForward**；**不**以「T 日」为前日 |

**滚动两次举例**（`SCHEDULE_DAYS=3`）：

- 第一次：目标 2026-04-25 → 窗 04-23 ~ 04-25；落库行 `SCHEDULE_DATE=2026-04-25`（整批快照）。
- 第二次：目标 2026-04-26 → 窗 04-24 ~ 04-26；落库行 `SCHEDULE_DATE=2026-04-26`；**衔接**上一天（04-25）的**计划与实绩**算欠产，并**不修改** 04-25 已存批次。

**核心设计事实（避免误解）**：

- **不是**在数据库里对「重叠自然日」做行级 **PATCH**；每目标日都是**全窗重算**后的新快照。
- **机台延续**主依赖 **MES 在机**（[ScheduleAdjustHandler#classifyContinuousAndNewSkus](../aps-lh/src/main/java/com/zlt/aps/lh/handler/ScheduleAdjustHandler.java)），不以上一轮排程结果为唯一机台真值。
- **量纲衔接**主依赖：**月计划日窗口 + 余量 + 前日欠产（carryForward）**（[ScheduleAdjustHandler](../aps-lh/src/main/java/com/zlt/aps/lh/handler/ScheduleAdjustHandler.java) + [LhBaseDataServiceImpl#loadPreviousScheduleResults](../aps-lh/src/main/java/com/zlt/aps/lh/service/impl/LhBaseDataServiceImpl.java)）。

---

## 2. 落地方向总览（小改动分层）

| 层次 | 内容 | 改动量 | 是否必选 |
|------|------|--------|----------|
| L1 契约与流程 | 对计划员、MES 使用哪套快照、已发布/未发布 | 无代码或仅文档/提示语 | **必选** |
| L2 可观测与追溯 | 日志/汇总信息中带「窗 [T,目标]」、批次、前日欠产摘要 | 小 | **强建议** |
| L3 可选表结构/字段 | 在**批次或汇总表**上记录 `windowStartDate`/`windowEndDate`/`paramVersion` | 小（需 DBA/评审） | 按需 |
| L4 对账与告警 | 离线与可选接口：相邻目标日窗口交集的「粗对比」、欠产/余量与 MP 对账 | 中 | 按需 |
| 排除 | 全窗冻结+增量重排、全局重优化、大改 `LhScheduleContext` 持久化 | 大 | 本阶段**明确不做** |

以下按 **L1 → L2 → L3** 给可执行任务；L4 单独成条供排期。

---

## 3. 阶段一（L1）：产品契约与 MES/现场规则 —— 0～极少代码

### 3.1 需书面固定的规则（建议 1 页《滚动排程使用说明》）

- **多快照并存时以谁为准**  
  - 方案 A（推荐、与现实现最一致）：**以「已发布 MES 的目标日」对应批次为执行真值**；未发布前允许多个目标日快照供 APS 内查看。  
  - 方案 B：只认「**目标日最大**且已发布」的一条（需 MES/报表统一过滤，**不改引擎**即可流程解决）。
- **预排 2 天的使用边界**：明确 T+1/T+2 为**预排、可大调**；T+0 为**主执行日**（可结合现发布校验 [PreValidationHandler#checkMesReleaseStatus](../aps-lh/src/main/java/com/zlt/aps/lh/handler/PreValidationHandler.java) 已有「已发布则禁止重排该目标日」）。
- **前日欠产**依赖：**日完成量**必须按约定时点（如夜班结束后）**落库/同步**到 [LhBaseDataServiceImpl#loadDayFinishQty](../aps-lh/src/main/java/com/zlt/aps/lh/service/impl/LhBaseDataServiceImpl.java) 所用源；**否则 carryForward 失真**。

### 3.2 接口/字段说明补全（对外部集成方）

- 在《接口说明》中写明：`scheduleDate` 入参 = **排程目标日**；**不是** 8 窗台起点 T。  
- 出参/列表页：对每条结果可展示 `batchNo` + 业务解释「目标日 D，规划窗 [T, D]」**无需改表也可先写在说明里**（见 L2）。

**验收**：计划员、MES 接口人签字确认**看数规则**与**排程/发布节奏**；无生产事故类歧义单。

---

## 4. 阶段二（L2）：小改动可观测性 —— 低侵入改代码

> 目标：滚动排产时，从日志/过程日志**一眼能还原**「本轮窗、前日、欠产是否进模型」，**不改算法**。

### 4.1 在排程**开始与结束**打结构化信息（建议）

- **实现位置**（二选一或都做，均为小改）：  
  - [LhScheduleServiceImpl#executeSchedule](../aps-lh/src/main/java/com/zlt/aps/lh/service/impl/LhScheduleServiceImpl.java)：`buildContext` 之后**一条 INFO**，包含 `factoryCode`、`scheduleTargetDate`、`scheduleDate`（T）、`scheduleDays`、`batchNo`（在生成批次后于后续步骤补打亦可）。  
  - 或 [AbsLhScheduleTemplate](../aps-lh/src/main/java/com/zlt/aps/lh/engine/template/AbsLhScheduleTemplate.java) / 首个 Handler 后：同字段。
- [ScheduleAdjustHandler#doHandle](../aps-lh/src/main/java/com/zlt/aps/lh/handler/ScheduleAdjustHandler.java) 在 `adjustPreviousSchedule` 之后**一条汇总 INFO**：`previousScheduleResultCount`（可为 0）、`carryForwardSkuCount`（`carryForwardQtyMap` 非 0 且纳入 pending 的物料数）、避免打印全量 SKU（敏感与体量）。

**验收**：在测试环境用「连续两个目标日」各跑一次，**日志**可复现与本文第 1 节表一致；无需翻库即可回答「第二次是否吃到了 04-25 的欠产」。

### 4.2 复用/扩展 [LhScheduleProcessLogMapper](../aps-lh/src/main/java/com/zlt/aps/lh/mapper/LhScheduleProcessLogMapper.java) 与过程日志表已有能力（若表支持扩展字段/摘要）

- 在「汇总日志」中写入一行可读摘要：`T=yyyy-MM-dd,D=yyyy-MM-dd,prevDayLoad=n,carryM=n`（具体字段以现有表结构为准，**无新表则仅 JSON 摘要**）。

**验收**：按 `batchNo` 可追溯本轮窗口与欠产条数。

---

## 5. 阶段三（L3）：可选的「窗元数据」落库（小表或少量字段）— 需评审后做

> **仅在**业务强依赖报表「按自然日看规划」**且**需区分多快照时启用；**否则 L1+L2 足够**。

**原则**：优先 **批次/汇总** 加字段，**不**在 `T_LH_SCHEDULE_RESULT` 行级全表加冗余（除非有强报表需求且评估过）。

- **候选 A**：`T_LH_SCHEDULE_PROCESS` 或现有批次汇总表（若有）增加：  
  - `WINDOW_START`（= T 日 0 点）  
  - `WINDOW_END`（= 目标日 0 点或**末班次结束日**由业务定）  
  - `SCHEDULE_PARAM_HASH` 或 `PARAM_VERSION`（可选，抄 `LhParams` 关键键值快照）
- **候选 B**：不扩表，只在 **L2 的 JSON 摘要**中持久化同样信息。

**改码锚点**：[ResultValidationHandler#addSummaryLog](../aps-lh/src/main/java/com/zlt/aps/lh/handler/ResultValidationHandler.java) 或 [SchedulePersistenceService](../aps-lh/src/main/java/com/zlt/aps/lh/service/impl/SchedulePersistenceService.java) 事务内写汇总。

**验收**：报表能按 `batchNo` 过滤出「窗 [T, D]」；与日志双源校验一致。

---

## 6. 阶段四（L4，按需）：对账与轻微告警 —— 不阻塞上线

- **离线条目**：  
  - 取相邻两个目标日 D、D+1 的两套结果，在**时间轴重叠的班次/日期**上对比计划量**粗差**（阈值可配置），输出 Excel/看板供计划员，**不自动回写**。
- **与 MP**：月累计完成、日完成与 [ScheduleAdjustHandler](../aps-lh/src/main/java/com/zlt/aps/lh/handler/ScheduleAdjustHandler.java) 中 `windowPlanQty`、`surplus` 定期抽检（可 SQL + 小工具）。

**说明**：此项**不改变**排产引擎，**风险最低**，适合作为滚动稳定后的**运营增强**。

---

## 7. 明确不纳入本落地方案的范围

- **修改 `replaceScheduleAtomically` 的删除维度**（例如从「按目标日」改为按「T」或按行），以免破坏**已发布**校验与 MES 对接。
- **按重叠自然日 UPDATE 行级**合并两次快照，避免与**换模/在机/首检**时序强耦合、回归爆炸。
- **全量「独立余量重排、丢弃 carryForward/在机」**（与现场欠产/连续性冲突；若未来要做，应另立项并配数据模拟）。

---

## 8. 与核心类的对照清单（供任务拆分）

| 主题 | 主类/位置 |
|------|------------|
| 目标日 / T 日 | [LhScheduleServiceImpl#buildContext](../aps-lh/src/main/java/com/zlt/aps/lh/service/impl/LhScheduleServiceImpl.java) |
| 前日结果、日完成、月数据 | [LhBaseDataServiceImpl#loadAllBaseData](../aps-lh/src/main/java/com/zlt/aps/lh/service/impl/LhBaseDataServiceImpl.java) |
| 欠产、窗口计划、续作/新增 | [ScheduleAdjustHandler](../aps-lh/src/main/java/com/zlt/aps/lh/handler/ScheduleAdjustHandler.java) |
| 8 班时间窗 | [LhScheduleTimeUtil](../aps-lh/src/main/java/com/zlt/aps/lh/util/LhScheduleTimeUtil.java) |
| 同目标日原子替换 | [SchedulePersistenceService#replaceScheduleAtomically](../aps-lh/src/main/java/com/zlt/aps/lh/service/impl/SchedulePersistenceService.java) |
| 发布门禁 | [PreValidationHandler#checkMesReleaseStatus](../aps-lh/src/main/java/com/zlt/aps/lh/handler/PreValidationHandler.java) |
| 同厂同目标日互斥 | [ScheduleExecutionGuard](../aps-lh/src/main/java/com/zlt/aps/lh/component/ScheduleExecutionGuard.java) |
| 欠产回归 | [ScheduleAdjustCarryForwardRegressionTest](../aps-lh/src/test/com/zlt/aps/lh/regression/ScheduleAdjustCarryForwardRegressionTest.java) |

**路径说明**：上表路径相对于本文所在目录 `docs/`，`../aps-lh/` 为 `aps-lh` 模块根目录下源码与测试。

---

## 9. 回归与上线检查表（必做项）

- [ ] 同目标日：已发布 MES 时，再次排程**被拒绝**（与现逻辑一致）。  
- [ ] 连续目标日：D、D+1 各排一次，确认 **D 的批次**不被 **D+1 落库**删除；仅 **D+1** 替换自身目标日。  
- [ ] **前日无结果**时：不报错，欠产为 0或按业务预期（可空转）。  
- [ ] 日志/过程日志中可看到 T、D、**carryForward 摘要**（完成 L2 后）。  
- [ ] 跨月：若仍拒绝（现 `checkCrossMonthWindow`），**计划侧**不安排跨月窗或另立项改规则。  

---

## 10. 排期与角色建议

| 阶段 | 工期（参考） | 角色 |
|------|----------------|------|
| L1 | 0.5~1 天 | 产品/计划/ IT + MES 代表 |
| L2 | 0.5~2 天 | 后端 1 人，含自测与日志评审 |
| L3 | 2~5 天 | 后端 + DBA，含脚本与回滚 |
| L4 | 视需求 | 数据/运维或后端 |

**结论**：在坚持**衔接式（欠产 + 在机 + 月计划重算）** 的前提下，**最小可落地**路径是 **L1 契约 + L2 可观测**；L3 仅在**报表/审计强需求**时上；大改**不作为**本滚动方案的必要组成。

---

*文档版本：与仓库实现同步，修订时请同步更新第 1 节「现状」中涉及的类名与 `SCHEDULE_DAYS` 配置说明。*
