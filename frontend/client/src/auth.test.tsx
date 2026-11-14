import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AuthProvider } from "./auth";
import { tokenStorage } from "./api";
import { LoginPage } from "./pages/public";

afterEach(() => {
  cleanup();
  tokenStorage.clear();
  vi.unstubAllGlobals();
});

describe("admin sign-in", () => {
  it("allows switching this tab to an admin while another session is signed in", async () => {
    tokenStorage.set("customer-session-token");
    const fetchMock = vi.fn(async (
      input: RequestInfo | URL,
      _init?: RequestInit,
    ) => {
      if (String(input) === "/api/users/me") {
        return new Response(JSON.stringify({
          id: 8,
          fullName: "Signed-in Customer",
          email: "customer@example.test",
          phone: null,
          role: "USER",
          enabled: true,
        }), { status: 200 });
      }
      if (String(input) === "/api/auth/login") {
        return new Response(JSON.stringify({
          id: 9,
          fullName: "Administrator",
          email: "admin@example.test",
          phone: null,
          role: "ADMIN",
          enabled: true,
          accessToken: "admin-session-token",
          tokenType: "Bearer",
          expiresInSeconds: 3600,
        }), { status: 200 });
      }
      throw new Error(`Unexpected request: ${String(input)}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/admin/login"]}>
          <Routes>
            <Route path="/admin/login" element={<LoginPage adminOnly />} />
            <Route path="/admin/dashboard" element={<p>Admin dashboard</p>} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>,
    );

    expect(await screen.findByText(/Currently signed in as customer@example.test/))
      .toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Email address"), {
      target: { value: "admin@example.test" },
    });
    fireEvent.change(screen.getByLabelText("Password"), {
      target: { value: "AdminPassword1" },
    });
    fireEvent.click(screen.getByRole("button", { name: /sign in/i }));

    expect(await screen.findByText("Admin dashboard")).toBeInTheDocument();
    await waitFor(() => {
      expect(tokenStorage.get()).toBe("admin-session-token");
    });
    const loginCall = fetchMock.mock.calls.find(
      ([input]) => String(input) === "/api/auth/login",
    );
    expect(loginCall).toBeDefined();
    expect(new Headers(loginCall?.[1]?.headers).has("Authorization")).toBe(false);
  });
});
