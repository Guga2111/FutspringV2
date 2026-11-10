package com.futspring.backend.domain.finance.dto;

import com.futspring.backend.domain.finance.FinanceTypes.Classification;
import com.futspring.backend.domain.finance.FinanceTypes.ListStatus;
import com.futspring.backend.domain.finance.FinanceTypes.NoticeStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// The caller's own finance records for the account export: their charges in published lists and the payment notices
// they sent. Nothing about other people, no Pix of the receivers, no audit and no receipt bytes.
public record FinanceAccountExport(List<Charge> charges, List<Notice> notices) {

    public record Charge(
            Long id,
            Long peladaId,
            String peladaName,
            Long listId,
            String listName,
            Classification classification,
            ListStatus listStatus,
            List<FinanceViews.Component> components,
            String adjustment,
            String amountDue,
            String received,
            LocalDate dueDate,
            boolean canceled) {
    }

    public record Notice(
            Long id,
            Long chargeId,
            String amount,
            NoticeStatus status,
            Instant createdAt,
            Instant resolvedAt,
            String reason,
            boolean receiptSent) {
    }
}
