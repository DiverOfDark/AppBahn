package eu.appbahn.shared.crd.imagesource;

import com.fasterxml.jackson.annotation.JsonProperty;

/** All build modes the platform supports. Only the enum lives in this PR; execution lands later. */
public enum BuildMode {
    @JsonProperty("dockerfile")
    DOCKERFILE,
    @JsonProperty("peelbox")
    PEELBOX,
    @JsonProperty("buildpack")
    BUILDPACK,
    @JsonProperty("nixpacks")
    NIXPACKS,
    @JsonProperty("railpack")
    RAILPACK
}
