# ShiftInfo 扩展与班次运行态 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `ShiftInfo` 上补齐班次定义字段（名称、偏移、编码、时长、跨日/月/年），引入 `ShiftRuntimeState` 与 `LhScheduleContext.shiftRuntimeStateMap`，并在工具类与数据初始化、排产分配链路中落地；规格见 `docs/superpowers/specs/2026-04-07-shiftinfo-design.md`。

**Architecture:** `aps-lh-api` 新增/修改 DTO 与上下文字段；`LhScheduleTimeUtil` 集中构造 `ShiftInfo`（含 `ZoneId.systemDefault()` 下的跨日/月/年计算与 `workDate` 日期归零）；S4.2 在参数加载后初始化 `Map<Integer, ShiftRuntimeState>`；`ContinuousProductionStrategy` 与 `NewSpecProductionStrategy` 的 `distributeToShifts` 在每次分班写入后同步更新对应班次的 `remainingCapacity`。本期**不**扩展 `LhScheduleResult` 持久化字段；若对外需班次运行态，后续单独开需求。

**Tech Stack:** Java 8、Maven、`aps-lh-api` / `aps-lh`、JUnit 5（`spring-boot-starter-test`）、`java.time`（`ZoneId`、`LocalDate`、`Instant`）、Lombok 仅用于 `LhScheduleContext` 既有 `@Data`；新增 `ShiftRuntimeState` 使用手写 getter/setter（符合项目规范）。

---

## 文件结构（创建 / 修改）

| 路径 | 职责 |
|------|------|
| `aps-lh-api/.../dto/ShiftRuntimeState.java` | 新建：班次运行态（可变） |
| `aps-lh-api/.../dto/ShiftInfo.java` | 修改：新增定义字段、`Serializable`、扩展构造 |
| `aps-lh-api/.../context/LhScheduleContext.java` | 修改：新增 `shiftRuntimeStateMap` |
| `aps-lh/.../util/LhScheduleTimeUtil.java` | 修改：`getScheduleShifts`、日期截断、跨边界、`initShiftRuntimeStateMap` |
| `aps-lh/.../handler/DataInitHandler.java` | 修改：S4.2 末尾初始化班次运行态 Map |
| `aps-lh/.../strategy/impl/ContinuousProductionStrategy.java` | 修改：`distributeToShifts` 传入 `LhScheduleContext` 并更新 Map |
| `aps-lh/.../strategy/impl/NewSpecProductionStrategy.java` | 同上 |
| `aps-lh/src/test/.../util/LhScheduleTimeUtilShiftInfoTest.java` | 新建：班次字段与跨日断言 |
| `aps-lh/src/test/.../dto/ShiftRuntimeStateTest.java` | 新建：运行态默认值单测（可选，可与上一文件合并） |

---

### Task 1: 新建 `ShiftRuntimeState`（含单测）

**Files:**
- Create: `aps-lh-api/src/main/java/com/zlt/aps/lh/api/domain/dto/ShiftRuntimeState.java`
- Test: `aps-lh/src/test/com/zlt/aps/lh/dto/ShiftRuntimeStateTest.java`

- [ ] **Step 1: 编写失败单测**

```java
package com.zlt.aps.lh.dto;

import com.zlt.aps.lh.api.domain.dto.ShiftRuntimeState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShiftRuntimeStateTest {

    @Test
    void 新建实例_默认字段可写() {
        ShiftRuntimeState s = new ShiftRuntimeState();
        s.setShiftIndex(3);
        s.setRemainingCapacity(100);
        s.setAvailable(false);
        s.setUnavailableReason("停机");
        assertEquals(3, s.getShiftIndex());
        assertEquals(100, s.getRemainingCapacity());
        assertFalse(s.isAvailable());
        assertEquals("停机", s.getUnavailableReason());
    }
}
```

- [ ] **Step 2: 运行单测确认失败**

Run: `cd /Users/Jason/IdeaProjects/test/aps-lh-parent && mvn -pl aps-lh -am test -Dtest=ShiftRuntimeStateTest`

