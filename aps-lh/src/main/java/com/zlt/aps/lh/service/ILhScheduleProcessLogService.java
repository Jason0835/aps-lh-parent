package com.zlt.aps.lh.service;

import java.util.List;
import java.util.Map;

/**
 * 排程过程日志服务接口
 *
 * @author APS
 */
public interface ILhScheduleProcessLogService {

    /**
     * 根据批次号删除排程日志
     *
     * @param batchNo 批次号
     * @return 删除记录数
     */
    int deleteByBatchNo(String batchNo);

    /**
     * 批量插入排程过程日志
     *
     * @param list 日志列表
     * @return 插入记录数
     */
    int insertBatch(List<Map<String, Object>> list);
}
