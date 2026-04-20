package com.zlt.aps.lh.regression;

import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.engine.strategy.impl.DefaultEndingJudgmentStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

    @Test
    void isEnding_shouldUseTargetScheduleQtyInsteadOfSurplusQty() {
        LhScheduleContext context = new LhScheduleContext();
        context.setScheduleDate(new java.util.Date());
        SkuScheduleDTO dto = new SkuScheduleDTO();
        dto.setSurplusQty(10);
        dto.setPendingQty(10);
        dto.setTargetScheduleQty(120);
        dto.setShiftCapacity(10);
        dto.setDailyCapacity(90);

        assertFalse(strategy.isEnding(context, dto), "目标量已超过窗口总产能时，不应仅因余量较小而误判收尾");
        assertEquals(12, strategy.calculateEndingShifts(context, dto));
    }

    private SkuScheduleDTO sku(int pendingQty, int shiftCapacity) {
        SkuScheduleDTO dto = new SkuScheduleDTO();
        dto.setPendingQty(pendingQty);
        dto.setTargetScheduleQty(pendingQty);
        dto.setShiftCapacity(shiftCapacity);
        return dto;
    }
}