Expected: 编译失败（类不存在）。

- [ ] **Step 3: 实现类**

```java
package com.zlt.aps.lh.api.domain.dto;

import java.io.Serializable;

/**
 * 班次运行态（剩余产能、可用性等），与 {@link ShiftInfo} 定义分离。
 */
public class ShiftRuntimeState implements Serializable {

    private static final long serialVersionUID = 1L;

    private int shiftIndex;
    private int remainingCapacity;
    private boolean available;
    private String unavailableReason;

    public int getShiftIndex() {
        return shiftIndex;
    }

    public void setShiftIndex(int shiftIndex) {
        this.shiftIndex = shiftIndex;
    }

    public int getRemainingCapacity() {
        return remainingCapacity;
    }

    public void setRemainingCapacity(int remainingCapacity) {
        this.remainingCapacity = remainingCapacity;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public String getUnavailableReason() {
        return unavailableReason;
    }

    public void setUnavailableReason(String unavailableReason) {
        this.unavailableReason = unavailableReason;
    }
}
```

- [ ] **Step 4: 运行单测确认通过**

Run: `mvn -pl aps-lh -am test -Dtest=ShiftRuntimeStateTest`

Expected: `BUILD SUCCESS`，测试通过。

- [ ] **Step 5: Commit**

```bash
git add aps-lh-api/src/main/java/com/zlt/aps/lh/api/domain/dto/ShiftRuntimeState.java \
        aps-lh/src/test/com/zlt/aps/lh/dto/ShiftRuntimeStateTest.java
git commit -m "feat(api): 新增 ShiftRuntimeState 班次运行态 DTO"
```

---

### Task 2: 扩展 `ShiftInfo` 并实现 `Serializable`

**Files:**
- Modify: `aps-lh-api/src/main/java/com/zlt/aps/lh/api/domain/dto/ShiftInfo.java`

- [ ] **Step 1: 将 `ShiftInfo` 改为实现 `Serializable`，新增常量与字段**

在类声明处增加 `implements Serializable`，增加 `serialVersionUID = 1L`。在原有 5 个字段基础上增加（全部 `private final`）：

- `String shiftName`
- `int dateOffset`
- `String shiftCode`
- `int durationMinutes`
- `boolean crossesCalendarDay`
- `boolean crossesMonth`
- `boolean crossesYear`

新增完整构造函数（单一入口，避免多处 `new` 参数不一致）：

```java
public ShiftInfo(int shiftIndex, ShiftEnum shiftType, Date workDate,
                   Date startTime, Date endTime,
                   String shiftName, int dateOffset, String shiftCode, int durationMinutes,
                   boolean crossesCalendarDay, boolean crossesMonth, boolean crossesYear) {
    this.shiftIndex = shiftIndex;
    this.shiftType = shiftType;
    this.workDate = workDate;
    this.startTime = startTime;
    this.endTime = endTime;
    this.shiftName = shiftName;
    this.dateOffset = dateOffset;
    this.shiftCode = shiftCode;
    this.durationMinutes = durationMinutes;
    this.crossesCalendarDay = crossesCalendarDay;
    this.crossesMonth = crossesMonth;
    this.crossesYear = crossesYear;
}
```

删除旧 5 参构造，或保留为 **deprecated 委托** 到新构造（推荐直接删除，由 Task 3 统一改调用方）。

为每个新字段补充 JavaDoc + getter；布尔字段使用 `isCrossesCalendarDay()`、`isCrossesMonth()`、`isCrossesYear()`。

更新 `toString()` 包含新字段摘要。

- [ ] **Step 2: Commit**

```bash
git add aps-lh-api/src/main/java/com/zlt/aps/lh/api/domain/dto/ShiftInfo.java
git commit -m "feat(api): ShiftInfo 增加班次定义字段并实现 Serializable"
```

---

### Task 3: `LhScheduleTimeUtil` — 日期截断、跨边界、`getScheduleShifts`、初始化 Map

