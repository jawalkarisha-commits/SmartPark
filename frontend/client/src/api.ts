const API_BASE = (import.meta.env.VITE_API_BASE_URL || "").replace(/\/+$/, "");
const TOKEN_KEY = "smartpark.access-token";

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

function shouldSendAccessToken(path: string, method?: string) {
  const normalizedMethod = (method || "GET").toUpperCase();
  const pathname = path.split("?", 1)[0];

  if (
    pathname.startsWith("/api/auth/") ||
    [
      "/api/users/register",
      "/api/users/login",
      "/api/users/forgot-password",
      "/api/users/reset-password",
      "/api/settings/vehicle-types",
    ].includes(pathname)
  ) {
    return false;
  }

  return !(
    normalizedMethod === "GET" &&
    (
      pathname === "/api/parking-slots" ||
      pathname.startsWith("/api/parking-slots/") ||
      pathname === "/api/bookings/availability" ||
      pathname === "/api/bookings/estimate"
    )
  );
}

export async function api<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const token = sessionStorage.getItem(TOKEN_KEY);
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");
  const isFormData = typeof FormData !== "undefined" && options.body instanceof FormData;
  if (options.body && !isFormData && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (token && shouldSendAccessToken(path, options.method)) {
    headers.set("Authorization", `Bearer ${token}`);
  } else {
    headers.delete("Authorization");
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
  });
  const text = await response.text();
  let payload: unknown;
  try {
    payload = text ? JSON.parse(text) : null;
  } catch {
    payload = text;
  }
  if (!response.ok) {
    if (response.status === 401 && token) {
      sessionStorage.removeItem(TOKEN_KEY);
      window.dispatchEvent(new Event("smartpark:unauthorized"));
    }
    const body = payload as { message?: string; detail?: string } | null;
    const message = body?.message || body?.detail || `Request failed (${response.status})`;
    throw new ApiError(
      response.status === 401 && message === "Authentication is required"
        ? "Sign in is required for this action. Account registration is public; if this appears while creating an account, the request is reaching a protected endpoint. Open the SmartPark portal and try again."
        : message,
      response.status,
    );
  }
  return payload as T;
}

export async function downloadFile(path: string) {
  const token = sessionStorage.getItem(TOKEN_KEY);
  const response = await fetch(`${API_BASE}${path}`, {
    headers: token ? { Authorization: `Bearer ${token}`, Accept: "*/*" } : { Accept: "*/*" },
  });
  if (!response.ok) {
    const text = await response.text();
    let message = `Request failed (${response.status})`;
    try {
      const payload = JSON.parse(text) as { message?: string; detail?: string };
      message = payload.message || payload.detail || message;
    } catch {
      if (text) message = text;
    }
    if (response.status === 401 && token) {
      sessionStorage.removeItem(TOKEN_KEY);
      window.dispatchEvent(new Event("smartpark:unauthorized"));
    }
    throw new ApiError(message, response.status);
  }
  const blob = await response.blob();
  const disposition = response.headers.get("Content-Disposition") || "";
  const filename = disposition.match(/filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/i);
  const decodedName = filename?.[1]
    ? decodeURIComponent(filename[1])
    : filename?.[2] || "government-document";
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = objectUrl;
  link.download = decodedName;
  document.body.append(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(objectUrl);
}

export const tokenStorage = {
  get: () => sessionStorage.getItem(TOKEN_KEY),
  set: (token: string) => sessionStorage.setItem(TOKEN_KEY, token),
  clear: () => sessionStorage.removeItem(TOKEN_KEY),
};

export function queryString(values: Record<string, string | number | undefined>) {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(values)) {
    if (value !== undefined && value !== "") query.set(key, String(value));
  }
  return query.toString();
}

export function toLocalDateTimeValue(date: Date) {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}

export function currency(value: number) {
  return new Intl.NumberFormat(undefined, {
    style: "currency",
    currency: import.meta.env.VITE_CURRENCY_CODE || "INR",
  }).format(value);
}
