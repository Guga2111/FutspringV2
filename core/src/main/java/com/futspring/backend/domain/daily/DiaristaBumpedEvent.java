package com.futspring.backend.domain.daily;

/** A mensalista took a diarista's spot; published inside the confirmation's transaction. */
public record DiaristaBumpedEvent(Long bumpId) {
}
