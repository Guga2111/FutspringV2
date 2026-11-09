package com.futspring.backend.domain.notification.dto;

// The VAPID public key (base64url, 65-byte uncompressed P-256 point): the applicationServerKey of pushManager.subscribe
public record VapidPublicKeyDTO(String publicKey) {
}
