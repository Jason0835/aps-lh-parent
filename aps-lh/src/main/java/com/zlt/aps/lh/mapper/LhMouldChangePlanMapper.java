package com.zlt.aps.lh.mapper;

import com.zlt.aps.lh.api.domain.entity.LhMouldChangePlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 模具交替计划Mapper
 *
 * @author APS
 */
@Mapper
public interface LhMouldChangePlanMapper {

    /**
     * 根据排程日期和工厂删除
     *
     * @param scheduleDate 排程日期
     * @param factoryCode  分厂编号
     * @return 删除记录数
     */
    int deleteByDateAndFactory(@Param("scheduleDate") Date scheduleDate, @Param("factoryCode") String factoryCode);

    /**
     * 批量插入模具交替计划
     *
     * @param list 模具交替计划列表
     * @return 插入记录数
     */
    int insertBatch(@Param("list") List<LhMouldChangePlan> list);

    /**
     * 根据排程日期和工厂查询
     *
     * @param scheduleDate 排程日期
     * @param factoryCode  分厂编号
     * @return 模具交替计划列表
     */
    List<LhMouldChangePlan> selectByDateAndFactory(@Param("scheduleDate") Date scheduleDate, @Param("factoryCode") String factoryCode);
}
