package io.micronaut.oracle.dev.duality.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonSubView;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Serdeable
@JsonSubView(entity = OrderLine.class, operations = { JsonView.Operation.INSERT, JsonView.Operation.UPDATE,
    JsonView.Operation.DELETE }, alias = "l")
public record OrderLineDocument(@Id @JsonProperty("_id") Long id, String sku, String description, Integer quantity,
    BigDecimal unitPrice, @TypeDef(type = DataType.JSON) Map<String, Object> attributes) {
  public OrderLineDocument {
    attributes = immutableMap(attributes);
  }

  private static Map<String, Object> immutableMap(Map<String, Object> value) {
    if (value == null) {
      return Map.of();
    }
    return Collections.unmodifiableMap(new LinkedHashMap<>(value));
  }
}
