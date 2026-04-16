package com.zlt.aps.lh.regression;

import com.zlt.aps.lh.api.domain.dto.MachineScheduleDTO;
import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.engine.strategy.impl.DefaultMachineMatchStrategy;
import com.zlt.aps.mdm.api.domain.entity.MdmSkuMouldRel;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 机台匹配回归：SKU存在多条模具关系时，不应把关系条数误当成待选前的用模数。
 */
class DefaultMachineMatchStrategyRegressionTest {

    @Test
    void matchMachines_usesMouldQtyInsteadOfRelationCount() {
        DefaultMachineMatchStrategy strategy = new DefaultMachineMatchStrategy();
        LhScheduleContext context = new LhScheduleContext();

        MachineScheduleDTO machine = new MachineScheduleDTO();
        machine.setMachineCode("M1");
        machine.setStatus("1");
        machine.setMaxMoldNum(1);

        context.setMachineScheduleMap(new LinkedHashMap<>());
        context.getMachineScheduleMap().put(machine.getMachineCode(), machine);
        context.setMachineAssignmentMap(new LinkedHashMap<>());

        SkuScheduleDTO sku = new SkuScheduleDTO();
        sku.setMaterialCode("MAT-1");

        context.getSkuMouldRelMap().put("MAT-1", Arrays.asList(
                mouldRel("MOULD-A"),
                mouldRel("MOULD-B"),
                mouldRel("MOULD-C")
        ));

        List<MachineScheduleDTO> candidates = strategy.matchMachines(context, sku);

        assertEquals(1, candidates.size(), "应按SKU实际用模数判断，不应因模具关系条数多而误过滤机台");
        assertEquals("M1", candidates.get(0).getMachineCode());
    }

    private MdmSkuMouldRel mouldRel(String mouldCode) {
        MdmSkuMouldRel rel = new MdmSkuMouldRel();
        rel.setMouldCode(mouldCode);
        return rel;
    }
}
