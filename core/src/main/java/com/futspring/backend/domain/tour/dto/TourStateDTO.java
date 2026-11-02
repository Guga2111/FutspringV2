package com.futspring.backend.domain.tour.dto;

import com.futspring.backend.domain.tour.TourKey;

import java.util.List;

// The tours the caller has already seen (finished or closed)
public record TourStateDTO(List<TourKey> seen) {
}
