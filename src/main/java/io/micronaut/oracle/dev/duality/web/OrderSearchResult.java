package io.micronaut.oracle.dev.duality.web;

import io.micronaut.oracle.dev.duality.model.OrderDocument;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Objects;

@Serdeable
public record OrderSearchResult(OrderSearchCriteria criteria, List<OrderDocument> orders) {
  public OrderSearchResult {
    criteria = Objects.requireNonNull(criteria, "Search criteria is required");
    orders = List.copyOf(orders);
  }
}
