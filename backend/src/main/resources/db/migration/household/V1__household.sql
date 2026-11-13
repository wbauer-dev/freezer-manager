create schema if not exists household;

create table household.household
(
    id         uuid        not null primary key,
    name       text        not null check (length(name) between 1 and 50),
    founded_at timestamptz not null,
    version    bigint      not null
);

create table household.member
(
    household_id uuid        not null references household.household (id) on delete cascade,
    user_id      text        not null,
    display_name text        not null,
    role         text        not null check (role in ('OWNER', 'MEMBER')),
    joined_at    timestamptz not null,
    primary key (household_id, user_id)
);

create index member_user_id_idx on household.member (user_id);