**Files:**
- Modify: `aps-lh/src/main/java/com/zlt/aps/lh/util/LhScheduleTimeUtil.java`
- Test: `aps-lh/src/test/com/zlt/aps/lh/util/LhScheduleTimeUtilShiftInfoTest.java`

- [ ] **Step 1: 编写失败单测（断言夜班跨日、dateOffset）**

```java
package com.zlt.aps.lh.util;

import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.dto.ShiftInfo;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LhScheduleTimeUtilShiftInfoTest {

    @Test
    void getScheduleShifts_第三班夜班跨自然日且偏移为1() {
        LhScheduleContext ctx = new LhScheduleContext();
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.JANUARY, 15, 10, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date scheduleDate = cal.getTime();

        List<ShiftInfo> shifts = LhScheduleTimeUtil.getScheduleShifts(ctx, scheduleDate);
        assertEquals(8, shifts.size());
        ShiftInfo class3 = shifts.get(2);
        assertEquals(3, class3.getShiftIndex());
        assertEquals(1, class3.getDateOffset());
        assertTrue(class3.isCrossesCalendarDay(), "夜班应跨自然日");
        assertEquals(class3.getShiftType().getCode(), class3.getShiftCode());
        assertTrue(class3.getDurationMinutes() > 0);
        assertNotNull(class3.getShiftName());
    }

    @Test
    void getScheduleShifts_第一班为T日偏移0且不跨自然日() {
        LhScheduleContext ctx = new LhScheduleContext();
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.MARCH, 1, 8, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        List<ShiftInfo> shifts = LhScheduleTimeUtil.getScheduleShifts(ctx, cal.getTime());
        ShiftInfo class1 = shifts.get(0);
        assertEquals(0, class1.getDateOffset());
        assertFalse(class1.isCrossesCalendarDay());
    }
}
```

Run: `mvn -pl aps-lh -am test -Dtest=LhScheduleTimeUtilShiftInfoTest`

Expected: 编译失败（方法/构造不存在）。

- [ ] **Step 2: 在 `LhScheduleTimeUtil` 中增加私有工具方法**

```java
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();

private static Date toDateStartOfDay(Date any, ZoneId zone) {
    LocalDate ld = any.toInstant().atZone(zone).toLocalDate();
    return Date.from(ld.atStartOfDay(zone).toInstant());
}

private static void fillCrossFlags(Date start, Date end, ZoneId zone,
        boolean[] outDay, boolean[] outMonth, boolean[] outYear) {
    LocalDate d1 = start.toInstant().atZone(zone).toLocalDate();
    LocalDate d2 = end.toInstant().atZone(zone).toLocalDate();
    outDay[0] = !d1.equals(d2);
    outMonth[0] = d1.getYear() != d2.getYear() || d1.getMonthValue() != d2.getMonthValue();
    outYear[0] = d1.getYear() != d2.getYear();
}

private static String buildShiftName(int dateOffset, ShiftEnum type) {
    String prefix;
    if (dateOffset == 0) {
        prefix = "T日";
    } else {
        prefix = "T+" + dateOffset + "日";
    }
    return prefix + type.getDescription();
}
```

- [ ] **Step 3: 重写 `getScheduleShifts` 内 8 次 `new ShiftInfo`**

对每一班：

1. 计算 `workDate`：使用 `toDateStartOfDay(逻辑归属日, DEFAULT_ZONE)`（逻辑归属日与现实现一致：早/中用当日；夜班用 `tPlusXDay`）。
2. 计算 `dateOffset`：班次 1–2 为 `0`，3–5 为 `1`，6–8 为 `2`。
3. `durationMinutes = getShiftDurationHours(context) * 60`。
4. `shiftCode = shiftType.getCode()`。
5. `shiftName = buildShiftName(dateOffset, shiftType)`。
6. 调用 `fillCrossFlags(startTime, endTime, DEFAULT_ZONE, ...)` 得到三个 boolean。
7. `new ShiftInfo(shiftIndex, shiftType, workDate, startTime, endTime, shiftName, dateOffset, shiftCode, durationMinutes, crossesDay, crossesMonth, crossesYear)`。

