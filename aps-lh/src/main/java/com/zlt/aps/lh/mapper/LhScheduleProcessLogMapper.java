package com.zlt.aps.lh.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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
public interface LhScheduleProcessLogMapper extends BaseMapper<LhScheduleProcessLog> {

    /**
     * 批量插入排程过程日志
     *
     * @param list 日志列表
     * @return 插入记录数
     */
    int insertBatch(@Param("list") List<LhScheduleProcessLog> list);
}
