package com.futspring.backend.domain.mail;

/**
 * An image embedded in the e-mail itself (CID attachment), referenced in the HTML as src="cid:{contentId}".
 * Works for any recipient, unlike an image URL the mail client has to download (localhost, not deployed yet).
 */
public record InlineImage(String contentId, String filename, String contentType, byte[] content) {
}
