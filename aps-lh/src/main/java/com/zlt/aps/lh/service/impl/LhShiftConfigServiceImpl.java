package com.zlt.aps.lh.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zlt.aps.lh.api.constant.LhScheduleConstant;
import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.dto.ShiftInfo;
import com.zlt.aps.lh.api.domain.entity.LhShiftConfig;
import com.zlt.aps.lh.api.enums.DeleteFlagEnum;
import com.zlt.aps.lh.api.enums.ShiftEnum;
import com.zlt.aps.lh.mapper.LhShiftConfigMapper;
import com.zlt.aps.lh.service.ILhShiftConfigService;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 硫化班次配置加载与解析
 *
 * @author APS
 */
@Slf4j
@Service
public class LhShiftConfigServiceImpl implements ILhShiftConfigService {

    @Resource
    private LhShiftConfigMapper lhShiftConfigMapper;

    @Override
    public List<ShiftInfo> resolveAndAttachScheduleShifts(LhScheduleContext context) {
        String factoryCode = context.getFactoryCode();
        Date scheduleDate = context.getScheduleDate();
        if (StringUtils.isEmpty(factoryCode) || scheduleDate == null) {
            throw new IllegalArgumentException("工厂编号或排程T日为空，无法加载班次配置");
        }

        List<LhShiftConfig> rows = lhShiftConfigMapper.selectList(
                new LambdaQueryWrapper<LhShiftConfig>()
                        .eq(LhShiftConfig::getFactoryCode, factoryCode)
                        .eq(LhShiftConfig::getIsDelete, DeleteFlagEnum.NORMAL.getCode())
                        .orderByAsc(LhShiftConfig::getShiftIndex));

        List<ShiftInfo> shifts;
        if (CollectionUtils.isEmpty(rows)) {
            log.info("工厂[{}]无班次表数据，使用默认班次模板", factoryCode);
            shifts = LhScheduleTimeUtil.buildDefaultScheduleShifts(context, scheduleDate);
        } else {
            int scheduleDays = LhScheduleTimeUtil.getScheduleDays(context);
            shifts = buildShiftsFromConfig(context, scheduleDate, rows, scheduleDays);
        }

        context.setScheduleWindowShifts(shifts);
        return shifts;
    }

