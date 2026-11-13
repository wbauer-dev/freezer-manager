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
  members: Member[];
}

export interface Member {
  userId: string;
  displayName: string;
  role: Role;
  joinedAt: string;
}

/** Same limit as the backend's Household aggregate. */
export const MAX_HOUSEHOLD_NAME_LENGTH = 50;
