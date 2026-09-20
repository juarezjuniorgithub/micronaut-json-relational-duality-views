package io.micronaut.oracle.dev.duality.web;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record EtagConflictReport(Long orderId, String staleEtag, String databaseStatusAfterConcurrentChange,
    boolean conflictDetected, String exceptionType, String message) {
}