- [ ] **Step 4: 新增 `initShiftRuntimeStateMap`**

```java
import com.zlt.aps.lh.api.domain.dto.ShiftRuntimeState;

public static void initShiftRuntimeStateMap(LhScheduleContext context, List<ShiftInfo> shifts) {
    Map<Integer, ShiftRuntimeState> map = new LinkedHashMap<>(8);
    for (ShiftInfo shift : shifts) {
        ShiftRuntimeState s = new ShiftRuntimeState();
        s.setShiftIndex(shift.getShiftIndex());
        s.setAvailable(true);
        s.setRemainingCapacity(0);
        s.setUnavailableReason(null);
        map.put(shift.getShiftIndex(), s);
    }
    context.setShiftRuntimeStateMap(map);
}
```

文件顶部增加 `import java.util.LinkedHashMap;`、`import java.util.Map;`（若未有）。

- [ ] **Step 5: 运行单测**

Run: `mvn -pl aps-lh -am test -Dtest=LhScheduleTimeUtilShiftInfoTest`

Expected: `BUILD SUCCESS`。

- [ ] **Step 6: 可选 — 跨年场景单测**

增加用例：`scheduleDate = 2025-12-31`，断言某一夜班 `isCrossesYear()` 为 `true`（若默认班次小时下确实跨入 2026；若断言失败则调整用例日期或夜班索引直至与实现一致）。

- [ ] **Step 7: Commit**

```bash
git add aps-lh/src/main/java/com/zlt/aps/lh/util/LhScheduleTimeUtil.java \
        aps-lh/src/test/com/zlt/aps/lh/util/LhScheduleTimeUtilShiftInfoTest.java
git commit -m "feat(lh): 班次工具类填充 ShiftInfo 新字段并支持初始化运行态 Map"
```

---

### Task 4: `LhScheduleContext` 增加 `shiftRuntimeStateMap`

**Files:**
- Modify: `aps-lh-api/src/main/java/com/zlt/aps/lh/api/domain/context/LhScheduleContext.java`

- [ ] **Step 1: 增加 import 与字段（与其它 Map 同风格）**

```java
import com.zlt.aps.lh.api.domain.dto.ShiftRuntimeState;

/** 班次运行态，key=班次索引 1～8 */
private Map<Integer, ShiftRuntimeState> shiftRuntimeStateMap = new LinkedHashMap<>(8);
```

`@Data` 会自动生成 getter/setter。

- [ ] **Step 2: Commit**

```bash
git add aps-lh-api/src/main/java/com/zlt/aps/lh/api/domain/context/LhScheduleContext.java
git commit -m "feat(api): LhScheduleContext 增加 shiftRuntimeStateMap"
```

---

### Task 5: `DataInitHandler` 在 S4.2 初始化班次运行态

**Files:**
- Modify: `aps-lh/src/main/java/com/zlt/aps/lh/handler/DataInitHandler.java`

- [ ] **Step 1: 在 `doHandle` 中 `buildStandardDataObjects(context);` 之后增加**

```java
import com.zlt.aps.lh.api.domain.dto.ShiftInfo;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;

List<ShiftInfo> windowShifts = LhScheduleTimeUtil.getScheduleShifts(context, context.getScheduleDate());
LhScheduleTimeUtil.initShiftRuntimeStateMap(context, windowShifts);
```

确保 `context.getScheduleDate()` 在此步骤前已赋值（与现网一致）。

- [ ] **Step 2: Commit**

```bash
git add aps-lh/src/main/java/com/zlt/aps/lh/handler/DataInitHandler.java
git commit -m "feat(lh): 数据初始化阶段建立班次运行态 Map"
```

---

### Task 6: `ContinuousProductionStrategy` — `distributeToShifts` 同步 `remainingCapacity`

**Files:**
- Modify: `aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/ContinuousProductionStrategy.java`

- [ ] **Step 1: 修改 `buildScheduleResult` 内调用**

