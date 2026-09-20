package io.micronaut.oracle.dev.duality.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.annotation.sql.JoinColumn;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Serdeable
@JsonView(value = "JDV_ORDER_DV", entity = PurchaseOrder.class, alias = "o")
public record OrderDocument(@Id @JsonProperty("_id") Long id, String orderNumber, String status, LocalDateTime placedAt,
    @Relation(Relation.Kind.MANY_TO_ONE) @JoinColumn(name = "customer_id", referencedColumnName = "id") CustomerDocument customer,
    @Relation(Relation.Kind.ONE_TO_MANY) @JoinColumn(name = "id", referencedColumnName = "order_id") List<OrderLineDocument> lines,
    @Relation(Relation.Kind.ONE_TO_MANY) @JoinColumn(name = "id", referencedColumnName = "order_id") List<ShipmentDocument> shipments,
    @TypeDef(type = DataType.JSON) Map<String, Object> auditTrail,
    @JsonProperty("_metadata") DualityMetadata metadata) {
  public OrderDocument {
    lines = immutableList(lines);
    shipments = immutableList(shipments);
    auditTrail = immutableMap(auditTrail);
  }

  public OrderDocument withId(Long newId) {
    return new OrderDocument(newId, orderNumber, status, placedAt, customer, lines, shipments, auditTrail, metadata);
  }

  public OrderDocument withStatus(String newStatus) {
    return new OrderDocument(id, orderNumber, newStatus, placedAt, customer, lines, shipments, auditTrail, metadata);
  }

  private static <T> List<T> immutableList(List<T> value) {
    if (value == null) {
      return List.of();
    }
    return List.copyOf(value);
  }

  private static Map<String, Object> immutableMap(Map<String, Object> value) {
    if (value == null) {
      return Map.of();
    }
    return Collections.unmodifiableMap(new LinkedHashMap<>(value));
  }
}
