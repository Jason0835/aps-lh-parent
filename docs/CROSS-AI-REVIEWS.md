---
phase: adhoc
topic: 夜班提前收尾后下一 SKU 无换模（夜班凌晨换模顺延修复）
reviewers: [codex, claude]
reviewed_at: 2026-04-24T11:20:00Z
plans_reviewed: [夜班凌晨换模顺延修复 — Cursor plan / 仓库实现核对]
note: 本仓库无 GSD `.planning/` 与 `ROADMAP.md`；未执行 `gsd-tools commit`。Gemini CLI 未安装，未参与评审。
---

# Cross-AI Plan Review — 夜班凌晨换模顺延修复（Adhoc）

## Gemini Review

未执行：本机 `gemini` CLI 不可用（`command -v gemini` → missing）。

---

## Codex Review

### Summary

基于当前仓库代码核对，这份方案整体方向是对的，而且实现方式比较克制：根因定位准确，修复点集中，`DefaultMouldChangeBalanceStrategy` 与 `ContinuousProductionStrategy` 也都已经切到统一时间工具，能直接覆盖「夜班跨日 00:00–05:59 不应再顺延到后一天早班」的默认场景。问题在于，这份方案对「配置化边界」的收口还不完整，且验证深度仍停留在策略级回归测试，没有把「真实业务链路下下一 SKU 是否补出换模/换活字块」做成端到端证明，所以离「可放心上线」还差最后一层验证。

### Strengths

- 根因判断具体且可验证，和当前代码改动一致：禁止换模窗口退出逻辑已从「一律 `clearTime + 1 day`」改成「晚间次日早班、凌晨当日早班」两段语义。
- 修复范围控制得比较好，没有扩散到无关策略；核心落在公共时间工具与直接消费者上，属于典型「最小改动量」方案。
- 方案把两个语义刻意拆开：`禁止换模窗口结束后的早班` 与 `中班额度满后的日历次日早班` 不是一回事，区分必要且正确。
- 已补回归测试覆盖核心反例与对照：`02:00 -> 当日 06:00`、`21:00 -> 次日 06:00`。
- 没有明显性能和安全问题；改动为常量时间判断。

### Concerns

- **HIGH**：`isNoMouldChangeTime` 与新 util 使用 `MORNING_START_HOUR` 作为禁换模结束边界，而配置中存在 `NO_MOULD_CHANGE_END_HOUR` / `getNoMouldChangeEndHour()`；若未来「禁换模结束 ≠ 早班开始」，实现会与业务语义偏离。
- **MEDIUM**：测试只证明 `DefaultMouldChangeBalanceStrategy` 的时间分配，未证明端到端「夜班提前收尾后下一 SKU 在早/中班补出切换」；换活字块/衔接链路缺少对应回归。
- **MEDIUM**：边界样例不完整（如 `05:59:59` / `06:00:00` / `19:59:59` / `20:00:00`），早满→中、中满→日历次日早班等组合断言不足。
- **LOW**：手工验证步骤可更具体（排程日、机台、SKU、校验字段）。

### Suggestions

- 明确配置口径：禁换模结束是否永远等于早班开始；否则应让 `isNoMouldChangeTime` 与新 util 使用 `NO_MOULD_CHANGE_END_HOUR`。
- 补参数化/边界回归与一条更贴业务的 `ContinuousProductionStrategy` 衔接用例。
- 手工验收单：固定 `scheduleDate`、K2024、`3302002531`→`3302001884`，核对切换时间落在同日早班而非再顺延一天。

### Risk Assessment

**MEDIUM** — 默认参数下修复大概率命中当前缺陷且改动面小；配置边界与端到端验证未收口，残余风险主要在非默认参数与全链路证明。

（Codex 说明：只读评审环境未实际执行 Maven/测试命令。）

---

## Claude Review

### 1. Summary

