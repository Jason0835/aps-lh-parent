package com.zlt.aps.lh.api.util;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.zlt.aps.lh.api.enums.ShiftEnum;

import java.util.Calendar;
import java.util.Date;
import java.util.Objects;

/**
 * 由业务日（T+offset 的 0 点）、班次类型、时刻字符串合成绝对起止时间；
 * 日界与偏移使用 Hutool {@link DateUtil}，时刻落在某日时使用与硫化排程默认模板（Calendar 组合年月日时分秒）相同的默认时区语义，避免与 8 班硬编码时间轴不一致
 *
 * @author APS
 */
public final class ShiftBoundaryUtil {

    private ShiftBoundaryUtil() {
    }

    /**
     * 将某日 0 点基准与时分秒合成为绝对时间（与排程模块 {@code Calendar#getInstance()} 默认时区一致）
     */
    private static Date atTimeOnDay(Date dayStart, int hour, int minute, int second) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(dayStart);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, second);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    /**
     * 合成班次绝对开始、结束时间
     *
     * @param workDate     业务日 0 点（已含 T+offset）
     * @param dateOffset   日期偏移（夜班分支需要）
     * @param shiftType    班次类型
     * @param startTimeHms 开始 HH:mm:ss（或 Hutool 可解析的变体）
     * @param endTimeHms   结束 HH:mm:ss（或 Hutool 可解析的变体）
     * @return 长度为 2，[0] 开始 [1] 结束
     */
    public static Date[] resolveAbsoluteBounds(Date workDate, int dateOffset, ShiftEnum shiftType,
            String startTimeHms, String endTimeHms) {
        Objects.requireNonNull(workDate, "workDate");
        Objects.requireNonNull(shiftType, "shiftType");
        int[] hs = ShiftTimeParseUtil.parseToHms(startTimeHms);
        int[] he = ShiftTimeParseUtil.parseToHms(endTimeHms);
        Date anchorDay = DateUtil.beginOfDay(workDate);

        Date start;
        Date end;

        if (shiftType == ShiftEnum.NIGHT_SHIFT) {
            if (dateOffset > 0) {
                Date prevDay = DateUtil.offsetDay(anchorDay, -1);
                start = atTimeOnDay(prevDay, hs[0], hs[1], hs[2]);
                end = atTimeOnDay(anchorDay, he[0], he[1], he[2]);
            } else {
                start = atTimeOnDay(anchorDay, hs[0], hs[1], hs[2]);
                Date nextDay = DateUtil.offsetDay(anchorDay, 1);
                end = atTimeOnDay(nextDay, he[0], he[1], he[2]);
            }
        } else {
            start = atTimeOnDay(anchorDay, hs[0], hs[1], hs[2]);
            end = atTimeOnDay(anchorDay, he[0], he[1], he[2]);
            if (!end.after(start)) {
                end = DateUtil.offsetDay(end, 1);
            }
        }

        return new Date[]{start, end};
    }

    /**
     * 根据绝对起止时刻计算是否跨自然日、跨自然月、跨年（与 {@link DateUtil} 所用默认日历/时区一致）
     *
     * @param start    开始
     * @param end      结束
     * @param outDay   长度 1，输出是否跨自然日
     * @param outMonth 长度 1，输出是否跨自然月
     * @param outYear  长度 1，输出是否跨年
     */
    public static void fillCrossFlags(Date start, Date end, boolean[] outDay, boolean[] outMonth, boolean[] outYear) {
        if (start == null || end == null) {
            outDay[0] = false;
            outMonth[0] = false;
            outYear[0] = false;
            return;
        }
        DateTime d1 = DateUtil.date(start);
        DateTime d2 = DateUtil.date(end);
        outDay[0] = !DateUtil.isSameDay(start, end);
        outMonth[0] = d1.year() != d2.year() || d1.month() != d2.month();
        outYear[0] = d1.year() != d2.year();
    }
}
