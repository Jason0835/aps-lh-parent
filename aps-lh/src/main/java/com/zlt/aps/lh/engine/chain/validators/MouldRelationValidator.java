package com.zlt.aps.lh.engine.chain.validators;

import com.zlt.aps.lh.api.constant.LhDataValidationGroupConstant;
import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.api.enums.ValidationPolicyEnum;
import com.zlt.aps.lh.engine.chain.IDataValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SKU与模具关系校验器
 *
 * @author APS
 */
@Slf4j
@Component
public class MouldRelationValidator implements IDataValidator {

    @Override
    public boolean validate(LhScheduleContext context) {
        if (context.getSkuMouldRelMap() == null || context.getSkuMouldRelMap().isEmpty()) {
            log.warn("SKU与模具关系数据为空, 工厂: {}", context.getFactoryCode());
            context.addValidationError("[" + getValidatorName() + "] SKU与模具关系数据为空, 工厂: " + context.getFactoryCode());
            return false;
        }
        long missingMouldCount = context.getMonthPlanList().stream()
                .filter(p -> p.getMaterialCode() != null
                        && !context.getSkuMouldRelMap().containsKey(p.getMaterialCode()))
                .count();
        if (missingMouldCount > 0) {
            log.warn("有{}个月计划SKU缺少模具关系数据（可能正常，如续作时已有模具）", missingMouldCount);
            context.addValidationError("[" + getValidatorName() + "] 月计划SKU缺少模具关系数据, 工厂: " + context.getFactoryCode());
            return false;
        }
        log.info("模具关系校验通过, SKU模具关系数: {}", context.getSkuMouldRelMap().size());
        return true;
    }

    @Override
    public String getValidatorName() {
        return "SKU与模具关系校验";
    }

    @Override
    public int getGroup() {
        return LhDataValidationGroupConstant.BASE_DATA_INTEGRITY;
    }

    @Override
    public ValidationPolicyEnum getValidationPolicy() {
        return ValidationPolicyEnum.COLLECT_ALL;
    }

    @Override
    public int getOrder() {
        return 30;
    }
}
