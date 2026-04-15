package com.zlt.aps.lh.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 数据校验器开关配置
 * <p>key 为校验器唯一标识，value 为是否启用；未配置时默认启用。</p>
 *
 * @author APS
 */
@Data
@Component
@ConfigurationProperties(prefix = "aps.lh.validation")
public class DataValidatorProperties {

    /** 校验器启用状态配置 */
    private Map<String, Boolean> validators = new LinkedHashMap<>(16);

    /**
     * 判断指定校验器是否启用
     *
     * @param validatorKey 校验器唯一标识
     * @return true 表示启用，false 表示禁用
     */
    public boolean isValidatorEnabled(String validatorKey) {
        if (CollectionUtils.isEmpty(validators)) {
            return true;
        }
        Boolean enabled = validators.get(validatorKey);
        if (Objects.isNull(enabled)) {
            return true;
        }
        return enabled;
    }
}
