
````md
# 角色

你是一名硫化排程业务专家、硫化排程算法专家，同时也是资深 Java 开发工程师。

# 目标

优化硫化排程逻辑，支持：

> 当某个 SKU 待排产量较大，一台硫化机台在排程窗口内产能无法排完时，允许同一个 SKU 拆分到多台可用硫化机台上排产。

要求尽量基于项目现有排程流程做最小改动，不重写主流程，不新增大而全的算法框架。

---

# 一、待排产量口径调整

## 1.1 待排产量定义

SKU 的待排产量应取：

> 月计划中，该 SKU 在当前排程窗口内的计划量总和。

例如：

排程窗口：2026-05-07 ~ 2026-05-09

则 SKU 待排产量为：

```java
待排产量 = day7 + day8 + day9
````

注意：原描述中的 `day87` 应理解为 `day8`。

## 1.2 处理要求

需要检查项目中当前待排产量的计算逻辑，避免只取排程目标日当天计划量。

应改为按排程窗口起止日期动态汇总窗口内每天的计划量。

例如：

```java
BigDecimal pendingQty = sumPlanQtyInScheduleWindow(monthPlan, scheduleStartDate, scheduleEndDate);
```

## 1.3 注意事项

* 汇总时必须考虑月计划余量，不能超过月计划剩余可排量。
* 如果窗口跨月，需要兼容跨月排程逻辑。
* 如果项目中已有窗口日计划拆解方法，应优先复用。
* 不要写死 day7、day8、day9，应根据排程窗口日期动态取值。
* 需避免重复扣减月计划余量。

---

# 二、支持一个 SKU 在多台机台上排产

## 2.1 当前问题

当前逻辑可能存在如下限制：

* 一个 SKU 只会绑定到一台硫化机台；
* 一台机台产能不足时，剩余待排量无法继续分配到其他机台；
* 收尾判断、月计划余量判断、胎胚库存判断可能默认一个 SKU 只在单机台生产。

需要调整为：

> SKU 可以在多台满足条件的硫化机台上连续分配，直到该 SKU 待排产量排完，或者没有可用机台/班次产能为止。

## 2.2 排产逻辑要求

当选择某个 SKU 排产时：

1. 计算 SKU 当前窗口待排产量；
2. 获取该 SKU 可用的硫化机台列表；
3. 按项目现有机台优先级排序逻辑排序；
4. 依次尝试在每台机台上排产；
5. 每排一台机台后，扣减该 SKU 剩余待排产量；
6. 如果剩余待排产量仍大于 0，则继续找下一台可用机台；
7. 直到：

   * SKU 待排产量排完；
   * 或排程窗口内无可用产能；
   * 或无更多可匹配机台。

伪代码示例：

```java
BigDecimal remainingQty = calcWindowPendingQty(sku, scheduleWindow);

List<Machine> candidateMachines = findCandidateMachines(sku);
candidateMachines = sortCandidateMachines(candidateMachines, sku);

