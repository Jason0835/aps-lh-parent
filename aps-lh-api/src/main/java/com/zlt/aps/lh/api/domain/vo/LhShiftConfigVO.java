package com.zlt.aps.lh.api.domain.vo;

import com.zlt.aps.lh.api.domain.entity.LhShiftConfig;
import com.zlt.aps.lh.api.enums.ShiftEnum;
import com.zlt.aps.lh.api.util.ShiftBoundaryUtil;
import com.zlt.aps.lh.api.util.ShiftDateUtil;
import com.zlt.aps.lh.api.util.ShiftTypeParseUtil;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

/**
 * 排程用班次视图：继承表字段；业务日、绝对起止时刻、跨日/月/年等由排程 T 日与配置推导。
 *
 * @author APS
 */
public class LhShiftConfigVO extends LhShiftConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 排程 T 日（引擎时间轴起点），用于推导业务日与绝对时刻 */
    private Date scheduleBaseDate;

    /** 缓存的合成开始时刻 */
    private Date cachedShiftStart;

    /** 缓存的合成结束时刻 */
    private Date cachedShiftEnd;

    /** 是否跨越自然日 */
    private boolean crossesCalendarDay;

    /** 是否跨越自然月 */
    private boolean crossesMonth;

    /** 是否跨越自然年 */
    private boolean crossesYear;

    /**
     * 已为当前字段状态尝试过合成且失败；避免在数据未变时重复计算，任意影响边界的字段变更后由 {@link #invalidateBoundsCache()} 清零
     */
    private boolean failedBoundsResolution;

    /** 与缓存起止对应的输入指纹，见 {@link #computeBoundsCacheKey()} */
    private String boundsCacheKey;

    /**
     * 设置排程 T 日锚点（由解析入口注入）
     *
     * @param scheduleBaseDate T 日
     */
    public void setScheduleBaseDate(Date scheduleBaseDate) {
        this.scheduleBaseDate = scheduleBaseDate;
        invalidateBoundsCache();
    }

    /**
     * 获取排程 T 日锚点
     *
     * @return T 日
     */
    public Date getScheduleBaseDate() {
        return scheduleBaseDate;
    }

    private void invalidateBoundsCache() {
        this.cachedShiftStart = null;
        this.cachedShiftEnd = null;
        this.boundsCacheKey = null;
        this.crossesCalendarDay = false;
        this.crossesMonth = false;
        this.crossesYear = false;
        this.failedBoundsResolution = false;
    }

    /**
     * 由影响绝对边界的字段拼成缓存键（与 setter/锚点变更保持一致）
     *
     * @return 指纹串
     */
    private String computeBoundsCacheKey() {
        return (scheduleBaseDate == null ? "null" : Long.toString(scheduleBaseDate.getTime()))
                + '|' + String.valueOf(getDateOffset())
                + '|' + String.valueOf(getShiftType())
                + '|' + String.valueOf(getStartTime())
                + '|' + String.valueOf(getEndTime());
    }

    @Override
    public void setStartTime(String startTime) {
        super.setStartTime(startTime);
        invalidateBoundsCache();
    }

    @Override
    public void setEndTime(String endTime) {
        super.setEndTime(endTime);
        invalidateBoundsCache();
    }

    @Override
    public void setDateOffset(Integer dateOffset) {
        super.setDateOffset(dateOffset);
        invalidateBoundsCache();
    }

    @Override
    public void setShiftType(String shiftType) {
        super.setShiftType(shiftType);
        invalidateBoundsCache();
    }

    /**
     * 由 T 日与日期偏移推导的业务日 0 点
     *
     * @return 业务日，无法推导时返回 null
     */
    public Date getWorkDate() {
        if (scheduleBaseDate == null || getDateOffset() == null) {
            return null;
        }
        return ShiftDateUtil.workDateFromScheduleT(scheduleBaseDate, getDateOffset());
    }

    /**
     * 解析班次类型枚举
     *
     * @return 枚举，无法识别返回 null
     */
    public ShiftEnum resolveShiftTypeEnum() {
        return ShiftTypeParseUtil.parse(getShiftType());
    }

    /**
     * 合成后的班次绝对开始时间
     *
     * @return 开始时间
     */
    public Date getShiftStartDateTime() {
        ensureBoundsResolved();
        return cachedShiftStart;
    }

    /**
     * 合成后的班次绝对结束时间
     *
     * @return 结束时间
     */
    public Date getShiftEndDateTime() {
        ensureBoundsResolved();
        return cachedShiftEnd;
    }

    private void ensureBoundsResolved() {
        if (failedBoundsResolution) {
            return;
        }
        String key = computeBoundsCacheKey();
        if (cachedShiftStart != null && Objects.equals(boundsCacheKey, key)) {
            return;
        }
        cachedShiftStart = null;
        cachedShiftEnd = null;
        boundsCacheKey = null;
        Date wd = getWorkDate();
        ShiftEnum type = resolveShiftTypeEnum();
        if (wd == null || type == null || StringUtils.isEmpty(getStartTime()) || StringUtils.isEmpty(getEndTime())) {
            crossesCalendarDay = false;
            crossesMonth = false;
            crossesYear = false;
            failedBoundsResolution = true;
            return;
        }
        int offset = Objects.requireNonNull(getDateOffset());
        Date[] pair = ShiftBoundaryUtil.resolveAbsoluteBounds(wd, offset, type, getStartTime(), getEndTime());
        cachedShiftStart = pair[0];
        cachedShiftEnd = pair[1];
        boundsCacheKey = key;
        boolean[] d = new boolean[1];
        boolean[] m = new boolean[1];
        boolean[] y = new boolean[1];
        ShiftBoundaryUtil.fillCrossFlags(cachedShiftStart, cachedShiftEnd, d, m, y);
        crossesCalendarDay = d[0];
        crossesMonth = m[0];
        crossesYear = y[0];
        failedBoundsResolution = false;
    }

    /**
     * 是否跨越自然日
     *
     * @return true 跨日
     */
    public boolean isCrossesCalendarDay() {
        ensureBoundsResolved();
        return crossesCalendarDay;
    }

    /**
     * 是否跨越自然月
     *
     * @return true 跨月
     */
    public boolean isCrossesMonth() {
        ensureBoundsResolved();
        return crossesMonth;
    }

    /**
     * 是否跨越自然年
     *
     * @return true 跨年
     */
    public boolean isCrossesYear() {
        ensureBoundsResolved();
        return crossesYear;
    }

    /**
     * 班次时长（分钟）。
     * <p>当 {@code shiftDuration}（小时）配置为正数时，直接按配置换算为分钟，<b>不与</b>合成起止时刻再校验；
     * 否则使用 {@link #getShiftStartDateTime()} 与 {@link #getShiftEndDateTime()} 的时间差（分钟）。</p>
     *
     * @return 分钟数
     */
    public int getDurationMinutes() {
        if (getShiftDuration() != null && getShiftDuration() > 0) {
            return getShiftDuration() * 60;
        }
        Date s = getShiftStartDateTime();
        Date e = getShiftEndDateTime();
        if (s == null || e == null) {
            return 0;
        }
        long diff = e.getTime() - s.getTime();
        if (diff <= 0) {
            return 0;
        }
        return (int) (diff / 60000L);
    }

    /**
     * 是否为夜班
     *
     * @return true 夜班
     */
    public boolean isNightShift() {
        return ShiftEnum.NIGHT_SHIFT == resolveShiftTypeEnum();
    }

    /**
     * 是否为早班
     *
     * @return true 早班
     */
    public boolean isMorningShift() {
        return ShiftEnum.MORNING_SHIFT == resolveShiftTypeEnum();
    }

    /**
     * 是否为中班
     *
     * @return true 中班
     */
    public boolean isAfternoonShift() {
        return ShiftEnum.AFTERNOON_SHIFT == resolveShiftTypeEnum();
    }

    /**
     * 是否允许换模（早班、中班允许；中班禁止规则由时间工具另行判断）
     *
     * @return true 允许
     */
    public boolean isAllowMouldChange() {
        return !isNightShift();
    }

    @Override
    public String toString() {
        return "LhShiftConfigVO{shiftIndex=" + getShiftIndex()
                + ", shiftType=" + getShiftType()
                + ", workDate=" + getWorkDate()
                + ", startHms=" + getStartTime()
                + ", endHms=" + getEndTime()
                + ", crossDay=" + (getShiftStartDateTime() != null && isCrossesCalendarDay())
                + ", start=" + getShiftStartDateTime()
                + ", end=" + getShiftEndDateTime()
                + "}";
    }
}
