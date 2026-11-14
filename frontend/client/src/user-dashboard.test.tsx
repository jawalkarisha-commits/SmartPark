import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { AuthProvider } from "./auth";
import { tokenStorage } from "./api";
import { BookingHistoryPage, UserDashboardPage } from "./pages/user";
import type { Booking } from "./types";

afterEach(() => {
  cleanup();
  tokenStorage.clear();
  vi.unstubAllGlobals();
});

function makeBooking(overrides: Partial<Booking> = {}): Booking {
  const startTime = new Date(Date.now() + 60 * 60_000).toISOString();
  return {
    id: 41,
    bookingReference: "ref-12345678",
    userId: 7,
    vehicleId: 8,
    vehicleNumber: "MH01AB1234",
    slotId: 9,
    slotNumber: "A1",
    startTime,
    endTime: new Date(Date.parse(startTime) + 60 * 60_000).toISOString(),
    status: "PENDING_PAYMENT",
    paymentStatus: "UNPAID",
    hourlyRate: 100,
    billingPeriod: "HOURLY",
    billedRate: 100,
    amount: 100,
    createdAt: "2026-11-14T10:00:00",
    holdExpiresAt: "2026-11-14T10:05:00",
    actualEntryTime: null,
    actualExitTime: null,
    lateHours: 0,
    lateFeeAmount: 0,
    depositAmount: 50,
    paidAmount: 0,
    depositRefundAmount: 0,
    finalAmount: 100,
    balanceDue: 100,
    platformFeeAmount: 0,
    ownerEarnings: 0,
    settlementPaidAt: null,
    ...overrides,
  };
}

