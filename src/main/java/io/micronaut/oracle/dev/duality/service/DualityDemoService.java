package io.micronaut.oracle.dev.duality.service;

import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.oracle.dev.duality.model.OrderDocument;
import io.micronaut.oracle.dev.duality.repository.OrderDocumentRepository;
import io.micronaut.oracle.dev.duality.web.BootstrapReport;
import io.micronaut.oracle.dev.duality.web.DemoOverview;
import io.micronaut.oracle.dev.duality.web.DualityFeature;
import io.micronaut.oracle.dev.duality.web.EtagConflictReport;
import io.micronaut.oracle.dev.duality.web.OrderSearchCriteria;
import io.micronaut.oracle.dev.duality.web.OrderSearchResult;
import io.micronaut.oracle.dev.duality.web.RawJsonDocument;
import io.micronaut.oracle.dev.duality.web.RelationalOrderSnapshot;
import io.micronaut.transaction.annotation.OracleTransactional;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Singleton
public class DualityDemoService {
  private static final Set<String> ALLOWED_STATUSES = Set.of("PLACED", "PAID", "PACKED", "SHIPPED", "CANCELLED");

  private final DualitySchemaService schemaService;
  private final RawDualityJdbcClient rawClient;
  private final OrderDocumentRepository orderDocuments;

  public DualityDemoService(DualitySchemaService schemaService, RawDualityJdbcClient rawClient,
      OrderDocumentRepository orderDocuments) {
    this.schemaService = schemaService;
    this.rawClient = rawClient;
    this.orderDocuments = orderDocuments;
  }

  public DemoOverview overview() {
    RawDualityJdbcClient.DatabaseIdentity database = rawClient.databaseIdentity();
    return new DemoOverview("micronaut-json-relational-duality-views",
        database.productName() + " " + database.productVersion(), database.dataSourceType(),
        List.of(
            new DualityFeature("Micronaut Data JDBC JSON view mapping",
                "OrderDocument uses @JsonView over JDV_ORDER_DV"),
            new DualityFeature("Nested JSON document shape",
                "CustomerDocument, OrderLineDocument, and ShipmentDocument use @JsonSubView"),
            new DualityFeature("Document-level CRUD",
                "OrderDocumentRepository extends CrudRepository<OrderDocument, Long>"),
            new DualityFeature("Partial Oracle updatability",
                "JDV_ORDER_DV keeps customers insert/update but nodelete"),
            new DualityFeature("ETAG optimistic concurrency",
                "OrderDocument maps _metadata.etag and /etag-conflict triggers a stale update"),
            new DualityFeature("Native JSON columns",
                "preferences, auditTrail, attributes, and deliveryWindow are Oracle JSON columns"),
            new DualityFeature("Duality document search",
                "GET /search applies JSON_EXISTS predicates to JDV_ORDER_DV.DATA with bound values"),
            new DualityFeature("Scalar arrays, hidden keys, and check option",
                "JDV_OPEN_ORDER_SUMMARY_DV demonstrates raw Oracle duality view clauses"),
            new DualityFeature("Oracle UCP and client info",
                "application.properties configures UCP and repository OCSID module/action labels")),
        List.of("POST /duality/bootstrap?recreate=true", "GET /duality/orders", "GET /duality/orders/{id}",
            "POST /duality/orders", "PUT /duality/orders/{id}", "PATCH /duality/orders/{id}/status",
            "DELETE /duality/orders/{id}", "GET /duality/orders/{id}/json", "GET /duality/orders/{id}/relational",
            "GET /duality/search?status={status}&customerEmail={email}&sku={sku}&priority={priority}"
                + "&preferenceChannel={channel}",
            "GET /duality/summaries/open", "POST /duality/orders/{id}/etag-conflict", "GET /duality/ddl"));
  }

  public BootstrapReport bootstrap(boolean recreate) {
    return schemaService.bootstrap(recreate);
  }

  public java.util.Map<String, String> ddl() {
    return schemaService.viewDefinitions();
  }

  @OracleTransactional(readOnly = true, priority = OracleTransactional.Priority.MEDIUM)
  public List<OrderDocument> listOrders(Optional<String> status) {
    return status.map(this::normalizeStatus).map(orderDocuments::findByStatus).orElseGet(orderDocuments::findAll);
  }

  @OracleTransactional(readOnly = true, priority = OracleTransactional.Priority.MEDIUM)
  public OrderSearchResult searchOrders(OrderSearchCriteria criteria) {
    OrderSearchCriteria normalized = normalizeSearchCriteria(criteria);
    List<OrderDocument> orders = rawClient.searchOrderIds(normalized).stream().map(orderDocuments::findById)
        .flatMap(Optional::stream).toList();
    return new OrderSearchResult(normalized, orders);
  }

  @OracleTransactional(readOnly = true, priority = OracleTransactional.Priority.MEDIUM)
  public Optional<OrderDocument> findOrder(Long id) {
    return orderDocuments.findById(id);
  }

  @OracleTransactional(priority = OracleTransactional.Priority.HIGH)
  public OrderDocument createOrder(OrderDocument document) {
    OrderDocument normalized = normalizeForWrite(document, document.id());
    return orderDocuments.insert(normalized);
  }

