package com.zlt.aps.lh.component;


import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 3位自增序列号生成器（线程安全）
 * 每个业务键（businessKey）对应独立的计数器，从001开始自增
 */
public class IncrSerialGenerator {
    // 存储业务键与对应计数器的映射，ConcurrentHashMap保证线程安全
    private static final ConcurrentHashMap<String, AtomicInteger> BUSINESS_COUNTER_MAP = new ConcurrentHashMap<>();
    // 3位序列号最大值
    private static final int MAX_VALUE = 999;
    // 每个业务默认的起始值（重置后从1开始，对应001）
    private static final int DEFAULT_START_VALUE = 1;
    // 默认的业务键
    private static final String DEFAULT_BUSINESS_KEY = "DEFAULT_BUSINESS_KEY";

    /**
     * 生成3位自增序列号
     * @return 对应业务的3位序列号（如001、010、999）
     */
    public static String generateSerial() {
        return generateSerial(DEFAULT_BUSINESS_KEY);
    }

    /**
     * 按业务键生成3位自增序列号
     * @param businessKey 业务唯一标识
     * @return 对应业务的3位序列号（如001、010、999）
     */
    public static String generateSerial(String businessKey) {
        if (StringUtils.isEmpty(businessKey)) {
            throw new IllegalArgumentException("业务键（businessKey）不能为空！");
        }
        // 1、初始化当前业务的计数器（不存在则创建，初始值为0，自增后为1）
        BUSINESS_COUNTER_MAP.putIfAbsent(businessKey, new AtomicInteger(0));
        AtomicInteger counter = BUSINESS_COUNTER_MAP.get(businessKey);
        // 2、自增并获取当前值
        int current = counter.incrementAndGet();
        // 3、超过999时自动重置当前业务的计数器
        if (current > MAX_VALUE) {
            // 按业务键重置
            resetSerial(businessKey);
            // 重置后起始为1
            current = DEFAULT_START_VALUE;
        }
        // 4、格式化补零，确保3位
        return String.format("%03d", current);
    }

    /**
     * 重置指定业务的序列号（重置后从001开始）
     * @param businessKey 业务唯一标识
     */
    public static void resetSerial(String businessKey) {
        if (StringUtils.isEmpty(businessKey)) {
            throw new IllegalArgumentException("业务键（businessKey）不能为空！");
        }
        BUSINESS_COUNTER_MAP.put(businessKey, new AtomicInteger(DEFAULT_START_VALUE - 1));
    }

    /**
     * 清空指定业务的计数器
     * @param businessKey 业务唯一标识
     */
    public static void clearBusinessCounter(String businessKey) {
        if (StringUtils.isNotEmpty(businessKey)) {
            BUSINESS_COUNTER_MAP.remove(businessKey);
        }
    }

}