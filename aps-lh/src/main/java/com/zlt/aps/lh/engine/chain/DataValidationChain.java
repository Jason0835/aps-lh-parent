package com.zlt.aps.lh.engine.chain;

import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 数据校验链
 * <p>自动收集所有IDataValidator实现, 按order排序后依次执行校验。
 * 任一校验器失败则整体失败。</p>
 *
 * @author APS
 */
@Slf4j
@Component
public class DataValidationChain {

    @Resource
    private List<IDataValidator> validators;

    private final List<IDataValidator> orderedValidators = new ArrayList<>();

    @PostConstruct
    public void init() {
        if (validators != null) {
            orderedValidators.addAll(validators);
            orderedValidators.sort(Comparator.comparingInt(IDataValidator::getOrder));
        }
        log.info("数据校验链初始化完成, 校验器数量: {}", orderedValidators.size());
    }

    /**
     * 执行所有校验器
     *
     * @param context 排程上下文
     * @return true-全部通过, false-存在失败
     */
    public boolean validate(LhScheduleContext context) {
        for (IDataValidator validator : orderedValidators) {
            log.info("执行校验器: {}", validator.getValidatorName());
            boolean passed = validator.validate(context);
            if (!passed) {
                log.warn("校验器[{}]校验失败", validator.getValidatorName());
                return false;
            }
        }
        log.info("所有数据校验通过");
        return true;
    }
}
