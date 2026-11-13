-- Spring Modulith event publication registry (transactional outbox for events between modules).
-- Schema taken from spring-modulith-events-jdbc (schemas/v2/schema-postgresql.sql).
create table if not exists event_publication
(
    id                     uuid                     not null,
    listener_id            text                     not null,
    event_type             text                     not null,
    serialized_event       text                     not null,
    publication_date       timestamp with time zone not null,
    completion_date        timestamp with time zone,
    status                 text,
    completion_attempts    int,
    last_resubmission_date timestamp with time zone,
    primary key (id)
);

create index if not exists event_publication_serialized_event_hash_idx on event_publication using hash (serialized_event);
create index if not exists event_publication_by_completion_date_idx on event_publication (completion_date);
