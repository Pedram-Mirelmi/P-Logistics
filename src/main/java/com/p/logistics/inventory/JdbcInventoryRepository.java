package com.p.logistics.inventory;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

@Repository
@Primary
public class JdbcInventoryRepository implements InventoryRepository {

    private final DataSource dataSource;

    public JdbcInventoryRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<InventoryItem> findByProductId(String productId) {
        String sql = """
                SELECT product_id, quantity
                FROM inventory_item
                WHERE product_id = ?
                """;

        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, productId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    InventoryItem item = new InventoryItem();
                    item.setProductId(resultSet.getString("product_id"));
                    item.setQuantity(resultSet.getInt("quantity"));

                    return Optional.of(item);
                }
            }

            return Optional.empty();

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean reserve(String productId, int quantity) {
        String updateInventorySql = """
            UPDATE inventory_item
            SET quantity = quantity - ?
            WHERE product_id = ?
              AND quantity >= ?
            """;

        String insertReservationSql = """
            INSERT INTO inventory_reservation(id, product_id, quantity)
            VALUES (?, ?, ?)
            """;

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            try {
                try (PreparedStatement update = connection.prepareStatement(updateInventorySql)) {
                    update.setInt(1, quantity);
                    update.setString(2, productId);
                    update.setInt(3, quantity);

                    int affected = update.executeUpdate();

                    if (affected == 0) {
                        connection.rollback();
                        return false;
                    }
                }

                if (true) {
                    throw new SQLException("Forced failure");
                }

                try (PreparedStatement insert = connection.prepareStatement(insertReservationSql)) {
                    insert.setString(1, UUID.randomUUID().toString());
                    insert.setString(2, productId);
                    insert.setInt(3, quantity);

                    insert.executeUpdate();
                }

                connection.commit();
                return true;

            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void save(InventoryItem item) {
        String sql = """
                    INSERT INTO inventory_item
                    VALUES (?, ?)
                    ON CONFLICT (product_id)
                    DO UPDATE SET quantity = EXCLUDED.quantity
                """;

        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
        ) {
            statement.setString(1, item.getProductId());
            statement.setInt(2, item.getQuantity());

            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}