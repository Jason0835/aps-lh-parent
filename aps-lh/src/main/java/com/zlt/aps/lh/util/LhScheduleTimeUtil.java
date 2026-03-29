package com.zlt.aps.lh.util;

import com.zlt.aps.lh.api.constant.LhScheduleConstant;
import com.zlt.aps.lh.api.enums.ShiftEnum;
import com.zlt.aps.lh.api.domain.context.LhScheduleContext;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * 硫化排程时间工具类
 * <p>提供班次时间计算、排程日期推算等通用时间处理方法</p>
 *
 * @author APS
 */
public final class LhScheduleTimeUtil {

    /**
     * 参数代码 - 班次数量（每天3个班）
     */
    public static final String PARAM_SHIFTS_PER_DAY = "SHIFTS_PER_DAY";

    /**
     * 参数代码 - 夜班开始小时
     */
    public static final String PARAM_NIGHT_START_HOUR = "NIGHT_START_HOUR";

    /**
     * 参数代码 - 早班开始小时
     */
    public static final String PARAM_MORNING_START_HOUR = "MORNING_START_HOUR";

    /**
     * 参数代码 - 中班开始小时
     */
    public static final String PARAM_AFTERNOON_START_HOUR = "AFTERNOON_START_HOUR";

    /**
     * 参数代码 - 每班时长（小时）
     */
    public static final String PARAM_SHIFT_DURATION_HOURS = "SHIFT_DURATION_HOURS";

    /**
     * 参数代码 - 禁止换模开始小时
     */
    public static final String PARAM_NO_MOULD_CHANGE_START = "NO_MOULD_CHANGE_START";

    /**
     * 参数代码 - 换模含预热时间（小时）
     */
    public static final String PARAM_MOULD_CHANGE_TOTAL_HOURS = "MOULD_CHANGE_TOTAL_HOURS";

    /**
     * 参数代码 - 首检时间（小时）
     */
    public static final String PARAM_FIRST_INSPECTION_HOURS = "FIRST_INSPECTION_HOURS";

    /**
     * 参数代码 - 每日换模上限
     */
    public static final String PARAM_DAILY_MOULD_CHANGE_LIMIT = "DAILY_MOULD_CHANGE_LIMIT";

    /**
     * 参数代码 - 早班换模上限
     */
    public static final String PARAM_MORNING_MOULD_CHANGE_LIMIT = "MORNING_MOULD_CHANGE_LIMIT";

    /**
     * 参数代码 - 中班换模上限
     */
    public static final String PARAM_AFTERNOON_MOULD_CHANGE_LIMIT = "AFTERNOON_MOULD_CHANGE_LIMIT";

    /**
     * 参数代码 - 收尾判定班次数（默认3天=9班，但触发收尾标注是3天内）
     */
    public static final String PARAM_ENDING_DETECT_DAYS = "ENDING_DETECT_DAYS";

    /**
     * 参数代码 - 结构收尾预判天数
     */
    public static final String PARAM_STRUCTURE_ENDING_DAYS = "STRUCTURE_ENDING_DAYS";

    /**
     * 参数代码 - 机台收尾时间容差（分钟）
     */
    public static final String PARAM_ENDING_TOLERANCE_MINUTES = "ENDING_TOLERANCE_MINUTES";

    /**
     * 参数代码 - 保养耗时（小时）
     */
    public static final String PARAM_MAINTENANCE_DURATION_HOURS = "MAINTENANCE_DURATION_HOURS";

    /**
     * 参数代码 - 胶囊上机预热时间（小时，2.5h）
     */
    public static final String PARAM_CAPSULE_PREHEAT_HOURS = "CAPSULE_PREHEAT_HOURS";

    private LhScheduleTimeUtil() {
    }

    /**
     * 获取夜班开始小时（从参数或默认值）
     *
     * @param context 排程上下文
     * @return 夜班开始小时
     */
    public static int getNightStartHour(LhScheduleContext context) {
        return context.getParamIntValue(PARAM_NIGHT_START_HOUR, LhScheduleConstant.NIGHT_SHIFT_START_HOUR);
    }

