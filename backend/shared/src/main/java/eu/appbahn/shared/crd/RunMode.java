package eu.appbahn.shared.crd;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Whether a resource runs as a long-lived service or a one-shot task. */
public enum RunMode {
    @JsonProperty("Continuous")
    CONTINUOUS,

    @JsonProperty("Task")
    TASK
}
