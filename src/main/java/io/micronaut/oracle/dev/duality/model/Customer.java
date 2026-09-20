package io.micronaut.oracle.dev.duality.model;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Serdeable
@MappedEntity("JRDV_CUSTOMERS")
public record Customer(@Id Long id, String name, String email, @MappedProperty("loyalty_status") String loyaltyStatus,
    @TypeDef(type = DataType.JSON) Map<String, Object> preferences) {
  public Customer {
    preferences = immutableMap(preferences);
  }

  private static Map<String, Object> immutableMap(Map<String, Object> value) {
    if (value == null) {
      return Map.of();
    }
    return Collections.unmodifiableMap(new LinkedHashMap<>(value));
  }
}