将

```java
remaining = distributeToShifts(result, shifts, startTime, sku.getLhTimeSeconds(), sku.getMouldQty(), remaining);
```

改为传入 `context`：

```java
remaining = distributeToShifts(context, result, shifts, startTime, sku.getLhTimeSeconds(), sku.getMouldQty(), remaining);
```

- [ ] **Step 2: 修改 `distributeToShifts` 方法签名与实现**

```java
import com.zlt.aps.lh.api.domain.dto.ShiftRuntimeState;
import java.util.Map;
import org.springframework.util.CollectionUtils;

private int distributeToShifts(LhScheduleContext context,
                               LhScheduleResult result,
                               List<ShiftInfo> shifts,
                               Date startTime,
                               int lhTimeSeconds,
                               int mouldQty,
                               int remaining) {
    if (lhTimeSeconds <= 0 || mouldQty <= 0 || remaining <= 0) {
        return remaining;
    }
    Map<Integer, ShiftRuntimeState> stateMap = context.getShiftRuntimeStateMap();

    boolean started = false;
    for (ShiftInfo shift : shifts) {
        if (remaining <= 0) {
            break;
        }
        if (!started) {
            if (startTime != null && !startTime.before(shift.getEndTime()) && shift != shifts.get(shifts.size() - 1)) {
                continue;
            }
            started = true;
        }

        Date effectiveStart = (startTime != null && startTime.after(shift.getStartTime()))
                ? startTime : shift.getStartTime();
        if (effectiveStart.after(shift.getEndTime())) {
            continue;
        }

        long availableSeconds = (shift.getEndTime().getTime() - effectiveStart.getTime()) / 1000L;
        if (availableSeconds <= 0) {
            continue;
        }

        int shiftMaxQty = (int) (availableSeconds / lhTimeSeconds) * mouldQty;
        int shiftQty = Math.min(remaining, shiftMaxQty);

        setShiftPlanQty(result, shift.getShiftIndex(), shiftQty, effectiveStart, shift.getEndTime());
        remaining -= shiftQty;
        startTime = null;

        if (!CollectionUtils.isEmpty(stateMap)) {
            ShiftRuntimeState st = stateMap.get(shift.getShiftIndex());
            if (st != null) {
                st.setRemainingCapacity(Math.max(0, shiftMaxQty - shiftQty));
            }
        }
    }
    return remaining;
}
```

说明：多 SKU 连续排产时，同一上下文 Map 会被后续分配覆盖；与规格中「过程态」一致，若以后要机台级累计，需与 `MachineScheduleDTO.shiftAvailable` 或 `machineShiftCapacityMap` 协同迭代。

- [ ] **Step 3: 全模块编译**

Run: `mvn -pl aps-lh -am compile -q`

Expected: 成功。

- [ ] **Step 4: Commit**

```bash
git add aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/ContinuousProductionStrategy.java
git commit -m "feat(lh): 续作策略分配班次时写入 shiftRuntimeStateMap 剩余产能"
```

---

### Task 7: `NewSpecProductionStrategy` — 同步 `distributeToShifts`

**Files:**
- Modify: `aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/NewSpecProductionStrategy.java`

- [ ] **Step 1: 修改 `buildNewSpecScheduleResult` 内调用**

将

```java
int remaining = distributeToShifts(result, shifts, startTime, sku.getLhTimeSeconds(), sku.getMouldQty(), pendingQty, capacityCalculate);
```

改为：

```java
int remaining = distributeToShifts(context, result, shifts, startTime, sku.getLhTimeSeconds(), sku.getMouldQty(), pendingQty, capacityCalculate);
```

- [ ] **Step 2: 替换 `distributeToShifts` 方法（保留产能策略分支，仅在 `shiftQty > 0` 时写 Map）**

