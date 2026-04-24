package com.zlt.aps.lh.regression;

import com.zlt.aps.lh.api.domain.dto.MachineScheduleDTO;
import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.api.domain.entity.LhMachineOnlineInfo;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.handler.ScheduleAdjustHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 续作归类回归：停用或不可排机台的 MES 在机记录不能抢占续作 SKU。
 */
class ContinuousSkuAssignmentRegressionTest {

    private final ScheduleAdjustHandler handler = new ScheduleAdjustHandler();

    @Test
    void classifyContinuousAndNewSkus_shouldSkipMesMachineThatIsNotSchedulable() {
        LhScheduleContext context = new LhScheduleContext();

        SkuScheduleDTO sku = new SkuScheduleDTO();
        sku.setMaterialCode("3302001316");
        sku.setMaterialDesc("测试物料");
        sku.setStructureName("STRUCT-1");

        Map<String, java.util.List<SkuScheduleDTO>> structureSkuMap = new LinkedHashMap<>();
        structureSkuMap.put("STRUCT-1", new ArrayList<SkuScheduleDTO>());
        structureSkuMap.get("STRUCT-1").add(sku);
        context.setStructureSkuMap(structureSkuMap);

        Map<String, LhMachineOnlineInfo> machineOnlineInfoMap = new LinkedHashMap<>();
        machineOnlineInfoMap.put("K1110", buildOnlineInfo("K1110", "3302001316"));
        machineOnlineInfoMap.put("K1113", buildOnlineInfo("K1113", "3302001316"));
        context.setMachineOnlineInfoMap(machineOnlineInfoMap);

        Map<String, MachineScheduleDTO> machineScheduleMap = new LinkedHashMap<>();
        machineScheduleMap.put("K1113", buildMachine("K1113", "1"));
        context.setMachineScheduleMap(machineScheduleMap);

        ReflectionTestUtils.invokeMethod(handler, "classifyContinuousAndNewSkus", context);

        assertEquals(1, context.getContinuousSkuList().size());
        assertEquals("K1113", context.getContinuousSkuList().get(0).getContinuousMachineCode());
        assertEquals(0, context.getNewSpecSkuList().size());
    }

    private LhMachineOnlineInfo buildOnlineInfo(String machineCode, String materialCode) {
        LhMachineOnlineInfo onlineInfo = new LhMachineOnlineInfo();
        onlineInfo.setLhCode(machineCode);
        onlineInfo.setMaterialCode(materialCode);
        return onlineInfo;
    }

    private MachineScheduleDTO buildMachine(String machineCode, String status) {
        MachineScheduleDTO machine = new MachineScheduleDTO();
        machine.setMachineCode(machineCode);
        machine.setStatus(status);
        return machine;
    }
}
