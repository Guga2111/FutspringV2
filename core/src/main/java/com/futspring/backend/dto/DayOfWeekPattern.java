package com.futspring.backend.dto;

// Pelada.dayOfWeek is stored as a java.time.DayOfWeek name; the scheduler parses it with DayOfWeek.valueOf
final class DayOfWeekPattern {

    static final String REGEX = "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY";
    static final String MESSAGE = "Dia da semana inválido";

    private DayOfWeekPattern() {
    }
}
