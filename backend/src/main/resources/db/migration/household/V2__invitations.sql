-- Part of the household aggregate. Spring Data JDBC rewrites all invitations when the household is saved.
-- Used and revoked invitations are deleted; expired ones are cleared away when an owner invites again.
create table household.invitation
(
    household_id uuid        not null references household.household (id) on delete cascade,
    code         text        not null,
    created_at   timestamptz not null,
    expires_at   timestamptz not null,
    primary key (household_id, code)
);

-- Whoever opens an invitation link is not a member yet: the household is looked up by the code alone.
create unique index invitation_code_idx on household.invitation (code);
