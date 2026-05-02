package eu.appbahn.shared.crd.imagesource;

import com.fasterxml.jackson.annotation.JsonProperty;

/** All build modes the platform supports. Only the enum lives in this PR; execution lands later. */
public enum BuildMode {
    @JsonProperty("Dockerfile")
    DOCKERFILE,
    @JsonProperty("Peelbox")
    PEELBOX,
    @JsonProperty("Buildpack")
    BUILDPACK,
    @JsonProperty("Nixpacks")
    NIXPACKS,
    @JsonProperty("Railpack")
    RAILPACK
}
