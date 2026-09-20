package io.micronaut.oracle.dev.duality.service;

import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.connection.annotation.Connectable;
import io.micronaut.oracle.dev.duality.web.BootstrapReport;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
@Connectable
public class DualitySchemaService {
  public static final String ORDER_DUALITY_VIEW = "JDV_ORDER_DV";
  public static final String OPEN_ORDER_SUMMARY_VIEW = "JDV_OPEN_ORDER_SUMMARY_DV";

  private static final Logger LOG = LoggerFactory.getLogger(DualitySchemaService.class);
  private static final List<String> TABLES = List.of("JRDV_SHIPMENTS", "JRDV_ORDER_LINES", "JRDV_ORDERS",
      "JRDV_CUSTOMERS");
  private static final List<String> LEGACY_TABLES = List.of("JDV_SHIPMENTS", "JDV_ORDER_LINES", "JDV_ORDERS",
      "JDV_CUSTOMERS");
  private static final List<String> CREATED_OBJECTS = List.of("JRDV_CUSTOMERS table", "JRDV_ORDERS table",
      "JRDV_ORDER_LINES table", "JRDV_SHIPMENTS table", "JRDV_ORDERS_STATUS_IDX index", "JRDV_LINES_SKU_IDX index",
      "JRDV_LINES_COLOR_IDX JSON functional index", ORDER_DUALITY_VIEW + " JSON relational duality view",
      OPEN_ORDER_SUMMARY_VIEW + " JSON relational duality summary view");

  private static final String CREATE_CUSTOMERS = """
      CREATE TABLE JRDV_CUSTOMERS (
          ID NUMBER(19) NOT NULL,
          NAME VARCHAR2(120 CHAR) NOT NULL,
          EMAIL VARCHAR2(200 CHAR) NOT NULL,
          LOYALTY_STATUS VARCHAR2(30 CHAR) DEFAULT 'STANDARD' NOT NULL,
          PREFERENCES JSON,
          CREATED_AT TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
          CONSTRAINT JRDV_CUSTOMERS_PK PRIMARY KEY (ID),
          CONSTRAINT JRDV_CUSTOMERS_EMAIL_UK UNIQUE (EMAIL),
          CONSTRAINT JRDV_CUSTOMERS_LOYALTY_CK CHECK (LOYALTY_STATUS IN ('STANDARD', 'GOLD', 'PLATINUM'))
      )
      """;

  private static final String CREATE_ORDERS = """
      CREATE TABLE JRDV_ORDERS (
          ID NUMBER(19) NOT NULL,
          ORDER_NUMBER VARCHAR2(40 CHAR) NOT NULL,
          CUSTOMER_ID NUMBER(19) NOT NULL,
          STATUS VARCHAR2(30 CHAR) DEFAULT 'PLACED' NOT NULL,
          PLACED_AT TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
          UPDATED_AT TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
          AUDIT_TRAIL JSON,
          CONSTRAINT JRDV_ORDERS_PK PRIMARY KEY (ID),
          CONSTRAINT JRDV_ORDERS_NUMBER_UK UNIQUE (ORDER_NUMBER),
          CONSTRAINT JRDV_ORDERS_CUSTOMER_FK FOREIGN KEY (CUSTOMER_ID) REFERENCES JRDV_CUSTOMERS (ID),
          CONSTRAINT JRDV_ORDERS_STATUS_CK CHECK (STATUS IN ('PLACED', 'PAID', 'PACKED', 'SHIPPED', 'CANCELLED'))
      )
      """;

  private static final String CREATE_ORDER_LINES = """
      CREATE TABLE JRDV_ORDER_LINES (
          ID NUMBER(19) NOT NULL,
          ORDER_ID NUMBER(19) NOT NULL,
          SKU VARCHAR2(80 CHAR) NOT NULL,
          DESCRIPTION VARCHAR2(200 CHAR) NOT NULL,
          QUANTITY NUMBER(10) NOT NULL,
          UNIT_PRICE NUMBER(12, 2) NOT NULL,
          ATTRIBUTES JSON,
          CONSTRAINT JRDV_ORDER_LINES_PK PRIMARY KEY (ID),
          CONSTRAINT JRDV_LINES_ORDER_FK FOREIGN KEY (ORDER_ID) REFERENCES JRDV_ORDERS (ID) ON DELETE CASCADE,
          CONSTRAINT JRDV_LINES_QTY_CK CHECK (QUANTITY > 0),
          CONSTRAINT JRDV_LINES_PRICE_CK CHECK (UNIT_PRICE >= 0)
      )
      """;

