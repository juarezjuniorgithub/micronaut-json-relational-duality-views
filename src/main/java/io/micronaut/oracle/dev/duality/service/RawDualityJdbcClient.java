package io.micronaut.oracle.dev.duality.service;

import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.connection.annotation.Connectable;
import io.micronaut.oracle.dev.duality.web.OrderSearchCriteria;
import io.micronaut.oracle.dev.duality.web.RawJsonDocument;
import io.micronaut.oracle.dev.duality.web.RelationalOrderSnapshot;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Singleton
@Connectable
public class RawDualityJdbcClient {
  private final DataSource dataSource;

  public RawDualityJdbcClient(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  public DatabaseIdentity databaseIdentity() {
    try (Connection connection = dataSource.getConnection()) {
      DatabaseMetaData metadata = connection.getMetaData();
      return new DatabaseIdentity(metadata.getDatabaseProductName(), metadata.getDatabaseProductVersion(),
          dataSource.getClass().getName());
    } catch (SQLException e) {
      throw new DataAccessException("Unable to inspect Oracle database connection", e);
    }
  }

  public Optional<RawJsonDocument> orderDocument(Long id) {
    String sql = """
        SELECT json_serialize(DATA RETURNING CLOB PRETTY) AS DOCUMENT
        FROM JDV_ORDER_DV
        WHERE json_value(DATA, '$._id' RETURNING NUMBER) = ?
        """;
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, id);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return Optional.empty();
        }
        return Optional
            .of(new RawJsonDocument(DualitySchemaService.ORDER_DUALITY_VIEW, readClob(resultSet, "DOCUMENT")));
      }
    } catch (SQLException e) {
      throw new DataAccessException("Unable to read raw JSON document from " + DualitySchemaService.ORDER_DUALITY_VIEW,
          e);
    }
  }

  public List<RawJsonDocument> openOrderSummaries() {
    String sql = """
        SELECT json_serialize(DATA RETURNING CLOB PRETTY) AS DOCUMENT
        FROM JDV_OPEN_ORDER_SUMMARY_DV
        ORDER BY json_value(DATA, '$._id' RETURNING NUMBER)
        """;
    List<RawJsonDocument> documents = new ArrayList<>();
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet resultSet = statement.executeQuery()) {
      while (resultSet.next()) {
        documents
            .add(new RawJsonDocument(DualitySchemaService.OPEN_ORDER_SUMMARY_VIEW, readClob(resultSet, "DOCUMENT")));
      }
      return documents;
    } catch (SQLException e) {
      throw new DataAccessException(
          "Unable to read raw JSON summaries from " + DualitySchemaService.OPEN_ORDER_SUMMARY_VIEW, e);
    }
  }

  /**
   * Finds root document identifiers by applying fixed SQL/JSON predicates to the
   * duality view DATA column. All user-provided values are passed as JDBC and
   * SQL/JSON bind variables.
   */
  public List<Long> searchOrderIds(OrderSearchCriteria criteria) {
    List<String> predicates = new ArrayList<>();
    List<String> values = new ArrayList<>();
    addJsonPredicate(predicates, values, criteria.status(),
        "json_exists(DATA, '$?(@.status == $status)' PASSING ? AS \"status\")");
    addJsonPredicate(predicates, values, criteria.customerEmail(),
        "json_exists(DATA, '$?(@.customer.email == $customerEmail)' PASSING ? AS \"customerEmail\")");
    addJsonPredicate(predicates, values, criteria.sku(),
        "json_exists(DATA, '$.lines[*]?(@.sku == $sku)' PASSING ? AS \"sku\")");
    addJsonPredicate(predicates, values, criteria.priority(),
        "json_exists(DATA, '$?(@.auditTrail.priority == $priority)' PASSING ? AS \"priority\")");
    addJsonPredicate(predicates, values, criteria.preferenceChannel(),
        "json_exists(DATA, '$?(@.customer.preferences.channel == $preferenceChannel)' "
            + "PASSING ? AS \"preferenceChannel\")");
    if (predicates.isEmpty()) {
      throw new IllegalArgumentException("Provide at least one JSON document search criterion");
    }

    String sql = """
        SELECT json_value(DATA, '$._id' RETURNING NUMBER) AS ID
        FROM JDV_ORDER_DV
        """ + " WHERE " + String.join(" AND ", predicates) + """
        ORDER BY json_value(DATA, '$._id' RETURNING NUMBER)
        """;
    List<Long> ids = new ArrayList<>();
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int index = 0; index < values.size(); index++) {
        statement.setString(index + 1, values.get(index));
      }
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          ids.add(resultSet.getLong("ID"));
        }
      }
      return ids;
    } catch (SQLException e) {
      throw new DataAccessException("Unable to search JSON documents in " + DualitySchemaService.ORDER_DUALITY_VIEW, e);
    }
  }

  public Optional<RelationalOrderSnapshot> relationalSnapshot(Long id) {
    String sql = """
        SELECT o.ID,
               o.ORDER_NUMBER,
               o.STATUS,
               o.PLACED_AT,
               c.NAME AS CUSTOMER_NAME,
               c.EMAIL AS CUSTOMER_EMAIL,
               json_serialize(c.PREFERENCES RETURNING CLOB) AS CUSTOMER_PREFERENCES,
               json_serialize(o.AUDIT_TRAIL RETURNING CLOB) AS AUDIT_TRAIL,
               NVL((SELECT SUM(l.QUANTITY * l.UNIT_PRICE)
                    FROM JRDV_ORDER_LINES l
                    WHERE l.ORDER_ID = o.ID), 0) AS ORDER_TOTAL
        FROM JRDV_ORDERS o
        JOIN JRDV_CUSTOMERS c ON c.ID = o.CUSTOMER_ID
        WHERE o.ID = ?
        """;
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, id);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return Optional.empty();
        }
        return Optional.of(new RelationalOrderSnapshot(resultSet.getLong("ID"), resultSet.getString("ORDER_NUMBER"),
            resultSet.getString("STATUS"), toLocalDateTime(resultSet.getTimestamp("PLACED_AT")),
            resultSet.getString("CUSTOMER_NAME"), resultSet.getString("CUSTOMER_EMAIL"),
            readClob(resultSet, "CUSTOMER_PREFERENCES"), readClob(resultSet, "AUDIT_TRAIL"),
            resultSet.getBigDecimal("ORDER_TOTAL"), lineRows(connection, id), shipmentRows(connection, id)));
      }
    } catch (SQLException e) {
      throw new DataAccessException("Unable to read relational order snapshot", e);
    }
  }

  public int patchOrderStatus(Long id, String status) {
    String sql = """
        UPDATE JDV_ORDER_DV dv
        SET DATA = json_transform(DATA, SET '$.status' = ?)
        WHERE json_value(DATA, '$._id' RETURNING NUMBER) = ?
        """;
    return executeOrderUpdate(sql, statement -> {
      statement.setString(1, status);
      statement.setLong(2, id);
    });
  }

  public int updateUnderlyingOrderStatus(Long id, String status) {
    String sql = """
        UPDATE JRDV_ORDERS
        SET STATUS = ?, UPDATED_AT = SYSTIMESTAMP
        WHERE ID = ?
        """;
    return executeOrderUpdate(sql, statement -> {
      statement.setString(1, status);
      statement.setLong(2, id);
    });
  }

  public Optional<String> orderStatus(Long id) {
    String sql = "SELECT STATUS FROM JRDV_ORDERS WHERE ID = ?";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, id);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return Optional.empty();
        }
        return Optional.of(resultSet.getString("STATUS"));
      }
    } catch (SQLException e) {
      throw new DataAccessException("Unable to read order status", e);
    }
  }

  private int executeOrderUpdate(String sql, StatementBinder binder) {
    try (Connection connection = dataSource.getConnection()) {
      boolean previousAutoCommit = connection.getAutoCommit();
      connection.setAutoCommit(false);
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        binder.bind(statement);
        int updated = statement.executeUpdate();
        connection.commit();
        return updated;
      } catch (SQLException e) {
        connection.rollback();
        throw e;
      } finally {
        connection.setAutoCommit(previousAutoCommit);
      }
    } catch (SQLException e) {
      throw new DataAccessException("Unable to execute Oracle order update", e);
    }
  }

  private List<RelationalOrderSnapshot.LineRow> lineRows(Connection connection, Long orderId) throws SQLException {
    String sql = """
        SELECT ID, SKU, DESCRIPTION, QUANTITY, UNIT_PRICE,
               json_serialize(ATTRIBUTES RETURNING CLOB) AS ATTRIBUTES
        FROM JRDV_ORDER_LINES
        WHERE ORDER_ID = ?
        ORDER BY ID
        """;
    List<RelationalOrderSnapshot.LineRow> rows = new ArrayList<>();
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, orderId);
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          rows.add(new RelationalOrderSnapshot.LineRow(resultSet.getLong("ID"), resultSet.getString("SKU"),
              resultSet.getString("DESCRIPTION"), resultSet.getInt("QUANTITY"), resultSet.getBigDecimal("UNIT_PRICE"),
              readClob(resultSet, "ATTRIBUTES")));
        }
      }
    }
    return rows;
  }

  private List<RelationalOrderSnapshot.ShipmentRow> shipmentRows(Connection connection, Long orderId)
      throws SQLException {
    String sql = """
        SELECT ID, CARRIER, TRACKING_NUMBER, SHIPPED_AT,
               json_serialize(DELIVERY_WINDOW RETURNING CLOB) AS DELIVERY_WINDOW
        FROM JRDV_SHIPMENTS
        WHERE ORDER_ID = ?
        ORDER BY ID
        """;
    List<RelationalOrderSnapshot.ShipmentRow> rows = new ArrayList<>();
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, orderId);
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          rows.add(new RelationalOrderSnapshot.ShipmentRow(resultSet.getLong("ID"), resultSet.getString("CARRIER"),
              resultSet.getString("TRACKING_NUMBER"), toLocalDateTime(resultSet.getTimestamp("SHIPPED_AT")),
              readClob(resultSet, "DELIVERY_WINDOW")));
        }
      }
    }
    return rows;
  }

  private static String readClob(ResultSet resultSet, String columnName) throws SQLException {
    Clob clob = resultSet.getClob(columnName);
    if (clob == null) {
      return null;
    }
    return clob.getSubString(1, Math.toIntExact(clob.length()));
  }

  private static void addJsonPredicate(List<String> predicates, List<String> values, String value, String predicate) {
    if (value != null) {
      predicates.add(predicate);
      values.add(value);
    }
  }

  private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
    if (timestamp == null) {
      return null;
    }
    return timestamp.toLocalDateTime();
  }

  @FunctionalInterface
  private interface StatementBinder {
    void bind(PreparedStatement statement) throws SQLException;
  }

  public record DatabaseIdentity(String productName, String productVersion, String dataSourceType) {
  }
}
