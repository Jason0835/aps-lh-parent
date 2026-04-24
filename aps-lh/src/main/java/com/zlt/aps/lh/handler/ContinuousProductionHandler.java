package com.zlt.aps.lh.handler;

import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.api.enums.ScheduleStepEnum;
import com.zlt.aps.lh.api.enums.ScheduleTypeEnum;
import com.zlt.aps.lh.engine.factory.ScheduleStrategyFactory;
import com.zlt.aps.lh.engine.strategy.IProductionStrategy;
import com.zlt.aps.lh.engine.strategy.ISkuPriorityStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * S4.4 续作规格排产处理器
 * <p>对前日延续的SKU进行产能分配、胎胚库存匹配与降模处理</p>
 *
 * @author APS
 */
@Slf4j
@Component
public class ContinuousProductionHandler extends AbsScheduleStepHandler {

    @Resource
    private ScheduleStrategyFactory strategyFactory;

    @Override
    protected void doHandle(LhScheduleContext context) {
        ISkuPriorityStrategy priorityStrategy = strategyFactory.getSkuPriorityStrategy();
        priorityStrategy.sortByPriority(context);

        IProductionStrategy strategy = strategyFactory.getProductionStrategy(
                ScheduleTypeEnum.CONTINUOUS.getCode());

        // S4.4.1 MES在机原物料延续生产与续作收尾
        strategy.scheduleContinuousEnding(context);

        // S4.4.2 收尾后衔接排产（同产品结构直续优先，其次换活字块）
        strategy.scheduleTypeBlockChange(context);

        // S4.4.3 班次计划量分配
        strategy.allocateShiftPlanQty(context);

        // S4.4.4 胎胚库存调整
        strategy.adjustEmbryoStock(context);

        // S4.4.5 降模排产
        strategy.scheduleReduceMould(context);
    }

    @Override
    protected String getStepName() {
        return ScheduleStepEnum.S4_4_CONTINUOUS_PRODUCTION.getDescription();
    }
}
