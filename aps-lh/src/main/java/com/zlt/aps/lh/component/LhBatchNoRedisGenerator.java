package com.zlt.aps.lh.component;

import com.zlt.aps.lh.api.constant.LhScheduleConstant;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 原子自增生成排程批次号（LHPC+yyyyMMdd+流水），按厂、按日隔离；Key 首次创建时设置 24 小时过期。
 *
 * @author APS
 */
@Component
public class LhBatchNoRedisGenerator {

    private static final String REDIS_KEY_PREFIX = "aps:lh:batch:seq:";
    private static final int TTL_HOURS = 24;
    private static final int MAX_SEQ_NO = 999;


    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 生成下一个批次号
     *
     * @param scheduleDate 排程日期
     * @param factoryCode  分厂编号
     * @return 批次号，如 LHPC20260330001
     */
    public String nextBatchNo(Date scheduleDate, String factoryCode) {
        String dateStr = LhScheduleTimeUtil.getDateStr(scheduleDate);
        String batchPrefix = LhScheduleConstant.BATCH_NO_PREFIX + dateStr;
        String redisKey = REDIS_KEY_PREFIX + factoryCode + ":" + dateStr;
        Long seq = stringRedisTemplate.opsForValue().increment(redisKey);
        if (seq == null) {
            throw new IllegalStateException("Redis INCR 返回 null，请检查 Redis 是否可用");
        }
        if (seq == 1L) {
            stringRedisTemplate.expire(redisKey, TTL_HOURS, TimeUnit.HOURS);
        }
        String tail = seq <= MAX_SEQ_NO ? String.format("%03d", seq) : String.valueOf(seq);
        return batchPrefix + tail;
    }
}
