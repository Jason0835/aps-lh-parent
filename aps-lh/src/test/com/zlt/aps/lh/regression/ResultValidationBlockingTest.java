package com.zlt.aps.lh.regression;

import com.zlt.aps.lh.api.domain.entity.LhScheduleResult;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.engine.observer.ScheduleEventPublisher;
import com.zlt.aps.lh.exception.ScheduleException;
import com.zlt.aps.lh.handler.ResultValidationHandler;
import com.zlt.aps.lh.service.impl.SchedulePersistenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 结果强校验：关键字段缺失时必须阻断持久化。
 */
@ExtendWith(MockitoExtension.class)
class ResultValidationBlockingTest {

    @Mock
    private ScheduleEventPublisher scheduleEventPublisher;

    @Mock
    private SchedulePersistenceService schedulePersistenceService;

    @InjectMocks
    private ResultValidationHandler handler;

    @Test
    void handle_throwsWhenSpecEndTimeMissing() {
        LhScheduleContext context = new LhScheduleContext();
        context.setFactoryCode("FC01");
        context.setBatchNo("LHPC20260413001");

        LhScheduleResult result = new LhScheduleResult();
        result.setLhMachineCode("M1");
        result.setMaterialCode("MAT-1");
        result.setScheduleType("02");
        result.setIsChangeMould("1");
        result.setMouldCode("MOULD-1");
        context.setScheduleResultList(Collections.singletonList(result));

        assertThrows(ScheduleException.class, () -> handler.handle(context));

        verify(schedulePersistenceService, never()).replaceScheduleAtomically(context);
        verify(scheduleEventPublisher, never()).publish(any());
    }
}
