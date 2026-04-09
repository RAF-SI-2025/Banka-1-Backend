package com.banka1.order.entity.enums;

/**
 * Lifecycle status of a brokerage order.
 */
public enum OrderStatus {
    /** Created but waiting for an explicit client/agent confirmation before finalization. */
    PENDING_CONFIRMATION,
    /** Waiting for supervisor approval (applies to agents with needApproval=true or exceeded limits). */
    PENDING,
    /** Approved by a supervisor and queued for execution. */
    APPROVED,
    /** Rejected by a supervisor or automatically declined (e.g. expired settlement date). */
    DECLINED,
    /** All portions of the order have been successfully executed. */
    DONE,
    /** The order was cancelled before full execution. */
    CANCELLED
}
