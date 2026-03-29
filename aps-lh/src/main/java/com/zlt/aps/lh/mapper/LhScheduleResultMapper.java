package com.zlt.aps.lh.mapper;

import com.zlt.aps.lh.api.domain.entity.LhScheduleResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 硫化排程结果Mapper
 *
 * @author APS
 */
@Mapper
public interface LhScheduleResultMapper {

    /**
     * 根据批次号删除排程结果
     *
     * @param batchNo 批次号
     * @return 删除记录数
     */
    int deleteByBatchNo(@Param("batchNo") String batchNo);

    /**
     * 根据排程日期和工厂删除
     *
     * @param scheduleDate 排程日期
     * @param factoryCode  分厂编号
     * @return 删除记录数
     */
    int deleteByDateAndFactory(@Param("scheduleDate") Date scheduleDate, @Param("factoryCode") String factoryCode);

    /**
     * 批量插入排程结果
     *
     * @param list 排程结果列表
     * @return 插入记录数
     */
    int insertBatch(@Param("list") List<LhScheduleResult> list);

    /**
     * 根据排程日期和工厂查询
     *
     * @param scheduleDate 排程日期
     * @param factoryCode  分厂编号
     * @return 排程结果列表
     */
    List<LhScheduleResult> selectByDateAndFactory(@Param("scheduleDate") Date scheduleDate, @Param("factoryCode") String factoryCode);

    /**
     * 查询前日排程
     *
     * @param scheduleDate 排程日期
     * @param factoryCode  分厂编号
     * @return 前日排程结果列表
     */
    List<LhScheduleResult> selectPreviousSchedule(@Param("scheduleDate") Date scheduleDate, @Param("factoryCode") String factoryCode);

    /**
     * 检查是否已下发MES
     *
     * @param scheduleDate 排程日期
     * @param factoryCode  分厂编号
     * @return 已下发记录数
     */
    int countReleasedByDate(@Param("scheduleDate") Date scheduleDate, @Param("factoryCode") String factoryCode);

    /**
     * 根据批次号查询排程结果
     *
     * @param batchNo 批次号
     * @return 排程结果列表
     */
    List<LhScheduleResult> selectByBatchNo(@Param("batchNo") String batchNo);

    /**
     * 更新批次号对应记录的发布状态
     *
     * @param batchNo         批次号
     * @param releaseStatus   发布状态 (0-未发布, 1-已发布, 2-发布失败)
     * @return 更新记录数
     */
    int updateReleaseStatus(@Param("batchNo") String batchNo, @Param("releaseStatus") String releaseStatus);
}
