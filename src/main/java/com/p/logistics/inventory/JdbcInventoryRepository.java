package com.p.logistics.inventory;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

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

    @Override
    public boolean reserve(String productId, int quantity) {
        String sql = """
            UPDATE inventory_item
            SET quantity = quantity - ?
            WHERE product_id = ?
              AND quantity >= ?
            """;

        try (
            Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql);
        ) {
            statement.setInt(1, quantity);
            statement.setString(2, productId);
            statement.setInt(3, quantity);

            int updatedRows = statement.executeUpdate();

            return updatedRows == 1;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}