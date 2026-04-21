# Spring Framework plain JDBC wiring

Open this reference when the common path in `SKILL.md` is not enough and the blocker is transaction-scoped connections, `SqlRowSet`, `RowMapper` reuse, or `DataSourceTransactionManager` with plain JDBC.

## Transaction-scoped connections

Obtain a connection that participates in the current Spring transaction with `DataSourceUtils`:

```java
Connection connection = DataSourceUtils.getConnection(dataSource);
try (PreparedStatement ps = connection.prepareStatement(
        "UPDATE items SET quantity = quantity - ? WHERE id = ?")) {
    ps.setInt(1, amount);
    ps.setLong(2, itemId);
    ps.executeUpdate();
}
finally {
    DataSourceUtils.releaseConnection(connection, dataSource);
}
```

Use this when lower-level JDBC code must share the same transaction-bound connection as the surrounding Spring-managed work.

## DataSourceTransactionManager with plain JDBC

Register the transaction manager explicitly:

```java
@Bean
DataSourceTransactionManager transactionManager(DataSource dataSource) {
    return new DataSourceTransactionManager(dataSource);
}
```

Use `@Transactional` on service methods when the application pairs plain JDBC with `DataSourceTransactionManager`.

Use `TransactionTemplate` when each method needs its own transaction boundary without method-level annotations.

```java
TransactionTemplate tx = new TransactionTemplate(transactionManager);
tx.executeWithoutResult(status -> jdbc.update(
    "UPDATE items SET quantity = quantity - ? WHERE id = ?",
    amount,
    itemId
));
```

## SqlRowSet for disconnected result sets

Query into a `SqlRowSet` when the result must survive beyond the connection lifetime:

```java
SqlRowSet rows = jdbc.queryForRowSet(
    "SELECT id, name FROM items WHERE warehouse = ?",
    warehouseId
);
while (rows.next()) {
    Item item = new Item(rows.getLong("id"), rows.getString("name"));
}
```

Use this when the data must be processed after the connection is released or when building in-memory datasets.

## Reusable RowMapper

Extract a `RowMapper` for reuse across query methods:

```java
class ItemRowMapper implements RowMapper<Item> {
    @Override
    public Item mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Item(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getInt("quantity")
        );
    }
}
```

Register as a bean and inject where needed:

```java
@Bean
ItemRowMapper itemRowMapper() {
    return new ItemRowMapper();
}
```

Use in queries:

```java
class InventoryDao {
    private final JdbcTemplate jdbc;
    private final RowMapper<Item> itemRowMapper;

    InventoryDao(JdbcTemplate jdbc, RowMapper<Item> itemRowMapper) {
        this.jdbc = jdbc;
        this.itemRowMapper = itemRowMapper;
    }

    List<Item> findAll() {
        return jdbc.query(
            "SELECT id, name, quantity FROM items",
            itemRowMapper
        );
    }
}
```

## NamedParameterJdbcTemplate

Use named parameters instead of positional `?` placeholders:

```java
@Service
class InventoryDao {
    private final NamedParameterJdbcTemplate named;

    InventoryDao(DataSource dataSource) {
        this.named = new NamedParameterJdbcTemplate(dataSource);
    }

    Item findById(Long id) {
        return named.queryForObject(
            "SELECT id, name, quantity FROM items WHERE id = :id",
            Map.of("id", id),
            (rs, rowNum) -> new Item(rs.getLong("id"), rs.getString("name"), rs.getInt("quantity"))
        );
    }
}
```

Use `MapSqlParameterSource` or `BeanPropertySqlParameterSource` for complex parameter maps.

## Decision points

| Situation | Use |
| --- | --- |
| One-off transactional operation | `TransactionTemplate` |
| Consistent transaction boundaries on service methods | `DataSourceTransactionManager` + `@Transactional` |
| Result must outlive connection | `SqlRowSet` |
| Reusable mapping logic across queries | `RowMapper` as a bean |
| Named parameters over `?` | `NamedParameterJdbcTemplate` |
