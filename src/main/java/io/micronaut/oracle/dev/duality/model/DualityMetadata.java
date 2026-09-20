package io.micronaut.oracle.dev.duality.model;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected
public record DualityMetadata(String etag, String asof) {
}
