package eu.appbahn.platform.api.tunnel;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum CommandStatus {
    @JsonProperty("Ok")
    OK,

    @JsonProperty("InvalidArgument")
    INVALID_ARGUMENT,

    @JsonProperty("Conflict")
    CONFLICT,

    @JsonProperty("NotFound")
    NOT_FOUND,

    @JsonProperty("InternalError")
    INTERNAL_ERROR,

    /** Platform-side verdict written by the timeout sweeper when a command sat past expires_at. */
    @JsonProperty("Timeout")
    TIMEOUT
}
