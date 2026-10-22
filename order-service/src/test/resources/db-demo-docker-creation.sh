docker run --name p-logistics-demo-logistics-db \                                                                                                             ✔
  -e POSTGRES_DB=logistics \
  -e POSTGRES_USER=logistics \
  -e POSTGRES_PASSWORD=logistics \
  -p 5433:5432 \
  -d postgres:17