  @OracleTransactional(priority = OracleTransactional.Priority.HIGH)
  public Optional<OrderDocument> replaceOrder(Long id, OrderDocument document) {
    if (!orderDocuments.existsById(id)) {
      return Optional.empty();
    }
    OrderDocument normalized = normalizeForWrite(document, id);
    return Optional.of(orderDocuments.update(normalized));
  }

  @OracleTransactional(priority = OracleTransactional.Priority.MEDIUM)
  public boolean deleteOrder(Long id) {
    if (!orderDocuments.existsById(id)) {
      return false;
    }
    orderDocuments.deleteById(id);
    return true;
  }

  public Optional<OrderDocument> patchOrderStatus(Long id, String status) {
    String normalizedStatus = normalizeStatus(status);
    int updated = rawClient.patchOrderStatus(id, normalizedStatus);
    if (updated == 0) {
      return Optional.empty();
    }
    return orderDocuments.findById(id);
  }

  public Optional<RawJsonDocument> rawOrderDocument(Long id) {
    return rawClient.orderDocument(id);
  }

  public Optional<RelationalOrderSnapshot> relationalOrder(Long id) {
    return rawClient.relationalSnapshot(id);
  }

  public List<RawJsonDocument> openOrderSummaries() {
    return rawClient.openOrderSummaries();
  }

  public EtagConflictReport demonstrateEtagConflict(Long id) {
    OrderDocument staleDocument = orderDocuments.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Order " + id + " does not exist"));

    String staleEtag = staleDocument.metadata() == null ? null : staleDocument.metadata().etag();
    String concurrentStatus = "SHIPPED".equals(staleDocument.status()) ? "PAID" : "SHIPPED";
    String attemptedStatus = "PACKED".equals(concurrentStatus) ? "PAID" : "PACKED";

    rawClient.updateUnderlyingOrderStatus(id, concurrentStatus);
    try {
      orderDocuments.update(staleDocument.withStatus(attemptedStatus));
      return new EtagConflictReport(id, staleEtag, rawClient.orderStatus(id).orElse(concurrentStatus), false, null,
          "The stale document update unexpectedly succeeded");
    } catch (DataAccessException e) {
      return new EtagConflictReport(id, staleEtag, rawClient.orderStatus(id).orElse(concurrentStatus), true,
          e.getClass().getName(), rootMessage(e));
    }
  }

  private OrderDocument normalizeForWrite(OrderDocument document, Long id) {
    Objects.requireNonNull(document, "Order document is required");
    Objects.requireNonNull(id, "Order document _id is required");
    Objects.requireNonNull(document.orderNumber(), "Order document orderNumber is required");
    Objects.requireNonNull(document.placedAt(), "Order document placedAt is required");
    Objects.requireNonNull(document.customer(), "Order document customer is required");
    if (document.lines().isEmpty()) {
      throw new IllegalArgumentException("Order document must contain at least one line item");
    }
    return document.withId(id).withStatus(normalizeStatus(document.status()));
  }

  private String normalizeStatus(String status) {
    if (status == null || status.isBlank()) {
      throw new IllegalArgumentException("Order status is required");
    }
    String normalized = status.trim().toUpperCase(Locale.ROOT);
    if (!ALLOWED_STATUSES.contains(normalized)) {
      throw new IllegalArgumentException("Unsupported order status: " + status);
    }
    return normalized;
  }

  private OrderSearchCriteria normalizeSearchCriteria(OrderSearchCriteria criteria) {
    Objects.requireNonNull(criteria, "Search criteria is required");
    String customerEmail = normalizeOptionalText(criteria.customerEmail(), "customerEmail");
    String sku = normalizeOptionalText(criteria.sku(), "sku");
    String priority = normalizeOptionalText(criteria.priority(), "priority");
    String preferenceChannel = normalizeOptionalText(criteria.preferenceChannel(), "preferenceChannel");
    OrderSearchCriteria normalized = new OrderSearchCriteria(normalizeOptionalStatus(criteria.status()),
        customerEmail == null ? null : customerEmail.toLowerCase(Locale.ROOT),
        sku == null ? null : sku.toUpperCase(Locale.ROOT), priority == null ? null : priority.toLowerCase(Locale.ROOT),
        preferenceChannel == null ? null : preferenceChannel.toLowerCase(Locale.ROOT));
    if (normalized.status() == null && normalized.customerEmail() == null && normalized.sku() == null
        && normalized.priority() == null && normalized.preferenceChannel() == null) {
      throw new IllegalArgumentException("Provide at least one JSON document search criterion");
    }
    return normalized;
  }

  private String normalizeOptionalStatus(String status) {
    return status == null || status.isBlank() ? null : normalizeStatus(status);
  }

  private String normalizeOptionalText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim();
    if (normalized.length() > 200) {
      throw new IllegalArgumentException(fieldName + " must not exceed 200 characters");
    }
    return normalized;
  }

  private static String rootMessage(Throwable throwable) {
    Throwable cursor = throwable;
    while (cursor.getCause() != null) {
      cursor = cursor.getCause();
    }
    return cursor.getMessage();
  }
}
