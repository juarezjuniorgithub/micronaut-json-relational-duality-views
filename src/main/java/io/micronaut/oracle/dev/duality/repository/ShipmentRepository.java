package io.micronaut.oracle.dev.duality.repository;

import io.micronaut.data.connection.annotation.ClientInfo;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.oracle.dev.duality.model.Shipment;

import java.util.List;

@ClientInfo.Attribute(name = "OCSID.MODULE", value = "duality-shipments")
@JdbcRepository(dialect = Dialect.ORACLE)
public interface ShipmentRepository extends CrudRepository<Shipment, Long> {

  @ClientInfo.Attribute(name = "OCSID.ACTION", value = "find-shipments-by-order")
  List<Shipment> findByOrderId(Long orderId);
}
