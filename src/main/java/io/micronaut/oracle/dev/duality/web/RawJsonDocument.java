package io.micronaut.oracle.dev.duality.web;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record RawJsonDocument(String viewName, String document) {
}
