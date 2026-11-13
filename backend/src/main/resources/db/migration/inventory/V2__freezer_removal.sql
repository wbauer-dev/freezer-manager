-- Removed freezers are kept, so the history of items that were stored in them stays complete.
alter table inventory.freezer add column removed_at timestamptz;

-- The name of a removed freezer can be used again.
drop index inventory.freezer_name_per_household;
create unique index freezer_name_per_household on inventory.freezer (household_id, lower(name)) where removed_at is null;
