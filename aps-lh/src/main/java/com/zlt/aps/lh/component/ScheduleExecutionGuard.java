package com.zlt.aps.lh.component;

import com.zlt.aps.lh.api.enums.ScheduleStepEnum;
import com.zlt.aps.lh.exception.ScheduleErrorCode;
import com.zlt.aps.lh.exception.ScheduleException;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 排程执行互斥保护组件。
 *
 * <p>按工厂+目标日维度加分布式锁，避免同一排程窗口被并发执行。</p>
 *
 * @author APS
 */
@Slf4j
@Component
public class ScheduleExecutionGuard {

    private static final long DEFAULT_LOCK_TTL_MINUTES = 120L;
    private static final String LOCK_KEY_PREFIX = "APS:LH:SCHEDULE:LOCK:";

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) "
                    + "else return 0 end",
            Long.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 获取排程执行锁。
     *
     * @param factoryCode 工厂编码
     * @param targetDate  目标日
     * @return 锁令牌
     */
    public String acquire(String factoryCode, Date targetDate) {
        String lockKey = buildLockKey(factoryCode, targetDate);
        String token = UUID.randomUUID().toString();
        try {
            Boolean locked = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, token, DEFAULT_LOCK_TTL_MINUTES, TimeUnit.MINUTES);
            if (Boolean.TRUE.equals(locked)) {
                log.info("排程执行锁获取成功, key: {}", lockKey);
                return token;
            }
            throw new ScheduleException(ScheduleStepEnum.S4_1_PRE_VALIDATION,
                    ScheduleErrorCode.SCHEDULE_IN_PROGRESS,
                    factoryCode, null,
                    "当前工厂目标日已有排程执行中，请稍后重试。目标日: " + LhScheduleTimeUtil.getDateStr(targetDate));
        } catch (ScheduleException e) {
            throw e;
        } catch (Exception e) {
            log.error("排程执行锁获取失败, key: {}", lockKey, e);
            throw new ScheduleException(ScheduleStepEnum.S4_1_PRE_VALIDATION,
                    ScheduleErrorCode.SCHEDULE_IN_PROGRESS,
                    factoryCode, null,
                    "排程执行锁获取失败，请检查 Redis 可用性后重试", e);
        }
    }

    /**
     * 释放排程执行锁。
     *
     * @param factoryCode 工厂编码
     * @param targetDate  目标日
     * @param token       锁令牌
     */
    public void release(String factoryCode, Date targetDate, String token) {
        if (token == null) {
            return;
        }
        String lockKey = buildLockKey(factoryCode, targetDate);
        try {
            stringRedisTemplate.execute(RELEASE_SCRIPT, Collections.singletonList(lockKey), token);
            log.info("排程执行锁释放完成, key: {}", lockKey);
        } catch (Exception e) {
            log.warn("排程执行锁释放失败, key: {}, 原因: {}", lockKey, e.getMessage());
        }
    }

    private String buildLockKey(String factoryCode, Date targetDate) {
        return LOCK_KEY_PREFIX + factoryCode + ":" + LhScheduleTimeUtil.getDateStr(targetDate);
    }
}
