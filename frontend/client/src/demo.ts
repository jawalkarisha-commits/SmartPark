import type {
  AuthResponse,
  BillingPeriod,
  Booking,
  ChargeEstimate,
  Facility,
  OwnerApproval,
  OwnerBooking,
  OwnerOverview,
  OwnerVerification,
  ParkingSlot,
  Role,
  User,
  Vehicle,
} from "./types";

const STORAGE_KEY = "smartpark.demo:v1";
const DEMO_ADMIN_EMAIL = "admin@smartpark.demo";
const DEMO_ADMIN_PASSWORD = "DemoAdmin123";

interface DemoAccount extends User {
  passwordDigest: string;
  ownerOtp?: string;
  emailVerified?: boolean;
  approvalStatus?: OwnerVerification["approvalStatus"];
}

interface DemoFacility extends Facility {
  ownerUserId: number | null;
}

interface DemoVehicle extends Vehicle {
  ownerUserId: number;
}

interface DemoState {
  accounts: DemoAccount[];
  pendingRegistrations: {
    account: DemoAccount;
    otp: string;
  }[];
  facilities: DemoFacility[];
  slots: ParkingSlot[];
  vehicles: DemoVehicle[];
  bookings: Booking[];
  nextId: number;
}

export const demoAdminCredentials = {
  email: DEMO_ADMIN_EMAIL,
  password: DEMO_ADMIN_PASSWORD,
};

class DemoApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
    this.name = "DemoApiError";
  }
}

function initialState(): DemoState {
  const facility: DemoFacility = {
    id: 1,
    name: "Latur Central Parking",
    address: "Ganj Golai, Latur, Maharashtra, India",
    totalCapacity: 3,
    operatingStart: "00:00:00",
    operatingEnd: "23:59:00",
    latitude: 18.4088,
    longitude: 76.5604,
    ownerUserId: null,
  };
  const makeSlot = (id: number, number: string, vehicleType: string, hourlyPrice: number): ParkingSlot => ({
    id,
    slotNumber: number,
    floorNumber: 0,
    location: "Ganj Golai",
    zone: "Central",
    vehicleType,
    hourlyPrice,
    dailyPrice: hourlyPrice * 8,
    monthlyPrice: hourlyPrice * 160,
    status: "AVAILABLE",
    available: true,
    facilityId: facility.id,
    facilityName: facility.name,
    facilityAddress: facility.address,
    latitude: facility.latitude,
    longitude: facility.longitude,
  });
  return {
    accounts: [],
    pendingRegistrations: [],
    facilities: [facility],
    slots: [
      makeSlot(1, "L-01", "Four-wheeler", 30),
      makeSlot(2, "L-02", "Two-wheeler", 15),
      makeSlot(3, "L-03", "Four-wheeler", 25),
    ],
    vehicles: [],
    bookings: [],
    nextId: 4,
  };
}

function getState(): DemoState {
  let serialized: string | null;
  try {
    serialized = localStorage.getItem(STORAGE_KEY);
  } catch {
    throw new DemoApiError("Browser storage is unavailable. Enable local storage to use demo mode.", 500);
  }
  if (!serialized) {
    const state = initialState();
    saveState(state);
    return state;
  }
  try {
    const state: unknown = JSON.parse(serialized);
    if (
      typeof state !== "object" ||
      state === null ||
      !("accounts" in state) ||
      !("pendingRegistrations" in state) ||
      !("facilities" in state) ||
      !("slots" in state) ||
      !("vehicles" in state) ||
      !("bookings" in state) ||
      !("nextId" in state) ||
      !Array.isArray(state.accounts) ||
      !Array.isArray(state.pendingRegistrations) ||
      !Array.isArray(state.facilities) ||
      !Array.isArray(state.slots) ||
      !Array.isArray(state.vehicles) ||
      !Array.isArray(state.bookings) ||
      typeof state.nextId !== "number"
    ) {
      throw new Error("Stored demo data has an unsupported format.");
    }
    return state as DemoState;
  } catch (reason) {
    throw new DemoApiError(
      reason instanceof Error ? `Demo data could not be read: ${reason.message}` : "Demo data could not be read.",
      500,
    );
  }
}

function saveState(state: DemoState) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    throw new DemoApiError("Demo data could not be saved in this browser. Check available storage.", 500);
  }
}

