package com.zlt.aps.lh.engine.chain.validators;

import com.zlt.aps.lh.api.domain.entity.LhMachineInfo;
import com.zlt.aps.lh.context.LhScheduleContext;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 机台信息校验器测试。
 */
class MachineInfoValidatorTest {

    private final MachineInfoValidator validator = new MachineInfoValidator();

    @Test
    void validate_passesWhenStatusHasTrailingSpace() {
        LhScheduleContext context = new LhScheduleContext();
        context.setFactoryCode("F01");
        context.setFactoryName("一厂");
        context.setMachineInfoMap(buildMachineMap("M01", "1 "));

        boolean passed = validator.validate(context);

        assertTrue(passed);
    }

    @Test
    void validate_failsWhenNoEnabledMachine() {
        LhScheduleContext context = new LhScheduleContext();
        context.setFactoryCode("F01");
        context.setFactoryName("一厂");
        context.setMachineInfoMap(buildMachineMap("M01", "0"));

        boolean passed = validator.validate(context);

        assertFalse(passed);
        assertFalse(context.getValidationErrorList().isEmpty());
    }

    /**
     * 构建机台信息 Map
     *
     * @param machineCode 机台编码
     * @param status 机台状态
     * @return 机台信息 Map
     */
    private Map<String, LhMachineInfo> buildMachineMap(String machineCode, String status) {
        LhMachineInfo machineInfo = new LhMachineInfo();
        machineInfo.setMachineCode(machineCode);
        machineInfo.setStatus(status);
        Map<String, LhMachineInfo> machineInfoMap = new LinkedHashMap<>(4);
        machineInfoMap.put(machineCode, machineInfo);
        return machineInfoMap;
    }
}
