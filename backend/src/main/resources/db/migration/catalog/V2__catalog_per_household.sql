-- Each household gets its own catalog that every member can change.

create table catalog.household_catalog
(
    household_id uuid        not null primary key,
    -- Language of the system defaults the catalog started with; null if it started empty.
    language     text,
    started_at   timestamptz not null,
    version      bigint      not null
);

alter table catalog.food_category rename to food_category_template;
alter table catalog.product drop constraint product_category_id_fkey;

create table catalog.food_category
(
    id                uuid   not null primary key,
    household_id      uuid   not null,
    name              text   not null check (length(name) between 1 and 40),
    emoji             text   not null check (length(emoji) between 1 and 16),
    shelf_life_months int    not null check (shelf_life_months between 1 and 60),
    position          int    not null,
    version           bigint not null
);

create unique index food_category_name_per_household on catalog.food_category (household_id, lower(name));

-- Households that already have products keep using the former global categories, now as their own.
-- All other households start with the system defaults in their language when a member first opens the catalog.
create temporary table category_migration on commit drop as
select gen_random_uuid() as id, households.household_id, template.id as key, template.name,
       case template.id
           when 'beef-lamb' then '🥩'
           when 'pork' then '🥓'
           when 'minced-meat' then '🍔'
           when 'poultry' then '🍗'
           when 'fish-lean' then '🐟'
           when 'fish-fatty' then '🍣'
           when 'vegetables' then '🥦'
           when 'fruit' then '🍓'
           when 'herbs' then '🌿'
           when 'bread' then '🥖'
           when 'cakes' then '🍰'
           when 'cooked-meals' then '🍲'
           when 'soups-sauces' then '🥣'
           when 'dairy' then '🧀'
           else '📦'
           end as emoji,
       template.shelf_life_months, template.position
from (select distinct household_id from catalog.product) households
         cross join catalog.food_category_template template;

insert into catalog.food_category (id, household_id, name, emoji, shelf_life_months, position, version)
select id, household_id, name, emoji, shelf_life_months, position, 0
from category_migration;

insert into catalog.household_catalog (household_id, language, started_at, version)
select distinct household_id, 'en', now(), 0
from category_migration;

alter table catalog.product add column category uuid;

update catalog.product product
set category = migration.id
from category_migration migration
where migration.household_id = product.household_id
  and migration.key = product.category_id;

alter table catalog.product drop column category_id;
alter table catalog.product rename column category to category_id;
alter table catalog.product alter column category_id set not null;
alter table catalog.product add foreign key (category_id) references catalog.food_category (id);
create index product_category on catalog.product (category_id);

drop table catalog.food_category_template;