The plan correctly identifies a real bug — `clearTime + 1 day` unconditionally skipped the same-calendar-day morning for post-midnight night-shift segments — and the fix is surgical and well-targeted: a single utility method with a clear hour-based branching rule, wired into the mould balance strategy alongside a retained `getNextCalendarDayMorningStart` for the afternoon-quota-exhausted path. The implementation matches the plan, the semantic distinction between the two "next morning" methods is documented, and regression tests cover the two critical branches. A few edge cases around non-standard config and the interaction loop merit attention.

### 2. Strengths

- Root cause diagnosis is precise; the blanket `clearTime + 1 day` rule is identified as the exact cause.
- Single-responsibility utility with clear JavaDoc (evening → next day, early morning → same day).
- Two-method semantic split is correct and should not be merged.
- Deduplication: `ContinuousProductionStrategy` delegates to shared util.
- `DefaultFirstInspectionBalanceStrategy` JavaDoc prevents future confusion.
- Loop guard `MAX_ALLOCATION_ATTEMPTS = 16` prevents infinite loops in degenerate cases.
- Regression tests cover both branches (02:00 / 21:00).
- Null safety on the new util.

### 3. Concerns

- **MEDIUM** — Config edge case: if `morningStartHour >= noMouldChangeStartHour`, the loop could spin until `MAX_ALLOCATION_ATTEMPTS`; loop guard saves it but no explicit validation rejects invalid config. Unlikely with default 6/20.
- **MEDIUM** — Minute-level boundary hours (e.g. 19:59 vs 20:00) untested though hour logic is consistent.
- **LOW** — No test combining downtime ending at ~03:00 in no-mould window with same-day morning allocation (closest to original field scenario).
- **LOW** — `null` return on exhaustion is pre-existing pattern for callers.

### 4. Suggestions

- Add boundary-hour tests (20:00:00, 05:59:59).
- Add downtime + early-morning combined test (e.g. downtime ends 03:30 → same-day 06:00).
- Consider logging or validation when `morningStartHour >= noMouldChangeStartHour`.
- Parameterize regression tests; consider Maven-standard `src/test/java` if applicable.

### 5. Risk Assessment: **LOW**

Narrow fix, correct root cause, two-path design matches business semantics. Residual risk mainly invalid config ordering; default/reasonable configs are safe. Supplemental boundary tests would reduce risk further.

---

## Consensus Summary

两台外部评审均认为：**根因与「禁止换模后第一个早班」的日期口径修复方向正确**，拆分为「禁止窗口顺延 util」与「日历次日早班（配额用尽）」是必要的。

### Agreed Strengths

- 根因明确（凌晨段多跳一天）。
- 公共工具方法 + 续作/换模策略共用，降低分叉。
- 两类「下一早班」语义分离正确。
- 已有 02:00 / 21:00 对照回归有价值。

### Agreed Concerns（按优先级）

1. **配置与实现一致性**：`NO_MOULD_CHANGE_END_HOUR` 与仅用 `morningHour` 判断禁止时段的关系未在方案/实现中完全收口（Codex 标 HIGH，Claude 标 MEDIUM，均建议明确业务约束或改实现）。
2. **验证深度**：策略单测充分，**缺少端到端**（现场 K2024 / SKU 链）或 `ContinuousProductionStrategy` 衔接级断言（双方一致）。
3. **边界与组合**：整点外边界、停机结束落入凌晨禁止段等用例仍可加强（双方一致）。

### Divergent Views

- **整体风险**：Codex 给出 **MEDIUM**（偏配置与非默认场景）；Claude 给出 **LOW**（认为默认与合理配置下回归面小）。建议在是否强依赖「禁换模结束 = 早班开始」上拍板后统一风险评级。

---

## 后续（GSD 流程）

若项目在其它根目录已初始化 GSD，可在该目录执行：

```bash
/gsd:plan-phase <N> --reviews
```

并将本文件路径或内容提供给规划步骤。本仓库若要纳入 GSD，需先初始化 `.planning/` 与阶段目录后再运行 `gsd-tools init phase-op <N>` 生成标准 `{padded_phase}-REVIEWS.md` 路径。
