import { Component, computed, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Observable } from 'rxjs';

import { InitialsAvatar } from '../shared/initials-avatar';
import { problemMessage } from '../shared/problem-message';
import { HouseholdApi } from './household-api';
import { HouseholdShell } from './household-shell';
import { HouseholdDetails, Invitation, Member, invitationLink } from './household.model';

/** A change to a member that waits for confirmation. */
interface PendingChange {
  action: 'remove' | 'make-owner';
  userId: string;
}

/**
 * Who belongs to the household. Owners invite people with a link, remove members and make members owners; everyone
 * can leave. The rules (e.g. the last owner can't leave) are the backend's; its answers are shown as they come.
 */
@Component({
  selector: 'app-members-page',
  imports: [DatePipe, MatButtonModule, MatIconModule, MatTooltipModule, InitialsAvatar],
  templateUrl: './members-page.html',
  styleUrl: './members-page.scss',
})
export class MembersPage {
  readonly householdId = input.required<string>();

  private readonly api = inject(HouseholdApi);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  /** The frame around the page shows the number of members. */
  private readonly shell = inject(HouseholdShell, { optional: true });

  protected readonly household = httpResource<HouseholdDetails>(
    () => `/api/households/${this.householdId()}`,
  );
  protected readonly isOwner = computed(
    () => this.household.hasValue() && this.household.value().myRole === 'OWNER',
  );
  /** Only owners see invitations: whoever has a link can join. */
  protected readonly invitations = httpResource<Invitation[]>(() =>
    this.isOwner() ? `/api/households/${this.householdId()}/invitations` : undefined,
  );

  protected readonly pending = signal<PendingChange | null>(null);
  protected readonly confirmingLeave = signal(false);
  protected readonly busy = signal(false);
  /** The invitation just created, highlighted in the list. */
  protected readonly created = signal<string | null>(null);
  protected readonly canShare = typeof navigator !== 'undefined' && 'share' in navigator;
  protected readonly link = invitationLink;

  protected isPending(member: Member, action: PendingChange['action']): boolean {
    const pending = this.pending();
    return pending?.userId === member.userId && pending.action === action;
  }

  protected invite(): void {
    this.run(this.api.invite(this.householdId()), (invitation) => {
      this.invitations.update((invitations) => [invitation, ...(invitations ?? [])]);
      this.created.set(invitation.code);
    });
  }

  protected async copy(invitation: Invitation): Promise<void> {
    try {
      await navigator.clipboard.writeText(invitationLink(invitation));
      this.notify($localize`:@@members.invitations.copied:Link copied`);
    } catch {
      this.notify(
        $localize`:@@members.invitations.copyFailed:The link could not be copied. Select it and copy it yourself.`,
      );
    }
  }

  protected async share(invitation: Invitation, householdName: string): Promise<void> {
    try {
      await navigator.share({
        title: householdName,
        text: $localize`:@@members.invitations.shareText:Join ${householdName}:name: in Freezer Manager`,
        url: invitationLink(invitation),
      });
    } catch {
      // Closing the share sheet is not an error worth mentioning.
    }
  }

  protected revoke(invitation: Invitation): void {
    this.invitations.update((invitations) =>
      invitations?.filter((other) => other.code !== invitation.code),
    );
    this.run(this.api.revokeInvitation(this.householdId(), invitation.code), () =>
      this.notify($localize`:@@members.invitations.revoked:Invitation revoked`),
    );
  }

  protected makeOwner(member: Member): void {
    this.pending.set(null);
    this.run(this.api.makeOwner(this.householdId(), member.userId), (household) =>
      this.household.set(household),
    );
  }

  protected remove(member: Member): void {
    this.pending.set(null);
    this.run(this.api.removeMember(this.householdId(), member.userId), (household) => {
      this.household.set(household);
      this.shell?.refresh();
      this.notify($localize`:@@members.removed:${member.displayName}:name: is no longer a member`);
    });
  }

  protected leave(household: HouseholdDetails): void {
    this.confirmingLeave.set(false);
    this.run(this.api.leave(household.id), () => {
      this.notify($localize`:@@members.leave.done:You left ${household.name}:name:`);
      this.router.navigate(['/households']);
    });
  }

  protected removeLabel(member: Member): string {
    return $localize`:@@members.removeNamed:Remove ${member.displayName}:name: from the household`;
  }

  protected makeOwnerLabel(member: Member): string {
    return $localize`:@@members.makeOwnerNamed:Make ${member.displayName}:name: an owner`;
  }

  private run<T>(command: Observable<T>, onSuccess: (result: T) => void): void {
    this.busy.set(true);
    command.subscribe({
      next: (result) => {
        this.busy.set(false);
        onSuccess(result);
      },
      error: (error) => {
        this.busy.set(false);
        this.household.reload();
        this.invitations.reload();
        this.snackBar.open(
          problemMessage(
            error,
            $localize`:@@common.error.retry:That did not work. Please try again.`,
          ),
          $localize`:@@common.ok:OK`,
          { duration: 6000 },
        );
      },
    });
  }

  private notify(message: string): void {
    this.snackBar.open(message, undefined, { duration: 3000 });
  }
}
