package io.micronaut.oracle.dev.duality.repository;

import io.micronaut.data.connection.annotation.ClientInfo;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.oracle.dev.duality.model.OrderDocument;

import java.util.List;
import java.util.Optional;

@ClientInfo.Attribute(name = "OCSID.MODULE", value = "duality-order-documents")
@JdbcRepository(dialect = Dialect.ORACLE)
public interface OrderDocumentRepository extends CrudRepository<OrderDocument, Long> {

  @ClientInfo.Attribute(name = "OCSID.ACTION", value = "find-order-by-number")
  Optional<OrderDocument> findByOrderNumber(String orderNumber);

  @ClientInfo.Attribute(name = "OCSID.ACTION", value = "find-orders-by-status")
  List<OrderDocument> findByStatus(String status);
}
