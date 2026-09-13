package order;

public record OrderLine(
        String productId,
        int quantity
) {}