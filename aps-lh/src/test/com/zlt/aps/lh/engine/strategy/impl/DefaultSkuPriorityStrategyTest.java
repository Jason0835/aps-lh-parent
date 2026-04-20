package com.zlt.aps.lh.engine.strategy.impl;

import com.zlt.aps.lh.api.constant.LhScheduleParamConstant;
import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.context.LhScheduleConfig;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.engine.strategy.IEndingJudgmentStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 默认SKU优先级排序回归测试。
 */
class DefaultSkuPriorityStrategyTest {

    private final DefaultSkuPriorityStrategy strategy = new DefaultSkuPriorityStrategy();
    private final IEndingJudgmentStrategy endingJudgmentStrategy = mock(IEndingJudgmentStrategy.class);

    DefaultSkuPriorityStrategyTest() {
        ReflectionTestUtils.setField(strategy, "endingJudgmentStrategy", endingJudgmentStrategy);
    }

    @Test
    void sortByPriority_shouldPreferDeliveryLockedSku() {
        SkuScheduleDTO unlocked = sku("MAT-A");
        SkuScheduleDTO locked = sku("MAT-B");
        locked.setDeliveryLocked(true);

        LhScheduleContext context = contextWithNewSpec(unlocked, locked);

        strategy.sortByPriority(context);

        assertEquals("MAT-B", context.getNewSpecSkuList().get(0).getMaterialCode());
        assertEquals("MAT-A", context.getNewSpecSkuList().get(1).getMaterialCode());
    }

    @Test
    void sortByPriority_shouldPreferLargerDelayDays() {
        SkuScheduleDTO delayOne = sku("MAT-A");
        delayOne.setDelayDays(1);
        SkuScheduleDTO delayThree = sku("MAT-B");
        delayThree.setDelayDays(3);

        LhScheduleContext context = contextWithNewSpec(delayOne, delayThree);

        strategy.sortByPriority(context);

        assertEquals("MAT-B", context.getNewSpecSkuList().get(0).getMaterialCode());
        assertEquals("MAT-A", context.getNewSpecSkuList().get(1).getMaterialCode());
    }

    @Test
    void sortByPriority_shouldPreferStructureEndingSkuWithLaterEndingDay() {
        SkuScheduleDTO normal = sku("MAT-N");
        normal.setStructureName("S2");
        SkuScheduleDTO endingLate = sku("MAT-L");
        endingLate.setStructureName("S1");
        endingLate.setEndingDaysRemaining(4);
        SkuScheduleDTO endingEarly = sku("MAT-E");
        endingEarly.setStructureName("S1");
        endingEarly.setEndingDaysRemaining(2);

        when(endingJudgmentStrategy.isEnding(any(LhScheduleContext.class), same(endingLate))).thenReturn(true);
        when(endingJudgmentStrategy.isEnding(any(LhScheduleContext.class), same(endingEarly))).thenReturn(true);
        when(endingJudgmentStrategy.isEnding(any(LhScheduleContext.class), same(normal))).thenReturn(false);

        LhScheduleContext context = contextWithNewSpec(normal, endingEarly, endingLate);
        Map<String, List<SkuScheduleDTO>> structureSkuMap = new LinkedHashMap<>();
        structureSkuMap.put("S1", Arrays.asList(endingEarly, endingLate));
        structureSkuMap.put("S2", Collections.singletonList(normal));
        context.setStructureSkuMap(structureSkuMap);
        context.setScheduleConfig(new LhScheduleConfig(Collections.singletonMap(
                LhScheduleParamConstant.STRUCTURE_ENDING_DAYS, "5")));

        strategy.sortByPriority(context);

        assertEquals("MAT-L", context.getNewSpecSkuList().get(0).getMaterialCode());
        assertEquals("MAT-E", context.getNewSpecSkuList().get(1).getMaterialCode());
        assertEquals("MAT-N", context.getNewSpecSkuList().get(2).getMaterialCode());
    }

    @Test
    void sortByPriority_shouldFallbackToDefaultStructureEndingDaysWhenConfigMissing() {
        SkuScheduleDTO normal = sku("MAT-N");
        normal.setStructureName("S2");
        SkuScheduleDTO ending = sku("MAT-E");
        ending.setStructureName("S1");
        ending.setEndingDaysRemaining(4);

        when(endingJudgmentStrategy.isEnding(any(LhScheduleContext.class), same(ending))).thenReturn(true);
        when(endingJudgmentStrategy.isEnding(any(LhScheduleContext.class), same(normal))).thenReturn(false);

        LhScheduleContext context = contextWithNewSpec(normal, ending);
        Map<String, List<SkuScheduleDTO>> structureSkuMap = new LinkedHashMap<>();
        structureSkuMap.put("S1", Collections.singletonList(ending));
        structureSkuMap.put("S2", Collections.singletonList(normal));
        context.setStructureSkuMap(structureSkuMap);

        // 不设置 scheduleConfig，验证走默认 5 天阈值。
        strategy.sortByPriority(context);

        assertEquals("MAT-E", context.getNewSpecSkuList().get(0).getMaterialCode());
        assertEquals("MAT-N", context.getNewSpecSkuList().get(1).getMaterialCode());
    }

    @Test
    void sortByPriority_shouldCompareSupplyChainPendingQtyStepByStep() {
        SkuScheduleDTO cycleHigh = sku("MAT-C");
        cycleHigh.setCycleProductionPendingQty(10);
        SkuScheduleDTO highSmall = sku("MAT-H1");
        highSmall.setHighPriorityPendingQty(5);
        SkuScheduleDTO highLarge = sku("MAT-H2");
        highLarge.setHighPriorityPendingQty(5);
        highLarge.setCycleProductionPendingQty(8);

        LhScheduleContext context = contextWithNewSpec(cycleHigh, highSmall, highLarge);

        strategy.sortByPriority(context);

        assertEquals("MAT-H2", context.getNewSpecSkuList().get(0).getMaterialCode());
        assertEquals("MAT-H1", context.getNewSpecSkuList().get(1).getMaterialCode());
        assertEquals("MAT-C", context.getNewSpecSkuList().get(2).getMaterialCode());
    }

    private LhScheduleContext contextWithNewSpec(SkuScheduleDTO... skus) {
        LhScheduleContext context = new LhScheduleContext();
        context.setNewSpecSkuList(new java.util.ArrayList<>(Arrays.asList(skus)));
        return context;
    }

    private SkuScheduleDTO sku(String materialCode) {
        SkuScheduleDTO sku = new SkuScheduleDTO();
        sku.setMaterialCode(materialCode);
        sku.setDelayDays(0);
        sku.setStructureName("DEFAULT");
        return sku;
    }
}
