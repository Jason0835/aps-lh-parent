package com.zlt.aps.lh.engine.strategy.impl;

import com.zlt.aps.lh.api.constant.LhScheduleConstant;
import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.engine.strategy.IProductionShutdownStrategy;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import com.zlt.aps.mdm.api.domain.entity.MdmWorkCalendar;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Calendar;
import java.util.Date;

/**
 * 默认开停产处理策略实现
 * <p>根据工作日历计算停产递减比例和开产首日调整</p>
 *
 * @author APS
 */
@Slf4j
@Component
public class DefaultProductionShutdownStrategy implements IProductionShutdownStrategy {

    @Override
    public BigDecimal calculateShutdownRate(LhScheduleContext context, String machineCode, Date targetDate) {
        MdmWorkCalendar calendar = findWorkCalendar(context, targetDate);
        if (calendar == null) {
            return BigDecimal.ONE;
        }

        // 若工作日历有配置比例，直接使用
        if (calendar.getRate() != null && calendar.getRate() < 100) {
            return BigDecimal.valueOf(calendar.getRate()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }

        // 停产日返回0
        if (isShutdownByCalendar(calendar)) {
            return BigDecimal.ZERO;
        }

        // 仅依据上下文已加载的排程窗口日历（T～T+SCHEDULE_DAYS-1），向后最多看 SCHEDULE_DAYS-1 天停产（offset 与递减比例见常量注释）
        int maxAheadDays = Math.max(0, LhScheduleTimeUtil.getScheduleDays(context) - 1);
        for (int offset = 1; offset <= maxAheadDays; offset++) {
            Date futureDate = LhScheduleTimeUtil.addDays(targetDate, offset);
            MdmWorkCalendar futureCalendar = findWorkCalendar(context, futureDate);
            if (futureCalendar != null && isShutdownByCalendar(futureCalendar)) {
                switch (offset) {
                    case 1:
                        return BigDecimal.valueOf(LhScheduleConstant.SHUTDOWN_DAY_MINUS_1_RATE).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    case 2:
                        return BigDecimal.valueOf(LhScheduleConstant.SHUTDOWN_DAY_MINUS_2_RATE).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    default:
                        break;
                }
            }
        }

        return BigDecimal.ONE;
    }

    @Override
    public boolean isShutdownDay(LhScheduleContext context, String machineCode, Date targetDate) {
        MdmWorkCalendar calendar = findWorkCalendar(context, targetDate);
        return calendar != null && isShutdownByCalendar(calendar);
    }

    @Override
    public boolean isStartupDay(LhScheduleContext context, String machineCode, Date targetDate) {
        // 当天是开产，前一天是停产，则为开产首日
        if (isShutdownDay(context, machineCode, targetDate)) {
            return false;
        }
        Date previousDay = LhScheduleTimeUtil.addDays(targetDate, -1);
        return isShutdownDay(context, machineCode, previousDay);
    }

    @Override
    public int adjustCapacityForShutdown(LhScheduleContext context, SkuScheduleDTO skuDto, int originalCapacity) {
        if (originalCapacity <= 0) {
            return 0;
        }
        Date targetDate = context.getScheduleDate();
        String machineCode = skuDto.getContinuousMachineCode();

        // 开产首日约50%产能
        if (isStartupDay(context, machineCode, targetDate)) {
            int adjusted = (int) (originalCapacity * LhScheduleConstant.STARTUP_FIRST_DAY_RATE / 100.0);
            log.debug("开产首日产能调整, 机台: {}, 原始: {}, 调整后: {}", machineCode, originalCapacity, adjusted);
            return adjusted;
        }

        // 按停产递减比例调整
        BigDecimal rate = calculateShutdownRate(context, machineCode, targetDate);
        if (rate.compareTo(BigDecimal.ONE) < 0) {
            int adjusted = rate.multiply(BigDecimal.valueOf(originalCapacity)).setScale(0, RoundingMode.DOWN).intValue();
            log.debug("停产递减产能调整, 机台: {}, 比例: {}, 原始: {}, 调整后: {}", machineCode, rate, originalCapacity, adjusted);
            return adjusted;
        }

        return originalCapacity;
    }

    /**
     * 从工作日历列表中查找指定日期的记录（硫化工序）
     */
    private MdmWorkCalendar findWorkCalendar(LhScheduleContext context, Date targetDate) {
        if (targetDate == null || context.getWorkCalendarList() == null) {
            return null;
        }
        Calendar cal = Calendar.getInstance();
        cal.setTime(targetDate);
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DAY_OF_MONTH);

        for (MdmWorkCalendar wc : context.getWorkCalendarList()) {
            if (LhScheduleConstant.PROC_CODE_LH.equals(wc.getProcCode())
                    && year == (wc.getYear() != null ? wc.getYear() : 0)
                    && month == (wc.getMonth() != null ? wc.getMonth() : 0)
                    && day == (wc.getDay() != null ? wc.getDay() : 0)) {
                return wc;
            }
        }
        return null;
    }

    /**
     * 根据工作日历判断是否为停产日
     * <p>dayFlag="0"，或三班均为停产标志</p>
     */
    private boolean isShutdownByCalendar(MdmWorkCalendar calendar) {
        if ("0".equals(calendar.getDayFlag())) {
            return true;
        }
        // 三班全部停产也视为停产日
        boolean shift1Stop = "0".equals(calendar.getOneShiftFlag());
        boolean shift2Stop = "0".equals(calendar.getTwoShiftFlag());
        boolean shift3Stop = "0".equals(calendar.getThreeShiftFlag());
        return shift1Stop && shift2Stop && shift3Stop;
    }
}
