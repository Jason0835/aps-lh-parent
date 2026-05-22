package com.zlt.aps.lh.engine.chain.validators;

import com.zlt.aps.lh.api.constant.LhDataValidationGroupConstant;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.api.enums.ValidationPolicyEnum;
import com.zlt.aps.lh.engine.chain.IDataValidator;
import com.zlt.aps.mdm.api.domain.entity.MdmSkuConstructionRef;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * SKU与示方书关系校验器
 * <p>校验 SKU 与示方书关系数据中的硫化示方书号(lhNo)和硫化示方书类型(lhType)是否为空。</p>
 *
 * @author APS
 */
@Slf4j
@Component
public class SkuConstructionValidator implements IDataValidator {

    private static final String VALIDATOR_KEY = "skuConstructionValidator";

    @Override
    public boolean validate(LhScheduleContext context) {
        Map<String, MdmSkuConstructionRef> refMap = context.getSkuConstructionRefMap();
        if (CollectionUtils.isEmpty(refMap)) {
            log.warn("SKU与示方书关系数据为空, 工厂: {}", context.getFactoryCode());
            context.addValidationError("[" + getValidatorName() + "] SKU与示方书关系数据为空, 工厂: "
                    + context.getFactoryDisplayName());
            return false;
        }
        // 收集 lhNo 或 lhType 为空的物料编码
        Map<String, String> missingFieldMap = new LinkedHashMap<>();
        for (Map.Entry<String, MdmSkuConstructionRef> entry : refMap.entrySet()) {
            String materialCode = entry.getKey();
            MdmSkuConstructionRef ref = entry.getValue();
            if (Objects.isNull(ref)) {
                continue;
            }
            if (StringUtils.isEmpty(ref.getLhNo()) && StringUtils.isEmpty(ref.getLhType())) {
                missingFieldMap.put(materialCode, "硫化示方书号和硫化示方书类型均为空");
            } else if (StringUtils.isEmpty(ref.getLhNo())) {
                missingFieldMap.put(materialCode, "硫化示方书号为空");
            } else if (StringUtils.isEmpty(ref.getLhType())) {
                missingFieldMap.put(materialCode, "硫化示方书类型为空");
            }
        }
        if (!missingFieldMap.isEmpty()) {
            StringBuilder errorMsg = new StringBuilder("[").append(getValidatorName()).append("] ");
            errorMsg.append("硫化示方书数据不完整: ");
            for (Map.Entry<String, String> missingEntry : missingFieldMap.entrySet()) {
                errorMsg.append("[物料编码:").append(missingEntry.getKey())
                        .append(", ").append(missingEntry.getValue()).append("]; ");
            }
            String errorText = errorMsg.toString();
            log.warn("SKU与示方书关系校验失败, 工厂: {}, 不完整物料数: {}, 详情: {}",
                    context.getFactoryCode(), missingFieldMap.size(), errorText);
            context.addValidationError(errorText);
            return false;
        }
        log.info("SKU与示方书关系校验通过, 数据条数: {}", refMap.size());
        return true;
    }

    @Override
    public String getValidatorName() {
        return "SKU与示方书关系校验";
    }

    @Override
    public String getValidatorKey() {
        return VALIDATOR_KEY;
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
        return 25;
    }
}
