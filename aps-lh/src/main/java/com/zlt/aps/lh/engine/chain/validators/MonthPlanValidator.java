package com.zlt.aps.lh.engine.chain.validators;

import com.zlt.aps.lh.api.constant.LhDataValidationGroupConstant;
import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.api.enums.ValidationPolicyEnum;
import com.zlt.aps.lh.engine.chain.IDataValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 月生产计划数据校验器
 *
 * @author APS
 */
@Slf4j
@Component
public class MonthPlanValidator implements IDataValidator {

    @Override
    public boolean validate(LhScheduleContext context) {
        if (context.getMonthPlanList() == null || context.getMonthPlanList().isEmpty()) {
            log.warn("月生产计划数据为空, 工厂: {}", context.getFactoryCode());
            context.addValidationError("[" + getValidatorName() + "] 月生产计划数据为空, 工厂: " + context.getFactoryCode());
            return false;
        }
        long invalidCount = context.getMonthPlanList().stream()
                .filter(p -> p.getMaterialCode() == null || p.getMaterialCode().isEmpty())
                .count();
        if (invalidCount > 0) {
            log.warn("月生产计划存在{}条物料编码为空的记录", invalidCount);
            context.addValidationError("[" + getValidatorName() + "] 存在 " + invalidCount + " 条物料编码为空的月计划记录");
            return false;
        }
        return true;
    }

    @Override
    public String getValidatorName() {
        return "月生产计划校验";
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
        return 10;
    }
}
