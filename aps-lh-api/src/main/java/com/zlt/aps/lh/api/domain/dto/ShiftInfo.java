package com.zlt.aps.lh.api.domain.dto;

import com.zlt.aps.lh.api.enums.ShiftEnum;

import java.util.Date;

/**
 * 排程班次信息（硫化排程中使用的班次数据对象）
 *
 * @author APS
 */
public class ShiftInfo {

    /** 班次索引（1-8，对应class1到class8） */
    private final int shiftIndex;

    /** 班次类型（夜班/早班/中班） */
    private final ShiftEnum shiftType;

    /** 所属日期 */
    private final Date workDate;

    /** 班次开始时间 */
    private final Date startTime;

    /** 班次结束时间 */
    private final Date endTime;

    /**
     * 构造班次信息
     *
     * @param shiftIndex 班次索引
     * @param shiftType  班次类型
     * @param workDate   所属日期
     * @param startTime  开始时间
     * @param endTime    结束时间
     */
    public ShiftInfo(int shiftIndex, ShiftEnum shiftType, Date workDate, Date startTime, Date endTime) {
        this.shiftIndex = shiftIndex;
        this.shiftType = shiftType;
        this.workDate = workDate;
        this.startTime = startTime;
        this.endTime = endTime;
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
                + ", 开始=" + startTime + ", 结束=" + endTime + "}";
    }
}
