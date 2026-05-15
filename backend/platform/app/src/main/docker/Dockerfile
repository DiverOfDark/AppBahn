# syntax=docker/dockerfile:1.23

# Common base: Chainguard Wolfi + OpenJDK 25 JRE. All three subsequent
# stages FROM `jre` so the AOT cache is binary-compatible — same `lib/modules`
# blob across training and runtime. `openjdk-25-default-jvm` is a metapackage
# that pulls `openjdk-25-jre` and exposes `/usr/bin/java`; it tracks whatever
# 25.x.y build the Wolfi apk repo currently ships (rebuilt daily by Chainguard
# for CVE patching).
FROM cgr.dev/chainguard/wolfi-base:latest AS jre
RUN apk add --no-cache openjdk-25-default-jvm

# Stage 1 — extract the layered Spring Boot jar into the optimised launch
# layout (application.jar + lib/). The Gradle stage copies bootJar's output to
# /workspace/application.jar before the build kicks off.
FROM jre AS extract
WORKDIR /workspace
COPY application.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --destination application

# Stage 2 — JVM AOT cache training run. Boots the Spring context with
# `spring.context.exit=onRefresh` and persists the resulting AOT cache. Lazy
# initialisation defers most beans, but Servlet Filters (idempotencyFilter)
# stay eager and pull JPA + Flyway through their constructor chain. The DB
# overrides keep Flyway off, pin the Hibernate dialect (no JDBC sniffing),
# and tell Hikari not to block on a real connection. The cache still captures
# the Spring framework, Jetty, Jackson, Hibernate, and fabric8 class graph;
# everything not exercised here JITs at runtime.
FROM jre AS aot
WORKDIR /workspace/application
COPY --from=extract /workspace/application/ ./
ENV SPRING_MAIN_LAZY_INITIALIZATION=true \
    SPRING_FLYWAY_ENABLED=false \
    SPRING_JPA_HIBERNATE_DDL_AUTO=none \
    SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect \
    SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT=-1 \
    PLATFORM_KUBERNETES_ENABLED=false \
    SPRING_AUTOCONFIGURE_EXCLUDE=org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration,org.springframework.boot.security.oauth2.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
# `-D` system property (vs SPRING_JPA_PROPERTIES_…) so the underscore in the
# Hibernate property name survives Spring's relaxed-binding env-var mangling.
# Without this Hibernate sniffs JDBC metadata at EMF init, blocks 30 s on the
# stub connection, then times out — slow but harmless.
RUN java -XX:AOTCacheOutput=app.aot -Dspring.context.exit=onRefresh \
    -Dhibernate.boot.allow_jdbc_metadata_access=false \
    -jar application.jar

# Stage 3 — runtime image. Numeric UID/GID 65532 (the conventional nonroot
# UID for distroless-style images) — Kubernetes' `runAsNonRoot` admission
# requires a numeric USER, and Wolfi base has no preallocated unprivileged
# user, so we use the bare UID without an /etc/passwd entry.
FROM jre AS runtime
WORKDIR /app
USER 65532:65532
COPY --from=aot --chown=65532:65532 /workspace/application/ ./
EXPOSE 8080
ENTRYPOINT ["java", "-XX:AOTCache=app.aot", "-jar", "application.jar"]
