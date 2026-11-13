-- Every newly frozen item carries a label code. Items frozen before labels existed have none.
alter table inventory.frozen_item add column label_code text;

-- A label sits on one package at a time.
create unique index frozen_item_label_in_freezer on inventory.frozen_item (household_id, label_code)
    where state = 'IN_FREEZER' and label_code is not null;
