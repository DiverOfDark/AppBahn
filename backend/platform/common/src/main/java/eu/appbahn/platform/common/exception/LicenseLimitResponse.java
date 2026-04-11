package eu.appbahn.platform.common.exception;

public record LicenseLimitResponse(int status, String error, String message, int current, int limit) {}
