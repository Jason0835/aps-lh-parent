package com.zlt.aps.lh.engine.chain.validators;

import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.engine.chain.IDataValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 硫化机台信息校验器
 *
 * @author APS
 */
@Slf4j
@Component
public class MachineInfoValidator implements IDataValidator {

    @Override
    public boolean validate(LhScheduleContext context) {
        if (context.getMachineInfoMap() == null || context.getMachineInfoMap().isEmpty()) {
            log.warn("硫化机台信息为空, 工厂: {}", context.getFactoryCode());
            return false;
        }
        // 检查是否有启用状态的机台
        long enabledCount = context.getMachineInfoMap().values().stream()
                .filter(m -> "0".equals(m.getStatus()))
                .count();
        if (enabledCount == 0) {
            log.warn("无启用状态的硫化机台, 工厂: {}", context.getFactoryCode());
            return false;
        }
        log.info("硫化机台校验通过, 总数: {}, 启用: {}", context.getMachineInfoMap().size(), enabledCount);
        return true;
    }

    @Override
    public String getValidatorName() {
        return "硫化机台信息校验";
    }

    @Override
    public int getOrder() {
        return 20;
    }
}
