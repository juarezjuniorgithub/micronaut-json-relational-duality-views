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
@MappedEntity("JRDV_SHIPMENTS")
public record Shipment(@Id Long id, @MappedProperty("order_id") Long orderId, String carrier,
    @MappedProperty("tracking_number") String trackingNumber, @MappedProperty("shipped_at") LocalDateTime shippedAt,
    @TypeDef(type = DataType.JSON) @MappedProperty("delivery_window") Map<String, Object> deliveryWindow) {
  public Shipment {
    deliveryWindow = immutableMap(deliveryWindow);
  }

  private static Map<String, Object> immutableMap(Map<String, Object> value) {
    if (value == null) {
      return Map.of();
    }
    return Collections.unmodifiableMap(new LinkedHashMap<>(value));
  }
}
