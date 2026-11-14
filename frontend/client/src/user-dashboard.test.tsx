import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { AuthProvider } from "./auth";
import { tokenStorage } from "./api";
import { UserDashboardPage } from "./pages/user";
import type { Booking } from "./types";

afterEach(() => {
  cleanup();
  tokenStorage.clear();
  vi.unstubAllGlobals();
});

describe("customer dashboard payments", () => {
  it("shows a demo deposit action and updates payment status after payment", async () => {
    const pending: Booking = {
      id: 41,
      bookingReference: "ref-12345678",
      userId: 7,
      vehicleId: 8,
      vehicleNumber: "MH01AB1234",
      slotId: 9,
      slotNumber: "A1",
      startTime: "2026-11-15T10:00:00",
      endTime: "2026-11-15T11:00:00",
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
    };
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
});
