package com.futspring.backend.domain.finance;

public final class FinanceTypes {
    private FinanceTypes() {}
    public enum AuditOperation { SETTINGS, MANAGERS, TEMPLATE, LIST_SAVE, PUBLISH, CLOSE, REOPEN, CANCEL, DUPLICATE, CHARGE_REPLACE, CHARGE_SAVE, PARTICIPANTS, CHARGE_CANCEL, RECEIVE, REPORT, CONFIRM, REJECT, CASH, REFUND, REVERSE, QR, MANAGERS_REVOKED, DAILY_DETACHED }
    public enum Visibility { INDIVIDUAL, TRANSPARENT }
    public enum Classification { DAILY, MONTHLY, OTHER }
    public enum ListStatus { DRAFT, OPEN, CLOSED, CANCELED }
    public enum NoticeStatus { PENDING, CONFIRMED, REJECTED }
    public enum MovementKind { OPENING, RECEIPT, INCOME, EXPENSE, REFUND, REVERSAL }
    public enum PaymentMethod { PIX, CASH, TRANSFER, OTHER }
    public enum PaymentStatus { UNPAID, PARTIAL, PAID }
}
