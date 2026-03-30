package com.zlt.aps.lh.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zlt.aps.lh.api.domain.entity.LhUnscheduledResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 硫化未排产结果Mapper
 *
 * @author APS
 */
@Mapper
public interface LhUnscheduledResultMapper extends BaseMapper<LhUnscheduledResult> {

    /**
     * 根据排程日期和工厂删除
     *
     * @param scheduleDate 排程日期
     * @param factoryCode  分厂编号
     * @return 删除记录数
     */
    int deleteByDateAndFactory(@Param("scheduleDate") Date scheduleDate, @Param("factoryCode") String factoryCode);

    /**
     * 批量插入未排产结果
     *
     * @param list 未排产结果列表
     * @return 插入记录数
     */
    int insertBatch(@Param("list") List<LhUnscheduledResult> list);

    /**
     * 根据排程日期和工厂查询
     *
     * @param scheduleDate 排程日期
     * @param factoryCode  分厂编号
     * @return 未排产结果列表
     */
    List<LhUnscheduledResult> selectByDateAndFactory(@Param("scheduleDate") Date scheduleDate, @Param("factoryCode") String factoryCode);
}
