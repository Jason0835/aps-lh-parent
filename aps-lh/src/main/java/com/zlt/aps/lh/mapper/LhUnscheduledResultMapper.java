package com.zlt.aps.lh.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zlt.aps.lh.api.domain.entity.LhUnscheduledResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 硫化未排产结果Mapper
 *
 * @author APS
 */
@Mapper
public interface LhUnscheduledResultMapper extends BaseMapper<LhUnscheduledResult> {

    /**
     * 批量插入未排产结果
     *
     * @param list 未排产结果列表
     * @return 插入记录数
     */
    int insertBatch(@Param("list") List<LhUnscheduledResult> list);
}
