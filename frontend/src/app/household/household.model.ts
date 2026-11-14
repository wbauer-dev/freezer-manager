/** Representations returned by the backend's Household & Access module. */

export type Role = 'OWNER' | 'MEMBER';

export interface HouseholdSummary {
  id: string;
  name: string;
  myRole: Role;
  memberCount: number;
}

export interface HouseholdDetails {
  id: string;
  name: string;
  foundedAt: string;
  myRole: Role;
  members: Member[];
}

export interface Member {
  userId: string;
  displayName: string;
  role: Role;
  joinedAt: string;
  /** The current user. */
  me: boolean;
}

/** An open invitation: whoever opens its link can join once, until it expires. Only owners see them. */
export interface Invitation {
  code: string;
  createdAt: string;
  expiresAt: string;
}

/** Same limit as the backend's Household aggregate. */
export const MAX_HOUSEHOLD_NAME_LENGTH = 50;

/** The link to send; it opens the join page, which accepts the invitation after logging in. */
export function invitationLink(invitation: Invitation): string {
  return `${location.origin}/join/${invitation.code}`;
}
