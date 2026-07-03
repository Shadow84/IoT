package com.example.demo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Data
public class AlertPageRequest {

    @Min(value = 0, message = "page must be >= 0")
    private int page = 0;

    @Min(value = 1, message = "size must be >= 1")
    @Max(value = 100, message = "size must be <= 100")
    private int size = 20;

    private String sortBy = "openedAt";

    @Pattern(regexp = "asc|desc", flags = Pattern.Flag.CASE_INSENSITIVE,
            message = "sortDir must be 'asc' or 'desc'")
    private String sortDir = "desc";

    public Pageable toPageable() {
        var direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }
}
