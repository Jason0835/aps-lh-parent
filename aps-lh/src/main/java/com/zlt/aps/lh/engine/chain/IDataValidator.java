package com.zlt.aps.lh.engine.chain;

import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.api.enums.ValidationPolicyEnum;

/**
 * 数据校验器接口(责任链节点)
 * <p>同一 {@link #getGroup()} 内各校验器的 {@link #getValidationPolicy()} 必须一致。</p>
 *
 * @author APS
 */
public interface IDataValidator {

    /**
     * 执行校验
     * <p>失败时应调用 {@link LhScheduleContext#addValidationError(String)} 写入可读原因，并返回 false。</p>
     *
     * @param context 排程上下文
     * @return true 表示本项校验通过，false 表示未通过
     */
    boolean validate(LhScheduleContext context);

    /**
     * 获取校验器唯一标识
     * <p>用于外部配置启停开关，默认根据实现类名生成 lowerCamelCase 标识。</p>
     *
     * @return 校验器唯一标识
     */
    default String getValidatorKey() {
        String simpleName = this.getClass().getSimpleName();
        if (simpleName.length() <= 1) {
            return simpleName.toLowerCase();
        }
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }

    /**
     * 判断当前校验器在本次上下文中是否启用
     * <p>默认返回 true，表示由责任链继续执行；实现类可按工厂、排程模式等上下文特征覆写。</p>
     *
     * @param context 排程上下文
     * @return true 表示启用，false 表示跳过当前校验器
     */
    default boolean isEnabled(LhScheduleContext context) {
        return true;
    }

    /**
     * 获取校验器名称
     *
     * @return 校验器名称
     */
    String getValidatorName();

    /**
     * 获取校验分组编号（越小越先执行；同组内再按 {@link #getOrder()} 排序）
     *
     * @return 分组编号，建议使用 {@link com.zlt.aps.lh.api.constant.LhDataValidationGroupConstant} 中的常量
     */
    int getGroup();

    /**
     * 获取组内执行策略（与同组其他校验器须一致）
     *
     * @return 聚合全跑或遇错即停
     */
    ValidationPolicyEnum getValidationPolicy();

    /**
     * 获取校验顺序(同组内值越小越先执行)
     *
     * @return 排序值
     */
    int getOrder();
}
