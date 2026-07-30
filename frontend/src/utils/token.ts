const ACCESS_KEY = 'access_token';
const REFRESH_KEY = 'refresh_token';
const USER_KEY = 'user_info';

export function getAccessToken(): string | null {
  try {
    return localStorage.getItem(ACCESS_KEY);
  } catch {
    return null;
  }
}

export function getRefreshToken(): string | null {
  try {
    return localStorage.getItem(REFRESH_KEY);
  } catch {
    return null;
  }
}

export function setTokens(accessToken: string, refreshToken: string): void {
  try {
    localStorage.setItem(ACCESS_KEY, accessToken);
    localStorage.setItem(REFRESH_KEY, refreshToken);
  } catch {
    // localStorage 不可用时静默失败
  }
}

export function removeTokens(): void {
  try {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  } catch {
    // localStorage 不可用时静默失败
  }
}

export interface StoredUserInfo {
  userId: string;
  userName: string;
  role: string;
}

export function saveUserInfo(info: StoredUserInfo): void {
  try {
    localStorage.setItem(USER_KEY, JSON.stringify(info));
  } catch {
    // localStorage 不可用时静默失败
  }
}

export function getStoredUserInfo(): StoredUserInfo | null {
  try {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? (JSON.parse(raw) as StoredUserInfo) : null;
  } catch {
    return null;
  }
}

export function removeUserInfo(): void {
  try {
    localStorage.removeItem(USER_KEY);
  } catch {
    // localStorage 不可用时静默失败
  }
}
