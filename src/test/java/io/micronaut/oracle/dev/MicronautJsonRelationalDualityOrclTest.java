package io.micronaut.oracle.dev;

import io.micronaut.data.annotation.JsonSubView;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.oracle.dev.duality.model.CustomerDocument;
import io.micronaut.oracle.dev.duality.model.OrderDocument;
import io.micronaut.oracle.dev.duality.model.OrderLineDocument;
import io.micronaut.oracle.dev.duality.model.PurchaseOrder;
import io.micronaut.oracle.dev.duality.repository.OrderDocumentRepository;
import io.micronaut.oracle.dev.duality.service.DualitySchemaService;
import io.micronaut.oracle.dev.duality.service.RawDualityJdbcClient;
import io.micronaut.oracle.dev.duality.web.BootstrapReport;
import io.micronaut.oracle.dev.duality.web.OrderSearchCriteria;
import io.micronaut.oracle.dev.duality.web.RawJsonDocument;
import io.micronaut.oracle.dev.duality.web.RelationalOrderSnapshot;
import io.micronaut.runtime.EmbeddedApplication;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import jakarta.inject.Inject;

import java.util.List;

@MicronautTest
class MicronautJsonRelationalDualityOrclTest {

  @Inject
  EmbeddedApplication<?> application;

  @Inject
  DualitySchemaService schemaService;

  @Inject
  OrderDocumentRepository orderDocuments;

  @Inject
  RawDualityJdbcClient rawClient;

  @Inject
  @Client("/")
  HttpClient httpClient;

  @Test
  void testItWorks() {
    Assertions.assertTrue(application.isRunning());
  }

  @Test
  void thymeleafWorkspaceAndStaticAssetsAreServed() {
    BlockingHttpClient client = httpClient.toBlocking();

    assertWorkspacePage(client, "/ui", "Connection Check", "Bootstrap", "Read and Compare");
    assertWorkspacePage(client, "/ui/read-compare", "Order tools", "View DDL", "response-output");
    assertWorkspacePage(client, "/ui/search", "Search document fields", "document-search-form", "response-output");
    assertWorkspacePage(client, "/ui/documents", "Draft order 5010", "document-editor", "response-output");

    HttpResponse<String> stylesheet = client.exchange(HttpRequest.GET("/assets/duality-workspace.css"), String.class);
    Assertions.assertEquals(HttpStatus.OK, stylesheet.status());
    Assertions.assertTrue(stylesheet.body().contains("--mn-orange"));

    HttpResponse<String> script = client.exchange(HttpRequest.GET("/assets/duality-workspace.js"), String.class);
    Assertions.assertEquals(HttpStatus.OK, script.status());
    Assertions.assertTrue(script.body().contains("searchDocuments"));
  }

  @Test
  void jsonViewAnnotationsDescribeTheOracleDualityView() {
    JsonView jsonView = OrderDocument.class.getAnnotation(JsonView.class);
    Assertions.assertNotNull(jsonView);
    Assertions.assertEquals("JDV_ORDER_DV", jsonView.value());
    Assertions.assertEquals(PurchaseOrder.class, jsonView.entity());
    Assertions.assertEquals("o", jsonView.alias());

    JsonSubView customer = CustomerDocument.class.getAnnotation(JsonSubView.class);
    Assertions.assertNotNull(customer);
    Assertions.assertArrayEquals(new JsonView.Operation[] { JsonView.Operation.INSERT, JsonView.Operation.UPDATE },
        customer.operations());

    JsonSubView line = OrderLineDocument.class.getAnnotation(JsonSubView.class);
    Assertions.assertArrayEquals(
        new JsonView.Operation[] { JsonView.Operation.INSERT, JsonView.Operation.UPDATE, JsonView.Operation.DELETE },
        line.operations());
  }

