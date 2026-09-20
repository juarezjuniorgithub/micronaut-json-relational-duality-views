package io.micronaut.oracle.dev.duality.repository;

import io.micronaut.data.connection.annotation.ClientInfo;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.oracle.dev.duality.model.OrderLine;

import java.util.List;

@ClientInfo.Attribute(name = "OCSID.MODULE", value = "duality-order-lines")
@JdbcRepository(dialect = Dialect.ORACLE)
public interface OrderLineRepository extends CrudRepository<OrderLine, Long> {

  @ClientInfo.Attribute(name = "OCSID.ACTION", value = "find-lines-by-order")
  List<OrderLine> findByOrderId(Long orderId);
}
