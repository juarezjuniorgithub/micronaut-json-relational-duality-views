package io.micronaut.oracle.dev.duality.model;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Serdeable
@MappedEntity("JRDV_ORDERS")
public record PurchaseOrder(@Id Long id, @MappedProperty("order_number") String orderNumber,
    @MappedProperty("customer_id") Long customerId, String status, @MappedProperty("placed_at") LocalDateTime placedAt,
    @TypeDef(type = DataType.JSON) @MappedProperty("audit_trail") Map<String, Object> auditTrail) {
  public PurchaseOrder {
    auditTrail = immutableMap(auditTrail);
  }

  private static Map<String, Object> immutableMap(Map<String, Object> value) {
    if (value == null) {
      return Map.of();
    }
    return Collections.unmodifiableMap(new LinkedHashMap<>(value));
  }
}