  @Test
  void ddlExercisesOracleDualityViewClauses() {
    Assertions.assertTrue(DualitySchemaService.CREATE_ORDER_DUALITY_VIEW.contains("FROM JRDV_ORDERS o"));
    Assertions.assertFalse(DualitySchemaService.CREATE_ORDER_DUALITY_VIEW.contains("FROM JDV_ORDERS o"));
    Assertions.assertTrue(DualitySchemaService.CREATE_ORDER_DUALITY_VIEW.contains("WITH NOCHECK"));
    Assertions.assertTrue(DualitySchemaService.CREATE_ORDER_DUALITY_VIEW.contains("WITH INSERT UPDATE NODELETE"));
    Assertions.assertTrue(DualitySchemaService.CREATE_ORDER_DUALITY_VIEW.contains("WITH INSERT UPDATE DELETE"));
    Assertions.assertTrue(DualitySchemaService.CREATE_OPEN_ORDER_SUMMARY_VIEW.contains("HIDDEN"));
    Assertions.assertTrue(DualitySchemaService.CREATE_OPEN_ORDER_SUMMARY_VIEW.contains("WITH CHECK OPTION"));
  }

  @Test
  void bootstrapCreatesLiveDualityViewsAndRepositoriesReadDocuments() {
    BootstrapReport report = schemaService.bootstrap(true);
    Assertions.assertEquals(2L, report.rowCounts().get("JDV_ORDER_DV"));

    List<OrderDocument> paidOrders = orderDocuments.findByStatus("PAID");
    Assertions.assertEquals(1, paidOrders.size());
    OrderDocument paidOrder = paidOrders.getFirst();
    Assertions.assertEquals("PO-26AI-5001", paidOrder.orderNumber());
    Assertions.assertFalse(paidOrder.lines().isEmpty());
    Assertions.assertNotNull(paidOrder.customer());
    Assertions.assertNotNull(paidOrder.metadata());
    Assertions.assertNotNull(paidOrder.metadata().etag());

    RawJsonDocument rawDocument = rawClient.orderDocument(5001L).orElseThrow();
    Assertions.assertTrue(rawDocument.document().contains("\"orderNumber\""));
    Assertions.assertTrue(rawDocument.document().contains("\"lines\""));

    RelationalOrderSnapshot snapshot = rawClient.relationalSnapshot(5001L).orElseThrow();
    Assertions.assertEquals("PO-26AI-5001", snapshot.orderNumber());
    Assertions.assertEquals(2, snapshot.lines().size());

    Assertions.assertEquals(List.of(5001L), rawClient
        .searchOrderIds(new OrderSearchCriteria("PAID", "asha.rao@example.com", "DB26AI-JSON", "expedite", "mobile")));
  }