  private static final String CREATE_SHIPMENTS = """
      CREATE TABLE JRDV_SHIPMENTS (
          ID NUMBER(19) NOT NULL,
          ORDER_ID NUMBER(19) NOT NULL,
          CARRIER VARCHAR2(80 CHAR) NOT NULL,
          TRACKING_NUMBER VARCHAR2(120 CHAR),
          SHIPPED_AT TIMESTAMP,
          DELIVERY_WINDOW JSON,
          CONSTRAINT JRDV_SHIPMENTS_PK PRIMARY KEY (ID),
          CONSTRAINT JRDV_SHIPMENTS_ORDER_FK FOREIGN KEY (ORDER_ID) REFERENCES JRDV_ORDERS (ID) ON DELETE CASCADE
      )
      """;

  public static final String CREATE_ORDER_DUALITY_VIEW = """
      CREATE OR REPLACE JSON RELATIONAL DUALITY VIEW JDV_ORDER_DV AS
      SELECT JSON {
          '_id' : o.id,
          'orderNumber' : o.order_number,
          'status' : o.status,
          'placedAt' : o.placed_at,
          'customer' :
              (SELECT JSON {
                  '_id' : c.id,
                  'name' : c.name,
                  'email' : c.email WITH NOCHECK,
                  'loyaltyStatus' : c.loyalty_status,
                  'preferences' : c.preferences
              }
              FROM JRDV_CUSTOMERS c WITH INSERT UPDATE NODELETE
              WHERE c.id = o.customer_id),
          'lines' :
              [SELECT JSON {
                  '_id' : l.id,
                  'sku' : l.sku,
                  'description' : l.description,
                  'quantity' : l.quantity,
                  'unitPrice' : l.unit_price,
                  'attributes' : l.attributes
              }
              FROM JRDV_ORDER_LINES l WITH INSERT UPDATE DELETE
              WHERE l.order_id = o.id],
          'shipments' :
              [SELECT JSON {
                  '_id' : s.id,
                  'carrier' : s.carrier,
                  'trackingNumber' : s.tracking_number,
                  'shippedAt' : s.shipped_at,
                  'deliveryWindow' : s.delivery_window
              }
              FROM JRDV_SHIPMENTS s WITH INSERT UPDATE DELETE
              WHERE s.order_id = o.id],
          'auditTrail' : o.audit_trail WITH NOCHECK
      }
      FROM JRDV_ORDERS o WITH INSERT UPDATE DELETE
      """;

  public static final String CREATE_OPEN_ORDER_SUMMARY_VIEW = """
      CREATE OR REPLACE JSON RELATIONAL DUALITY VIEW JDV_OPEN_ORDER_SUMMARY_DV AS
      SELECT JSON {
          '_id' : o.id,
          'orderNumber' : o.order_number,
          'status' : o.status WITH NOUPDATE,
          'customer' :
              (SELECT JSON {
                  'customerId' : c.id HIDDEN,
                  'name' : c.name
              }
              FROM JRDV_CUSTOMERS c WITH NOINSERT NOUPDATE NODELETE
              WHERE c.id = o.customer_id),
          'lineSkus' :
              [SELECT l.sku
              FROM JRDV_ORDER_LINES l
              WHERE l.order_id = o.id]
      }
      FROM JRDV_ORDERS o WITH UPDATE
      WHERE o.status <> 'CANCELLED' WITH CHECK OPTION
      """;

  private final DataSource dataSource;