for (Machine machine : candidateMachines) {
    if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) {
        break;
    }

    BigDecimal machineAvailableCapacity = calcMachineAvailableCapacity(machine, sku, scheduleWindow);
    if (machineAvailableCapacity.compareTo(BigDecimal.ZERO) <= 0) {
        continue;
    }

    BigDecimal scheduleQty = remainingQty.min(machineAvailableCapacity);

    createScheduleResult(machine, sku, scheduleQty);

    remainingQty = remainingQty.subtract(scheduleQty);

    updateMonthPlanRemainQty(sku, scheduleQty);
    updateMachineCapacity(machine, scheduleQty);
}
```

## 2.3 关键约束

多机台排产时，必须保持以下逻辑正确：

* 月计划余量只扣减实际排产量；
* 同一 SKU 在多台机台上的排产结果需要分别保存；
* 每条排程结果都要保存对应机台、班次、产量；
* 不允许同一机台同一班次重复占用产能；
* 不允许 SKU 总排产量超过：

  * 当前窗口待排产量；
  * 月计划余量；
  * 胎胚库存限制；
  * 可用机台产能总和。

---

# 三、胎胚库存上调逻辑调整

## 3.1 当前问题

当前可能存在逻辑：

> 只要胎胚库存大于窗口计划量，就上调 SKU 待排产量到胎胚库存量。

该逻辑需要调整。

## 3.2 新规则

胎胚库存上调只允许在“收尾”场景下触发。

即：

> 只有判断该 SKU 在当前排程窗口内可以完成月计划收尾时，才允许将待排产量上调到胎胚库存量。

## 3.3 调整后的逻辑

原逻辑可能类似：

```java
targetQty = max(windowPlanQty, embryoStockQty);
```

需要调整为：

```java
if (isSkuCanFinishInWindow(sku, scheduleWindow)) {
    targetQty = max(windowPlanQty, embryoStockQty);
} else {
    targetQty = windowPlanQty;
}
```

## 3.4 注意事项

* 非收尾场景下，不允许因为胎胚库存较多而强行增加排产量；
* 收尾场景下，需要确保胎胚库存能够被合理消化，避免月底或停产时胎胚剩余；
* 胎胚库存上调后的目标产量仍不能超过多机台可用总产能；
* 如果一个胎胚对应多个 SKU，需要沿用项目已有的胎胚库存分配逻辑，避免某一个 SKU 抢占全部胎胚库存。

---

# 四、收尾判断逻辑调整

## 4.1 当前问题

当前收尾判断可能只考虑单台机台产能，例如：

> 某 SKU 的月计划余量 <= 当前机台在窗口内可生产量，则认为可以收尾。

这在支持一个 SKU 多机台排产后是不准确的。

## 4.2 新收尾判断规则

收尾判断应调整为：

> 如果 SKU 的月计划余量可以在当前排程窗口内，通过多台可用硫化机台合计产能完成，则认为该 SKU 可以收尾。

公式：

```java
是否收尾 = SKU月计划余量 <= SKU在当前窗口内所有可用机台的可生产总量
```

即：

```java
boolean canFinish = monthRemainQty.compareTo(totalAvailableCapacityInWindow) <= 0;
```

其中：

```java
totalAvailableCapacityInWindow = sum(每台可用机台在排程窗口内可用于该 SKU 的剩余产能)
```

## 4.3 多机台产能计算要求

计算 SKU 可用总产能时，需要考虑：

* 机台是否匹配该 SKU；
* 机台是否可用；
* 机台在窗口内是否已有续作、换模、换活字块、保养、清洗等占用；
* 班次剩余产能；
* 单模/双模产能差异；
* 特殊物料、定点机台、尺寸、模套、宽基、芯片胎等硬性匹配规则；
* 已经被其他 SKU 占用的产能；
* 当前排程窗口起止时间。

## 4.4 注意事项

收尾判断不能只看第一台机台，也不能只看当前正在排的机台。

应该基于：

```java
List<Machine> candidateMachines = findCandidateMachines(sku);
BigDecimal totalCapacity = sumAvailableCapacity(candidateMachines, sku, scheduleWindow);
```

---

# 五、整体排产流程建议

建议将 SKU 排产流程调整为以下顺序：

## 5.1 计算窗口计划量

```java
BigDecimal windowPlanQty = calcWindowPlanQty(monthPlan, scheduleWindow);
```

## 5.2 计算月计划余量

```java
BigDecimal monthRemainQty = calcMonthRemainQty(monthPlan);
```

## 5.3 计算多机台可用总产能

```java
BigDecimal totalAvailableCapacity = calcSkuTotalAvailableCapacityInWindow(sku, scheduleWindow);
```

## 5.4 判断是否可收尾

```java
boolean canFinishInWindow = monthRemainQty.compareTo(totalAvailableCapacity) <= 0;
```

## 5.5 计算目标排产量

```java
BigDecimal targetQty = windowPlanQty.min(monthRemainQty);

if (canFinishInWindow) {
    targetQty = targetQty.max(embryoStockQty);
    targetQty = targetQty.min(monthRemainQty);
}

targetQty = targetQty.min(totalAvailableCapacity);
```

说明：

* 非收尾：目标排产量 = 窗口计划量；
* 收尾：允许上调到胎胚库存；
* 最终不能超过月计划余量；
* 最终不能超过多机台总可用产能。

## 5.6 多机台拆量排产

```java
BigDecimal remainingQty = targetQty;

