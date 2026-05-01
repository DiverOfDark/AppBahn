package eu.appbahn.platform.common.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

/**
 * Retry the annotated service method on a Hibernate optimistic-lock conflict.
 *
 * <p>Resource-cache and image-source-cache rows carry a {@code @Version} column. The operator's
 * sync path and a user-facing API mutation can race on the same row, producing
 * {@link ObjectOptimisticLockingFailureException}. Without retry the user gets a 409 and is
 * expected to retry client-side — instead we absorb it transparently here.
 *
 * <p>Place on {@code @Transactional} service methods. Spring Retry's proxy is ordered above the
 * transactional proxy by default, so each retry attempt starts a fresh transaction with a fresh
 * version read.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Retryable(
        retryFor = ObjectOptimisticLockingFailureException.class,
        maxAttempts = 5,
        backoff = @Backoff(delay = 100, multiplier = 2.0, maxDelay = 1000))
public @interface RetryOnConflict {}
