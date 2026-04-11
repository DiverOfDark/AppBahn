package eu.appbahn.platform.common.exception;

public record QuotaExceededResponse(
        int status, String error, String message, String dimension, Number limit, String level) {}
