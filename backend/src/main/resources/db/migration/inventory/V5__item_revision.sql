-- Every event of an item carries the item's next revision, so other modules can tell an old event from a newer one.
-- Existing items have announced their past events without one; their next event is revision 1.
alter table inventory.frozen_item add column revision bigint not null default 0;
