package com.futspring.backend.domain.pelada.dto;

import com.futspring.backend.domain.pelada.Pelada;

/** Public invitation preview: never include members, location, chat or sporting history. */
public record PeladaPreviewDTO(Long id, String name, String image, String dayOfWeek, String timeOfDay) {
    public static PeladaPreviewDTO from(Pelada p) {
        return new PeladaPreviewDTO(p.getId(), p.getName(), p.getImage(), p.getDayOfWeek(), p.getTimeOfDay());
    }
}
