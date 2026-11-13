create schema if not exists inventory;

create table inventory.freezer
(
    id           uuid   not null primary key,
    household_id uuid   not null,
    name         text   not null check (length(name) between 1 and 40),
    version      bigint not null
);

create unique index freezer_name_per_household on inventory.freezer (household_id, lower(name));

-- Part of the freezer aggregate. Spring Data JDBC rewrites all compartments when a freezer is saved, which is why
-- frozen items reference compartment ids without a foreign key.
create table inventory.compartment
(
    freezer_id uuid not null references inventory.freezer (id) on delete cascade,
    position   int  not null,
    id         uuid not null unique,
    name       text not null,
    primary key (freezer_id, position)
);

-- product_id refers to the catalog module. Modules don't reference each other's tables.
create table inventory.frozen_item
(
    id                     uuid    not null primary key,
    household_id           uuid    not null,
    freezer_id             uuid    not null references inventory.freezer (id),
    compartment_id         uuid    not null,
    product_id             uuid    not null,
    product_name           text    not null,
    note                   text,
    portions_initial       int     not null check (portions_initial between 1 and 99),
    portions_remaining     int     not null check (portions_remaining between 0 and portions_initial),
    grams_per_portion      int check (grams_per_portion > 0),
    frozen_on              date    not null,
    best_before_date       date    not null,
    best_before_overridden boolean not null,
    frozen_by              text    not null,
    state                  text    not null check (state in ('IN_FREEZER', 'CONSUMED', 'DISCARDED')),
    discard_reason         text,
    removed_at             timestamptz,
    version                bigint  not null
);

create index frozen_item_eat_first on inventory.frozen_item (household_id, best_before_date) where state = 'IN_FREEZER';
create index frozen_item_in_compartment on inventory.frozen_item (compartment_id) where state = 'IN_FREEZER';

create table inventory.withdrawal
(
    frozen_item_id uuid        not null references inventory.frozen_item (id) on delete cascade,
    position       int         not null,
    portions       int         not null check (portions > 0),
    taken_by       text        not null,
    taken_by_name  text        not null,
    taken_at       timestamptz not null,
    primary key (frozen_item_id, position)
);
