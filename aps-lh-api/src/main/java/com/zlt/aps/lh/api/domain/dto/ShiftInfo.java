package com.zlt.aps.lh.api.domain.dto;

import com.zlt.aps.lh.api.enums.ShiftEnum;

import java.io.Serializable;
import java.util.Date;

/**
 * 排程班次信息（硫化排程中使用的班次数据对象）
 *
 * @author APS
 */
public class ShiftInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 班次索引（1～N，N≤8，对应 class1 到 classN） */
    private final int shiftIndex;

    /** 班次类型（夜班/早班/中班） */
    private final ShiftEnum shiftType;

    /** 所属日期 */
    private final Date workDate;

    /** 班次开始时间 */
    private final Date startTime;

    /** 班次结束时间 */
    private final Date endTime;

    /** 班次展示名称（含相对 T 日偏移前缀） */
    private final String shiftName;

    /**
     * 相对排程 T 日的日历偏移：0 表示 T 日，1 表示 T+1，2 表示 T+2
     */
    private final int dateOffset;

    /** 班次类型编码，与 {@link ShiftEnum#getCode()} 一致 */
    private final String shiftCode;

    /** 班次时长（分钟） */
    private final int durationMinutes;

    /** 是否跨越自然日（开始、结束在当地时区不属于同一日历日） */
    private final boolean crossesCalendarDay;

    /** 是否跨越自然月（开始、结束在当地时区不属于同一自然月） */
    private final boolean crossesMonth;

    /** 是否跨越自然年（开始、结束在当地时区不属于同一自然年） */
    private final boolean crossesYear;

    /**
     * 构造班次信息（全字段）
     *
     * @param shiftIndex          班次索引（1～8）
     * @param shiftType           班次类型枚举
     * @param workDate            逻辑归属日（当日 0 点）
     * @param startTime           班次开始时间
     * @param endTime             班次结束时间
     * @param shiftName           班次展示名称
     * @param dateOffset          相对 T 日偏移（0/1/2）
     * @param shiftCode           班次类型编码
     * @param durationMinutes     班次时长（分钟）
     * @param crossesCalendarDay  是否跨自然日
     * @param crossesMonth        是否跨自然月
     * @param crossesYear         是否跨自然年
     */
    public ShiftInfo(int shiftIndex, ShiftEnum shiftType, Date workDate, Date startTime, Date endTime,
            String shiftName, int dateOffset, String shiftCode, int durationMinutes,
            boolean crossesCalendarDay, boolean crossesMonth, boolean crossesYear) {
        this.shiftIndex = shiftIndex;
        this.shiftType = shiftType;
        this.workDate = workDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.shiftName = shiftName;
        this.dateOffset = dateOffset;
        this.shiftCode = shiftCode;
        this.durationMinutes = durationMinutes;
        this.crossesCalendarDay = crossesCalendarDay;
        this.crossesMonth = crossesMonth;
        this.crossesYear = crossesYear;
    }

    /**
     * 获取班次索引
     *
     * @return 班次索引
     */
    public int getShiftIndex() {
        return shiftIndex;
    }

    /**
     * 获取班次类型
     *
     * @return 班次类型
     */
    public ShiftEnum getShiftType() {
        return shiftType;
    }

    /**
     * 获取所属日期
     *
     * @return 工作日期
     */
    public Date getWorkDate() {
        return workDate;
    }

    /**
     * 获取班次开始时间
     *
     * @return 开始时间
     */
    public Date getStartTime() {
        return startTime;
    }

    /**
     * 获取班次结束时间
     *
     * @return 结束时间
     */
    public Date getEndTime() {
        return endTime;
    }

    /**
     * 获取班次展示名称
     *
     * @return 班次名称
     */
    public String getShiftName() {
        return shiftName;
    }

    /**
     * 获取相对排程 T 日的日历偏移
     *
     * @return 0 表示 T 日，1 表示 T+1，2 表示 T+2
     */
    public int getDateOffset() {
        return dateOffset;
    }

    /**
     * 获取班次类型编码
     *
     * @return 与枚举编码一致
     */
    public String getShiftCode() {
        return shiftCode;
    }

    /**
     * 获取班次时长（分钟）
     *
     * @return 时长分钟数
     */
    public int getDurationMinutes() {
        return durationMinutes;
    }

    /**
     * 是否跨越自然日
     *
     * @return true-跨日
     */
    public boolean isCrossesCalendarDay() {
        return crossesCalendarDay;
    }

    /**
     * 是否跨越自然月
     *
     * @return true-跨月
     */
    public boolean isCrossesMonth() {
        return crossesMonth;
    }

    /**
     * 是否跨越自然年
     *
     * @return true-跨年
     */
    public boolean isCrossesYear() {
        return crossesYear;
    }

    /**
     * 判断是否为夜班
     *
     * @return true-夜班
     */
    public boolean isNightShift() {
        return ShiftEnum.NIGHT_SHIFT == shiftType;
    }

    /**
     * 判断是否为早班
     *
     * @return true-早班
     */
    public boolean isMorningShift() {
        return ShiftEnum.MORNING_SHIFT == shiftType;
    }

    /**
     * 判断是否为中班
     *
     * @return true-中班
     */
    public boolean isAfternoonShift() {
        return ShiftEnum.AFTERNOON_SHIFT == shiftType;
    }

    /**
     * 判断是否允许换模（早班和中班允许，但中班20:00后不允许）
     *
     * @return true-允许换模
     */
    public boolean isAllowMouldChange() {
        return !isNightShift();
    }

    @Override
    public String toString() {
        return "ShiftInfo{班次索引=" + shiftIndex + ", 类型=" + shiftType.getDescription()
                + ", 名称=" + shiftName + ", 偏移=T+" + dateOffset + ", 编码=" + shiftCode
                + ", 时长分钟=" + durationMinutes + ", 跨日=" + crossesCalendarDay
                + ", 跨月=" + crossesMonth + ", 跨年=" + crossesYear
                + ", 开始=" + startTime + ", 结束=" + endTime + "}";
    }
}
