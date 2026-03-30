package com.zlt.aps.lh.service;

import com.zlt.aps.lh.api.domain.context.LhScheduleContext;

/**
 * 硫化排程基础数据服务接口
 * <p>负责加载排程所需的所有基础数据到上下文</p>
 *
 * @author APS
 */
public interface ILhBaseDataService {

    /**
     * 加载所有基础数据到排程上下文
     *
     * @param context 排程上下文
     */
    void loadAllBaseData(LhScheduleContext context);
}
