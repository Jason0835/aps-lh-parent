package com.zlt.aps.lh.engine.chain.validators;

import com.zlt.aps.lh.api.constant.LhDataValidationGroupConstant;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.api.enums.ValidationPolicyEnum;
import com.zlt.aps.lh.engine.chain.IDataValidator;
import com.zlt.aps.lh.util.MouldStatusUtil;
import com.zlt.aps.mdm.api.domain.entity.MdmModelInfo;
import com.zlt.aps.mdm.api.domain.entity.MdmSkuMouldRel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SKU与模具关系校验器
 *
 * @author APS
 */
@Slf4j
@Component
public class MouldRelationValidator implements IDataValidator {
    private static final String VALIDATOR_KEY = "mouldRelationValidator";
    private static final String MOULD_CODE_DELIMITER = "、";
    private static final String MISSING_MODEL_INFO_ERROR_TEMPLATE = "[%s] 模具台账缺失，模具号: %s";
    private static final String DISABLED_MODEL_ERROR_TEMPLATE = "[%s] 模具状态为禁用，模具号: %s";

    @Override
    public boolean validate(LhScheduleContext context) {
        if (CollectionUtils.isEmpty(context.getSkuMouldRelMap())) {
            log.warn("SKU与模具关系数据为空, 工厂: {}", context.getFactoryCode());
            context.addValidationError("[" + getValidatorName() + "] SKU与模具关系数据为空, 工厂: "
                    + context.getFactoryDisplayName());
            return false;
        }
        long missingMouldCount = context.getMonthPlanList().stream()
                .filter(p -> p.getMaterialCode() != null
                        && !context.getSkuMouldRelMap().containsKey(p.getMaterialCode()))
                .count();
        if (missingMouldCount > 0) {
            log.warn("有{}个月计划SKU缺少模具关系数据（可能正常，如续作时已有模具）", missingMouldCount);
            context.addValidationError("[" + getValidatorName() + "] 月计划SKU缺少模具关系数据, 工厂: "
                    + context.getFactoryDisplayName());
            return false;
        }
        // 仅校验月计划中出现的SKU，保持首现顺序用于稳定错误展示
        Set<String> monthPlanSkuSet = context.getMonthPlanList().stream()
                .map(p -> p.getMaterialCode())
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> missingModelInfoMouldCodeSet = new LinkedHashSet<>();
        Set<String> disabledMouldCodeSet = new LinkedHashSet<>();
        Map<String, MdmModelInfo> modelInfoMap = context.getModelInfoMap();
        for (String materialCode : monthPlanSkuSet) {
            List<MdmSkuMouldRel> mouldRelList = context.getSkuMouldRelMap().get(materialCode);
            if (CollectionUtils.isEmpty(mouldRelList)) {
                continue;
            }
            // 逐个模具号校验是否存在台账且状态启用
            for (MdmSkuMouldRel mouldRel : mouldRelList) {
                String mouldCode = StringUtils.trim(mouldRel.getMouldCode());
                if (StringUtils.isEmpty(mouldCode)) {
                    continue;
                }
                MdmModelInfo modelInfo = modelInfoMap.get(mouldCode);
                if (Objects.isNull(modelInfo)) {
                    missingModelInfoMouldCodeSet.add(mouldCode);
                    continue;
                }
                if (!MouldStatusUtil.isEnabled(modelInfo.getMouldStatus())) {
                    disabledMouldCodeSet.add(mouldCode);
                }
            }
        }
        if (!missingModelInfoMouldCodeSet.isEmpty()) {
            String mouldCodeText = String.join(MOULD_CODE_DELIMITER, missingModelInfoMouldCodeSet);
            log.warn("检测到模具台账缺失, 工厂: {}, 模具号: {}", context.getFactoryCode(), mouldCodeText);
            context.addValidationError(String.format(MISSING_MODEL_INFO_ERROR_TEMPLATE, getValidatorName(), mouldCodeText));
        }
        if (!disabledMouldCodeSet.isEmpty()) {
            String mouldCodeText = String.join(MOULD_CODE_DELIMITER, disabledMouldCodeSet);
            log.warn("检测到禁用模具, 工厂: {}, 模具号: {}", context.getFactoryCode(), mouldCodeText);
            context.addValidationError(String.format(DISABLED_MODEL_ERROR_TEMPLATE, getValidatorName(), mouldCodeText));
        }
        if (!missingModelInfoMouldCodeSet.isEmpty() || !disabledMouldCodeSet.isEmpty()) {
            return false;
        }
        log.info("模具关系校验通过, SKU模具关系数: {}", context.getSkuMouldRelMap().size());
        return true;
    }

    @Override
    public String getValidatorName() {
        return "SKU与模具关系校验";
    }

    /**
     * 获取校验器唯一标识
     *
     * @return 校验器唯一标识
     */
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
        return 30;
    }
}
