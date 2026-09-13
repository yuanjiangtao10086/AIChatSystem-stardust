import { computed, ref } from "vue";

/**
 * Application-wide theme state.
 *
 * The preference is stored as `system | light | dark` (never as a resolved colour) so following the OS
 * stays possible after the user once picked a theme manually. Only the resolved value is written to the
 * DOM as `data-theme`, which is what every CSS variable override keys off.
 *
 * The state is module-level on purpose: the toggle lives in two places (top bar and chat sidebar) and
 * both must always show the same value without prop drilling.
 */
export type ThemePreference = "system" | "light" | "dark";
export type ResolvedTheme = "light" | "dark";

const STORAGE_KEY = "stardust.theme";
const DARK_QUERY = "(prefers-color-scheme: dark)";

const readPreference = (): ThemePreference => {
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY);
    if (stored === "light" || stored === "dark" || stored === "system") {
      return stored;
    }
  } catch {
    // Storage can be unavailable (private mode, blocked cookies): falling back to the OS choice is fine.
  }
  return "system";
};

const systemPrefersDark = (): boolean =>
  typeof window.matchMedia === "function"
    ? window.matchMedia(DARK_QUERY).matches
    : false;

const resolveTheme = (preference: ThemePreference): ResolvedTheme =>
  preference === "system"
    ? systemPrefersDark()
      ? "dark"
      : "light"
    : preference;

export const themePreference = ref<ThemePreference>(readPreference());
export const resolvedTheme = ref<ResolvedTheme>(
  resolveTheme(themePreference.value)
);

let mediaQuery: MediaQueryList | null = null;

const apply = (theme: ResolvedTheme): void => {
  const root = document.documentElement;
  root.dataset.theme = theme;
  // Lets the browser render native controls (scrollbars, form fields) with the same scheme.
  root.style.colorScheme = theme;
};

const sync = (): void => {
  resolvedTheme.value = resolveTheme(themePreference.value);
  apply(resolvedTheme.value);
};

export function useTheme() {
  const setPreference = (next: ThemePreference): void => {
    themePreference.value = next;
    try {
      window.localStorage.setItem(STORAGE_KEY, next);
    } catch {
      // Persistence is best-effort; the in-memory preference still applies for this session.
    }
    sync();
  };

  const toggle = (): void =>
    setPreference(resolvedTheme.value === "dark" ? "light" : "dark");

  const isDark = computed(() => resolvedTheme.value === "dark");

  return { themePreference, resolvedTheme, isDark, setPreference, toggle };
}

/**
 * Applies the stored theme before the first paint and keeps following the OS while the preference is
 * `system`. Called once from `main.ts`; safe to call again (idempotent listener registration).
 */
export function initTheme(): void {
  sync();
  if (mediaQuery || typeof window.matchMedia !== "function") {
    return;
  }
  mediaQuery = window.matchMedia(DARK_QUERY);
  if (typeof mediaQuery.addEventListener === "function") {
    mediaQuery.addEventListener("change", () => {
      if (themePreference.value === "system") {
        sync();
      }
    });
  }
}
