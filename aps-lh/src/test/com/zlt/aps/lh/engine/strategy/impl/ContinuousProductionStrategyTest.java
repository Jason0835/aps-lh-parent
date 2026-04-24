package com.zlt.aps.lh.engine.strategy.impl;

import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.engine.strategy.IEndingJudgmentStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 续作收尾机台候选SKU选料回归测试。
 */
class ContinuousProductionStrategyTest {

    @Test
    void selectPreferredSkuFromCandidates_shouldPickFirstForPriorityOneCandidates() {
        ContinuousProductionStrategy strategy = new ContinuousProductionStrategy();
        IEndingJudgmentStrategy endingJudgmentStrategy = mock(IEndingJudgmentStrategy.class);
        ReflectionTestUtils.setField(strategy, "endingJudgmentStrategy", endingJudgmentStrategy);

        SkuScheduleDTO sku1585 = sku("3302001585");
        SkuScheduleDTO sku2022 = sku("3302002022");
        List<SkuScheduleDTO> priorityOneCandidates = Arrays.asList(sku1585, sku2022);
        when(endingJudgmentStrategy.isEnding(any(LhScheduleContext.class), same(sku2022))).thenReturn(true);

        SkuScheduleDTO selected = ReflectionTestUtils.invokeMethod(
                strategy, "selectPreferredSkuFromCandidates", new LhScheduleContext(), priorityOneCandidates);

        assertEquals("3302001585", selected.getMaterialCode(),
                "第一层候选应严格按月度排序首位选料，不允许收尾SKU插队");
    }

    @Test
    void selectPreferredSkuFromCandidates_shouldPickFirstForPriorityTwoCandidates() {
        ContinuousProductionStrategy strategy = new ContinuousProductionStrategy();
        IEndingJudgmentStrategy endingJudgmentStrategy = mock(IEndingJudgmentStrategy.class);
        ReflectionTestUtils.setField(strategy, "endingJudgmentStrategy", endingJudgmentStrategy);

        SkuScheduleDTO sku1585 = sku("3302001585");
        SkuScheduleDTO sku2022 = sku("3302002022");
        List<SkuScheduleDTO> priorityTwoCandidates = Arrays.asList(sku1585, sku2022);
        when(endingJudgmentStrategy.isEnding(any(LhScheduleContext.class), same(sku2022))).thenReturn(true);

        SkuScheduleDTO selected = ReflectionTestUtils.invokeMethod(
                strategy, "selectPreferredSkuFromCandidates", new LhScheduleContext(), priorityTwoCandidates);

        assertEquals("3302001585", selected.getMaterialCode(),
                "第二层候选应严格按月度排序首位选料，不允许收尾SKU插队");
    }

    @Test
    void selectPreferredSkuFromCandidates_shouldReturnNullWhenCandidatesEmpty() {
        ContinuousProductionStrategy strategy = new ContinuousProductionStrategy();

        SkuScheduleDTO selected = ReflectionTestUtils.invokeMethod(
                strategy, "selectPreferredSkuFromCandidates", new LhScheduleContext(), Collections.emptyList());

        assertNull(selected, "候选为空时应返回null");
    }

    private SkuScheduleDTO sku(String materialCode) {
        SkuScheduleDTO sku = new SkuScheduleDTO();
        sku.setMaterialCode(materialCode);
        return sku;
    }
}
