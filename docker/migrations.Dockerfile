FROM flyway/flyway:12-alpine

COPY db/migration-app /flyway/sql

CMD ["migrate"]
