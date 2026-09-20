package io.micronaut.oracle.dev.duality.web;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record DemoOverview(String application, String database, String datasource, List<DualityFeature> features,
    List<String> endpoints) {
}
