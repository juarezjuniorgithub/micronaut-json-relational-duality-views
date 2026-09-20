package io.micronaut.oracle.dev.duality.model;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Serdeable
@MappedEntity("JRDV_ORDER_LINES")
public record OrderLine(@Id Long id, @MappedProperty("order_id") Long orderId, String sku, String description,
    Integer quantity, @MappedProperty("unit_price") BigDecimal unitPrice,
    @TypeDef(type = DataType.JSON) Map<String, Object> attributes) {
  public OrderLine {
    attributes = immutableMap(attributes);
  }

  private static Map<String, Object> immutableMap(Map<String, Object> value) {
    if (value == null) {
      return Map.of();
    }
    return Collections.unmodifiableMap(new LinkedHashMap<>(value));
  }
}
