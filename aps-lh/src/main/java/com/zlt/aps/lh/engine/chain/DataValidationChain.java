package com.zlt.aps.lh.engine.chain;

import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.api.enums.ValidationPolicyEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据校验链
 * <p>收集所有 {@link IDataValidator} 实现，按 (group, order) 排序后按组执行：
 * {@link ValidationPolicyEnum#COLLECT_ALL} 组内全量执行并聚合错误，若该组结束后存在错误则不再执行后续组；
 * {@link ValidationPolicyEnum#FAIL_FAST} 组内遇错即停并结束整条链。</p>
 *
 * @author APS
 */
@Slf4j
@Component
public class DataValidationChain {

    @Resource
    private List<IDataValidator> validators;

    private final List<IDataValidator> orderedValidators = new ArrayList<>();

    private List<List<IDataValidator>> groupSegments = new ArrayList<>();

    @PostConstruct
    public void init() {
        orderedValidators.clear();
        groupSegments = new ArrayList<>();
        if (validators != null) {
            orderedValidators.addAll(validators);
            orderedValidators.sort(Comparator.comparingInt(IDataValidator::getGroup)
                    .thenComparingInt(IDataValidator::getOrder));
            assertConsistentPolicyPerGroup();
            groupSegments = partitionByGroup(orderedValidators);
        }
        log.info("数据校验链初始化完成, 校验器数量: {}, 分组数: {}", orderedValidators.size(), groupSegments.size());
    }

    /**
     * 执行全部校验组
     * <p>开始前会清空上下文中的 {@link LhScheduleContext#getValidationErrorList()}。</p>
     *
     * @param context 排程上下文
     * @return true 表示全部通过且无校验错误信息
     */
    public boolean validate(LhScheduleContext context) {
        context.getValidationErrorList().clear();
        for (List<IDataValidator> segment : groupSegments) {
            ValidationPolicyEnum policy = segment.get(0).getValidationPolicy();
            if (policy == ValidationPolicyEnum.COLLECT_ALL) {
                for (IDataValidator validator : segment) {
                    runOneValidator(context, validator);
                }
                if (!context.getValidationErrorList().isEmpty()) {
                    log.warn("数据校验未通过(聚合模式), 当前已累计 {} 条错误", context.getValidationErrorList().size());
                    return false;
                }
            } else {
                for (IDataValidator validator : segment) {
                    if (!runOneValidator(context, validator)) {
                        log.warn("数据校验未通过(短路模式), 校验器: {}", validator.getValidatorName());
                        return false;
                    }
                }
            }
        }
        log.info("所有数据校验通过");
        return true;
    }

    /**
     * 执行单个校验器；失败时若未写入任何错误信息则补充一条默认说明
     *
     * @param context   排程上下文
     * @param validator 校验器
     * @return true 表示该校验器通过
     */
    private boolean runOneValidator(LhScheduleContext context, IDataValidator validator) {
        log.info("执行校验器: {} (组:{}, 策略:{})", validator.getValidatorName(),
                validator.getGroup(), validator.getValidationPolicy());
        int errorSizeBefore = context.getValidationErrorList().size();
        boolean passed = validator.validate(context);
        if (!passed && context.getValidationErrorList().size() == errorSizeBefore) {
            context.addValidationError("[" + validator.getValidatorName() + "] 校验未通过");
        }
        if (!passed) {
            log.warn("校验器[{}]校验失败", validator.getValidatorName());
        }
        return passed;
    }

    private void assertConsistentPolicyPerGroup() {
        Map<Integer, ValidationPolicyEnum> groupPolicy = new LinkedHashMap<>();
        for (IDataValidator validator : orderedValidators) {
            int group = validator.getGroup();
            ValidationPolicyEnum policy = validator.getValidationPolicy();
            if (!groupPolicy.containsKey(group)) {
                groupPolicy.put(group, policy);
            } else if (groupPolicy.get(group) != policy) {
                throw new IllegalStateException(String.format(
                        "校验组 %d 内策略不一致: 已有 %s, 当前校验器[%s]为 %s",
                        group, groupPolicy.get(group), validator.getValidatorName(), policy));
            }
        }
    }

    private static List<List<IDataValidator>> partitionByGroup(List<IDataValidator> sorted) {
        List<List<IDataValidator>> segments = new ArrayList<>();
        if (sorted.isEmpty()) {
            return segments;
        }
        List<IDataValidator> current = new ArrayList<>();
        int prevGroup = sorted.get(0).getGroup();
        for (IDataValidator validator : sorted) {
            if (validator.getGroup() != prevGroup) {
                segments.add(current);
                current = new ArrayList<>();
                prevGroup = validator.getGroup();
            }
            current.add(validator);
        }
        segments.add(current);
        return segments;
    }
}
