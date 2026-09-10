export type UserRole = "USER" | "ADMIN" | "SUPER_ADMIN";
export type UserStatus = "NORMAL" | "BANNED" | "DISABLED" | "DELETED";

export interface UserProfile {
  id: string;
  email: string;
  displayName: string;
  status: UserStatus;
  roles: UserRole[];
}

export interface AuthPayload {
  accessToken: string;
  tokenType: "Bearer";
  expiresIn: number;
  user: UserProfile;
}

export interface LoginInput {
  email: string;
  password: string;
}

export interface RegisterInput extends LoginInput {
  displayName: string;
}