    /**
     * 获取早班开始小时（从参数或默认值）
     *
     * @param context 排程上下文
     * @return 早班开始小时
     */
    public static int getMorningStartHour(LhScheduleContext context) {
        return context.getParamIntValue(PARAM_MORNING_START_HOUR, LhScheduleConstant.MORNING_SHIFT_START_HOUR);
    }

    /**
     * 获取中班开始小时（从参数或默认值）
     *
     * @param context 排程上下文
     * @return 中班开始小时
     */
    public static int getAfternoonStartHour(LhScheduleContext context) {
        return context.getParamIntValue(PARAM_AFTERNOON_START_HOUR, LhScheduleConstant.AFTERNOON_SHIFT_START_HOUR);
    }

    /**
     * 获取每班时长（从参数或默认值）
     *
     * @param context 排程上下文
     * @return 每班时长（小时）
     */
    public static int getShiftDurationHours(LhScheduleContext context) {
        return context.getParamIntValue(PARAM_SHIFT_DURATION_HOURS, LhScheduleConstant.SHIFT_DURATION_HOURS);
    }

    /**
     * 获取换模含预热总时长（小时）
     *
     * @param context 排程上下文
     * @return 换模总时长（小时）
     */
    public static int getMouldChangeTotalHours(LhScheduleContext context) {
        return context.getParamIntValue(PARAM_MOULD_CHANGE_TOTAL_HOURS, LhScheduleConstant.MOULD_CHANGE_TOTAL_HOURS);
    }

    /**
     * 获取首检时间（小时）
     *
     * @param context 排程上下文
     * @return 首检时间（小时）
     */
    public static int getFirstInspectionHours(LhScheduleContext context) {
        return context.getParamIntValue(PARAM_FIRST_INSPECTION_HOURS, LhScheduleConstant.FIRST_INSPECTION_HOURS);
    }

    /**
     * 获取禁止换模开始小时
     *
     * @param context 排程上下文
     * @return 禁止换模开始小时（默认20点）
     */
    public static int getNoMouldChangeStartHour(LhScheduleContext context) {
        return context.getParamIntValue(PARAM_NO_MOULD_CHANGE_START, LhScheduleConstant.NO_MOULD_CHANGE_START_HOUR);
    }

    /**
     * 获取每日换模总上限
     *
     * @param context 排程上下文
     * @return 每日换模总上限（默认15台）
     */
    public static int getDailyMouldChangeLimit(LhScheduleContext context) {
        return context.getParamIntValue(PARAM_DAILY_MOULD_CHANGE_LIMIT, LhScheduleConstant.DEFAULT_DAILY_MOULD_CHANGE_LIMIT);
    }

    /**
     * 获取早班换模上限
     *
     * @param context 排程上下文
     * @return 早班换模上限（默认8台）
     */
    public static int getMorningMouldChangeLimit(LhScheduleContext context) {
        return context.getParamIntValue(PARAM_MORNING_MOULD_CHANGE_LIMIT, LhScheduleConstant.DEFAULT_MORNING_MOULD_CHANGE_LIMIT);
    }

    /**
     * 获取中班换模上限
     *
     * @param context 排程上下文
     * @return 中班换模上限（默认7台）
     */
    public static int getAfternoonMouldChangeLimit(LhScheduleContext context) {
        return context.getParamIntValue(PARAM_AFTERNOON_MOULD_CHANGE_LIMIT, LhScheduleConstant.DEFAULT_AFTERNOON_MOULD_CHANGE_LIMIT);
    }

    /**
     * 获取收尾判定天数（可配置，默认3天）
     *
     * @param context 排程上下文
     * @return 收尾判定天数
     */
    public static int getEndingDetectDays(LhScheduleContext context) {
        return context.getParamIntValue(PARAM_ENDING_DETECT_DAYS, LhScheduleConstant.DEFAULT_ENDING_DAYS);
    }