    /**
     * 将表记录转为 {@link ShiftInfo} 并校验
     */
    private List<ShiftInfo> buildShiftsFromConfig(LhScheduleContext context, Date scheduleDate,
            List<LhShiftConfig> rows, int scheduleDays) {
        int n = rows.size();
        if (n > LhScheduleConstant.MAX_SHIFT_SLOT_COUNT) {
            throw new IllegalArgumentException("班次配置超过上限" + LhScheduleConstant.MAX_SHIFT_SLOT_COUNT + "，当前=" + n);
        }
        for (int i = 0; i < n; i++) {
            LhShiftConfig row = rows.get(i);
            int expect = i + 1;
            if (row.getShiftIndex() == null || row.getShiftIndex() != expect) {
                throw new IllegalArgumentException(
                        "班次序号必须从1连续递增，期望 SHIFT_INDEX=" + expect + "，实际=" + row.getShiftIndex());
            }
            if (row.getDateOffset() == null) {
                throw new IllegalArgumentException("DATE_OFFSET 不能为空，SHIFT_INDEX=" + expect);
            }
            if (row.getDateOffset() < 0 || row.getDateOffset() >= scheduleDays) {
                throw new IllegalArgumentException(
                        "DATE_OFFSET 越界：要求 0≤DATE_OFFSET<排程天数(" + scheduleDays + ")，SHIFT_INDEX=" + expect);
            }
            if (row.getStartTime() == null || row.getEndTime() == null) {
                throw new IllegalArgumentException("START_TIME/END_TIME 不能为空，SHIFT_INDEX=" + expect);
            }
            ShiftEnum type = parseShiftType(row.getShiftType());
            if (type == null) {
                throw new IllegalArgumentException("无法识别班次类型：" + row.getShiftType() + "，SHIFT_INDEX=" + expect);
            }
        }

        List<ShiftInfo> list = new ArrayList<>(n);
        for (LhShiftConfig row : rows) {
            ShiftEnum shiftType = parseShiftType(row.getShiftType());
            int dateOffset = row.getDateOffset();
            Date anchorDay = LhScheduleTimeUtil.clearTime(LhScheduleTimeUtil.addDays(scheduleDate, dateOffset));

            int[] hs = extractHms(row.getStartTime());
            int[] he = extractHms(row.getEndTime());

            Date startTime;
            Date endTime;
            if (shiftType == ShiftEnum.NIGHT_SHIFT) {
                if (dateOffset > 0) {
                    startTime = LhScheduleTimeUtil.buildTime(LhScheduleTimeUtil.addDays(anchorDay, -1),
                            hs[0], hs[1], hs[2]);
                    endTime = LhScheduleTimeUtil.buildTime(anchorDay, he[0], he[1], he[2]);
                } else {
                    startTime = LhScheduleTimeUtil.buildTime(anchorDay, hs[0], hs[1], hs[2]);
                    endTime = LhScheduleTimeUtil.buildTime(LhScheduleTimeUtil.addDays(anchorDay, 1),
                            he[0], he[1], he[2]);
                }
            } else {
                startTime = LhScheduleTimeUtil.buildTime(anchorDay, hs[0], hs[1], hs[2]);
                endTime = LhScheduleTimeUtil.buildTime(anchorDay, he[0], he[1], he[2]);
                if (!endTime.after(startTime)) {
                    endTime = LhScheduleTimeUtil.addDays(endTime, 1);
                }
            }

            Date workDate;
            if (row.getWorkDate() != null) {
                workDate = LhScheduleTimeUtil.clearTime(row.getWorkDate());
            } else {
                workDate = anchorDay;
            }

            int durationMinutes = resolveDurationMinutes(row, startTime, endTime);
            String shiftName = StringUtils.isNotEmpty(row.getShiftName())
                    ? row.getShiftName()
                    : buildShiftDisplayName(dateOffset, shiftType);

            boolean[] crossDay = new boolean[1];
            boolean[] crossMonth = new boolean[1];
            boolean[] crossYear = new boolean[1];
            LhScheduleTimeUtil.fillCrossFlagsForShift(startTime, endTime, crossDay, crossMonth, crossYear);

            list.add(new ShiftInfo(row.getShiftIndex(), shiftType, workDate, startTime, endTime,
                    shiftName, dateOffset, shiftType.getCode(), durationMinutes,
                    crossDay[0], crossMonth[0], crossYear[0]));
        }
        return list;
    }

    private static int resolveDurationMinutes(LhShiftConfig row, Date startTime, Date endTime) {
        if (row.getShiftDuration() != null && row.getShiftDuration() > 0) {
            return row.getShiftDuration() * 60;
        }
        long diff = endTime.getTime() - startTime.getTime();
        if (diff <= 0) {
            return 0;
        }
        return (int) (diff / 60000L);
    }

    private static int[] extractHms(Date d) {
        Calendar c = Calendar.getInstance();
        c.setTime(Objects.requireNonNull(d));
        return new int[]{
                c.get(Calendar.HOUR_OF_DAY),
                c.get(Calendar.MINUTE),
                c.get(Calendar.SECOND)
        };
    }

    /**
     * 解析班次类型：支持枚举编码 01/02/03 及中文 早班/中班/夜班
     *
     * @param raw 配置值
     * @return 枚举，无法识别返回 null
     */
    private static ShiftEnum parseShiftType(String raw) {
        if (StringUtils.isEmpty(raw)) {
            return null;
        }
        String t = raw.trim();
        ShiftEnum byCode = ShiftEnum.getByCode(t);
        if (byCode != null) {
            return byCode;
        }
        if (t.contains("夜")) {
            return ShiftEnum.NIGHT_SHIFT;
        }
        if (t.contains("早")) {
            return ShiftEnum.MORNING_SHIFT;
        }
        if (t.contains("中")) {
            return ShiftEnum.AFTERNOON_SHIFT;
        }
        return null;
    }

    private static String buildShiftDisplayName(int dateOffset, ShiftEnum type) {
        String prefix;
        if (dateOffset == 0) {
            prefix = "T日";
        } else {
            prefix = "T+" + dateOffset + "日";
        }
        return prefix + type.getDescription();
    }
}
