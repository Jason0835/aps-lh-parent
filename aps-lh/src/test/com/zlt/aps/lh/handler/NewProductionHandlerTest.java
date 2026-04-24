package com.zlt.aps.lh.handler;

import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.engine.factory.ScheduleStrategyFactory;
import com.zlt.aps.lh.engine.strategy.ICapacityCalculateStrategy;
import com.zlt.aps.lh.engine.strategy.IFirstInspectionBalanceStrategy;
import com.zlt.aps.lh.engine.strategy.IMachineMatchStrategy;
import com.zlt.aps.lh.engine.strategy.IMouldChangeBalanceStrategy;
import com.zlt.aps.lh.engine.strategy.IProductionStrategy;
import com.zlt.aps.lh.engine.strategy.ISkuPriorityStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

/**
 * S4.5 新增排产步骤顺序回归测试。
 */
@ExtendWith(MockitoExtension.class)
class NewProductionHandlerTest {

    @Mock
    private ScheduleStrategyFactory strategyFactory;

    @Mock
    private IProductionStrategy strategy;

    @Mock
    private ISkuPriorityStrategy skuPriorityStrategy;

    @Mock
    private IMachineMatchStrategy machineMatchStrategy;

    @Mock
    private IMouldChangeBalanceStrategy mouldChangeBalanceStrategy;

    @Mock
    private IFirstInspectionBalanceStrategy firstInspectionBalanceStrategy;

    @Mock
    private ICapacityCalculateStrategy capacityCalculateStrategy;

    @InjectMocks
    private NewProductionHandler handler;

    @Test
    void handle_shouldRunNewSpecPostStepsAfterScheduleNewSpecs() {
        when(strategyFactory.getProductionStrategy("02")).thenReturn(strategy);
        when(strategyFactory.getSkuPriorityStrategy()).thenReturn(skuPriorityStrategy);
        when(strategyFactory.getMachineMatchStrategy()).thenReturn(machineMatchStrategy);
        when(strategyFactory.getMouldChangeBalanceStrategy()).thenReturn(mouldChangeBalanceStrategy);
        when(strategyFactory.getFirstInspectionBalanceStrategy()).thenReturn(firstInspectionBalanceStrategy);
        when(strategyFactory.getCapacityCalculateStrategy()).thenReturn(capacityCalculateStrategy);

        handler.handle(new LhScheduleContext());

        InOrder inOrder = inOrder(skuPriorityStrategy, strategy);
        inOrder.verify(skuPriorityStrategy).sortByPriority(any(LhScheduleContext.class));
        inOrder.verify(strategy).scheduleNewSpecs(any(LhScheduleContext.class),
                any(IMachineMatchStrategy.class),
                any(IMouldChangeBalanceStrategy.class),
                any(IFirstInspectionBalanceStrategy.class),
                any(ICapacityCalculateStrategy.class));
        inOrder.verify(strategy).allocateShiftPlanQty(any(LhScheduleContext.class));
        inOrder.verify(strategy).adjustEmbryoStock(any(LhScheduleContext.class));
        inOrder.verify(strategy).scheduleReduceMould(any(LhScheduleContext.class));
    }
}