describe("customer booking actions", () => {
  it("shows a demo deposit action and updates payment status after payment", async () => {
    const pending = makeBooking({
      startTime: "2026-11-15T10:00:00",
      endTime: "2026-11-15T11:00:00",
    });
    const confirmed: Booking = {
      ...pending,
      status: "CONFIRMED",
      paymentStatus: "DEPOSIT_PAID",
      holdExpiresAt: null,
      paidAmount: 50,
      balanceDue: 50,
    };
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path === "/api/bookings/my") {
        return new Response(JSON.stringify([pending]), { status: 200 });
      }
      if (path === "/api/vehicles" || path.startsWith("/api/bookings/availability?")) {
        return new Response(JSON.stringify([]), { status: 200 });
      }
      if (path === "/api/bookings/41/deposit" && init?.method === "POST") {
        return new Response(JSON.stringify(confirmed), { status: 200 });
      }
      throw new Error(`Unexpected request: ${path}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(
      <AuthProvider>
        <MemoryRouter>
          <UserDashboardPage />
        </MemoryRouter>
      </AuthProvider>,
    );

    fireEvent.click(await screen.findByRole("button", { name: /Pay deposit/ }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        "/api/bookings/41/deposit",
        expect.objectContaining({ method: "POST" }),
      );
    });
    expect(await screen.findByText("Demo deposit recorded. No payment provider was opened."))
      .toBeInTheDocument();
    expect(screen.getByText("DEPOSIT PAID")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Pay deposit/ })).not.toBeInTheDocument();
  });

  it("shows parking and departure actions on the user home dashboard", async () => {
    const startTime = new Date(Date.now() - 5 * 60_000).toISOString();
    let current = makeBooking({
      status: "CONFIRMED",
      paymentStatus: "DEPOSIT_PAID",
      startTime,
      endTime: new Date(Date.parse(startTime) + 60 * 60_000).toISOString(),
      paidAmount: 50,
      balanceDue: 50,
    });
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path === "/api/bookings/my") {
        return new Response(JSON.stringify([current]), { status: 200 });
      }
      if (path === "/api/vehicles" || path.startsWith("/api/bookings/availability?")) {
        return new Response(JSON.stringify([]), { status: 200 });
      }
      if (path === "/api/bookings/41/parked" && init?.method === "POST") {
        current = { ...current, status: "ACTIVE", actualEntryTime: new Date().toISOString() };
        return new Response(JSON.stringify(current), { status: 200 });
      }
      if (path === "/api/bookings/41/depart" && init?.method === "POST") {
        current = {
          ...current,
          status: "COMPLETED",
          actualExitTime: new Date().toISOString(),
          paymentStatus: "BALANCE_DUE",
        };
        return new Response(JSON.stringify(current), { status: 200 });
      }
      throw new Error(`Unexpected request: ${path}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(
      <AuthProvider>
        <MemoryRouter>
          <UserDashboardPage />
        </MemoryRouter>
      </AuthProvider>,
    );

    fireEvent.click(await screen.findByRole("button", { name: "Vehicle is parked" }));
    expect(await screen.findByRole("button", { name: "I have left the space" }))
      .toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "I have left the space" }));
    expect(await screen.findByRole("button", { name: /Pay balance/ })).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/bookings/41/depart",
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("shows the parked action shortly before arrival but keeps it disabled until the start time", async () => {
    const startTime = new Date(Date.now() + 5 * 60_000).toISOString();
    const confirmed = makeBooking({
      status: "CONFIRMED",
      paymentStatus: "DEPOSIT_PAID",
      startTime,
      endTime: new Date(Date.parse(startTime) + 60 * 60_000).toISOString(),
      paidAmount: 50,
      balanceDue: 50,
    });
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path === "/api/bookings/my") {
        return new Response(JSON.stringify([confirmed]), { status: 200 });
      }
      if (path === "/api/vehicles" || path.startsWith("/api/bookings/availability?")) {
        return new Response(JSON.stringify([]), { status: 200 });
      }
      throw new Error(`Unexpected request: ${path}`);
    }));

    render(<MemoryRouter><BookingHistoryPage /></MemoryRouter>);

    expect(await screen.findByRole("button", { name: "Vehicle is parked" }))
      .toBeDisabled();
    expect(screen.getByText("Available at scheduled start")).toBeInTheDocument();
  });

  it("shows payment and parking actions in the bookings section and updates each booking", async () => {
    const startTime = new Date(Date.now() - 5 * 60_000).toISOString();
    let pending = makeBooking();
    let operational = makeBooking({
      id: 42,
      bookingReference: "ref-87654321",
      status: "CONFIRMED",
      paymentStatus: "DEPOSIT_PAID",
      startTime,
      endTime: new Date(Date.parse(startTime) + 60 * 60_000).toISOString(),
      paidAmount: 50,
      balanceDue: 50,
    });
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path === "/api/bookings/my") {
        return new Response(JSON.stringify([pending, operational]), { status: 200 });
      }
      if (path === "/api/bookings/41/deposit" && init?.method === "POST") {
        pending = { ...pending, status: "CONFIRMED", paymentStatus: "DEPOSIT_PAID", paidAmount: 50 };
        return new Response(JSON.stringify(pending), { status: 200 });
      }
      if (path === "/api/bookings/42/parked" && init?.method === "POST") {
        operational = { ...operational, status: "ACTIVE", actualEntryTime: new Date().toISOString() };
        return new Response(JSON.stringify(operational), { status: 200 });
      }
      if (path === "/api/bookings/42/depart" && init?.method === "POST") {
        operational = {
          ...operational,
          status: "COMPLETED",
          actualExitTime: new Date().toISOString(),
          paymentStatus: "BALANCE_DUE",
          balanceDue: 35,
        };
        return new Response(JSON.stringify(operational), { status: 200 });
      }
      if (path === "/api/bookings/42/settle" && init?.method === "POST") {
        operational = {
          ...operational,
          paymentStatus: "PAID",
          balanceDue: 0,
          settlementPaidAt: new Date().toISOString(),
        };
        return new Response(JSON.stringify(operational), { status: 200 });
      }
      throw new Error(`Unexpected request: ${path}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<MemoryRouter><BookingHistoryPage /></MemoryRouter>);

    fireEvent.click(await screen.findByRole("button", { name: /Pay deposit/ }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      "/api/bookings/41/deposit",
      expect.objectContaining({ method: "POST" }),
    ));

    fireEvent.click(screen.getByRole("button", { name: "Vehicle is parked" }));
    fireEvent.click(await screen.findByRole("button", { name: "I have left the space" }));
    fireEvent.click(await screen.findByRole("button", { name: /Pay balance/ }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      "/api/bookings/42/settle",
      expect.objectContaining({ method: "POST" }),
    ));
    expect(screen.queryByRole("button", { name: /Pay balance/ })).not.toBeInTheDocument();
    expect(screen.getByText("PAID")).toBeInTheDocument();
  });
});
