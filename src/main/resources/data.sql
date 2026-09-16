INSERT INTO inventory_item(product_id, quantity)
VALUES
    ('P1', 10),
    ('P2', 5),
    ('P3', 20)
    ON CONFLICT (product_id) DO NOTHING;