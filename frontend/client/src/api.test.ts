import { afterEach, describe, expect, it, vi } from "vitest";
import { api, tokenStorage } from "./api";

afterEach(() => {
  tokenStorage.clear();
  vi.unstubAllGlobals();
});

describe("API client", () => {
  it("adds the bearer token and decodes JSON responses", async () => {
    tokenStorage.set("signed-access-token");
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ value: "live" }), { status: 200 }),
    );
    vi.stubGlobal("fetch", fetchMock);

    await expect(api<{ value: string }>("/api/users/me")).resolves.toEqual({ value: "live" });
    const request = fetchMock.mock.calls[0][1] as RequestInit;
    const headers = new Headers(request.headers);
    expect(headers.get("Authorization")).toBe("Bearer signed-access-token");
    expect(headers.get("Accept")).toBe("application/json");
  });

  it("clears an expired token and surfaces the server error", async () => {
    tokenStorage.set("expired-token");
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ message: "Invalid or expired access token" }), { status: 401 }),
    ));

    await expect(api("/api/users/me")).rejects.toThrow("Invalid or expired access token");
    expect(tokenStorage.get()).toBeNull();
  });

  it("does not attach a stale access token to public owner registration", async () => {
    tokenStorage.set("expired-token");
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ role: "PARKING_OWNER" }), { status: 201 }),
    );
    vi.stubGlobal("fetch", fetchMock);

    await expect(api<{ role: string }>("/api/auth/register/owner", {
      method: "POST",
      body: JSON.stringify({ email: "owner@example.test" }),
    })).resolves.toEqual({ role: "PARKING_OWNER" });

    const request = fetchMock.mock.calls[0][1] as RequestInit;
    expect(new Headers(request.headers).has("Authorization")).toBe(false);
  });

  it("explains that account registration must not require authentication", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ message: "Authentication is required" }), { status: 401 }),
    ));

    await expect(api("/api/auth/register/owner", {
      method: "POST",
      body: JSON.stringify({ email: "owner@example.test" }),
    })).rejects.toThrow(
      "Sign in is required for this action. Account registration is public; if this appears while creating an account, the request is reaching a protected endpoint. Open the SmartPark portal and try again.",
    );
  });
});
