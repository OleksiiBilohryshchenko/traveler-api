package ua.sumdu.dds.travelerapi.exception;

import java.util.List;

public class ValidationException extends RuntimeException {
    private final List<String> errors;

    public ValidationException(List<String> errors) {
        super("Validation error");
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}
