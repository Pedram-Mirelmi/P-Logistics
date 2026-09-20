docker run --name p-logistics-demo-inventory-db \                                                                                                             ✔
  -e POSTGRES_DB=inventory \
  -e POSTGRES_USER=inventory \
  -e POSTGRES_PASSWORD=inventory \
  -p 5434:5432 \
  -d postgres:17