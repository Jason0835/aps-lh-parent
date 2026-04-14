package com.zlt.aps.lh.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zlt.aps.lh.api.domain.entity.LhScheduleResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 硫化排程结果Mapper
 *
 * @author APS
 */
@Mapper
public interface LhScheduleResultMapper extends BaseMapper<LhScheduleResult> {

    /**
     * 批量插入排程结果
     *
     * @param list 排程结果列表
     * @return 插入记录数
     */
    int insertBatch(@Param("list") List<LhScheduleResult> list);
}
