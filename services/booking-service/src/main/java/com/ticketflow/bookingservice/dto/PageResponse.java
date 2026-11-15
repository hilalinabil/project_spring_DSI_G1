package com.ticketflow.bookingservice.dto;

import java.util.List;

/** Stable JSON shape for a page of results (instead of serializing Spring's Page). */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