function parseBody<T>(options: RequestInit): T {
  if (typeof options.body !== "string") {
    throw new DemoApiError("This request is not supported in demo mode.", 400);
  }
  try {
    return JSON.parse(options.body) as T;
  } catch {
    throw new DemoApiError("The request body is not valid JSON.", 400);
  }
}

async function passwordDigest(password: string) {
  const bytes = new TextEncoder().encode(password);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

function makeOtp() {
  return String(Math.floor(100_000 + Math.random() * 900_000));
}

function cleanEmail(email: string) {
  return email.trim().toLocaleLowerCase();
}

function currentUser(state: DemoState, requiredRole?: Role): DemoAccount {
  const token = sessionStorage.getItem("smartpark.access-token") || "";
  const match = /^demo:(\d+)$/.exec(token);
  const account = match ? state.accounts.find((item) => item.id === Number(match[1])) : undefined;
  if (!account || !account.enabled) throw new DemoApiError("Sign in to continue.", 401);
  if (requiredRole && account.role !== requiredRole) throw new DemoApiError("This account cannot access that demo feature.", 403);
  return account;
}

function userProfile(account: DemoAccount): User {
  return {
    id: account.id,
    fullName: account.fullName,
    email: account.email,
    phone: account.phone,
    role: account.role,
    enabled: account.enabled,
  };
}

function authResponse(account: DemoAccount): AuthResponse {
  return {
    ...userProfile(account),
    accessToken: `demo:${account.id}`,
    tokenType: "Bearer",
    expiresInSeconds: 86400,
  };
}

function rolePath(email: string) {
  return `/api/${email}`;
}

function accountByEmail(state: DemoState, email: string) {
  const normalized = cleanEmail(email);
  return state.accounts.find((account) => cleanEmail(account.email) === normalized);
}

function ownerVerification(account: DemoAccount): OwnerVerification {
  return {
    ownerUserId: account.id,
    ownerName: account.fullName,
    email: account.email,
    phone: account.phone,
    emailVerified: account.emailVerified === true,
    documentUploaded: false,
    documentFileName: null,
    documentUploadedAt: null,
    approvalStatus: account.approvalStatus || "PENDING",
    approvalMethod: account.approvalStatus === "APPROVED" ? "AUTO" : null,
    requestedAt: new Date().toISOString(),
    rejectionReason: null,
    developmentOtp: account.ownerOtp || null,
  };
}

function facilityForSlot(state: DemoState, slot: ParkingSlot) {
  return state.facilities.find((facility) => facility.id === slot.facilityId);
}

function facilityOverview(state: DemoState, facility: DemoFacility): OwnerOverview {
  const slots = state.slots.filter((slot) => slot.facilityId === facility.id);
  const now = Date.now();
  const currentBookings = state.bookings.filter((booking) =>
    slots.some((slot) => slot.id === booking.slotId) &&
    ["PENDING_PAYMENT", "CONFIRMED", "ACTIVE", "OVERDUE"].includes(booking.status) &&
    (booking.status !== "PENDING_PAYMENT" ||
      (booking.holdExpiresAt !== null && new Date(booking.holdExpiresAt).getTime() > now)) &&
    new Date(booking.startTime).getTime() <= now &&
    new Date(booking.endTime).getTime() > now,
  );
  const bookedSlotIds = new Set(currentBookings.map((booking) => booking.slotId));
  const facilityBookings: OwnerBooking[] = state.bookings
    .filter((booking) => slots.some((slot) => slot.id === booking.slotId))
    .map((booking) => {
      const account = state.accounts.find((item) => item.id === booking.userId);
      const vehicle = state.vehicles.find((item) => item.id === booking.vehicleId);
      if (!account || !vehicle) {
        throw new DemoApiError("Booking customer details could not be loaded.", 500);
      }
      return {
        booking,
        customer: {
          id: account.id,
          fullName: account.fullName,
          email: account.email,
          phone: account.phone,
        },
        vehicle: {
          id: vehicle.id,
          vehicleNumber: vehicle.vehicleNumber,
          vehicleType: vehicle.vehicleType,
          brand: vehicle.brand,
          model: vehicle.model,
          color: vehicle.color,
        },
      };
    });
  return {
    facility,
    slots,
    bookings: facilityBookings,
    availableSlots: slots.filter((slot) =>
      slot.status === "AVAILABLE" && !bookedSlotIds.has(slot.id),
    ).length,
    bookedSlots: bookedSlotIds.size,
  };
}

function availableSlots(state: DemoState, params: URLSearchParams) {
  const startTime = params.get("startTime");
  const endTime = params.get("endTime");
  const query = params.get("locationQuery")?.toLocaleLowerCase();
  const vehicleType = params.get("vehicleType")?.toLocaleLowerCase();
  const latitude = Number(params.get("latitude"));
  const longitude = Number(params.get("longitude"));
  const radiusKm = Number(params.get("radiusKm"));
  const hasCoordinates = params.has("latitude") && params.has("longitude") && params.has("radiusKm");
  return state.slots
    .filter((slot) => slot.status === "AVAILABLE")
    .filter((slot) => !vehicleType || !slot.vehicleType || slot.vehicleType.toLocaleLowerCase() === vehicleType)
    .filter((slot) => {
      const facility = facilityForSlot(state, slot);
      return !query || `${facility?.name} ${facility?.address} ${slot.location}`.toLocaleLowerCase().includes(query);
    })
    .filter((slot) => {
      if (!hasCoordinates || !Number.isFinite(latitude) || !Number.isFinite(longitude) || !Number.isFinite(radiusKm) ||
        !slot.latitude || !slot.longitude) return true;
      const latRad = (slot.latitude * Math.PI) / 180;
      const centerRad = (latitude * Math.PI) / 180;
      const deltaLat = ((slot.latitude - latitude) * Math.PI) / 180;
      const deltaLon = ((slot.longitude - longitude) * Math.PI) / 180;
      const a = Math.sin(deltaLat / 2) ** 2 +
        Math.cos(centerRad) * Math.cos(latRad) * Math.sin(deltaLon / 2) ** 2;
      return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)) <= radiusKm;
    })
    .filter((slot) => {
      if (!startTime || !endTime) return true;
      const start = new Date(startTime).getTime();
      const end = new Date(endTime).getTime();
      return !state.bookings.some((booking) =>
        booking.slotId === slot.id &&
        booking.status !== "CANCELLED" &&
        new Date(booking.startTime).getTime() < end &&
        new Date(booking.endTime).getTime() > start,
      );
    });
}

