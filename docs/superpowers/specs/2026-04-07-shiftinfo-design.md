# ShiftInfo 扩展与班次运行态分离 — 设计说明

**日期**：2026-04-07  
**状态**：已定稿（待实现计划）  
**范围**：`aps-lh-api` 中 `ShiftInfo`、`LhScheduleContext` 及 `LhScheduleTimeUtil` 等与班次相关的引擎侧契约。

---

## 1. 目标与非目标

### 1.1 目标

- 在**班次定义**对象 `ShiftInfo` 上补充：班次名称、是否跨自然日/月/年、相对排程 T 日的**日期偏移**、班次编码、班次时长（分钟），以及**不含时间部分的业务日**（通过既有 `**workDate`** 表达，不新增 `shiftDate` 字段）。
- **剩余产能**、**是否可用**及**不可用原因**与定义分离，放在上下文中**显式类型**承载（见第 3 节），符合方案 B。
- 跨日/跨月/跨年边界按**时区**计算：优先使用上下文中的时区（若将来扩展）；**当前**未提供时使用 `ZoneId.systemDefault()`（已确认）。

### 1.2 非目标

- 不在本规格中规定前端展示文案的最终字表（`shiftName` 生成规则在实现中固定即可，可后续再抽配置）。
- 不强制在本期重构 `LhScheduleContext` 上既有 Lombok `@Data` 用法。

---

## 2. `ShiftInfo`（仅定义侧，保持不可变）

### 2.1 保留字段

- `shiftIndex`、`shiftType`、`workDate`、`startTime`、`endTime` — 语义与现网一致；`workDate` 表示**该班次归属的业务日**，**仅使用其日期部分**（时间部分在构造时规范为当日 0 点或项目既有约定，实现阶段与 `LhScheduleTimeUtil` 现有 `buildTime` 用法对齐并统一文档）。

### 2.2 新增字段


| 字段                   | 类型        | 说明                                                                 |
| -------------------- | --------- | ------------------------------------------------------------------ |
| `shiftName`          | `String`  | 展示用名称（如与 T/T+1/T+2 及 `ShiftEnum` 描述组合）。                            |
| `dateOffset`         | `int`     | 相对排程窗口 **T 日**（`scheduleDate`）的日历偏移：`0` = T 日，`1` = T+1，`2` = T+2。 |
| `shiftCode`          | `String`  | 冗余 `ShiftEnum` 的编码，便于序列化与外部系统对齐。                                   |
| `durationMinutes`    | `int`     | 班次时长（分钟），与上下文中的班次时长配置一致（如由小时数 × 60 得到）。                            |
| `crossesCalendarDay` | `boolean` | `startTime` 与 `endTime` 是否落在不同**自然日**（在选定 `ZoneId` 下）。             |
| `crossesMonth`       | `boolean` | 是否跨越不同**自然月**。                                                     |
| `crossesYear`        | `boolean` | 是否跨越不同**自然年**。                                                     |


### 2.3 构造与兼容

- 通过扩展构造函数或工厂方法，由 `**LhScheduleTimeUtil.getScheduleShifts`** 在生成 8 个班次时一次性写入上述字段。
- `toString` 与必要 getter 同步更新；**不**在 `ShiftInfo` 上增加剩余产能、可用性、原因等可变或运行态字段。

### 2.4 序列化

- `ShiftInfo` 实现 `java.io.Serializable`（与项目 Java 规范中对实体/DTO 的要求一致），`serialVersionUID` 按模块惯例赋值。

---

## 3. 运行态：`ShiftRuntimeState` + 上下文中的显式集合

### 3.1 类型

- 新增类型（建议类名 `**ShiftRuntimeState`**，包路径与 `ShiftInfo` 同级或 `dto` 子包，实现阶段按模块习惯定稿）。
- 建议字段：


| 字段                  | 类型           | 说明                                                                                 |
| ------------------- | ------------ | ---------------------------------------------------------------------------------- |
| `shiftIndex`        | `int`        | 与 `ShiftInfo.shiftIndex` 一致，主关联键。                                                  |
| `remainingCapacity` | `int`        | **剩余产能**；与现有策略中按班次秒数换算的整型产能一致；若后续与 SKU 维度 `BigDecimal` 统一，可在实现计划中调整为 `BigDecimal`。 |
| `available`         | `boolean`    | **班次是否可用**。                                                                        |
| `unavailableReason` | `String`（可空） | **不可用原因**；可用时为空或 null。                                                             |


- 该类实现 `Serializable`。

### 3.2 在 `LhScheduleContext` 中的存放形式

- 采用 **显式值类型** 的 `**Map<Integer, ShiftRuntimeState>`**，key = `shiftIndex`（1–8），**禁止**使用无业务含义的 `Map<String, Object>` 等裸结构。
- 初始容量建议 `8`，在生成班次列表后初始化 8 条记录（默认值：可用、剩余产能按现有逻辑初始化）。

### 3.3 与 `LhScheduleResult` 的关系

- 排程结束后，若结果对象需要对外暴露「班次级运行态」，可将同一结构**拷贝或引用汇总**到结果侧，避免过程态与结果态长期双源；具体写入点在实现计划中明确（本规格只要求语义一致）。

---

## 4. `LhScheduleTimeUtil` 职责

- 在 `getScheduleShifts` 中：根据 `scheduleDate`（T）、各班次 `startTime`/`endTime` 计算并填充 `dateOffset`、`crossesCalendarDay`、`crossesMonth`、`crossesYear`、`durationMinutes`、`shiftCode`、`shiftName`。
- **时区**：计算自然日/月/年时，使用 `ZoneId.systemDefault()`；若 `LhScheduleContext` 后续增加 `ZoneId` 字段，则优先使用上下文时区（本规格已预留）。

---

## 5. 影响面与验证

### 5.1 代码影响

- 策略类（如 `ContinuousProductionStrategy`、`NewSpecProductionStrategy`）中：只读 `ShiftInfo` 新字段一般向后兼容；**产能与可用性**读写迁移到 `LhScheduleContext` 的 `Map<Integer, ShiftRuntimeState>`。
- 所有 `new ShiftInfo(...)` 调用点需改为新签名或由工具类统一构造。

### 5.2 测试建议

- 针对 `getScheduleShifts`：普通工作日、月末、年末、跨年夜班等场景，断言 `dateOffset` 与跨日/月/年标志。
- 针对运行态：初始化默认值、策略内更新后 key 与 `shiftIndex` 一致。

---

## 6. 决策摘要（回顾）


| 议题               | 结论                                                        |
| ---------------- | --------------------------------------------------------- |
| 定义 vs 运行态        | 方案 B：`ShiftInfo` 仅定义；产能/可用/原因在 `ShiftRuntimeState`。       |
| 是否单独 `shiftDate` | 否；仅扩展 `**workDate` + `dateOffset`**。                      |
| 额外定义字段           | 包含 `**shiftCode`、`durationMinutes**`。                     |
| 不可用原因            | `**unavailableReason` 需要**。                               |
| 时区               | 同意：**先 `systemDefault()`，上下文有时区则优先上下文**。                  |
| 上下文承载方式          | **显式类型** + `**Map<Integer, ShiftRuntimeState>`**（非裸 Map）。 |


---

## 7. 后续步骤

- 用户审阅本规格文件并确认无修改后，使用 **writing-plans** 技能产出实现计划（含类路径、结果对象是否落库等）。