```java
import com.zlt.aps.lh.api.domain.dto.ShiftRuntimeState;
import org.springframework.util.CollectionUtils;

import java.util.Map;

private int distributeToShifts(LhScheduleContext context,
                               LhScheduleResult result,
                               List<ShiftInfo> shifts,
                               Date startTime,
                               int lhTimeSeconds,
                               int mouldQty,
                               int remaining,
                               ICapacityCalculateStrategy capacityCalculate) {
    if (lhTimeSeconds <= 0 || mouldQty <= 0 || remaining <= 0 || startTime == null) {
        return remaining;
    }
    Map<Integer, ShiftRuntimeState> stateMap = context.getShiftRuntimeStateMap();

    boolean started = false;
    for (ShiftInfo shift : shifts) {
        if (remaining <= 0) {
            break;
        }
        if (!started) {
            if (startTime.before(shift.getEndTime())) {
                started = true;
            } else {
                continue;
            }
        }

        Date effectiveStart = startTime.after(shift.getStartTime()) ? startTime : shift.getStartTime();
        if (!effectiveStart.before(shift.getEndTime())) {
            continue;
        }

        long availableSeconds = (shift.getEndTime().getTime() - effectiveStart.getTime()) / 1000L;
        if (availableSeconds <= 0) {
            continue;
        }

        int shiftMaxQty;
        if (startTime.equals(effectiveStart)) {
            shiftMaxQty = capacityCalculate.calculateFirstShiftQty(effectiveStart, shift.getEndTime(), lhTimeSeconds, mouldQty);
        } else {
            shiftMaxQty = capacityCalculate.calculateShiftCapacity(lhTimeSeconds, mouldQty);
        }

        int shiftQty = Math.min(remaining, shiftMaxQty);
        if (shiftQty > 0) {
            setShiftPlanQty(result, shift.getShiftIndex(), shiftQty, effectiveStart, shift.getEndTime());
            remaining -= shiftQty;
            startTime = shift.getEndTime();

            if (!CollectionUtils.isEmpty(stateMap)) {
                ShiftRuntimeState st = stateMap.get(shift.getShiftIndex());
                if (st != null) {
                    st.setRemainingCapacity(Math.max(0, shiftMaxQty - shiftQty));
                }
            }
        }
    }
    return remaining;
}
```

- [ ] **Step 3: 编译与 Commit**

Run: `mvn -pl aps-lh -am compile -q`

```bash
git add aps-lh/src/main/java/com/zlt/aps/lh/engine/strategy/impl/NewSpecProductionStrategy.java
git commit -m "feat(lh): 新增规格策略分配班次时写入 shiftRuntimeStateMap"
```

---

### Task 8: 全量测试与收尾

- [ ] **Step 1: 运行 `aps-lh` 模块测试**

Run: `mvn -pl aps-lh -am test`

Expected: `BUILD SUCCESS`。

- [ ] **Step 2: 若有失败，根据堆栈修复（常见：遗漏的 `distributeToShifts` 重载调用、import）**

- [ ] **Step 3: Commit（仅当有修复时）**

```bash
git commit -am "fix(lh): 班次运行态联调与测试修复"
```

---

## Spec 对照自检

| 规格要求 | 对应 Task |
|----------|-----------|
| `ShiftInfo` 新字段 + `workDate`/`dateOffset` + `shiftCode`/`durationMinutes` + 跨日/月/年 | Task 2、3 |
| `ShiftRuntimeState` + `Serializable` | Task 1 |
| `Map<Integer, ShiftRuntimeState>` 非裸 Map | Task 4 |
| S4.2 初始化 Map | Task 5 |
| 时区 `systemDefault()` | Task 3 中 `DEFAULT_ZONE` |
| 策略侧写入剩余产能 | Task 6、7 |
| `LhScheduleResult` 本期不扩展 | 本文 Architecture；不新增任务 |

---

## 执行交接

Plan complete and saved to `docs/superpowers/plans/2026-04-07-shiftinfo-implementation.md`. Two execution options:

**1. Subagent-Driven (recommended)** — 每个 Task 派生子代理，任务间人工快速复核，迭代快。

**2. Inline Execution** — 本会话用 executing-plans 按 Task 批量执行并设检查点。

Which approach do you want?
