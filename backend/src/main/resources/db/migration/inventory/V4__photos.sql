-- photo_id refers to the media module. Modules don't reference each other's tables.
alter table inventory.frozen_item add column photo_id uuid;
