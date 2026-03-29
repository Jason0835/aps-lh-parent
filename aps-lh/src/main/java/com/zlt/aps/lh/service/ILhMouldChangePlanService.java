package com.zlt.aps.lh.service;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 模具交替计划服务接口
 *
 * @author APS
 */
public interface ILhMouldChangePlanService {

    /**
     * 根据排程日期和工厂删除模具交替计划
     *
     * @param scheduleDate 排程日期
     * @param factoryCode  分厂编号
     * @return 删除记录数
     */
    int deleteByDateAndFactory(Date scheduleDate, String factoryCode);

    /**
     * 批量插入模具交替计划
     *
     * @param list 模具交替计划列表
     * @return 插入记录数
     */
    int insertBatch(List<Map<String, Object>> list);
}
