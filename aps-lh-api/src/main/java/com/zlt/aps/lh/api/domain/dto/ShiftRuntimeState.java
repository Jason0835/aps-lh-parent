package com.zlt.aps.lh.api.domain.dto;

import java.io.Serializable;

/**
 * 班次运行态（排程执行过程中与 {@link ShiftInfo} 分离的瞬时状态）
 * <p>描述某班次在算法运行时的剩余产能、是否可用及不可用原因等，不承载班次日历维度的静态信息。</p>
 *
 * @author APS
 */
public class ShiftRuntimeState implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 班次索引 */
    private int shiftIndex;

    /** 剩余产能 */
    private int remainingCapacity;

    /** 是否可用 */
    private boolean available;

    /** 不可用原因（可用时可为空） */
    private String unavailableReason;

    /**
     * 获取班次索引
     *
     * @return 班次索引
     */
    public int getShiftIndex() {
        return shiftIndex;
    }

    /**
     * 设置班次索引
     *
     * @param shiftIndex 班次索引
     */
    public void setShiftIndex(int shiftIndex) {
        this.shiftIndex = shiftIndex;
    }

    /**
     * 获取剩余产能
     *
     * @return 剩余产能
     */
    public int getRemainingCapacity() {
        return remainingCapacity;
    }

    /**
     * 设置剩余产能
     *
     * @param remainingCapacity 剩余产能
     */
    public void setRemainingCapacity(int remainingCapacity) {
        this.remainingCapacity = remainingCapacity;
    }

    /**
     * 是否可用
     *
     * @return true-可用，false-不可用
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * 设置是否可用
     *
     * @param available true-可用，false-不可用
     */
    public void setAvailable(boolean available) {
        this.available = available;
    }

    /**
     * 获取不可用原因
     *
     * @return 不可用原因，可用时可能为 null
     */
    public String getUnavailableReason() {
        return unavailableReason;
    }

    /**
     * 设置不可用原因
     *
     * @param unavailableReason 不可用原因
     */
    public void setUnavailableReason(String unavailableReason) {
        this.unavailableReason = unavailableReason;
    }
}
