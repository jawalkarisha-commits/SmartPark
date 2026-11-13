import { afterEach, describe, expect, it, vi } from "vitest";
import { api, isDemoMode, tokenStorage } from "./api";
import { demoAdminCredentials } from "./demo";

afterEach(() => {
  tokenStorage.clear();
  localStorage.removeItem("smartpark.demo:v1");
  vi.unstubAllGlobals();
});

describe("API client", () => {
  it("uses browser demo mode when no backend URL is configured", () => {
    expect(isDemoMode).toBe(true);
  });

  it("registers customers with an on-screen OTP and verifies it", async () => {
    const pending = await api<{ email: string; developmentOtp: string }>("/api/auth/register", {
      method: "POST",
      body: JSON.stringify({
        fullName: "Demo Customer",
        email: "customer@example.test",
        phone: "1234567890",
        password: "Parking1",
      }),
    });

    expect(pending.developmentOtp).toMatch(/^\d{6}$/);
    await expect(api("/api/auth/register/verify", {
      method: "POST",
      body: JSON.stringify({ email: pending.email, otp: "000000" }),
    })).rejects.toThrow("The verification code is invalid or expired.");

    const signedUp = await api<{ accessToken: string; role: string }>("/api/auth/register/verify", {
      method: "POST",
      body: JSON.stringify({ email: pending.email, otp: pending.developmentOtp }),
    });
    expect(signedUp.role).toBe("USER");
    tokenStorage.set(signedUp.accessToken);
    await expect(api<{ email: string }>("/api/users/me")).resolves.toMatchObject({
      email: "customer@example.test",
    });
  });

  it("supports demo vehicle registration, parking estimates, and bookings", async () => {
    const pending = await api<{ email: string; developmentOtp: string }>("/api/auth/register", {
      method: "POST",
      body: JSON.stringify({
        fullName: "Booking Customer",
        email: "booking@example.test",
        phone: "1234567890",
        password: "Parking1",
      }),
    });
    const account = await api<{ accessToken: string }>("/api/auth/register/verify", {
      method: "POST",
      body: JSON.stringify({ email: pending.email, otp: pending.developmentOtp }),
    });
    tokenStorage.set(account.accessToken);

    const vehicle = await api<{ id: number; vehicleType: string }>("/api/vehicles", {
      method: "POST",
      body: JSON.stringify({
        vehicleNumber: "MH24AB1234",
        vehicleType: "Four-wheeler",
        brand: "Demo",
        model: "Car",
        color: "Blue",
      }),
    });
    const startTime = new Date(Date.now() + 60 * 60 * 1000).toISOString();
    const endTime = new Date(Date.now() + 2 * 60 * 60 * 1000).toISOString();
    const slots = await api<{ id: number; slotNumber: string }[]>(
      `/api/bookings/availability?${new URLSearchParams({ startTime, endTime, vehicleType: vehicle.vehicleType })}`,
    );
    expect(slots[0].slotNumber).toBe("L-01");

    const estimate = await api<{ amount: number }>("/api/bookings/estimate?" + new URLSearchParams({
      slotId: String(slots[0].id),
      startTime,
      endTime,
      billingPeriod: "HOURLY",
    }));
    expect(estimate.amount).toBe(30);

    const booking = await api<{ id: number; status: string }>("/api/bookings", {
      method: "POST",
      body: JSON.stringify({
        vehicleId: vehicle.id,
        slotId: slots[0].id,
        startTime,
        endTime,
        billingPeriod: "HOURLY",
      }),
    });
    expect(booking.status).toBe("CONFIRMED");
    await expect(api<{ id: number }[]>(`/api/bookings/my`)).resolves.toHaveLength(1);
  });

  it("supports owner OTP setup and facility inventory in demo mode", async () => {
    const owner = await api<{ accessToken: string; role: string }>("/api/auth/register/owner", {
      method: "POST",
      body: JSON.stringify({
        fullName: "Demo Owner",
        email: "owner@example.test",
        phone: "1234567890",
        password: "Parking1",
      }),
    });
    expect(owner.role).toBe("PARKING_OWNER");
    tokenStorage.set(owner.accessToken);

    const verification = await api<{ developmentOtp: string }>("/api/owner/verification/otp", { method: "POST" });
    const approved = await api<{ approvalStatus: string }>("/api/owner/verification/otp/verify", {
      method: "POST",
      body: JSON.stringify({ otp: verification.developmentOtp }),
    });
    expect(approved.approvalStatus).toBe("APPROVED");

    const ownerToken = tokenStorage.get();
    const facility = await api<{ id: number }>("/api/owner/facilities", {
      method: "POST",
      body: JSON.stringify({
        name: "My demo lot",
        address: "Latur",
        totalCapacity: 1,
        operatingStart: "00:00:00",
        operatingEnd: "23:59:00",
        latitude: 18.4088,
        longitude: 76.5604,
      }),
    });
    const slot = await api<{ id: number; slotNumber: string }>(`/api/owner/facilities/${facility.id}/parking-slots`, {
      method: "POST",
      body: JSON.stringify({
        slotNumber: "OWNER-1",
        floorNumber: 0,
        location: "Ganj Golai",
        zone: "A",
        vehicleType: "Four-wheeler",
        hourlyPrice: 20,
        dailyPrice: 100,
        monthlyPrice: 1000,
        status: "AVAILABLE",
      }),
    });
    expect(slot.slotNumber).toBe("OWNER-1");

    const customer = await api<{ email: string; developmentOtp: string }>("/api/auth/register", {
      method: "POST",
      body: JSON.stringify({
        fullName: "Demo Booking Customer",
        email: "booking-customer@example.test",
        phone: "9876543210",
        password: "Parking1",
      }),
    });
    const customerAccount = await api<{ accessToken: string }>("/api/auth/register/verify", {
      method: "POST",
      body: JSON.stringify({ email: customer.email, otp: customer.developmentOtp }),
    });
    tokenStorage.set(customerAccount.accessToken);
    const vehicle = await api<{ id: number }>("/api/vehicles", {
      method: "POST",
      body: JSON.stringify({
        vehicleNumber: "MH24DEMO1",
        vehicleType: "Four-wheeler",
        brand: "Demo",
        model: "Car",
        color: "Blue",
      }),
    });
    const startTime = new Date(Date.now() + 60 * 60 * 1000).toISOString();
    const endTime = new Date(Date.now() + 2 * 60 * 60 * 1000).toISOString();
    await api("/api/bookings", {
      method: "POST",
      body: JSON.stringify({
        vehicleId: vehicle.id,
        slotId: slot.id,
        startTime,
        endTime,
        billingPeriod: "HOURLY",
      }),
    });

    tokenStorage.set(ownerToken);
    await expect(api<{
      slots: { slotNumber: string }[];
      availableSlots: number;
      bookedSlots: number;
      bookings: {
        customer: { fullName: string; email: string; phone: string };
        vehicle: { vehicleNumber: string; model: string };
      }[];
    }>(`/api/owner/facilities/${facility.id}/overview`)).resolves.toMatchObject({
      slots: [{ slotNumber: "OWNER-1" }],
      availableSlots: 1,
      bookedSlots: 0,
      bookings: [{
        customer: {
          fullName: "Demo Booking Customer",
          email: "booking-customer@example.test",
          phone: "9876543210",
        },
        vehicle: { vehicleNumber: "MH24DEMO1", model: "Car" },
      }],
    });
  });

  it("allows the documented demo admin account", async () => {
    const admin = await api<{ accessToken: string; role: string }>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify(demoAdminCredentials),
    });
    expect(admin.role).toBe("ADMIN");
    tokenStorage.set(admin.accessToken);
    await expect(api<{ totalSlots: number }>("/api/admin/dashboard")).resolves.toMatchObject({
      totalSlots: 3,
    });
  });

  it.skipIf(isDemoMode)("adds the bearer token and decodes JSON responses", async () => {
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

  it.skipIf(isDemoMode)("clears an expired token and surfaces the server error", async () => {
    tokenStorage.set("expired-token");
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ message: "Invalid or expired access token" }), { status: 401 }),
    ));

    await expect(api("/api/users/me")).rejects.toThrow("Invalid or expired access token");
    expect(tokenStorage.get()).toBeNull();
  });

  it.skipIf(isDemoMode)("does not attach a stale access token to public owner registration", async () => {
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

  it.skipIf(isDemoMode)("explains that account registration must not require authentication", async () => {
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
