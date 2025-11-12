package ua.sumdu.dds.travelerapi.handler;

import jakarta.validation.ConstraintViolationException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionSystemException;
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
    public ResponseEntity<Map<String, Object>> handleNotFoundException(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Resource not found"));
    }

    @ExceptionHandler(VersionConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleVersionConflict(VersionConflictException ex) {
        return Map.of(
                "error", ex.getMessage(),
                "current_version", ex.getCurrentVersion()
        );
    }

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidation(ValidationException ex) {
        return Map.of(
                "error", "Validation error",
                "details", ex.getErrors()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleMethodValidation(MethodArgumentNotValidException ex) {
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

    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<Map<String, Object>> handleTransactionSystemException(TransactionSystemException ex) {
        Throwable rootCause = ExceptionUtils.getRootCause(ex);
        if (rootCause == null) rootCause = ex;

        if (rootCause instanceof NotFoundException ||
                rootCause instanceof jakarta.persistence.EntityNotFoundException) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Resource not found"));
        }

        if (rootCause instanceof jakarta.validation.ConstraintViolationException) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Validation error"));
        }

        if (rootCause instanceof ValidationException ve) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Validation error", "details", ve.getErrors()));
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", "Transaction failed",
                        "details", rootCause.getMessage()
                ));
    }


}
