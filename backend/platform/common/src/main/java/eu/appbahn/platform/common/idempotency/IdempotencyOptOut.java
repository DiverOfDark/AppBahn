package eu.appbahn.platform.common.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a write endpoint as not safe for response replay. The {@code IdempotencyFilter} skips
 * fingerprinting and caching for handler methods bearing this annotation, and the OpenAPI
 * customizer omits the {@code Idempotency-Key} header parameter from the operation. Use for
 * SSE, multipart uploads, or any handler whose response stream cannot be replayed.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IdempotencyOptOut {}
