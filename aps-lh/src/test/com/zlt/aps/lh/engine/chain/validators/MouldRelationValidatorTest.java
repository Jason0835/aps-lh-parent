package com.zlt.aps.lh.engine.chain.validators;

import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.mdm.api.domain.entity.MdmModelInfo;
import com.zlt.aps.mdm.api.domain.entity.MdmSkuMouldRel;
import com.zlt.aps.mp.api.domain.entity.FactoryMonthPlanProductionFinalResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SKU与模具关系校验器测试。
 */
class MouldRelationValidatorTest {

    private static final Integer ENABLED_STATUS = 1;
    private static final Integer DISABLED_STATUS = 0;
    private final MouldRelationValidator validator = new MouldRelationValidator();

    @Test
    void validate_passesWhenModelInfoExistsAndEnabled() {
        LhScheduleContext context = buildContext(
                Collections.singletonList(buildMonthPlan("SKU1")),
                Collections.singletonMap("SKU1", Collections.singletonList(buildRel("SKU1", "M001"))),
                Collections.singletonMap("M001", buildModelInfo("M001", ENABLED_STATUS)));

        boolean passed = validator.validate(context);

        assertTrue(passed);
        assertTrue(context.getValidationErrorList().isEmpty());
    }

    @Test
    void validate_failsWhenModelDisabled() {
        LhScheduleContext context = buildContext(
                Collections.singletonList(buildMonthPlan("SKU1")),
                Collections.singletonMap("SKU1", Collections.singletonList(buildRel("SKU1", "M001"))),
                Collections.singletonMap("M001", buildModelInfo("M001", DISABLED_STATUS)));

        boolean passed = validator.validate(context);

        assertFalse(passed);
        assertTrue(context.getValidationErrorList().stream()
                .anyMatch(message -> message.contains("模具状态为禁用") && message.contains("M001")));
    }

    @Test
    void validate_failsWhenModelInfoMissing() {
        LhScheduleContext context = buildContext(
                Collections.singletonList(buildMonthPlan("SKU1")),
                Collections.singletonMap("SKU1", Collections.singletonList(buildRel("SKU1", "M001"))),
                new HashMap<>(4));

        boolean passed = validator.validate(context);

        assertFalse(passed);
        assertTrue(context.getValidationErrorList().stream()
                .anyMatch(message -> message.contains("模具台账缺失") && message.contains("M001")));
    }

    @Test
    void validate_failsWhenDuplicatedMouldCodesAndKeepsStableOrder() {
        Map<String, List<MdmSkuMouldRel>> skuMouldRelMap = new HashMap<>(4);
        skuMouldRelMap.put("SKU1", Arrays.asList(
                buildRel("SKU1", "M002"),
                buildRel("SKU1", "M001"),
                buildRel("SKU1", "M002")));
        skuMouldRelMap.put("SKU2", Arrays.asList(
                buildRel("SKU2", "M001"),
                buildRel("SKU2", "M003")));

        Map<String, MdmModelInfo> modelInfoMap = new HashMap<>(4);
        modelInfoMap.put("M002", buildModelInfo("M002", DISABLED_STATUS));
        modelInfoMap.put("M003", buildModelInfo("M003", DISABLED_STATUS));

        LhScheduleContext context = buildContext(
                Arrays.asList(buildMonthPlan("SKU1"), buildMonthPlan("SKU2")),
                skuMouldRelMap,
                modelInfoMap);

        boolean passed = validator.validate(context);

        assertFalse(passed);
        assertTrue(context.getValidationErrorList().stream()
                .anyMatch(message -> message.contains("模具台账缺失") && message.contains("M001")));
        assertTrue(context.getValidationErrorList().stream()
                .anyMatch(message -> message.contains("模具状态为禁用") && message.contains("M002、M003")));
    }

    /**
     * 构建排程上下文
     *
     * @param monthPlanList 月计划列表
     * @param skuMouldRelMap SKU与模具关系
     * @param modelInfoMap 模具台账
     * @return 排程上下文
     */
    private LhScheduleContext buildContext(List<FactoryMonthPlanProductionFinalResult> monthPlanList,
                                           Map<String, List<MdmSkuMouldRel>> skuMouldRelMap,
                                           Map<String, MdmModelInfo> modelInfoMap) {
        LhScheduleContext context = new LhScheduleContext();
        context.setFactoryCode("F01");
        context.setFactoryName("一厂");
        context.setMonthPlanList(monthPlanList);
        context.setSkuMouldRelMap(skuMouldRelMap);
        context.setModelInfoMap(modelInfoMap);
        return context;
    }

    /**
     * 构建月计划记录
     *
     * @param materialCode 物料编码
     * @return 月计划记录
     */
    private FactoryMonthPlanProductionFinalResult buildMonthPlan(String materialCode) {
        FactoryMonthPlanProductionFinalResult row = new FactoryMonthPlanProductionFinalResult();
        row.setMaterialCode(materialCode);
        return row;
    }

    /**
     * 构建SKU与模具关系
     *
     * @param materialCode 物料编码
     * @param mouldCode 模具号
     * @return SKU与模具关系
     */
    private MdmSkuMouldRel buildRel(String materialCode, String mouldCode) {
        MdmSkuMouldRel rel = new MdmSkuMouldRel();
        rel.setMaterialCode(materialCode);
        rel.setMouldCode(mouldCode);
        return rel;
    }

    /**
     * 构建模具台账
     *
     * @param mouldCode 模具号
     * @param mouldStatus 模具状态
     * @return 模具台账
     */
    private MdmModelInfo buildModelInfo(String mouldCode, Integer mouldStatus) {
        MdmModelInfo modelInfo = new MdmModelInfo();
        modelInfo.setMouldCode(mouldCode);
        modelInfo.setMouldStatus(mouldStatus);
        return modelInfo;
    }
}
