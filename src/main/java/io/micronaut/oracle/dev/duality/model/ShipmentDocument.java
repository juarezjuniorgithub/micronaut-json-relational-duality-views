package io.micronaut.oracle.dev.duality.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonSubView;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Serdeable
@JsonSubView(entity = Shipment.class, operations = { JsonView.Operation.INSERT, JsonView.Operation.UPDATE,
    JsonView.Operation.DELETE }, alias = "s")
public record ShipmentDocument(@Id @JsonProperty("_id") Long id, String carrier, String trackingNumber,
    LocalDateTime shippedAt, @TypeDef(type = DataType.JSON) Map<String, Object> deliveryWindow) {
  public ShipmentDocument {
    deliveryWindow = immutableMap(deliveryWindow);
  }

  private static Map<String, Object> immutableMap(Map<String, Object> value) {
    if (value == null) {
      return Map.of();
    }
    return Collections.unmodifiableMap(new LinkedHashMap<>(value));
  }
}
