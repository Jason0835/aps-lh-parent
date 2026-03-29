package com.zlt.aps.lh.engine.strategy;

import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;

import java.util.List;

/**
 * 插单处理策略
 * <p>处理紧急插单、锁定期内插单等特殊场景</p>
 */
public interface IInsertOrderStrategy {
    List<SkuScheduleDTO> processInsertOrders(LhScheduleContext context, List<SkuScheduleDTO> insertOrders);

    boolean validateInsertOrder(LhScheduleContext context, SkuScheduleDTO insertOrder);

    void adjustExistingSchedule(LhScheduleContext context, SkuScheduleDTO insertOrder);
}
