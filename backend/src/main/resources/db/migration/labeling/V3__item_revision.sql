-- The newest revision of the label's item whose event the label has applied; older events of that item are ignored.
-- 0 means none is known yet: existing labels accept the next event of their item, whatever its revision.
alter table labeling.label add column item_revision bigint not null default 0;