    /**
     * 获取机台收尾时间容差（分钟）
     *
     * @param context 排程上下文
     * @return 收尾时间容差（分钟，默认20分钟）
     */
    public static int getEndingToleranceMinutes(LhScheduleContext context) {
        return context.getParamIntValue(PARAM_ENDING_TOLERANCE_MINUTES, LhScheduleConstant.DEFAULT_ENDING_TIME_TOLERANCE_MINUTES);
    }

    /**
     * 根据排程日期（T日）获取排程的8个班次信息列表
     * <p>
     * 排程8个班次：<br/>
     * 1班：T日早班(6:00-14:00)<br/>
     * 2班：T日中班(14:00-22:00)<br/>
     * 3班：T+1日夜班(22:00-6:00)<br/>
     * 4班：T+1日早班(6:00-14:00)<br/>
     * 5班：T+1日中班(14:00-22:00)<br/>
     * 6班：T+2日夜班(22:00-6:00)<br/>
     * 7班：T+2日早班(6:00-14:00)<br/>
     * 8班：T+2日中班(14:00-22:00)
     * </p>
     *
     * @param context      排程上下文
     * @param scheduleDate 排程日期（T日）
     * @return 8个班次信息列表
     */
    public static List<ShiftInfo> getScheduleShifts(LhScheduleContext context, Date scheduleDate) {
        int morningHour = getMorningStartHour(context);
        int afternoonHour = getAfternoonStartHour(context);
        int nightHour = getNightStartHour(context);
        int shiftDuration = getShiftDurationHours(context);

        List<ShiftInfo> shifts = new ArrayList<>(8);

        // T日早班（班次1）
        Date tDayMorningStart = buildTime(scheduleDate, morningHour, 0, 0);
        Date tDayMorningEnd = addHours(tDayMorningStart, shiftDuration);
        shifts.add(new ShiftInfo(1, ShiftEnum.MORNING_SHIFT, scheduleDate, tDayMorningStart, tDayMorningEnd));

        // T日中班（班次2）
        Date tDayAfternoonStart = buildTime(scheduleDate, afternoonHour, 0, 0);
        Date tDayAfternoonEnd = addHours(tDayAfternoonStart, shiftDuration);
        shifts.add(new ShiftInfo(2, ShiftEnum.AFTERNOON_SHIFT, scheduleDate, tDayAfternoonStart, tDayAfternoonEnd));

        // T+1日（T日夜班22:00到次日6:00，T+1日夜班指T日22:00开始）
        Date tPlus1Day = addDays(scheduleDate, 1);
        // T+1日夜班开始时间 = T日22:00
        Date tPlus1NightStart = buildTime(scheduleDate, nightHour, 0, 0);
        Date tPlus1NightEnd = buildTime(tPlus1Day, morningHour, 0, 0);
        shifts.add(new ShiftInfo(3, ShiftEnum.NIGHT_SHIFT, tPlus1Day, tPlus1NightStart, tPlus1NightEnd));

        // T+1日早班（班次4）
        Date tPlus1MorningStart = buildTime(tPlus1Day, morningHour, 0, 0);
        Date tPlus1MorningEnd = addHours(tPlus1MorningStart, shiftDuration);
        shifts.add(new ShiftInfo(4, ShiftEnum.MORNING_SHIFT, tPlus1Day, tPlus1MorningStart, tPlus1MorningEnd));

        // T+1日中班（班次5）
        Date tPlus1AfternoonStart = buildTime(tPlus1Day, afternoonHour, 0, 0);
        Date tPlus1AfternoonEnd = addHours(tPlus1AfternoonStart, shiftDuration);
        shifts.add(new ShiftInfo(5, ShiftEnum.AFTERNOON_SHIFT, tPlus1Day, tPlus1AfternoonStart, tPlus1AfternoonEnd));

        // T+2日
        Date tPlus2Day = addDays(scheduleDate, 2);
        // T+2日夜班开始时间 = T+1日22:00
        Date tPlus2NightStart = buildTime(tPlus1Day, nightHour, 0, 0);
        Date tPlus2NightEnd = buildTime(tPlus2Day, morningHour, 0, 0);
        shifts.add(new ShiftInfo(6, ShiftEnum.NIGHT_SHIFT, tPlus2Day, tPlus2NightStart, tPlus2NightEnd));

        // T+2日早班（班次7）
        Date tPlus2MorningStart = buildTime(tPlus2Day, morningHour, 0, 0);
        Date tPlus2MorningEnd = addHours(tPlus2MorningStart, shiftDuration);
        shifts.add(new ShiftInfo(7, ShiftEnum.MORNING_SHIFT, tPlus2Day, tPlus2MorningStart, tPlus2MorningEnd));

        // T+2日中班（班次8）
        Date tPlus2AfternoonStart = buildTime(tPlus2Day, afternoonHour, 0, 0);
        Date tPlus2AfternoonEnd = addHours(tPlus2AfternoonStart, shiftDuration);
        shifts.add(new ShiftInfo(8, ShiftEnum.AFTERNOON_SHIFT, tPlus2Day, tPlus2AfternoonStart, tPlus2AfternoonEnd));

        return shifts;
    }

