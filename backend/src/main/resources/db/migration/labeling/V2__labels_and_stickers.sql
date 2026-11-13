-- A label is only the identity behind a QR code. What is printed belongs to each sticker in the print queue.

-- "Reusable" labels are now called persistent labels.
alter table labeling.label drop constraint label_kind_check;
update labeling.label set kind = 'PERSISTENT' where kind = 'REUSABLE';
alter table labeling.label add constraint label_kind_check check (kind in ('ONE_TIME', 'PERSISTENT'));

create table labeling.queued_sticker
(
    household_id uuid not null references labeling.label_sheet (household_id) on delete cascade,
    queue_index  int  not null,
    code         text not null,
    layout       text not null check (layout in ('QR_ONLY', 'QR_AND_CONTENT', 'CONTENT_ONLY')),
    title        text,
    frozen_on    date,
    best_before  date,
    primary key (household_id, queue_index)
);

-- Labels that were waiting to be printed keep their place, with what used to be printed on them.
insert into labeling.queued_sticker (household_id, queue_index, code, layout, title, frozen_on, best_before)
select q.household_id,
       q.queue_index,
       q.code,
       case when l.kind = 'PERSISTENT' then 'QR_ONLY' else 'QR_AND_CONTENT' end,
       case when l.kind = 'PERSISTENT' then null else coalesce(l.title, 'Frozen item') end,
       case when l.kind = 'PERSISTENT' then null else l.frozen_on end,
       case when l.kind = 'PERSISTENT' then null else l.best_before end
from labeling.queued_label q
         join labeling.label l on l.code = q.code;

drop table labeling.queued_label;

alter table labeling.label
    drop column title,
    drop column frozen_on,
    drop column best_before;
