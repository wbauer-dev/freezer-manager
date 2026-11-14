create schema if not exists shopping;

create table shopping.shopping_list
(
    household_id uuid   not null primary key,
    version      bigint not null
);

-- Part of the shopping list aggregate. Spring Data JDBC rewrites all entries when the list is saved.
-- product_id refers to the catalog module (null for free text). Modules don't reference each other's tables.
create table shopping.entry
(
    household_id    uuid        not null references shopping.shopping_list (household_id) on delete cascade,
    position        int         not null,
    id              uuid        not null unique,
    product_id      uuid,
    name            text        not null,
    quantity        text,
    source          text        not null check (source in ('MANUAL')),
    added_by        text        not null,
    added_by_name   text        not null,
    added_at        timestamptz not null,
    checked_by      text,
    checked_by_name text,
    checked_at      timestamptz,
    primary key (household_id, position)
);