  @Test
  void readmeRestEndpointsAreRegisteredAndExecutable() {
    BlockingHttpClient client = httpClient.toBlocking();

    HttpResponse<BootstrapReport> bootstrap = client
        .exchange(HttpRequest.create(HttpMethod.POST, "/duality/bootstrap?recreate=true"), BootstrapReport.class);
    Assertions.assertEquals(HttpStatus.OK, bootstrap.status());
    Assertions.assertEquals(2L, bootstrap.body().rowCounts().get("JDV_ORDER_DV"));

    assertOk(client, HttpRequest.GET("/duality"));
    assertOk(client, HttpRequest.GET("/duality/orders"));
    assertOk(client, HttpRequest.GET("/duality/orders?status=PAID"));
    assertSearchMatches(client,
        "/duality/search?status=PAID&customerEmail=asha.rao%40example.com&sku=DB26AI-JSON&priority=expedite&preferenceChannel=mobile",
        "PO-26AI-5001");
    assertSearchMatches(client, "/duality/search?sku=UCP-OPS", "PO-26AI-5002");
    assertOk(client, HttpRequest.GET("/duality/orders/5001"));
    assertOk(client, HttpRequest.GET("/duality/orders/5001/json"));
    assertOk(client, HttpRequest.GET("/duality/orders/5001/relational"));
    assertOk(client, HttpRequest.GET("/duality/summaries/open"));
    assertOk(client, HttpRequest.GET("/duality/ddl"));

    assertOk(client, HttpRequest.PATCH("/duality/orders/5001/status", "{\"status\":\"PACKED\"}"));
    assertOk(client, HttpRequest.PATCH("/duality/orders/5001/status", "{\"status\":\"PAID\"}"));

    String newOrder = """
        {
          "_id": 5010,
          "orderNumber": "PO-26AI-5010",
          "status": "PLACED",
          "placedAt": "2026-08-27T14:00:00",
          "customer": {
            "_id": 1002,
            "name": "Morgan Lee",
            "email": "morgan.lee@example.com",
            "loyaltyStatus": "GOLD",
            "preferences": {
              "channel": "web",
              "newsletter": false,
              "fulfillment": {
                "allowSubstitutions": true,
                "preferredWarehouse": "LHR-2"
              }
            }
          },
          "lines": [
            {
              "_id": 9010,
              "sku": "UCP-OPS",
              "description": "Oracle UCP operations checklist",
              "quantity": 2,
              "unitPrice": 19.00,
              "attributes": {
                "color": "green",
                "format": "laminated",
                "warehouse": "LHR-2"
              }
            }
          ],
          "shipments": [
            {
              "_id": 7010,
              "carrier": "DHL",
              "trackingNumber": "DHL-26AI-0010",
              "shippedAt": null,
              "deliveryWindow": {
                "from": "2026-08-29T09:00:00",
                "to": "2026-08-29T13:00:00",
                "timezone": "Europe/Dublin"
              }
            }
          ],
          "auditTrail": {
            "createdBy": "REST client",
            "priority": "normal",
            "events": [
              {
                "type": "CREATED",
                "at": "2026-08-27T14:00:00"
              }
            ]
          }
        }
        """;

    HttpResponse<String> created = client
        .exchange(HttpRequest.POST("/duality/orders", newOrder).contentType("application/json"), String.class);
    Assertions.assertEquals(HttpStatus.CREATED, created.status());
    assertOk(client, HttpRequest.GET("/duality/orders/5010"));

    String replacement = newOrder.replace("\"status\": \"PLACED\"", "\"status\": \"PAID\"");
    assertOk(client, HttpRequest.PUT("/duality/orders/5010", replacement).contentType("application/json"));

    HttpResponse<String> deleted = client.exchange(HttpRequest.DELETE("/duality/orders/5010"), String.class);
    Assertions.assertEquals(HttpStatus.NO_CONTENT, deleted.status());

    assertOk(client, HttpRequest.create(HttpMethod.POST, "/duality/orders/5001/etag-conflict"));
    assertOk(client, HttpRequest.create(HttpMethod.POST, "/duality/bootstrap?recreate=true"));
  }

  private static void assertOk(BlockingHttpClient client, HttpRequest<?> request) {
    HttpResponse<String> response = client.exchange(request, String.class);
    Assertions.assertEquals(HttpStatus.OK, response.status(), request.getMethodName() + " " + request.getPath());
  }

  private static void assertWorkspacePage(BlockingHttpClient client, String path, String... expectedContent) {
    HttpResponse<String> page = client.exchange(HttpRequest.GET(path), String.class);
    Assertions.assertEquals(HttpStatus.OK, page.status(), path);
    Assertions.assertEquals(MediaType.TEXT_HTML, page.getContentType().orElseThrow().getName(), path);
    for (String expected : expectedContent) {
      Assertions.assertTrue(page.body().contains(expected), path + " should contain " + expected);
    }
  }

  private static void assertSearchMatches(BlockingHttpClient client, String path, String orderNumber) {
    HttpResponse<String> response = client.exchange(HttpRequest.GET(path), String.class);
    Assertions.assertEquals(HttpStatus.OK, response.status(), path);
    Assertions.assertTrue(response.body().contains(orderNumber), path);
  }

}
