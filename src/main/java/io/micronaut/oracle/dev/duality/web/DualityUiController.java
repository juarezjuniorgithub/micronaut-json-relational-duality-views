package io.micronaut.oracle.dev.duality.web;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.views.View;

import java.util.Map;

/**
 * Serves the human-facing workspace while {@link DualityController} remains the
 * JSON API.
 */
@Controller
public class DualityUiController {

  @View("ui/index")
  @Get(value = "/ui", produces = MediaType.TEXT_HTML)
  public Map<String, Object> index() {
    return viewModel();
  }

  @View("ui/read-compare")
  @Get(value = "/ui/read-compare", produces = MediaType.TEXT_HTML)
  public Map<String, Object> readCompare() {
    return viewModel();
  }

  @View("ui/search")
  @Get(value = "/ui/search", produces = MediaType.TEXT_HTML)
  public Map<String, Object> search() {
    return viewModel();
  }

  @View("ui/documents")
  @Get(value = "/ui/documents", produces = MediaType.TEXT_HTML)
  public Map<String, Object> documents() {
    return viewModel();
  }

  private Map<String, Object> viewModel() {
    return Map.of("applicationName", "Micronaut JSON Relational Duality Views", "defaultOrderId", 5001,
        "defaultDraftId", 5010);
  }
}
