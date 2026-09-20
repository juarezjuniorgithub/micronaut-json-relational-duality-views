package io.micronaut.oracle.dev.duality.repository;

import io.micronaut.data.connection.annotation.ClientInfo;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.oracle.dev.duality.model.PurchaseOrder;

import java.util.List;

@ClientInfo.Attribute(name = "OCSID.MODULE", value = "duality-relational-orders")
@JdbcRepository(dialect = Dialect.ORACLE)
public interface PurchaseOrderRepository extends CrudRepository<PurchaseOrder, Long> {

  @ClientInfo.Attribute(name = "OCSID.ACTION", value = "find-relational-orders-by-status")
  List<PurchaseOrder> findByStatus(String status);
}
