package io.micronaut.oracle.dev.duality.web;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.http.MediaType;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.oracle.dev.duality.model.OrderDocument;
import io.micronaut.oracle.dev.duality.service.DualityDemoService;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/duality")
public class DualityController {
  private final DualityDemoService demoService;

  public DualityController(DualityDemoService demoService) {
    this.demoService = demoService;
  }

  @Get
  public DemoOverview overview() {
    return demoService.overview();
  }

  @Consumes(MediaType.ALL)
  @Post("/bootstrap{?recreate}")
  public BootstrapReport bootstrap(@QueryValue(defaultValue = "false") boolean recreate) {
    return demoService.bootstrap(recreate);
  }

  @Get("/ddl")
  public Map<String, String> ddl() {
    return demoService.ddl();
  }

  @Get("/orders{?status}")
  public List<OrderDocument> orders(@Nullable @QueryValue("status") String status) {
    return demoService.listOrders(Optional.ofNullable(status));
  }

  @Get("/search{?status,customerEmail,sku,priority,preferenceChannel}")
  public OrderSearchResult search(@Nullable @QueryValue("status") String status,
      @Nullable @QueryValue("customerEmail") String customerEmail, @Nullable @QueryValue("sku") String sku,
      @Nullable @QueryValue("priority") String priority,
      @Nullable @QueryValue("preferenceChannel") String preferenceChannel) {
    return demoService.searchOrders(new OrderSearchCriteria(status, customerEmail, sku, priority, preferenceChannel));
  }

  @Get("/orders/{id}")
  public HttpResponse<OrderDocument> order(Long id) {
    return demoService.findOrder(id).map(HttpResponse::ok).orElseGet(HttpResponse::notFound);
  }

  @Post("/orders")
  public HttpResponse<OrderDocument> create(@Body OrderDocument document) {
    return HttpResponse.created(demoService.createOrder(document));
  }

  @Put("/orders/{id}")
  public HttpResponse<?> replace(Long id, @Body OrderDocument document) {
    if (document.id() != null && !id.equals(document.id())) {
      return HttpResponse.badRequest(Map.of("error", "Path id must match document _id"));
    }
    return demoService.replaceOrder(id, document).map(HttpResponse::ok).orElseGet(HttpResponse::notFound);
  }

  @Patch("/orders/{id}/status")
  public HttpResponse<OrderDocument> patchStatus(Long id, @Body StatusChange change) {
    return demoService.patchOrderStatus(id, change.status()).map(HttpResponse::ok).orElseGet(HttpResponse::notFound);
  }

  @Delete("/orders/{id}")
  public HttpResponse<?> delete(Long id) {
    if (!demoService.deleteOrder(id)) {
      return HttpResponse.notFound();
    }
    return HttpResponse.noContent();
  }

  @Get("/orders/{id}/json")
  public HttpResponse<RawJsonDocument> rawOrder(Long id) {
    return demoService.rawOrderDocument(id).map(HttpResponse::ok).orElseGet(HttpResponse::notFound);
  }

  @Get("/orders/{id}/relational")
  public HttpResponse<RelationalOrderSnapshot> relationalOrder(Long id) {
    return demoService.relationalOrder(id).map(HttpResponse::ok).orElseGet(HttpResponse::notFound);
  }

  @Get("/summaries/open")
  public List<RawJsonDocument> openSummaries() {
    return demoService.openOrderSummaries();
  }

  @Consumes(MediaType.ALL)
  @Post("/orders/{id}/etag-conflict")
  public EtagConflictReport etagConflict(Long id) {
    return demoService.demonstrateEtagConflict(id);
  }

  @Error(exception = IllegalArgumentException.class)
  public HttpResponse<Map<String, String>> badRequest(IllegalArgumentException exception) {
    return HttpResponse.badRequest(Map.of("error", exception.getMessage()));
  }

  @Error(exception = DataAccessException.class)
  public HttpResponse<Map<String, String>> dataAccessError(DataAccessException exception) {
    return HttpResponse.serverError(Map.of("error", "Database operation failed", "detail", rootMessage(exception)));
  }

  private static String rootMessage(Throwable throwable) {
    Throwable cursor = throwable;
    while (cursor.getCause() != null) {
      cursor = cursor.getCause();
    }
    return cursor.getMessage();
  }
}
