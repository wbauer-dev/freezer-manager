create schema if not exists catalog;

create table catalog.food_category
(
    id                text primary key,
    name              text not null,
    shelf_life_months int  not null check (shelf_life_months > 0),
    position          int  not null unique
);

-- Guide values for home freezers at -18 °C.
insert into catalog.food_category (id, name, shelf_life_months, position)
values ('beef-lamb', 'Beef & lamb', 10, 1),
       ('pork', 'Pork', 6, 2),
       ('minced-meat', 'Minced meat', 3, 3),
       ('poultry', 'Poultry', 8, 4),
       ('fish-lean', 'Fish (lean)', 6, 5),
       ('fish-fatty', 'Fish (fatty)', 3, 6),
       ('vegetables', 'Vegetables', 12, 7),
       ('fruit', 'Fruit', 12, 8),
       ('herbs', 'Herbs', 8, 9),
       ('bread', 'Bread & rolls', 3, 10),
       ('cakes', 'Cakes & pastries', 3, 11),
       ('cooked-meals', 'Cooked meals', 3, 12),
       ('soups-sauces', 'Soups & sauces', 4, 13),
       ('dairy', 'Butter & cheese', 6, 14),
       ('other', 'Other', 3, 15);

create table catalog.product
(
    id           uuid   not null primary key,
    household_id uuid   not null,
    name         text   not null check (length(name) between 1 and 40),
    category_id  text   not null references catalog.food_category (id),
    version      bigint not null
);

create unique index product_name_per_household on catalog.product (household_id, lower(name));
