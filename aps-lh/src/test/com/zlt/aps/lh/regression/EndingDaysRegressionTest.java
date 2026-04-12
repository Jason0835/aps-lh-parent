package com.zlt.aps.lh.regression;

import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.engine.strategy.impl.DefaultEndingJudgmentStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 收尾天数折算边界回归。
 */
class EndingDaysRegressionTest {

    private final DefaultEndingJudgmentStrategy strategy = new DefaultEndingJudgmentStrategy();

    @Test
    void calculateEndingDays_usesCeilingAcrossThreeShiftsPerDay() {
        LhScheduleContext context = new LhScheduleContext();
        assertEquals(1, strategy.calculateEndingDays(context, sku(10, 10)));
        assertEquals(1, strategy.calculateEndingDays(context, sku(20, 10)));
        assertEquals(1, strategy.calculateEndingDays(context, sku(30, 10)));
        assertEquals(2, strategy.calculateEndingDays(context, sku(40, 10)));
    }

    private SkuScheduleDTO sku(int pendingQty, int shiftCapacity) {
        SkuScheduleDTO dto = new SkuScheduleDTO();
        dto.setPendingQty(pendingQty);
        dto.setShiftCapacity(shiftCapacity);
        return dto;
    }
}
