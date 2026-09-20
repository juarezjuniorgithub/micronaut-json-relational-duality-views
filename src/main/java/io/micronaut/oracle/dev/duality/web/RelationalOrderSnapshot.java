package io.micronaut.oracle.dev.duality.web;

import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Serdeable
public record RelationalOrderSnapshot(Long id, String orderNumber, String status, LocalDateTime placedAt,
    String customerName, String customerEmail, String customerPreferences, String auditTrail, BigDecimal total,
    List<LineRow> lines, List<ShipmentRow> shipments) {
  @Serdeable
  public record LineRow(Long id, String sku, String description, Integer quantity, BigDecimal unitPrice,
      String attributes) {
  }

  @Serdeable
  public record ShipmentRow(Long id, String carrier, String trackingNumber, LocalDateTime shippedAt,
      String deliveryWindow) {
  }
}