function estimateFor(slot: ParkingSlot, startTime: string, endTime: string, billingPeriod: BillingPeriod): ChargeEstimate {
  const start = new Date(startTime).getTime();
  const end = new Date(endTime).getTime();
  const durationMinutes = Math.ceil((end - start) / 60_000);
  if (!Number.isFinite(durationMinutes) || durationMinutes <= 0) {
    throw new DemoApiError("Choose a valid end time after the start time.", 400);
  }
  const billedRate = billingPeriod === "DAILY"
    ? slot.dailyPrice
    : billingPeriod === "MONTHLY"
      ? slot.monthlyPrice
      : slot.hourlyPrice;
  const periodMinutes = billingPeriod === "DAILY" ? 1440 : billingPeriod === "MONTHLY" ? 43_200 : 60;
  return {
    durationMinutes,
    hourlyRate: slot.hourlyPrice,
    billingPeriod,
    billedRate,
    amount: Math.round((billedRate * durationMinutes / periodMinutes) * 100) / 100,
  };
}

function bookingFor(state: DemoState, bookingId: number) {
  const booking = state.bookings.find((item) => item.id === bookingId);
  if (!booking) throw new DemoApiError("Booking was not found.", 404);
  return booking;
}

function slotFor(state: DemoState, slotId: number) {
  const slot = state.slots.find((item) => item.id === slotId);
  if (!slot) throw new DemoApiError("Parking space was not found.", 404);
  return slot;
}

function toBooking(
  state: DemoState,
  account: DemoAccount,
  slot: ParkingSlot,
  vehicle: DemoVehicle,
  startTime: string,
  endTime: string,
  billingPeriod: BillingPeriod,
): Booking {
  const estimate = estimateFor(slot, startTime, endTime, billingPeriod);
  const createdAt = new Date().toISOString();
  return {
    id: state.nextId++,
    bookingReference: `SP-DEMO-${Date.now().toString(36).toUpperCase()}`,
    userId: account.id,
    vehicleId: vehicle.id,
    vehicleNumber: vehicle.vehicleNumber,
    slotId: slot.id,
    slotNumber: slot.slotNumber,
    startTime: new Date(startTime).toISOString(),
    endTime: new Date(endTime).toISOString(),
    status: "CONFIRMED",
    hourlyRate: estimate.hourlyRate,
    billingPeriod,
    billedRate: estimate.billedRate,
    amount: estimate.amount,
    createdAt,
    holdExpiresAt: null,
    actualEntryTime: null,
    actualExitTime: null,
    lateHours: 0,
    lateFeeAmount: 0,
  };
}

