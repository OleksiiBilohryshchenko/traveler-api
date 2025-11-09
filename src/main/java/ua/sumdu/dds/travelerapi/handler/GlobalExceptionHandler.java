package ua.sumdu.dds.travelerapi.handler;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import ua.sumdu.dds.travelerapi.dto.ApiErrorResponse;
import ua.sumdu.dds.travelerapi.exception.NotFoundException;
import ua.sumdu.dds.travelerapi.exception.ValidationException;
import ua.sumdu.dds.travelerapi.exception.VersionConflictException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleNotFound(NotFoundException ex) {
        return Map.of("error", "Resource not found");
    }

    @ExceptionHandler(VersionConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleConflict(VersionConflictException ex) {
        return Map.of(
                "error", ex.getMessage(),
                "current_version", ex.getCurrentVersion()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        return ApiErrorResponse.of("Validation error");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleConstraintViolation(ConstraintViolationException ex) {
        return ApiErrorResponse.of("Validation error");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleJsonParsing(HttpMessageNotReadableException ex) {
        return ApiErrorResponse.of("Validation error");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleIllegalArgument(IllegalArgumentException ex) {
        return ApiErrorResponse.of(ex.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleCustomValidation(ValidationException ex) {
        return Map.of(
                "error", "Validation error",
                "details", ex.getErrors()
        );
    }


}
