package com.zlt.aps.lh.engine.chain;

import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.dto.ValidationResult;
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
 * 硫化排程基础数据校验链：注入全部 {@link IDataValidator}，启动时按 (group, order) 排序、按组切分并校验同组策略一致。
 * <p>{@link ValidationPolicyEnum#COLLECT_ALL} 组内全量执行，组结束后若有错误则不再执行后续组；
 * {@link ValidationPolicyEnum#FAIL_FAST} 组内遇错即停并结束整条链。{@link #validate(LhScheduleContext)} 会清空并重填上下文中的校验错误列表。</p>
 *
 * @author APS
 */
@Slf4j
@Component
public class DataValidationChain {

    @Resource
    private List<IDataValidator> validators;

    /**
     * 全部校验器按 (group, order) 排序后的线性列表，在 {@link #init()} 中构建
     */
    private final List<IDataValidator> orderedValidators = new ArrayList<>();

    /**
     * 按组切分后的执行段：每一段内 group 相同，且段内策略（COLLECT_ALL / FAIL_FAST）一致
     */
    private List<List<IDataValidator>> groupSegments = new ArrayList<>();

    /**
     * 初始化校验链：排序、校验同组策略一致、预计算分组段
     * <p>在 Spring 完成依赖注入后由容器回调；若同组策略不一致则抛异常，阻止应用带病启动。</p>
     */
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
     * 按分组段依次执行全部校验逻辑
     * <p>
     * 执行前会清空 {@code context} 中的 {@link LhScheduleContext#getValidationErrorList()}，表示<strong>仅针对本轮链式校验</strong>收集错误，
     * 避免与历史步骤残留混淆。各校验器失败时须向该列表追加说明；若返回 {@code false} 却未追加任何条目，则由
     * {@link #runOneValidator(LhScheduleContext, IDataValidator)} 补一条默认文案，避免前端无明细。
     * </p>
     *
     * @param context 排程上下文，用于读取基础数据并承载校验错误列表
     * @return {@code true} 表示所有分组段均通过且错误列表为空；{@code false} 表示已在聚合或短路语义下终止
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
     * 执行校验并返回结构化结果对象
     *
     * @param context 排程上下文
     * @return 校验结果对象，包含是否通过、错误列表及摘要信息
     */
    public ValidationResult validateWithResult(LhScheduleContext context) {
        boolean passed = validate(context);
        if (passed) {
            return ValidationResult.pass();
        }
        return ValidationResult.fail(context.getValidationErrorList());
    }

    /**
     * 执行单个校验器，并在「校验失败但未写入错误文案」时兜底补充一条提示
     *
     * @param context   排程上下文
     * @param validator 当前校验器实例
     * @return {@code true} 表示该校验器声明通过；{@code false} 表示未通过
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

    /**
     * 断言：每个 group 对应的 {@link ValidationPolicyEnum} 在全部校验器中唯一
     * <p>若同一 group 出现不同策略，说明配置错误，抛出 {@link IllegalStateException}。</p>
     */
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

    /**
     * 将已按 (group, order) 排好序的列表切分为连续「同组」段，供按组选择 COLLECT_ALL / FAIL_FAST 逻辑
     *
     * @param sorted 已排序的校验器列表，不得为 {@code null}（可为空列表）
     * @return 分段结果，外层顺序与 group 升序一致；空输入返回空列表
     */
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
