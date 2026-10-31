package com.futspring.backend.domain.finance;

import com.futspring.backend.domain.finance.dto.*;
import com.futspring.backend.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import static com.futspring.backend.domain.finance.FinanceAccess.*;
import static com.futspring.backend.domain.finance.FinanceTransactions.*;

@Service
@RequiredArgsConstructor
@Transactional
public class FinanceAttachmentService {
    private final FinanceAccess access;
    private final FinanceReadService reads;
    private final FinanceTransactions tx;
    private final FinanceQrRepository qrs;
    private final FinanceNoticeRepository notices;
    private final FinanceChargeRepository charges;
    private final FinanceFiles files;

    public FinanceViews.Result qr(Long id, Long listId, FinanceRequests.Command command, MultipartFile file, String email) {
        var image = files.verify(file);
        return tx.execute(id, email, command, "QR", "QR:" + listId, List.of(command, image.hash()), true, ctx -> {
            var list = listId == null ? null : reads.findList(id, listId);
            if (list != null) {
                require(list.getStatus() != FinanceTypes.ListStatus.CANCELED, "A lista está cancelada");
                if (list.getPublishedAt() != null) reason(command);
            }
            Long before = list == null ? ctx.account().getQrId() : list.getQrId();
            var qr = new FinanceQr(); qr.setPeladaId(id); qr.setContentType(image.contentType()); qr.setFileKey(files.store(image, true)); qrs.save(qr);
            if (list == null) ctx.account().setQrId(qr.getId()); else list.setQrId(qr.getId());
            return new Change(before, qr.getId(), listId);
        });
    }
    public record Image(byte[] bytes, String contentType) {}
    @Transactional(readOnly = true)
    public Image receipt(Long id, Long noticeId, String email) {
        var ctx = access.read(id, email);
        var notice = notices.findByIdAndPeladaId(noticeId, id).orElseThrow(() -> missing("Comprovante"));
        if (!ctx.manage() && !Objects.equals(notice.getUserId(), ctx.caller().getId())) throw missing("Comprovante");
        if (notice.getReceiptKey() == null || notice.getReceiptDeletedAt() != null || !access.now().isBefore(notice.getReceiptExpiresAt())) {
            throw new AppException(HttpStatus.GONE, "O comprovante expirou ou não está disponível");
        }
        return new Image(files.read(notice.getReceiptKey(), false), notice.getReceiptType());
    }
    @Transactional(readOnly = true)
    public Image qr(Long id, Long qrId, String email) {
        var ctx = access.read(id, email);
        var qr = qrs.findByIdAndPeladaId(qrId, id).orElseThrow(() -> missing("QR Code"));
        boolean visible = ctx.manage() || Objects.equals(ctx.account().getQrId(), qrId);
        if (!visible) {
            // Evaluate the linked lists and the viewer's own charge, never an unscoped file key.
            visible = qrs.visibleToMember(id, qrId, ctx.viewCash(), ctx.caller().getId());
        }
        if (!visible) throw missing("QR Code");
        return new Image(files.read(qr.getFileKey(), true), qr.getContentType());
    }
}
