create schema if not exists labeling;

-- item_id refers to the inventory module. Modules don't reference each other's tables.
create table labeling.label
(
    code         text        not null primary key,
    household_id uuid        not null,
    kind         text        not null check (kind in ('ONE_TIME', 'REUSABLE')),
    state        text        not null check (state in ('FREE', 'ATTACHED', 'USED_UP')),
    item_id      uuid,
    title        text,
    frozen_on    date,
    best_before  date,
    created_at   timestamptz not null,
    version      bigint      not null
);

create index label_household on labeling.label (household_id);

create table labeling.label_sheet
(
    household_id uuid   not null primary key,
    version      bigint not null
);

create table labeling.used_position
(
    household_id uuid not null references labeling.label_sheet (household_id) on delete cascade,
    position     int  not null,
    primary key (household_id, position)
);

create table labeling.queued_label
(
    household_id uuid not null references labeling.label_sheet (household_id) on delete cascade,
    queue_index  int  not null,
    code         text not null,
    primary key (household_id, queue_index)
);
