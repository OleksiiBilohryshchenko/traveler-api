package ua.sumdu.dds.travelerapi.dto;

import java.util.Map;

public record ApiErrorResponse(
        String error,
        Map<String, Object> details
) {
    public static ApiErrorResponse of(String error) {
        return new ApiErrorResponse(error, null);
    }

    public static ApiErrorResponse of(String error, Map<String, Object> details) {
        return new ApiErrorResponse(error, details);
    }
}