    /**
     * 根据时间点判断所在班次索引（1-8）
     *
     * @param context      排程上下文
     * @param scheduleDate 排程日期（T日）
     * @param time         时间点
     * @return 班次索引（1-8），若不在任意班次内返回-1
     */
    public static int getShiftIndex(LhScheduleContext context, Date scheduleDate, Date time) {
        List<ShiftInfo> shifts = getScheduleShifts(context, scheduleDate);
        for (ShiftInfo shift : shifts) {
            if (!time.before(shift.getStartTime()) && time.before(shift.getEndTime())) {
                return shift.getShiftIndex();
            }
        }
        return -1;
    }

    /**
     * 根据班次索引获取班次信息
     *
     * @param context      排程上下文
     * @param scheduleDate 排程日期（T日）
     * @param shiftIndex   班次索引（1-8）
     * @return 班次信息，未找到返回null
     */
    public static ShiftInfo getShiftByIndex(LhScheduleContext context, Date scheduleDate, int shiftIndex) {
        List<ShiftInfo> shifts = getScheduleShifts(context, scheduleDate);
        for (ShiftInfo shift : shifts) {
            if (shift.getShiftIndex() == shiftIndex) {
                return shift;
            }
        }
        return null;
    }

    /**
     * 判断指定时间是否在禁止换模时段（20:00 - 次日6:00）
     *
     * @param context 排程上下文
     * @param time    时间点
     * @return true-禁止换模，false-可以换模
     */
    public static boolean isNoMouldChangeTime(LhScheduleContext context, Date time) {
        int noChangeStart = getNoMouldChangeStartHour(context);
        int morningHour = getMorningStartHour(context);
        Calendar cal = Calendar.getInstance();
        cal.setTime(time);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        // 20点之后或者6点之前属于禁止换模时段
        return hour >= noChangeStart || hour < morningHour;
    }

    /**
     * 判断指定时间是否在早班时段
     *
     * @param context 排程上下文
     * @param time    时间点
     * @return true-早班时段
     */
    public static boolean isMorningShift(LhScheduleContext context, Date time) {
        int morningHour = getMorningStartHour(context);
        int afternoonHour = getAfternoonStartHour(context);
        Calendar cal = Calendar.getInstance();
        cal.setTime(time);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        return hour >= morningHour && hour < afternoonHour;
    }

    /**
     * 判断指定时间是否在中班时段
     *
     * @param context 排程上下文
     * @param time    时间点
     * @return true-中班时段
     */
    public static boolean isAfternoonShift(LhScheduleContext context, Date time) {
        int afternoonHour = getAfternoonStartHour(context);
        int nightHour = getNightStartHour(context);
        Calendar cal = Calendar.getInstance();
        cal.setTime(time);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        return hour >= afternoonHour && hour < nightHour;
    }

    /**
     * 获取某天早班开始时间
     *
     * @param context 排程上下文
     * @param date    日期
     * @return 早班开始时间
     */
    public static Date getMorningShiftStart(LhScheduleContext context, Date date) {
        return buildTime(date, getMorningStartHour(context), 0, 0);
    }

