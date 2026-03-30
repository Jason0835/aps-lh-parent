package com.zlt.aps.lh.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zlt.aps.lh.api.domain.entity.LhScheduleResult;
import com.zlt.aps.lh.component.LhBatchNoRedisGenerator;
import com.zlt.aps.lh.api.enums.DeleteFlagEnum;
import com.zlt.aps.lh.api.enums.ReleaseStatusEnum;
import com.zlt.aps.lh.mapper.LhScheduleResultMapper;
import com.zlt.aps.lh.service.ILhScheduleResultService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * 硫化排程结果服务实现
 *
 * @author APS
 */
@Slf4j
@Service
public class LhScheduleResultServiceImpl implements ILhScheduleResultService {

    @Resource
    private LhScheduleResultMapper mapper;

    @Resource
    private LhBatchNoRedisGenerator batchNoRedisGenerator;

    @Override
    public List<LhScheduleResult> selectByDateAndFactory(Date scheduleDate, String factoryCode) {
        return mapper.selectByDateAndFactory(scheduleDate, factoryCode);
    }

    @Override
    public List<LhScheduleResult> selectPreviousSchedule(Date scheduleDate, String factoryCode) {
        return mapper.selectPreviousSchedule(scheduleDate, factoryCode);
    }

    @Override
    public int deleteByDateAndFactory(Date scheduleDate, String factoryCode) {
        LambdaQueryWrapper<LhScheduleResult> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LhScheduleResult::getFactoryCode, factoryCode)
                .eq(LhScheduleResult::getScheduleDate, scheduleDate)
                .eq(LhScheduleResult::getIsDelete, DeleteFlagEnum.NORMAL.getCode());
        return mapper.delete(wrapper);
    }

    @Override
    public int insertBatch(List<LhScheduleResult> list) {
        if (list == null || list.isEmpty()) {
            return 0;
        }
        return mapper.insertBatch(list);
    }

    @Override
    public int countReleasedByDate(Date scheduleDate, String factoryCode) {
        LambdaQueryWrapper<LhScheduleResult> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LhScheduleResult::getFactoryCode, factoryCode)
                .eq(LhScheduleResult::getScheduleDate, scheduleDate)
                .eq(LhScheduleResult::getIsRelease, ReleaseStatusEnum.RELEASED.getCode())
                .eq(LhScheduleResult::getIsDelete, DeleteFlagEnum.NORMAL.getCode());
        return mapper.selectCount(wrapper).intValue();
    }

    @Override
    public String generateNextBatchNo(Date scheduleDate, String factoryCode) {
        return batchNoRedisGenerator.nextBatchNo(scheduleDate, factoryCode);
    }
}
