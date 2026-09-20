package io.micronaut.oracle.dev.duality.web;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record StatusChange(String status) {
}