  public DualitySchemaService(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  public BootstrapReport bootstrap(boolean recreate) {
    try (Connection connection = dataSource.getConnection()) {
      DatabaseMetaData metadata = connection.getMetaData();
      if (recreate) {
        dropDemoObjects(connection);
      }
      createTablesIfMissing(connection);
      createIndexesIfMissing(connection);
      createOrReplaceViews(connection);
      seedIfEmpty(connection);
      return new BootstrapReport(recreate, metadata.getDatabaseProductName(), metadata.getDatabaseProductVersion(),
          dataSource.getClass().getName(), CREATED_OBJECTS, rowCounts(connection));
    } catch (SQLException e) {
      throw new DataAccessException("Unable to bootstrap Oracle JSON relational duality demo schema", e);
    }
  }

  public Map<String, String> viewDefinitions() {
    Map<String, String> definitions = new LinkedHashMap<>();
    definitions.put(ORDER_DUALITY_VIEW, CREATE_ORDER_DUALITY_VIEW);
    definitions.put(OPEN_ORDER_SUMMARY_VIEW, CREATE_OPEN_ORDER_SUMMARY_VIEW);
    return definitions;
  }

  public Map<String, Long> rowCounts() {
    try (Connection connection = dataSource.getConnection()) {
      return rowCounts(connection);
    } catch (SQLException e) {
      throw new DataAccessException("Unable to read demo row counts", e);
    }
  }

  private void dropDemoObjects(Connection connection) throws SQLException {
    dropIfExists(connection, "DROP VIEW " + ORDER_DUALITY_VIEW);
    dropIfExists(connection, "DROP VIEW " + OPEN_ORDER_SUMMARY_VIEW);
    for (String table : TABLES) {
      dropIfExists(connection, "DROP TABLE " + table + " CASCADE CONSTRAINTS PURGE");
    }
    for (String legacyTable : LEGACY_TABLES) {
      dropIfExists(connection, "DROP TABLE " + legacyTable + " CASCADE CONSTRAINTS PURGE");
    }
  }

  private void createTablesIfMissing(Connection connection) throws SQLException {
    createTableIfMissing(connection, "JRDV_CUSTOMERS", CREATE_CUSTOMERS);
    createTableIfMissing(connection, "JRDV_ORDERS", CREATE_ORDERS);
    createTableIfMissing(connection, "JRDV_ORDER_LINES", CREATE_ORDER_LINES);
    createTableIfMissing(connection, "JRDV_SHIPMENTS", CREATE_SHIPMENTS);
  }

  private void createIndexesIfMissing(Connection connection) throws SQLException {
    createIndexIfMissing(connection, "JRDV_ORDERS_STATUS_IDX",
        "CREATE INDEX JRDV_ORDERS_STATUS_IDX ON JRDV_ORDERS (STATUS)");
    createIndexIfMissing(connection, "JRDV_LINES_SKU_IDX", "CREATE INDEX JRDV_LINES_SKU_IDX ON JRDV_ORDER_LINES (SKU)");
    createIndexIfMissing(connection, "JRDV_LINES_COLOR_IDX",
        "CREATE INDEX JRDV_LINES_COLOR_IDX ON JRDV_ORDER_LINES (JSON_VALUE(ATTRIBUTES, '$.color' RETURNING VARCHAR2(40)))");
  }

  private void createOrReplaceViews(Connection connection) throws SQLException {
    execute(connection, CREATE_ORDER_DUALITY_VIEW);
    execute(connection, CREATE_OPEN_ORDER_SUMMARY_VIEW);
  }

  private void seedIfEmpty(Connection connection) throws SQLException {
    if (countRows(connection, "JRDV_ORDERS") > 0) {
      LOG.info("Oracle JSON duality demo already contains orders; seed data left unchanged");
      return;
    }

    boolean previousAutoCommit = connection.getAutoCommit();
    connection.setAutoCommit(false);
    try {
      insertCustomer(connection, 1001L, "Asha Rao", "asha.rao@example.com", "PLATINUM", """
          {"channel":"mobile","newsletter":true,"fulfillment":{"allowSubstitutions":false,"preferredWarehouse":"DUB-1"}}
          """);
      insertCustomer(connection, 1002L, "Morgan Lee", "morgan.lee@example.com", "GOLD", """
          {"channel":"web","newsletter":false,"fulfillment":{"allowSubstitutions":true,"preferredWarehouse":"LHR-2"}}
          """);
      insertOrder(connection, 5001L, "PO-26AI-5001", 1001L, "PAID", LocalDateTime.of(2026, 8, 27, 9, 15),
          """
              {"createdBy":"bootstrap","priority":"expedite","events":[{"type":"CREATED","at":"2026-08-27T09:15:00"},{"type":"PAID","at":"2026-08-27T09:20:00"}]}
              """);
      insertOrder(connection, 5002L, "PO-26AI-5002", 1002L, "PLACED", LocalDateTime.of(2026, 8, 27, 10, 5), """
          {"createdBy":"bootstrap","priority":"normal","events":[{"type":"CREATED","at":"2026-08-27T10:05:00"}]}
          """);
      insertLine(connection, 9001L, 5001L, "DB26AI-JSON", "Oracle AI Database 26ai JSON workshop kit", 1,
          new BigDecimal("149.00"), """
              {"color":"red","license":"developer","warehouse":"DUB-1"}
              """);
      insertLine(connection, 9002L, 5001L, "MN-DATA-JDBC", "Micronaut Data JDBC field guide", 2,
          new BigDecimal("39.50"), """
              {"color":"blue","format":"printed","warehouse":"DUB-1"}
              """);
      insertLine(connection, 9003L, 5002L, "UCP-OPS", "Oracle UCP operations checklist", 1, new BigDecimal("19.00"), """
          {"color":"green","format":"laminated","warehouse":"LHR-2"}
          """);
      insertShipment(connection, 7001L, 5001L, "DHL", "DHL-26AI-0001", LocalDateTime.of(2026, 8, 27, 12, 30), """
          {"from":"2026-08-28T09:00:00","to":"2026-08-28T13:00:00","timezone":"Europe/Dublin"}
          """);
      connection.commit();
      LOG.info("Seeded Oracle JSON relational duality demo schema");
    } catch (SQLException e) {
      connection.rollback();
      throw e;
    } finally {
      connection.setAutoCommit(previousAutoCommit);
    }
  }

  private void insertCustomer(Connection connection, Long id, String name, String email, String loyaltyStatus,
      String preferences) throws SQLException {
    String sql = """
        INSERT INTO JRDV_CUSTOMERS (ID, NAME, EMAIL, LOYALTY_STATUS, PREFERENCES)
        VALUES (?, ?, ?, ?, JSON(?))
        """;
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, id);
      statement.setString(2, name);
      statement.setString(3, email);
      statement.setString(4, loyaltyStatus);
      statement.setString(5, preferences);
      statement.executeUpdate();
    }
  }

  private void insertOrder(Connection connection, Long id, String orderNumber, Long customerId, String status,
      LocalDateTime placedAt, String auditTrail) throws SQLException {
    String sql = """
        INSERT INTO JRDV_ORDERS (ID, ORDER_NUMBER, CUSTOMER_ID, STATUS, PLACED_AT, UPDATED_AT, AUDIT_TRAIL)
        VALUES (?, ?, ?, ?, ?, ?, JSON(?))
        """;
    Timestamp timestamp = Timestamp.valueOf(placedAt);
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, id);
      statement.setString(2, orderNumber);
      statement.setLong(3, customerId);
      statement.setString(4, status);
      statement.setTimestamp(5, timestamp);
      statement.setTimestamp(6, timestamp);
      statement.setString(7, auditTrail);
      statement.executeUpdate();
    }
  }

  private void insertLine(Connection connection, Long id, Long orderId, String sku, String description,
      Integer quantity, BigDecimal unitPrice, String attributes) throws SQLException {
    String sql = """
        INSERT INTO JRDV_ORDER_LINES (ID, ORDER_ID, SKU, DESCRIPTION, QUANTITY, UNIT_PRICE, ATTRIBUTES)
        VALUES (?, ?, ?, ?, ?, ?, JSON(?))
        """;
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, id);
      statement.setLong(2, orderId);
      statement.setString(3, sku);
      statement.setString(4, description);
      statement.setInt(5, quantity);
      statement.setBigDecimal(6, unitPrice);
      statement.setString(7, attributes);
      statement.executeUpdate();
    }
  }

  private void insertShipment(Connection connection, Long id, Long orderId, String carrier, String trackingNumber,
      LocalDateTime shippedAt, String deliveryWindow) throws SQLException {
    String sql = """
        INSERT INTO JRDV_SHIPMENTS (ID, ORDER_ID, CARRIER, TRACKING_NUMBER, SHIPPED_AT, DELIVERY_WINDOW)
        VALUES (?, ?, ?, ?, ?, JSON(?))
        """;
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, id);
      statement.setLong(2, orderId);
      statement.setString(3, carrier);
      statement.setString(4, trackingNumber);
      statement.setTimestamp(5, Timestamp.valueOf(shippedAt));
      statement.setString(6, deliveryWindow);
      statement.executeUpdate();
    }
  }

  private void createTableIfMissing(Connection connection, String tableName, String ddl) throws SQLException {
    if (!tableExists(connection, tableName)) {
      execute(connection, ddl);
    }
  }

  private void createIndexIfMissing(Connection connection, String indexName, String ddl) throws SQLException {
    if (!indexExists(connection, indexName)) {
      execute(connection, ddl);
    }
  }

  private boolean tableExists(Connection connection, String tableName) throws SQLException {
    String sql = "SELECT COUNT(*) FROM USER_TABLES WHERE TABLE_NAME = ?";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, tableName);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next() && resultSet.getLong(1) > 0;
      }
    }
  }

  private boolean indexExists(Connection connection, String indexName) throws SQLException {
    String sql = "SELECT COUNT(*) FROM USER_INDEXES WHERE INDEX_NAME = ?";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, indexName);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next() && resultSet.getLong(1) > 0;
      }
    }
  }

  private Map<String, Long> rowCounts(Connection connection) throws SQLException {
    Map<String, Long> counts = new LinkedHashMap<>();
    List<String> countableObjects = new ArrayList<>(TABLES);
    countableObjects.add(ORDER_DUALITY_VIEW);
    countableObjects.add(OPEN_ORDER_SUMMARY_VIEW);
    for (String object : countableObjects) {
      counts.put(object, countRows(connection, object));
    }
    return counts;
  }

  private long countRows(Connection connection, String objectName) throws SQLException {
    String sql = "SELECT COUNT(*) FROM " + objectName;
    try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
      resultSet.next();
      return resultSet.getLong(1);
    }
  }

  private void execute(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private void dropIfExists(Connection connection, String sql) throws SQLException {
    try {
      execute(connection, sql);
    } catch (SQLException e) {
      if (e.getErrorCode() != 942 && e.getErrorCode() != 4043) {
        throw e;
      }
    }
  }
}
