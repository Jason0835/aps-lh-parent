/**
 * Copyright (c) 2008, 智立通（厦门）科技有限公司 All rights reserved。
 */
package com.zlt.aps.lh.engine.strategy.impl;

import com.zlt.aps.lh.api.constant.LhScheduleConstant;
import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.engine.strategy.IMouldChangeBalanceStrategy;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 默认换模均衡策略实现
 * <p>控制每日换模总数(最多15台)和早/中班换模均衡(早班8台, 中班7台), 夜班不换模</p>
 *
 * @author APS
 */
@Slf4j
@Component
public class DefaultMouldChangeBalanceStrategy implements IMouldChangeBalanceStrategy {

    /** dailyMouldChangeCountMap value数组下标：[0]=早班换模数, [1]=中班换模数 */
    private static final int IDX_MORNING = 0;
    private static final int IDX_AFTERNOON = 1;
    private static final String DATE_KEY_FORMAT = "yyyy-MM-dd";

    @Override
    public boolean hasCapacity(LhScheduleContext context, Date targetDate) {
        String dateKey = formatDateKey(targetDate);
        int[] counts = context.getDailyMouldChangeCountMap().getOrDefault(dateKey, new int[]{0, 0});
        int totalUsed = counts[IDX_MORNING] + counts[IDX_AFTERNOON];
        int dailyLimit = getDailyLimit(context);
        return totalUsed < dailyLimit;
    }

    @Override
    public Date allocateMouldChange(LhScheduleContext context, Date endingTime) {
        if (endingTime == null) {
            return null;
        }

        Date adjustedTime = endingTime;

        // 最多向后探索5天，避免死循环
        for (int dayOffset = 0; dayOffset < 5; dayOffset++) {
            // 若在禁止换模时间段内（20:00-次日6:00），延后到次日早班开始时间
            if (LhScheduleTimeUtil.isNoMouldChangeTime(context, adjustedTime)) {
                adjustedTime = getNextMorningShiftStart(context, adjustedTime);
            }

            String dateKey = formatDateKey(adjustedTime);
            int[] counts = context.getDailyMouldChangeCountMap().computeIfAbsent(dateKey, k -> new int[]{0, 0});

            int morningLimit = getMorningLimit(context);
            int afternoonLimit = getAfternoonLimit(context);

            if (LhScheduleTimeUtil.isMorningShift(context, adjustedTime)) {
                // 当前时间在早班
                if (counts[IDX_MORNING] < morningLimit) {
                    counts[IDX_MORNING]++;
                    log.debug("换模分配到早班, 日期: {}, 早班已用: {}/{}", dateKey, counts[IDX_MORNING], morningLimit);
                    return adjustedTime;
                }
                // 早班已满，换模后移到当天中班开始时间
                adjustedTime = LhScheduleTimeUtil.getAfternoonShiftStart(context, adjustedTime);
                continue;
            }

            if (LhScheduleTimeUtil.isAfternoonShift(context, adjustedTime)) {
                // 当前时间在中班
                if (counts[IDX_AFTERNOON] < afternoonLimit) {
                    counts[IDX_AFTERNOON]++;
                    log.debug("换模分配到中班, 日期: {}, 中班已用: {}/{}", dateKey, counts[IDX_AFTERNOON], afternoonLimit);
                    return adjustedTime;
                }
                // 中班也满了，延后到次日早班
                adjustedTime = getNextMorningShiftStart(context, adjustedTime);
                continue;
            }

            // 夜班不换模，直接延后到次日早班
            adjustedTime = getNextMorningShiftStart(context, adjustedTime);
        }

        log.warn("换模均衡分配失败，无可用换模班次, 原始时间: {}", endingTime);
        return null;
    }

    @Override
    public int getRemainingCapacity(LhScheduleContext context, Date targetDate) {
        String dateKey = formatDateKey(targetDate);
        int[] counts = context.getDailyMouldChangeCountMap().getOrDefault(dateKey, new int[]{0, 0});
        int totalUsed = counts[IDX_MORNING] + counts[IDX_AFTERNOON];
        int dailyLimit = getDailyLimit(context);
        return Math.max(0, dailyLimit - totalUsed);
    }

    /**
     * 获取次日早班开始时间
     */
    private Date getNextMorningShiftStart(LhScheduleContext context, Date currentTime) {
        Date nextDay = LhScheduleTimeUtil.addDays(LhScheduleTimeUtil.clearTime(currentTime), 1);
        return LhScheduleTimeUtil.buildTime(nextDay, LhScheduleTimeUtil.getMorningStartHour(context), 0, 0);
    }

    private String formatDateKey(Date date) {
        return new SimpleDateFormat(DATE_KEY_FORMAT).format(date);
    }

    private int getDailyLimit(LhScheduleContext context) {
        return context.getParamIntValue("DAILY_MOULD_CHANGE_LIMIT", LhScheduleConstant.DEFAULT_DAILY_MOULD_CHANGE_LIMIT);
    }

    private int getMorningLimit(LhScheduleContext context) {
        return context.getParamIntValue("MORNING_MOULD_CHANGE_LIMIT", LhScheduleConstant.DEFAULT_MORNING_MOULD_CHANGE_LIMIT);
    }

    private int getAfternoonLimit(LhScheduleContext context) {
        return context.getParamIntValue("AFTERNOON_MOULD_CHANGE_LIMIT", LhScheduleConstant.DEFAULT_AFTERNOON_MOULD_CHANGE_LIMIT);
    }
}
