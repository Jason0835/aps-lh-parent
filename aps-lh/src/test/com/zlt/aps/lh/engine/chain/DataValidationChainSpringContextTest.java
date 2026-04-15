package com.zlt.aps.lh.engine.chain;

import com.zlt.aps.lh.api.constant.LhDataValidationGroupConstant;
import com.zlt.aps.lh.api.enums.ValidationPolicyEnum;
import com.zlt.aps.lh.config.DataValidatorProperties;
import com.zlt.aps.lh.context.LhScheduleContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据校验链 Spring 配置绑定测试。
 */
class DataValidationChainSpringContextTest {

    private final ApplicationContextRunner applicationContextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestDataValidationConfig.class);

    @Test
    void validate_skipsValidatorWhenDisabledBySpringProperty() {
        applicationContextRunner
                .withPropertyValues("aps.lh.validation.validators.configurableValidator=false")
                .run(context -> {
                    DataValidationChain dataValidationChain = context.getBean(DataValidationChain.class);
                    ConfigurableTestValidator configurableValidator = context.getBean(ConfigurableTestValidator.class);
                    AlwaysOnTestValidator alwaysOnTestValidator = context.getBean(AlwaysOnTestValidator.class);

                    boolean passed = dataValidationChain.validate(new LhScheduleContext());

                    assertTrue(passed);
                    assertEquals(0, configurableValidator.getValidateCount());
                    assertEquals(1, alwaysOnTestValidator.getValidateCount());
                    assertFalse(context.getBean(DataValidatorProperties.class)
                            .isValidatorEnabled("configurableValidator"));
                });
    }
}

/**
 * 数据校验链最小化测试配置。
 */
@Configuration
@EnableConfigurationProperties(DataValidatorProperties.class)
class TestDataValidationConfig {

    /**
     * 注册数据校验链
     *
     * @return 数据校验链
     */
    @Bean
    public DataValidationChain dataValidationChain() {
        return new DataValidationChain();
    }

    /**
     * 注册可配置测试校验器
     *
     * @return 可配置测试校验器
     */
    @Bean
    public ConfigurableTestValidator configurableTestValidator() {
        return new ConfigurableTestValidator();
    }

    /**
     * 注册始终启用测试校验器
     *
     * @return 始终启用测试校验器
     */
    @Bean
    public AlwaysOnTestValidator alwaysOnTestValidator() {
        return new AlwaysOnTestValidator();
    }
}

/**
 * 可通过配置关闭的测试校验器。
 */
class ConfigurableTestValidator implements IDataValidator {

    private final AtomicInteger validateCount = new AtomicInteger();

    /**
     * 执行校验
     *
     * @param context 排程上下文
     * @return true 表示校验通过
     */
    @Override
    public boolean validate(LhScheduleContext context) {
        validateCount.incrementAndGet();
        return true;
    }

    /**
     * 获取校验器唯一标识
     *
     * @return 校验器唯一标识
     */
    @Override
    public String getValidatorKey() {
        return "configurableValidator";
    }

    /**
     * 获取校验器名称
     *
     * @return 校验器名称
     */
    @Override
    public String getValidatorName() {
        return "可配置测试校验器";
    }

    /**
     * 获取校验分组
     *
     * @return 校验分组
     */
    @Override
    public int getGroup() {
        return LhDataValidationGroupConstant.BASE_DATA_INTEGRITY;
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
     * 获取校验执行顺序
     *
     * @return 校验执行顺序
     */
    @Override
    public int getOrder() {
        return 10;
    }

    /**
     * 获取校验执行次数
     *
     * @return 校验执行次数
     */
    public int getValidateCount() {
        return validateCount.get();
    }
}

/**
 * 始终启用的测试校验器。
 */
class AlwaysOnTestValidator implements IDataValidator {

    private final AtomicInteger validateCount = new AtomicInteger();

    /**
     * 执行校验
     *
     * @param context 排程上下文
     * @return true 表示校验通过
     */
    @Override
    public boolean validate(LhScheduleContext context) {
        validateCount.incrementAndGet();
        return true;
    }

    /**
     * 获取校验器唯一标识
     *
     * @return 校验器唯一标识
     */
    @Override
    public String getValidatorKey() {
        return "alwaysOnValidator";
    }

    /**
     * 获取校验器名称
     *
     * @return 校验器名称
     */
    @Override
    public String getValidatorName() {
        return "常驻测试校验器";
    }

    /**
     * 获取校验分组
     *
     * @return 校验分组
     */
    @Override
    public int getGroup() {
        return LhDataValidationGroupConstant.BASE_DATA_INTEGRITY;
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
     * 获取校验执行顺序
     *
     * @return 校验执行顺序
     */
    @Override
    public int getOrder() {
        return 20;
    }

    /**
     * 获取校验执行次数
     *
     * @return 校验执行次数
     */
    public int getValidateCount() {
        return validateCount.get();
    }
}
