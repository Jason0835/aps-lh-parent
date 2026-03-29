package com.zlt.aps.lh.engine.strategy;

import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 开停产处理策略
 * <p>处理停产递减、开产首日产能调整</p>
 */
public interface IProductionShutdownStrategy {
    BigDecimal calculateShutdownRate(LhScheduleContext context, String machineCode, Date targetDate);

    boolean isShutdownDay(LhScheduleContext context, String machineCode, Date targetDate);

    boolean isStartupDay(LhScheduleContext context, String machineCode, Date targetDate);

    int adjustCapacityForShutdown(LhScheduleContext context, SkuScheduleDTO skuDto, int originalCapacity);
}
