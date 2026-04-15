package com.zlt.aps.lh.regression;

import com.zlt.aps.lh.api.domain.dto.MachineScheduleDTO;
import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.api.domain.entity.LhScheduleResult;
import com.zlt.aps.lh.component.OrderNoGenerator;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.engine.strategy.ICapacityCalculateStrategy;
import com.zlt.aps.lh.engine.strategy.IFirstInspectionBalanceStrategy;
import com.zlt.aps.lh.engine.strategy.IMachineMatchStrategy;
import com.zlt.aps.lh.engine.strategy.IMouldChangeBalanceStrategy;
import com.zlt.aps.lh.engine.strategy.impl.NewSpecProductionStrategy;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import com.zlt.aps.mdm.api.domain.entity.MdmSkuMouldRel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 新增规格时间轴：换模、首检、开产和结束时间应形成单调递增链路。
 */
@ExtendWith(MockitoExtension.class)
class NewSpecTimelineRegressionTest {

    @Mock
    private OrderNoGenerator orderNoGenerator;

    @InjectMocks
    private NewSpecProductionStrategy strategy;

    @Mock
    private IMachineMatchStrategy machineMatchStrategy;

    @Mock
    private IMouldChangeBalanceStrategy mouldChangeBalanceStrategy;

    @Mock
    private IFirstInspectionBalanceStrategy inspectionBalanceStrategy;

    @Mock
    private ICapacityCalculateStrategy capacityCalculateStrategy;

    @Test
    void scheduleNewSpecs_keepsTimelineMonotonicAndUpdatesMachineState() {
        LhScheduleContext context = newContext();
        MachineScheduleDTO machine = machine("M1", "FC-M1", dateTime(2026, 4, 11, 6, 0));
        context.setMachineScheduleMap(new LinkedHashMap<>());
        context.getMachineScheduleMap().put("M1", machine);

        SkuScheduleDTO sku = newSku("MAT-NEW", "SPEC-NEW", "18", 8);
        context.getNewSpecSkuList().add(sku);

        MdmSkuMouldRel rel = new MdmSkuMouldRel();
        rel.setMouldCode("MOULD-01");
        context.getSkuMouldRelMap().put("MAT-NEW", Collections.singletonList(rel));

        when(orderNoGenerator.generateOrderNo(any())).thenReturn("LHGD20260411001");
        when(machineMatchStrategy.matchMachines(any(), any())).thenReturn(Collections.singletonList(machine));
        when(machineMatchStrategy.selectBestMachine(any(), any(), any(), any())).thenReturn(machine);
        when(capacityCalculateStrategy.calculateStartTime(any(), anyString(), any())).thenReturn(dateTime(2026, 4, 11, 8, 0));
        when(mouldChangeBalanceStrategy.allocateMouldChange(any(), any())).thenReturn(dateTime(2026, 4, 11, 8, 0));
        when(inspectionBalanceStrategy.allocateInspection(any(), anyString(), any()))
                .thenReturn(dateTime(2026, 4, 11, 16, 0));
        when(capacityCalculateStrategy.calculateFirstShiftQty(any(), any(), anyInt(), anyInt())).thenReturn(5);
        strategy.scheduleNewSpecs(context, machineMatchStrategy, mouldChangeBalanceStrategy,
                inspectionBalanceStrategy, capacityCalculateStrategy);

        assertEquals(1, context.getScheduleResultList().size());
        LhScheduleResult result = context.getScheduleResultList().get(0);
        assertEquals("MOULD-01", result.getMouldCode());
        assertNotNull(result.getClass2StartTime());
        assertEquals(dateTime(2026, 4, 11, 17, 0), result.getClass2StartTime());
        assertNotNull(result.getSpecEndTime());
        assertTrue(result.getSpecEndTime().after(result.getClass2StartTime()));
        assertEquals(result.getSpecEndTime(), result.getTdaySpecEndTime());
        assertEquals("MAT-NEW", machine.getCurrentMaterialCode());
        assertEquals("SPEC-NEW", machine.getPreviousSpecCode());
        assertEquals(result.getSpecEndTime(), machine.getEstimatedEndTime());
    }

    private LhScheduleContext newContext() {
        LhScheduleContext context = new LhScheduleContext();
        context.setFactoryCode("FC01");
        context.setBatchNo("LHPC20260411001");
        context.setScheduleDate(date(2026, 4, 11));
        context.setScheduleTargetDate(date(2026, 4, 13));
        context.setScheduleWindowShifts(LhScheduleTimeUtil.buildDefaultScheduleShifts(context, context.getScheduleDate()));
        return context;
    }

    private MachineScheduleDTO machine(String code, String name, java.util.Date estimatedEndTime) {
        MachineScheduleDTO dto = new MachineScheduleDTO();
        dto.setMachineCode(code);
        dto.setMachineName(name);
        dto.setStatus("0");
        dto.setMaxMoldNum(2);
        dto.setEstimatedEndTime(estimatedEndTime);
        return dto;
    }

    private SkuScheduleDTO newSku(String materialCode, String specCode, String proSize, int pendingQty) {
        SkuScheduleDTO dto = new SkuScheduleDTO();
        dto.setMaterialCode(materialCode);
        dto.setMaterialDesc(materialCode + "-DESC");
        dto.setStructureName("S1");
        dto.setSpecCode(specCode);
        dto.setProSize(proSize);
        dto.setPendingQty(pendingQty);
        dto.setDailyPlanQty(pendingQty);
        dto.setLhTimeSeconds(3600);
        dto.setMouldQty(1);
        dto.setScheduleType("02");
        return dto;
    }

    private static java.util.Date date(int y, int month, int day) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(Calendar.YEAR, y);
        c.set(Calendar.MONTH, month - 1);
        c.set(Calendar.DAY_OF_MONTH, day);
        return c.getTime();
    }

    private static java.util.Date dateTime(int y, int month, int day, int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(Calendar.YEAR, y);
        c.set(Calendar.MONTH, month - 1);
        c.set(Calendar.DAY_OF_MONTH, day);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        return c.getTime();
    }
}
