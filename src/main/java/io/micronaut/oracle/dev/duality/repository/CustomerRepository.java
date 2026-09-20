package io.micronaut.oracle.dev.duality.repository;

import io.micronaut.data.connection.annotation.ClientInfo;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.oracle.dev.duality.model.Customer;

import java.util.Optional;

@ClientInfo.Attribute(name = "OCSID.MODULE", value = "duality-customers")
@JdbcRepository(dialect = Dialect.ORACLE)
public interface CustomerRepository extends CrudRepository<Customer, Long> {

  @ClientInfo.Attribute(name = "OCSID.ACTION", value = "find-customer-by-email")
  Optional<Customer> findByEmail(String email);
}