    /**
     * 获取某天中班开始时间
     *
     * @param context 排程上下文
     * @param date    日期
     * @return 中班开始时间
     */
    public static Date getAfternoonShiftStart(LhScheduleContext context, Date date) {
        return buildTime(date, getAfternoonStartHour(context), 0, 0);
    }

    /**
     * 获取某天夜班开始时间（当天22:00）
     *
     * @param context 排程上下文
     * @param date    日期
     * @return 夜班开始时间
     */
    public static Date getNightShiftStart(LhScheduleContext context, Date date) {
        return buildTime(date, getNightStartHour(context), 0, 0);
    }

    /**
     * 判断两个时间是否在同一天
     *
     * @param date1 日期1
     * @param date2 日期2
     * @return true-同一天
     */
    public static boolean isSameDay(Date date1, Date date2) {
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(date1);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(date2);
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
                && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    /**
     * 清除时间中的时分秒，只保留日期部分
     *
     * @param date 日期时间
     * @return 仅日期（00:00:00）
     */
    public static Date clearTime(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    /**
     * 在指定日期基础上加N天
     *
     * @param date 基础日期
     * @param days 天数
     * @return 加天后的日期
     */
    public static Date addDays(Date date, int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DAY_OF_MONTH, days);
        return cal.getTime();
    }

    /**
     * 在指定时间基础上加N小时
     *
     * @param time  基础时间
     * @param hours 小时数
     * @return 加小时后的时间
     */
    public static Date addHours(Date time, int hours) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(time);
        cal.add(Calendar.HOUR_OF_DAY, hours);
        return cal.getTime();
    }

    /**
     * 在指定时间基础上加N分钟
     *
     * @param time    基础时间
     * @param minutes 分钟数
     * @return 加分钟后的时间
     */
    public static Date addMinutes(Date time, int minutes) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(time);
        cal.add(Calendar.MINUTE, minutes);
        return cal.getTime();
    }

    /**
     * 构建指定日期+时:分:秒的时间对象
     *
     * @param date   日期（年月日取此参数）
     * @param hour   小时
     * @param minute 分钟
     * @param second 秒
     * @return 组合后的时间
     */
    public static Date buildTime(Date date, int hour, int minute, int second) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, second);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    /**
     * 计算两个时间之间的秒数差
     *
     * @param start 开始时间
     * @param end   结束时间
     * @return 秒数差（可负）
     */
    public static long diffSeconds(Date start, Date end) {
        return (end.getTime() - start.getTime()) / 1000L;
    }

    /**
     * 计算两个时间之间的小时数差（向下取整）
     *
     * @param start 开始时间
     * @param end   结束时间
     * @return 小时数差（可负）
     */
    public static long diffHours(Date start, Date end) {
        return diffSeconds(start, end) / 3600L;
    }

    /**
     * 判断两个时间之差是否在指定分钟容差范围内（|time1 - time2| <= toleranceMinutes）
     *
     * @param time1             时间1
     * @param time2             时间2
     * @param toleranceMinutes  容差分钟数
     * @return true-在容差范围内
     */
    public static boolean withinTolerance(Date time1, Date time2, int toleranceMinutes) {
        long diffMs = Math.abs(time1.getTime() - time2.getTime());
        return diffMs <= (long) toleranceMinutes * 60 * 1000L;
    }

    /**
     * 获取日期字符串（yyyyMMdd格式）
     *
     * @param date 日期
     * @return 日期字符串，如"20260327"
     */
    public static String getDateStr(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DAY_OF_MONTH);
        return String.format("%04d%02d%02d", year, month, day);
    }

    /**
     * 班次信息内部类（排程中使用的班次数据对象）
     */
    public static class ShiftInfo {

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

        public ShiftInfo(int shiftIndex, ShiftEnum shiftType, Date workDate, Date startTime, Date endTime) {
            this.shiftIndex = shiftIndex;
            this.shiftType = shiftType;
            this.workDate = workDate;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public int getShiftIndex() {
            return shiftIndex;
        }

        public ShiftEnum getShiftType() {
            return shiftType;
        }

        public Date getWorkDate() {
            return workDate;
        }

        public Date getStartTime() {
            return startTime;
        }

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
}
