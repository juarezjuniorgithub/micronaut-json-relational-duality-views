package io.micronaut.oracle.dev.duality.web;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Map;

@Serdeable
public record BootstrapReport(boolean recreated, String databaseProduct, String databaseVersion, String dataSourceType,
    List<String> createdObjects, Map<String, Long> rowCounts) {
}
