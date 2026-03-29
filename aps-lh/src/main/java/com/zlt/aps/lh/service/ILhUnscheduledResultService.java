package com.zlt.aps.lh.service;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 硫化未排产结果服务接口
 *
 * @author APS
 */
public interface ILhUnscheduledResultService {

    /**
     * 根据排程日期和工厂删除未排产结果
     *
     * @param scheduleDate 排程日期
     * @param factoryCode  分厂编号
     * @return 删除记录数
     */
    int deleteByDateAndFactory(Date scheduleDate, String factoryCode);

    /**
     * 批量插入未排产结果
     *
     * @param list 未排产结果列表
     * @return 插入记录数
     */
    int insertBatch(List<Map<String, Object>> list);
}
