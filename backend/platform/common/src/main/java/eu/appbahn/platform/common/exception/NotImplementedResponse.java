package eu.appbahn.platform.common.exception;

/**
 * Body returned for {@link NotImplementedException}. Kept as a dedicated record
 * (not {@link eu.appbahn.platform.api.ErrorResponse}) so the wire shape is
 * exactly {@code {"error":"...","message":"..."}} with no status/details
 * fields tagging along.
 */
public record NotImplementedResponse(String error, String message) {}
