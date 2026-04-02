package com.zlt.aps.lh.engine.chain.validators;

import com.zlt.aps.lh.api.constant.LhDataValidationGroupConstant;
import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.api.enums.ValidationPolicyEnum;
import com.zlt.aps.lh.engine.chain.IDataValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SKU日硫化产能校验器
 *
 * @author APS
 */
@Slf4j
@Component
public class SkuCapacityValidator implements IDataValidator {

    @Override
    public boolean validate(LhScheduleContext context) {
        if (context.getSkuLhCapacityMap() == null || context.getSkuLhCapacityMap().isEmpty()) {
            log.warn("SKU日硫化产能数据为空, 工厂: {}", context.getFactoryCode());
            context.addValidationError("[" + getValidatorName() + "] SKU日硫化产能数据为空, 工厂: " + context.getFactoryCode());
            return false;
        }
        long missingCapacityCount = context.getMonthPlanList().stream()
                .filter(p -> p.getMaterialCode() != null
                        && !context.getSkuLhCapacityMap().containsKey(p.getMaterialCode()))
                .count();
        if (missingCapacityCount > 0) {
            log.warn("有{}个月计划SKU缺少硫化产能数据", missingCapacityCount);
            context.addValidationError("[" + getValidatorName() + "] 有 " + missingCapacityCount + " 个月计划SKU缺少硫化产能数据");
            return false;
        }
        long invalidTimeCount = context.getSkuLhCapacityMap().values().stream()
                .filter(c -> c.getVulcanizationTime() == null || c.getVulcanizationTime() <= 0)
                .count();
        if (invalidTimeCount > 0) {
            log.warn("有{}条SKU产能记录的硫化时间无效", invalidTimeCount);
            context.addValidationError("[" + getValidatorName() + "] 有 " + invalidTimeCount + " 条SKU产能记录的硫化时间无效(须为正数)");
            return false;
        }
        return true;
    }

    @Override
    public String getValidatorName() {
        return "SKU日硫化产能校验";
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
        return 50;
    }
}
