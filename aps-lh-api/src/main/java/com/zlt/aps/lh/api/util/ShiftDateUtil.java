package com.zlt.aps.lh.api.util;

import cn.hutool.core.date.DateUtil;

import java.util.Date;

/**
 * 班次业务日与日期偏移（与排程 T 日对齐），使用 Hutool 日期工具、默认时区与 {@link DateUtil} 一致
 *
 * @author APS
 */
public final class ShiftDateUtil {

    private ShiftDateUtil() {
    }

    /**
     * 由排程 T 日任意时刻与日期偏移得到业务日 0 点（与 LhScheduleTimeUtil.clearTime(addDays(T,offset)) 一致）
     *
     * @param scheduleT  排程 T 日（可为当日任意时刻，取日历日）
     * @param dateOffset 相对 T 的偏移天数
     * @return 业务日 00:00:00
     */
    public static Date workDateFromScheduleT(Date scheduleT, int dateOffset) {
        if (scheduleT == null) {
            return null;
        }
        Date tDayStart = DateUtil.beginOfDay(scheduleT);
        return DateUtil.offsetDay(tDayStart, dateOffset);
    }
}
