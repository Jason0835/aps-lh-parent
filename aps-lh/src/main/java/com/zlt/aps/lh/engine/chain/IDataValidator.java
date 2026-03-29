package com.zlt.aps.lh.engine.chain;

import com.zlt.aps.lh.api.domain.context.LhScheduleContext;

/**
 * 数据校验器接口(责任链节点)
 *
 * @author APS
 */
public interface IDataValidator {

    /**
     * 执行校验
     *
     * @param context 排程上下文
     * @return true-校验通过, false-校验失败
     */
    boolean validate(LhScheduleContext context);

    /**
     * 获取校验器名称
     *
     * @return 校验器名称
     */
    String getValidatorName();

    /**
     * 获取校验顺序(值越小越先执行)
     *
     * @return 排序值
     */
    int getOrder();
}
