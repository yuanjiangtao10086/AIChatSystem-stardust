import { ActionContext, Module } from "vuex";
import { apiRequest, setAccessToken } from "@/api/client";
import {
  AuthPayload,
  LoginInput,
  RegisterInput,
  UserProfile,
} from "@/types/auth";

export interface AuthState {
  accessToken: string | null;
  user: UserProfile | null;
  initialized: boolean;
}

export interface RootState {
  auth: AuthState;
}

type AuthContext = ActionContext<AuthState, RootState>;

let refreshPromise: Promise<string | null> | null = null;

export const authModule: Module<AuthState, RootState> = {
  namespaced: true,
  state: (): AuthState => ({
    accessToken: null,
    user: null,
    initialized: false,
  }),
  getters: {
    isAuthenticated: (state): boolean =>
      Boolean(state.accessToken && state.user),
    user: (state): UserProfile | null => state.user,
    canAccessAdmin: (state): boolean =>
      Boolean(
        state.user?.roles.some(
          (role) => role === "ADMIN" || role === "SUPER_ADMIN"
        )
      ),
  },
  mutations: {
    SET_SESSION(state, payload: AuthPayload): void {
      state.accessToken = payload.accessToken;
      state.user = payload.user;
    },
    SET_USER(state, user: UserProfile): void {
      state.user = user;
    },
    CLEAR_SESSION(state): void {
      state.accessToken = null;
      state.user = null;
    },
    MARK_INITIALIZED(state): void {
      state.initialized = true;
    },
  },
  actions: {
    async register(
      { commit }: AuthContext,
      input: RegisterInput
    ): Promise<void> {
      const payload = await apiRequest<AuthPayload>(
        "/api/v1/auth/register",
        { method: "POST", body: JSON.stringify(input) },
        false
      );
      setAccessToken(payload.accessToken);
      commit("SET_SESSION", payload);
    },
    async login({ commit }: AuthContext, input: LoginInput): Promise<void> {
      const payload = await apiRequest<AuthPayload>(
        "/api/v1/auth/login",
        { method: "POST", body: JSON.stringify(input) },
        false
      );
      setAccessToken(payload.accessToken);
      commit("SET_SESSION", payload);
    },
    refresh({ commit }: AuthContext): Promise<string | null> {
      if (!refreshPromise) {
        refreshPromise = apiRequest<AuthPayload>(
          "/api/v1/auth/refresh",
          { method: "POST", headers: { "X-CSRF-Guard": "1" } },
          false
        )
          .then((payload) => {
            setAccessToken(payload.accessToken);
            commit("SET_SESSION", payload);
            return payload.accessToken;
          })
          .catch(() => {
            setAccessToken(null);
            commit("CLEAR_SESSION");
            return null;
          })
          .finally(() => {
            refreshPromise = null;
          });
      }
      return refreshPromise;
    },
    async bootstrap({ state, commit, dispatch }: AuthContext): Promise<void> {
      if (state.initialized) return;
      const token = await dispatch("refresh");
      if (token) {
        await dispatch("fetchCurrentUser");
      }
      commit("MARK_INITIALIZED");
    },
    async fetchCurrentUser({ commit }: AuthContext): Promise<UserProfile> {
      const user = await apiRequest<UserProfile>("/api/v1/users/me");
      commit("SET_USER", user);
      return user;
    },
    async updateProfile(
      { commit }: AuthContext,
      displayName: string
    ): Promise<void> {
      const user = await apiRequest<UserProfile>("/api/v1/users/me", {
        method: "PATCH",
        body: JSON.stringify({ displayName }),
      });
      commit("SET_USER", user);
    },
    async changePassword(
      { commit }: AuthContext,
      input: { currentPassword: string; newPassword: string }
    ): Promise<void> {
      const payload = await apiRequest<AuthPayload>(
        "/api/v1/users/me/password",
        {
          method: "PUT",
          body: JSON.stringify(input),
        }
      );
      setAccessToken(payload.accessToken);
      commit("SET_SESSION", payload);
    },
    async logout({ commit }: AuthContext): Promise<void> {
      try {
        await apiRequest<void>(
          "/api/v1/auth/logout",
          { method: "POST", headers: { "X-CSRF-Guard": "1" } },
          false
        );
      } finally {
        setAccessToken(null);
        commit("CLEAR_SESSION");
      }
    },
  },
};
