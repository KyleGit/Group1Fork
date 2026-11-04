FROM mysql:8.0.46
COPY world.sql /docker-entrypoint-initdb.d/world.sql
ENV MYSQL_ROOT_PASSWORD=example
ENV MYSQL_ROOT_HOST=%
