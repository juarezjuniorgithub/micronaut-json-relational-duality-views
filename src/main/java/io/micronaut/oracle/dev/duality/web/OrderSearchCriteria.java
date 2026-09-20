package io.micronaut.oracle.dev.duality.web;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record OrderSearchCriteria(String status, String customerEmail, String sku, String priority,
    String preferenceChannel) {
}
