package inventory;

import java.util.Optional;

public interface InventoryRepository {

    Optional<InventoryItem> findByProductId(String productId);

    void save(InventoryItem item);

    boolean reserve(String productId, int quantity);
}