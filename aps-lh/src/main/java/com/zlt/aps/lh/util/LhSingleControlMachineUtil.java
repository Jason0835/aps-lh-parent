package com.zlt.aps.lh.util;

import com.zlt.aps.lh.api.constant.LhScheduleConstant;
import com.zlt.aps.lh.api.constant.LhScheduleParamConstant;
import com.zlt.aps.lh.context.LhScheduleContext;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 单控机台工具类。
 */
public final class LhSingleControlMachineUtil {

    /** 单控机台编码分隔正则 */
    private static final String MACHINE_CODE_SEPARATOR_REGEX = "[,，]";

    private LhSingleControlMachineUtil() {
    }

    /**
     * 解析单控基准机台编码。
     *
     * @param context 排程上下文
     * @return 单控基准机台编码集合
     */
    public static Set<String> resolveSingleControlBaseMachineCodes(LhScheduleContext context) {
        Set<String> machineCodeSet = new LinkedHashSet<>();
        if (Objects.isNull(context)) {
            return machineCodeSet;
        }
        String machineCodes = context.getParamValue(LhScheduleParamConstant.SINGLE_CONTROL_MACHINE_CODES,
                LhScheduleConstant.SINGLE_CONTROL_MACHINE_CODES);
        if (StringUtils.isEmpty(machineCodes)) {
            return machineCodeSet;
        }
        String[] machineCodeArray = machineCodes.split(MACHINE_CODE_SEPARATOR_REGEX);
        for (String machineCode : machineCodeArray) {
            String normalizedMachineCode = StringUtils.trim(machineCode);
            if (StringUtils.isNotEmpty(normalizedMachineCode)) {
                machineCodeSet.add(normalizedMachineCode);
            }
        }
        return machineCodeSet;
    }

    /**
     * 判断是否为单控基准机台。
     *
     * @param context 排程上下文
     * @param machineCode 机台编码
     * @return true-单控基准机台
     */
    public static boolean isSingleControlBaseMachine(LhScheduleContext context, String machineCode) {
        if (StringUtils.isEmpty(machineCode)) {
            return false;
        }
        Set<String> baseMachineCodeSet = resolveSingleControlBaseMachineCodes(context);
        return !CollectionUtils.isEmpty(baseMachineCodeSet) && baseMachineCodeSet.contains(machineCode);
    }

    /**
     * 判断是否为单控拆分机台。
     *
     * @param context 排程上下文
     * @param machineCode 机台编码
     * @return true-单控拆分机台
     */
    public static boolean isSingleControlSplitMachine(LhScheduleContext context, String machineCode) {
        if (StringUtils.isEmpty(machineCode)) {
            return false;
        }
        String baseMachineCode = resolveBaseMachineCode(machineCode);
        return StringUtils.isNotEmpty(baseMachineCode)
                && isSingleControlBaseMachine(context, baseMachineCode);
    }

    /**
     * 构建单控拆分机台编码。
     *
     * @param baseMachineCode 基准机台编码
     * @param leftRightMould 左右模
     * @return 拆分机台编码
     */
    public static String buildSplitMachineCode(String baseMachineCode, String leftRightMould) {
        if (StringUtils.isEmpty(baseMachineCode) || StringUtils.isEmpty(leftRightMould)) {
            return baseMachineCode;
        }
        return baseMachineCode + leftRightMould;
    }

    /**
     * 解析运行态机台对应的基础数据查找编码。
     *
     * @param context 排程上下文
     * @param machineCode 运行态机台编码
     * @return 基准机台编码；非单控拆分机台时返回自身
     */
    public static String resolveLookupMachineCode(LhScheduleContext context, String machineCode) {
        if (isSingleControlSplitMachine(context, machineCode)) {
            return resolveBaseMachineCode(machineCode);
        }
        return machineCode;
    }

    /**
     * 判断运行态机台编码是否可匹配基础数据机台编码。
     *
     * @param context 排程上下文
     * @param runtimeMachineCode 运行态机台编码
     * @param sourceMachineCode 基础数据机台编码
     * @return true-可匹配
     */
    public static boolean isCompatibleMachineCode(LhScheduleContext context,
                                                  String runtimeMachineCode,
                                                  String sourceMachineCode) {
        if (StringUtils.isEmpty(runtimeMachineCode) || StringUtils.isEmpty(sourceMachineCode)) {
            return false;
        }
        if (StringUtils.equals(runtimeMachineCode, sourceMachineCode)) {
            return true;
        }
        String runtimeBaseMachineCode = resolveBaseMachineCode(runtimeMachineCode);
        return StringUtils.isNotEmpty(runtimeBaseMachineCode)
                && isSingleControlBaseMachine(context, runtimeBaseMachineCode)
                && StringUtils.equals(runtimeBaseMachineCode, sourceMachineCode);
    }

    /**
     * 扩展配置机台对应的运行态机台编码集合。
     *
     * @param context 排程上下文
     * @param machineCode 配置机台编码
     * @return 运行态机台编码集合
     */
    public static Set<String> expandRuntimeMachineCodes(LhScheduleContext context, String machineCode) {
        Set<String> machineCodeSet = new LinkedHashSet<>(2);
        if (StringUtils.isEmpty(machineCode)) {
            return machineCodeSet;
        }
        if (isSingleControlBaseMachine(context, machineCode)) {
            machineCodeSet.add(buildSplitMachineCode(machineCode, LhScheduleConstant.LEFT_MOULD));
            machineCodeSet.add(buildSplitMachineCode(machineCode, LhScheduleConstant.RIGHT_MOULD));
            return machineCodeSet;
        }
        machineCodeSet.add(machineCode);
        return machineCodeSet;
    }

    /**
     * 判断左右模标识是否与运行态机台侧别兼容。
     *
     * @param machineCode 运行态机台编码
     * @param leftRightMould 左右模标识
     * @return true-兼容
     */
    public static boolean isLeftRightCompatible(String machineCode, String leftRightMould) {
        String splitSide = resolveSplitSide(machineCode);
        if (StringUtils.isEmpty(splitSide)
                || StringUtils.isEmpty(leftRightMould)
                || StringUtils.equalsIgnoreCase(leftRightMould, LhScheduleConstant.LEFT_RIGHT_MOULD)) {
            return true;
        }
        return StringUtils.equalsIgnoreCase(splitSide, leftRightMould);
    }

    /**
     * 解析拆分机台侧别。
     *
     * @param machineCode 机台编码
     * @return L/R；非拆分机台返回null
     */
    public static String resolveSplitSide(String machineCode) {
        if (StringUtils.isEmpty(machineCode)) {
            return null;
        }
        String normalizedMachineCode = machineCode.trim().toUpperCase(Locale.ROOT);
        if (normalizedMachineCode.endsWith(LhScheduleConstant.LEFT_MOULD)) {
            return LhScheduleConstant.LEFT_MOULD;
        }
        if (normalizedMachineCode.endsWith(LhScheduleConstant.RIGHT_MOULD)) {
            return LhScheduleConstant.RIGHT_MOULD;
        }
        return null;
    }

    /**
     * 从拆分机台编码还原基准机台编码。
     *
     * @param machineCode 机台编码
     * @return 基准机台编码，非拆分机台返回null
     */
    public static String resolveBaseMachineCode(String machineCode) {
        if (StringUtils.endsWith(machineCode, LhScheduleConstant.LEFT_MOULD)
                || StringUtils.endsWith(machineCode, LhScheduleConstant.RIGHT_MOULD)) {
            return machineCode.substring(0, machineCode.length() - 1);
        }
        return null;
    }
}
