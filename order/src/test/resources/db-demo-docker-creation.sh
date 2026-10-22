docker run --name p-logistics-demo-order-db \
  -e POSTGRES_DB=order \
  -e POSTGRES_USER=order \
  -e POSTGRES_PASSWORD=order \
  -p 5433:5432 \
  -d postgres:17
