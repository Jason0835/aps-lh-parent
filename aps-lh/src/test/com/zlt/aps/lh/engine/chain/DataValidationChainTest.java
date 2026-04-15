package com.zlt.aps.lh.engine.chain;

import com.zlt.aps.lh.api.constant.LhDataValidationGroupConstant;
import com.zlt.aps.lh.api.enums.ValidationPolicyEnum;
import com.zlt.aps.lh.config.DataValidatorProperties;
import com.zlt.aps.lh.context.LhScheduleContext;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据校验链启停能力回归测试。
 */
class DataValidationChainTest {

    @Test
    void validate_executesAllValidatorsWhenNoConfigProvided() {
        CountingValidator firstValidator = new CountingValidator(
                "firstValidator", "第一个校验器", 10, true, true);
        CountingValidator secondValidator = new CountingValidator(
                "secondValidator", "第二个校验器", 20, true, true);
        DataValidationChain chain = newChain(new DataValidatorProperties(), firstValidator, secondValidator);

        boolean passed = chain.validate(new LhScheduleContext());

        assertTrue(passed);
        assertEquals(1, firstValidator.getValidateCount());
        assertEquals(1, secondValidator.getValidateCount());
    }

    @Test
    void validate_skipsValidatorWhenDisabledByConfig() {
        CountingValidator firstValidator = new CountingValidator(
                "firstValidator", "第一个校验器", 10, true, true);
        CountingValidator secondValidator = new CountingValidator(
                "secondValidator", "第二个校验器", 20, true, true);
        DataValidatorProperties properties = new DataValidatorProperties();
        LinkedHashMap<String, Boolean> validators = new LinkedHashMap<>(4);
        validators.put(secondValidator.getValidatorKey(), false);
        properties.setValidators(validators);
        DataValidationChain chain = newChain(properties, firstValidator, secondValidator);

        boolean passed = chain.validate(new LhScheduleContext());

        assertTrue(passed);
        assertEquals(1, firstValidator.getValidateCount());
        assertEquals(0, secondValidator.getValidateCount());
    }

    @Test
    void validate_skipsValidatorWhenRuntimeHookDisabled() {
        CountingValidator firstValidator = new CountingValidator(
                "firstValidator", "第一个校验器", 10, true, true);
        CountingValidator secondValidator = new CountingValidator(
                "secondValidator", "第二个校验器", 20, false, true);
        DataValidationChain chain = newChain(new DataValidatorProperties(), firstValidator, secondValidator);

        boolean passed = chain.validate(new LhScheduleContext());

        assertTrue(passed);
        assertEquals(1, firstValidator.getValidateCount());
        assertEquals(0, secondValidator.getValidateCount());
    }

    @Test
    void validate_continuesWhenWholeGroupDisabled() {
        CountingValidator firstGroupValidator = new CountingValidator(
                "firstGroupValidator", "第一组校验器", 10, true, true);
        CountingValidator secondGroupValidator = new CountingValidator(
                "secondGroupValidator", "第二组校验器", 10, true, true, 200);
        DataValidatorProperties properties = new DataValidatorProperties();
        LinkedHashMap<String, Boolean> validators = new LinkedHashMap<>(4);
        validators.put(firstGroupValidator.getValidatorKey(), false);
        properties.setValidators(validators);
        DataValidationChain chain = newChain(properties, firstGroupValidator, secondGroupValidator);

        boolean passed = chain.validate(new LhScheduleContext());

        assertTrue(passed);
        assertEquals(0, firstGroupValidator.getValidateCount());
        assertEquals(1, secondGroupValidator.getValidateCount());
    }

    @Test
    void validate_doesNotAppendFallbackErrorForSkippedValidator() {
        CountingValidator skippedValidator = new CountingValidator(
                "skippedValidator", "被跳过校验器", 10, false, false);
        DataValidationChain chain = newChain(new DataValidatorProperties(), skippedValidator);
        LhScheduleContext context = new LhScheduleContext();

        boolean passed = chain.validate(context);

        assertTrue(passed);
        assertEquals(0, skippedValidator.getValidateCount());
        assertTrue(context.getValidationErrorList().isEmpty());
    }

    @Test
    void validate_stopsOnEnabledValidatorFailure() {
        CountingValidator passedValidator = new CountingValidator(
                "passedValidator", "通过校验器", 10, true, true);
        CountingValidator failedValidator = new CountingValidator(
                "failedValidator", "失败校验器", 20, true, false);
        DataValidationChain chain = newChain(new DataValidatorProperties(), passedValidator, failedValidator);
        LhScheduleContext context = new LhScheduleContext();

        boolean passed = chain.validate(context);

        assertFalse(passed);
        assertEquals(1, passedValidator.getValidateCount());
        assertEquals(1, failedValidator.getValidateCount());
        assertEquals(1, context.getValidationErrorList().size());
        assertTrue(context.getValidationErrorList().get(0).contains("失败校验器"));
    }

    /**
     * 构建测试用数据校验链
     *
     * @param properties 开关配置
     * @param validators 校验器列表
     * @return 数据校验链
     */
    private DataValidationChain newChain(DataValidatorProperties properties, IDataValidator... validators) {
        DataValidationChain chain = new DataValidationChain();
        ReflectionTestUtils.setField(chain, "dataValidatorProperties", properties);
        ReflectionTestUtils.setField(chain, "validators", Arrays.asList(validators));
        chain.init();
        return chain;
    }

    /**
     * 计数型测试校验器
     */
    private static class CountingValidator implements IDataValidator {
        private final String validatorKey;
        private final String validatorName;
        private final int order;
        private final boolean enabled;
        private final boolean passed;
        private final int group;
        private int validateCount;

        CountingValidator(String validatorKey, String validatorName, int order, boolean enabled, boolean passed) {
            this(validatorKey, validatorName, order, enabled, passed,
                    LhDataValidationGroupConstant.BASE_DATA_INTEGRITY);
        }

        CountingValidator(String validatorKey, String validatorName, int order, boolean enabled,
                          boolean passed, int group) {
            this.validatorKey = validatorKey;
            this.validatorName = validatorName;
            this.order = order;
            this.enabled = enabled;
            this.passed = passed;
            this.group = group;
        }

        /**
         * 执行校验
         *
         * @param context 排程上下文
         * @return true 表示通过，false 表示失败
         */
        @Override
        public boolean validate(LhScheduleContext context) {
            validateCount++;
            if (!passed) {
                return false;
            }
            return true;
        }

        /**
         * 获取校验器唯一标识
         *
         * @return 校验器唯一标识
         */
        @Override
        public String getValidatorKey() {
            return validatorKey;
        }

        /**
         * 判断当前校验器是否启用
         *
         * @param context 排程上下文
         * @return true 表示启用，false 表示禁用
         */
        @Override
        public boolean isEnabled(LhScheduleContext context) {
            return enabled;
        }

        /**
         * 获取校验器名称
         *
         * @return 校验器名称
         */
        @Override
        public String getValidatorName() {
            return validatorName;
        }

        /**
         * 获取校验分组
         *
         * @return 校验分组
         */
        @Override
        public int getGroup() {
            return group;
        }

        /**
         * 获取校验策略
         *
         * @return 校验策略
         */
        @Override
        public ValidationPolicyEnum getValidationPolicy() {
            return ValidationPolicyEnum.COLLECT_ALL;
        }

        /**
         * 获取执行顺序
         *
         * @return 执行顺序
         */
        @Override
        public int getOrder() {
            return order;
        }

        /**
         * 获取校验调用次数
         *
         * @return 校验调用次数
         */
        int getValidateCount() {
            return validateCount;
        }
    }
}