for (Machine machine : sortedCandidateMachines) {
    if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) {
        break;
    }

    BigDecimal machineCapacity = calcMachineAvailableCapacity(machine, sku, scheduleWindow);
    BigDecimal scheduleQty = remainingQty.min(machineCapacity);

    if (scheduleQty.compareTo(BigDecimal.ZERO) <= 0) {
        continue;
    }

    saveScheduleResult(machine, sku, scheduleQty);

    remainingQty = remainingQty.subtract(scheduleQty);
}
```

---

# 六、需要重点检查和修改的点

请在项目中重点搜索并检查以下类型逻辑：

## 6.1 SKU 和机台一对一绑定逻辑

搜索类似：

```java
Map<String, Machine>
Map<String, String> skuMachineMap
break;
return machine;
```

如果当前逻辑找到第一台机台后就 `break`，需要确认是否应调整为继续尝试其他机台。

## 6.2 待排产量计算逻辑

搜索：

```java
getDayPlanQty
targetDate
scheduleDate
planQty
dailyQty
```

确认是否只取了排程目标日当天计划量，需要改成窗口汇总。

## 6.3 收尾判断逻辑

搜索：

```java
isFinish
canFinish
tail
remainQty
monthRemainQty
```

将单机台产能判断改成多机台合计产能判断。

## 6.4 胎胚库存上调逻辑

搜索：

```java
embryoStock
stockQty
胎胚库存
max
```

确保只有收尾场景才允许上调到胎胚库存。

## 6.5 月计划扣减逻辑

确保多机台拆量后，月计划余量按每条排程结果实际产量累计扣减。

不能出现：

* 第一台扣减后，第二台又按原始待排量扣减；
* 多机台重复扣减；
* 排程结果产量和月计划扣减量不一致。

---

# 七、边界场景

需要覆盖以下场景：

## 7.1 一台机台可以排完

SKU 待排产量为 80，一台机台窗口产能为 100。

期望：

* 只排一台机台；
* 排产量为 80；
* 不继续占用其他机台。

## 7.2 一台机台排不完，需要多台机台

SKU 待排产量为 180。

机台 A 可排 100，机台 B 可排 80。

期望：

* SKU 同时在 A、B 两台机台上排产；
* A 排 100；
* B 排 80；
* SKU 剩余待排为 0。

## 7.3 多台机台也排不完

SKU 待排产量为 300。

机台 A 可排 100，机台 B 可排 80。

期望：

* A 排 100；
* B 排 80；
* 剩余 120 进入未满足量/欠产/后续排程；
* 不允许超产。

## 7.4 非收尾场景，胎胚库存大于窗口计划量

窗口计划量 100，胎胚库存 180，月计划余量 500。

多机台窗口产能 300。

因为不是收尾，期望：

* 目标排产量仍为 100；
* 不允许上调到 180。

## 7.5 收尾场景，胎胚库存大于窗口计划量

窗口计划量 100，胎胚库存 180，月计划余量 180。

多机台窗口产能 200。

因为可以收尾，期望：

* 目标排产量允许上调到 180；
* 多机台合计排完 180。

## 7.6 收尾判断必须考虑多机台

月计划余量 180。

单台机台最大只能排 100，两台机台合计可排 200。

期望：

* 应判断为可以收尾；
* 不能因为单台排不完就误判为不能收尾。

---

# 八、验收标准

完成后需要满足：

1. SKU 待排产量按排程窗口内月计划日计划量汇总；
2. 一个 SKU 可以拆分到多台硫化机台上排产；
3. 多机台拆量后，排程结果按机台分别保存；
4. 月计划余量按实际排产量准确扣减；
5. 非收尾场景不允许因为胎胚库存上调计划量；
6. 收尾场景才允许上调到胎胚库存；
7. 收尾判断基于多台可用机台在窗口内的合计产能；
8. 不允许超出窗口计划量、月计划余量、胎胚库存约束和机台可用产能；
9. 不破坏已有续作、换模、换活字块、定点机台、特殊物料、单控机台、小批量 SKU 等已有逻辑；
10. 增加必要日志，方便排查：

    * SKU 窗口计划量；
    * 月计划余量；
    * 胎胚库存量；
    * 是否收尾；
    * 多机台合计可用产能；
    * 每台机台分配产量；
    * 剩余未排量。

---

# 九、日志建议

关键位置增加日志：

```java
log.info("SKU多机台排产计算：sku={}, windowPlanQty={}, monthRemainQty={}, embryoStockQty={}, totalAvailableCapacity={}, canFinishInWindow={}, targetQty={}",
        skuCode, windowPlanQty, monthRemainQty, embryoStockQty, totalAvailableCapacity, canFinishInWindow, targetQty);

log.info("SKU多机台拆量：sku={}, machine={}, machineAvailableCapacity={}, scheduleQty={}, remainingQty={}",
        skuCode, machineCode, machineAvailableCapacity, scheduleQty, remainingQty);
```

---

# 十、实现要求

1. JDK 使用 Java 8。
2. 尽量复用项目已有方法。
3. 不新增重复的排程策略类，优先在现有 SKU 排产、机台匹配、目标量计算、收尾判断逻辑中扩展。
4. 保持最小改动量。
5. BigDecimal 比较必须使用 `compareTo`。
6. 产量计算统一使用 BigDecimal，避免 double 精度问题。
7. 不要硬编码排程窗口日期。
8. 不要硬编码 day7、day8、day9。
9. 需要补充单元测试或至少增加可复现的测试用例。

```

