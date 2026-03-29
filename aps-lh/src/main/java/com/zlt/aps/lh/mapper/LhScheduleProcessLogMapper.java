package com.zlt.aps.lh.mapper;

import com.zlt.aps.lh.api.domain.entity.LhScheduleProcessLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 排程过程日志Mapper
 *
 * @author APS
 */
@Mapper
public interface LhScheduleProcessLogMapper {

    /**
     * 根据批次号删除日志
     *
     * @param batchNo 批次号
     * @return 删除记录数
     */
    int deleteByBatchNo(@Param("batchNo") String batchNo);

    /**
     * 批量插入排程过程日志
     *
     * @param list 日志列表
     * @return 插入记录数
     */
    int insertBatch(@Param("list") List<LhScheduleProcessLog> list);

    /**
     * 根据批次号查询日志
     *
     * @param batchNo 批次号
     * @return 日志列表
     */
    List<LhScheduleProcessLog> selectByBatchNo(@Param("batchNo") String batchNo);
}
