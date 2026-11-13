create schema if not exists media;

create table media.photo
(
    id           uuid        not null primary key,
    household_id uuid        not null,
    width        int         not null,
    height       int         not null,
    uploaded_by  text        not null,
    uploaded_at  timestamptz not null,
    version      bigint      not null
);

-- Image data is kept apart from the metadata, so loading a photo's aggregate never loads megabytes.
create table media.photo_data
(
    photo_id uuid  not null references media.photo (id) on delete cascade,
    variant  text  not null check (variant in ('LARGE', 'THUMBNAIL')),
    content  bytea not null,
    primary key (photo_id, variant)
);