function accountForNewUser(state: DemoState, body: {
  fullName: string;
  email: string;
  phone: string;
  role: Role;
  passwordDigest: string;
}): DemoAccount {
  if (!body.fullName?.trim() || !body.email?.trim() || !body.passwordDigest) {
    throw new DemoApiError("Name, email, and password are required.", 400);
  }
  if (accountByEmail(state, body.email) ||
    state.pendingRegistrations.some((pending) => cleanEmail(pending.account.email) === cleanEmail(body.email))) {
    throw new DemoApiError("An account already exists for this email address.", 409);
  }
  return {
    id: state.nextId++,
    fullName: body.fullName.trim(),
    email: body.email.trim(),
    phone: body.phone?.trim() || null,
    role: body.role,
    enabled: true,
    passwordDigest: body.passwordDigest,
    emailVerified: false,
    approvalStatus: body.role === "PARKING_OWNER" ? "PENDING" : undefined,
  };
}

export async function handleDemoRequest(path: string, options: RequestInit = {}): Promise<unknown> {
  const url = new URL(path, window.location.origin);
  const method = (options.method || "GET").toUpperCase();
  const pathname = url.pathname;
  const state = getState();
  const response = <T>(value: T) => {
    saveState(state);
    return value;
  };

  if (pathname === "/api/auth/register" && method === "POST") {
    const body = parseBody<{ fullName: string; email: string; phone: string; password: string }>(options);
    if (body.password.length < 6 || !/[A-Z]/.test(body.password) || !/[0-9]/.test(body.password)) {
      throw new DemoApiError("Use at least 6 characters, including an uppercase letter and a number.", 400);
    }
    const account = accountForNewUser(state, {
      ...body,
      role: "USER",
      passwordDigest: await passwordDigest(body.password),
    });
    const otp = makeOtp();
    state.pendingRegistrations.push({ account, otp });
    return response({ email: account.email, developmentOtp: otp });
  }

  if (pathname === "/api/auth/register/resend" && method === "POST") {
    const { email } = parseBody<{ email: string }>(options);
    const pending = state.pendingRegistrations.find((item) => cleanEmail(item.account.email) === cleanEmail(email));
    if (!pending) throw new DemoApiError("No pending registration was found for this email.", 404);
    pending.otp = makeOtp();
    return response({ email: pending.account.email, developmentOtp: pending.otp });
  }

  if (pathname === "/api/auth/register/verify" && method === "POST") {
    const { email, otp } = parseBody<{ email: string; otp: string }>(options);
    const index = state.pendingRegistrations.findIndex((item) =>
      cleanEmail(item.account.email) === cleanEmail(email) && item.otp === otp,
    );
    if (index < 0) throw new DemoApiError("The verification code is invalid or expired.", 400);
    const pending = state.pendingRegistrations.splice(index, 1)[0];
    pending.account.emailVerified = true;
    state.accounts.push(pending.account);
    return response(authResponse(pending.account));
  }

  if (pathname === "/api/auth/register/owner" && method === "POST") {
    const body = parseBody<{ fullName: string; email: string; phone: string; password: string }>(options);
    if (body.password.length < 6 || !/[A-Z]/.test(body.password) || !/[0-9]/.test(body.password)) {
      throw new DemoApiError("Use at least 6 characters, including an uppercase letter and a number.", 400);
    }
    const account = accountForNewUser(state, {
      ...body,
      role: "PARKING_OWNER",
      passwordDigest: await passwordDigest(body.password),
    });
    state.accounts.push(account);
    return response(authResponse(account));
  }

  if (pathname === "/api/auth/login" && method === "POST") {
    const { email, password } = parseBody<{ email: string; password: string }>(options);
    if (cleanEmail(email) === DEMO_ADMIN_EMAIL && password === DEMO_ADMIN_PASSWORD) {
      let admin = accountByEmail(state, DEMO_ADMIN_EMAIL);
      if (!admin) {
        admin = {
          id: state.nextId++,
          fullName: "Demo Administrator",
          email: DEMO_ADMIN_EMAIL,
          phone: null,
          role: "ADMIN",
          enabled: true,
          passwordDigest: await passwordDigest(DEMO_ADMIN_PASSWORD),
        };
        state.accounts.push(admin);
      }
      return response(authResponse(admin));
    }
    const account = accountByEmail(state, email);
    if (!account || account.passwordDigest !== await passwordDigest(password)) {
      throw new DemoApiError("Email or password is incorrect.", 401);
    }
    if (!account.enabled) throw new DemoApiError("This account is disabled.", 403);
    return response(authResponse(account));
  }

  if (pathname === "/api/users/forgot-password" && method === "POST") {
    return response({ message: "Password reset is not available in browser demo mode." });
  }
  if (pathname === "/api/users/reset-password" && method === "POST") {
    throw new DemoApiError("Password reset is not available in browser demo mode.", 501);
  }

  if (pathname === "/api/users/me" && method === "GET") return userProfile(currentUser(state));
  if (pathname === "/api/users/me" && method === "PATCH") {
    const account = currentUser(state);
    const body = parseBody<Partial<User>>(options);
    account.fullName = body.fullName?.trim() || account.fullName;
    account.phone = body.phone ?? account.phone;
    return response(userProfile(account));
  }
  if (pathname === "/api/users/me/password" && method === "PUT") {
    throw new DemoApiError("Password changes are not available in browser demo mode.", 501);
  }

  if (pathname === "/api/settings/vehicle-types" && method === "GET") {
    return ["Two-wheeler", "Four-wheeler", "Heavy vehicle"];
  }

  if (pathname === "/api/vehicles" && method === "GET") {
    const account = currentUser(state);
    return state.vehicles.filter((vehicle) => vehicle.ownerUserId === account.id);
  }
  if (pathname === "/api/vehicles" && (method === "POST" || method === "PUT")) {
    const account = currentUser(state);
    const body = parseBody<Omit<Vehicle, "id">>(options);
    if (state.vehicles.some((vehicle) =>
      vehicle.ownerUserId === account.id && vehicle.vehicleNumber.toLowerCase() === body.vehicleNumber.toLowerCase(),
    )) throw new DemoApiError("This vehicle is already registered.", 409);
    const vehicle: DemoVehicle = {
      ...body,
      id: state.nextId++,
      ownerUserId: account.id,
      brand: body.brand || null,
      model: body.model || null,
      color: body.color || null,
    };
    state.vehicles.push(vehicle);
    return response(vehicle);
  }
  const vehicleMatch = /^\/api\/vehicles\/(\d+)$/.exec(pathname);
  if (vehicleMatch && method === "DELETE") {
    const account = currentUser(state);
    const vehicleId = Number(vehicleMatch[1]);
    state.vehicles = state.vehicles.filter((vehicle) =>
      !(vehicle.id === vehicleId && vehicle.ownerUserId === account.id),
    );
    return response(null);
  }

  if (pathname === "/api/bookings/availability" && method === "GET") {
    return availableSlots(state, url.searchParams);
  }
  if (pathname === "/api/parking-slots" && method === "GET") return state.slots;
  const publicSlotMatch = /^\/api\/parking-slots\/(\d+)$/.exec(pathname);
  if (publicSlotMatch && method === "GET") return slotFor(state, Number(publicSlotMatch[1]));
  if (pathname === "/api/locations/search" && method === "GET") {
    const query = url.searchParams.get("query")?.toLocaleLowerCase() || "";
    const places = [
      { label: "Latur, Maharashtra, India", latitude: 18.4088, longitude: 76.5604 },
      { label: "Pune, Maharashtra, India", latitude: 18.5204, longitude: 73.8567 },
      { label: "Mumbai, Maharashtra, India", latitude: 19.076, longitude: 72.8777 },
    ];
    return places.filter((place) => place.label.toLocaleLowerCase().includes(query));
  }

  if (pathname === "/api/bookings/estimate" && method === "GET") {
    const slot = slotFor(state, Number(url.searchParams.get("slotId")));
    return estimateFor(
      slot,
      url.searchParams.get("startTime") || "",
      url.searchParams.get("endTime") || "",
      (url.searchParams.get("billingPeriod") || "HOURLY") as BillingPeriod,
    );
  }
  if (pathname === "/api/bookings/my" && method === "GET") {
    const account = currentUser(state);
    return state.bookings.filter((booking) => booking.userId === account.id);
  }
  if (pathname === "/api/bookings" && method === "POST") {
    const account = currentUser(state);
    const body = parseBody<{
      vehicleId: number;
      slotId: number;
      startTime: string;
      endTime: string;
      billingPeriod: BillingPeriod;
    }>(options);
    const vehicle = state.vehicles.find((item) => item.id === body.vehicleId && item.ownerUserId === account.id);
    if (!vehicle) throw new DemoApiError("Choose one of your registered vehicles.", 400);
    const slot = slotFor(state, body.slotId);
    if (slot.vehicleType && slot.vehicleType.toLowerCase() !== vehicle.vehicleType.toLowerCase()) {
      throw new DemoApiError("This parking space is not compatible with your vehicle.", 400);
    }
    if (!availableSlots(state, new URLSearchParams({
      startTime: body.startTime,
      endTime: body.endTime,
    })).some((item) => item.id === slot.id)) {
      throw new DemoApiError("This space is no longer available for the selected time.", 409);
    }
    const booking = toBooking(state, account, slot, vehicle, body.startTime, body.endTime, body.billingPeriod);
    state.bookings.push(booking);
    return response(booking);
  }
  const bookingMatch = /^\/api\/bookings\/(\d+)$/.exec(pathname);
  if (bookingMatch && method === "GET") {
    const account = currentUser(state);
    const booking = bookingFor(state, Number(bookingMatch[1]));
    if (booking.userId !== account.id && account.role !== "ADMIN") throw new DemoApiError("Booking was not found.", 404);
    return booking;
  }
  const cancelBookingMatch = /^\/api\/bookings\/(\d+)\/cancel$/.exec(pathname);
  if (cancelBookingMatch && method === "POST") {
    const account = currentUser(state);
    const booking = bookingFor(state, Number(cancelBookingMatch[1]));
    if (booking.userId !== account.id && account.role !== "ADMIN") throw new DemoApiError("Booking was not found.", 404);
    booking.status = "CANCELLED";
    return response(booking);
  }

  if (pathname === "/api/owner/verification" && method === "GET") {
    const account = currentUser(state);
    if (account.role !== "PARKING_OWNER") throw new DemoApiError("Owner account required.", 403);
    return ownerVerification(account);
  }
  if (pathname === "/api/owner/verification/otp" && method === "POST") {
    const account = currentUser(state);
    if (account.role !== "PARKING_OWNER") throw new DemoApiError("Owner account required.", 403);
    account.ownerOtp = makeOtp();
    return response(ownerVerification(account));
  }
  if (pathname === "/api/owner/verification/otp/verify" && method === "POST") {
    const account = currentUser(state);
    const { otp } = parseBody<{ otp: string }>(options);
    if (!account.ownerOtp || otp !== account.ownerOtp) throw new DemoApiError("The verification code is invalid or expired.", 400);
    account.ownerOtp = undefined;
    account.emailVerified = true;
    account.approvalStatus = "APPROVED";
    return response(ownerVerification(account));
  }
  if (pathname === "/api/owner/verification/document" && method === "POST") {
    throw new DemoApiError("Identity documents are not collected in browser demo mode.", 501);
  }

  if (pathname === "/api/owner/facilities" && method === "GET") {
    const account = currentUser(state);
    return state.facilities.filter((facility) => facility.ownerUserId === account.id);
  }
  const ownerFacilityMatch = /^\/api\/owner\/facilities\/(\d+)(?:\/(overview|parking-slots(?:\/(\d+))?))?$/.exec(pathname);
  if (pathname === "/api/owner/facilities" && method === "POST") {
    const account = currentUser(state);
    if (account.role !== "PARKING_OWNER" || account.approvalStatus !== "APPROVED") {
      throw new DemoApiError("Complete demo owner verification before adding a facility.", 403);
    }
    const body = parseBody<Omit<Facility, "id">>(options);
    const facility: DemoFacility = {
      ...body,
      id: state.nextId++,
      ownerUserId: account.id,
      latitude: body.latitude ?? null,
      longitude: body.longitude ?? null,
    };
    state.facilities.push(facility);
    return response(facility);
  }
  if (ownerFacilityMatch) {
    const account = currentUser(state);
    const facilityId = Number(ownerFacilityMatch[1]);
    const facility = state.facilities.find((item) => item.id === facilityId && item.ownerUserId === account.id);
    if (!facility) throw new DemoApiError("Facility was not found.", 404);
    const subPath = ownerFacilityMatch[2];
    if (subPath === "overview" && method === "GET") return facilityOverview(state, facility);
    if (subPath === undefined && method === "PUT") {
      Object.assign(facility, parseBody<Partial<Facility>>(options));
      return response(facility);
    }
    if (subPath?.startsWith("parking-slots") && method === "POST") {
      const body = parseBody<Omit<ParkingSlot, "id" | "facilityId" | "facilityName" | "facilityAddress">>(options);
      const slot: ParkingSlot = {
        ...body,
        id: state.nextId++,
        facilityId: facility.id,
        facilityName: facility.name,
        facilityAddress: facility.address,
        latitude: facility.latitude,
        longitude: facility.longitude,
        available: body.status === "AVAILABLE",
      };
      state.slots.push(slot);
      facility.totalCapacity = Math.max(facility.totalCapacity, state.slots.filter((item) => item.facilityId === facility.id).length);
      return response(slot);
    }
    const slotMatch = /parking-slots\/(\d+)/.exec(subPath || "");
    if (slotMatch && method === "PUT") {
      const slot = slotFor(state, Number(slotMatch[1]));
      if (slot.facilityId !== facility.id) throw new DemoApiError("Parking space was not found.", 404);
      Object.assign(slot, parseBody<Partial<ParkingSlot>>(options));
      slot.available = slot.status === "AVAILABLE";
      return response(slot);
    }
    if (slotMatch && method === "DELETE") {
      const slotId = Number(slotMatch[1]);
      state.slots = state.slots.filter((slot) => !(slot.id === slotId && slot.facilityId === facility.id));
      return response(null);
    }
  }

  if (pathname === "/api/owner/bookings" && method === "GET") {
    const account = currentUser(state);
    const facilityIds = new Set(state.facilities.filter((item) => item.ownerUserId === account.id).map((item) => item.id));
    return state.bookings.filter((booking) => state.slots.some((slot) =>
      slot.id === booking.slotId && facilityIds.has(slot.facilityId || -1),
    ));
  }

  if (pathname === "/api/admin/dashboard" && method === "GET") {
    currentUser(state, "ADMIN");
    const bookings = state.bookings;
    const slots = state.slots;
    return {
      totalSlots: slots.length,
      availableSlots: slots.filter((slot) => slot.status === "AVAILABLE").length,
      occupiedSlots: 0,
      reservedSlots: bookings.filter((booking) => ["CONFIRMED", "PENDING_PAYMENT"].includes(booking.status)).length,
      maintenanceSlots: slots.filter((slot) => slot.status === "MAINTENANCE").length,
      totalUsers: state.accounts.filter((account) => account.role === "USER").length,
      totalBookings: bookings.length,
      confirmedBookings: bookings.filter((booking) => booking.status === "CONFIRMED").length,
      activeBookings: bookings.filter((booking) => booking.status === "ACTIVE").length,
      completedBookings: bookings.filter((booking) => booking.status === "COMPLETED").length,
      cancelledBookings: bookings.filter((booking) => booking.status === "CANCELLED").length,
      bookedValue: bookings.reduce((sum, booking) => sum + booking.amount, 0),
      completedBookingValue: bookings.filter((booking) => booking.status === "COMPLETED").reduce((sum, booking) => sum + booking.amount, 0),
    };
  }
  if (pathname === "/api/admin/analytics" && method === "GET") {
    currentUser(state, "ADMIN");
    return {
      from: url.searchParams.get("from") || "",
      to: url.searchParams.get("to") || "",
      groupBy: url.searchParams.get("groupBy") || "DAY",
      revenueTrend: [],
      mostBookedSlots: [],
      vehicleTypeDistribution: {},
      currentOccupancy: 0,
      confirmedBookings: state.bookings.filter((booking) => booking.status === "CONFIRMED").length,
    };
  }
  if (pathname === "/api/admin/owner-approvals" && method === "GET") {
    currentUser(state, "ADMIN");
    return [] satisfies OwnerApproval[];
  }

  if (pathname.startsWith("/api/payments/")) {
    throw new DemoApiError("Payments are disabled in browser demo mode. No charge was made.", 501);
  }

  if (pathname.startsWith("/api/")) {
    throw new DemoApiError(
      `This feature is not available in browser demo mode (${method} ${rolePath(pathname)}).`,
      501,
    );
  }
  throw new DemoApiError("The requested demo endpoint was not found.", 404);
}
