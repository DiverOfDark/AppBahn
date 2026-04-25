package eu.appbahn.platform.api.tunnel;

public enum CommandStatus {
    OK,
    INVALID_ARGUMENT,
    CONFLICT,
    NOT_FOUND,
    INTERNAL_ERROR,
    /** Platform-side verdict written by the timeout sweeper when a command sat past expires_at. */
    TIMEOUT
}
