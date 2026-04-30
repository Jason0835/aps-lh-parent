package com.zlt.aps.lh.regression;

import com.zlt.aps.lh.api.constant.LhScheduleParamConstant;
import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.component.TargetScheduleQtyResolver;
import com.zlt.aps.lh.context.LhScheduleConfig;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 排产目标量解析回归。
 */
class TargetScheduleQtyResolverRegressionTest {

    private final TargetScheduleQtyResolver resolver = new TargetScheduleQtyResolver();

    @Test
    void resolveInitialTargetQty_shouldUsePendingQtyWhenStockExceedsSurplus() {
        LhScheduleContext context = new LhScheduleContext();
        context.setScheduleConfig(createConfig("0"));
        SkuScheduleDTO sku = new SkuScheduleDTO();
        sku.setSurplusQty(80);
        sku.setPendingQty(120);

        int targetQty = resolver.resolveInitialTargetQty(context, sku);

        assertEquals(120, targetQty, "按需求排产时应允许胎胚库存放大后的待排量生效");
    }

    @Test
    void resolveInitialTargetQty_fullCapacityModeShouldUseCapacityLimitAfterStockExpansion() {
        LhScheduleContext context = new LhScheduleContext();
        context.setScheduleDate(date(2026, 4, 22));
        context.setScheduleWindowShifts(LhScheduleTimeUtil.buildDefaultScheduleShifts(context, context.getScheduleDate()));
        context.setScheduleConfig(createConfig("1"));
        SkuScheduleDTO sku = new SkuScheduleDTO();
        sku.setSurplusQty(80);
        sku.setPendingQty(200);
        sku.setShiftCapacity(10);

        int targetQty = resolver.resolveInitialTargetQty(context, sku);

        assertEquals(80, targetQty, "满排模式应在库存放大后继续受窗口理论产能限制");
    }

    private static LhScheduleConfig createConfig(String fullCapacityMode) {
        Map<String, String> paramMap = new HashMap<>(4);
        paramMap.put(LhScheduleParamConstant.ENABLE_FULL_CAPACITY_SCHEDULING, fullCapacityMode);
        return new LhScheduleConfig(paramMap);
    }

    private static java.util.Date date(int y, int month, int day) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(Calendar.YEAR, y);
        c.set(Calendar.MONTH, month - 1);
        c.set(Calendar.DAY_OF_MONTH, day);
        return c.getTime();
    }
}
