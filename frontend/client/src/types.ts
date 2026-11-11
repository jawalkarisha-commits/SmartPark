export type Role = "USER" | "PARKING_OWNER" | "ADMIN";
export type BookingStatus =
  | "PENDING_PAYMENT"
  | "CONFIRMED"
  | "ACTIVE"
  | "OVERDUE"
  | "COMPLETED"
  | "CANCELLED"
  | "EXPIRED";
export type SlotStatus = "AVAILABLE" | "MAINTENANCE";
export type BillingPeriod = "HOURLY" | "DAILY" | "MONTHLY";

export interface User {
  id: number;
  fullName: string;
  email: string;
  phone: string | null;
  role: Role;
  enabled: boolean;
}

export interface AuthResponse extends User {
  accessToken: string;
  tokenType: "Bearer";
  expiresInSeconds: number;
}

export interface Vehicle {
  id: number;
  vehicleNumber: string;
  vehicleType: string;
  brand: string | null;
  model: string | null;
  color: string | null;
}

export interface ParkingSlot {
  id: number;
  slotNumber: string;
  floorNumber: number | null;
  location: string | null;
  zone: string | null;
  vehicleType: string | null;
  hourlyPrice: number;
  dailyPrice: number;
  monthlyPrice: number;
  status: SlotStatus;
  available: boolean;
  facilityId?: number;
  facilityName?: string;
  facilityAddress?: string;
  latitude?: number | null;
  longitude?: number | null;
}

export interface Facility {
  id: number;
  name: string;
  address: string;
  totalCapacity: number;
  operatingStart: string;
  operatingEnd: string;
  latitude: number | null;
  longitude: number | null;
}

export interface Booking {
  id: number;
  bookingReference: string;
  userId: number;
  vehicleId: number;
  vehicleNumber: string;
  slotId: number;
  slotNumber: string;
  startTime: string;
  endTime: string;
  status: BookingStatus;
  hourlyRate: number;
  billingPeriod: BillingPeriod;
  billedRate: number;
  amount: number;
  createdAt: string;
  holdExpiresAt: string | null;
  actualEntryTime: string | null;
  actualExitTime: string | null;
  lateHours: number;
  lateFeeAmount: number;
  bookingGroupId?: number | null;
}

export interface BookingGroup {
  id: number;
  groupReference: string;
  totalAmount: number;
  bookings: Booking[];
}

export interface GroupChargeEstimate {
  totalAmount: number;
  vehicles: {
    vehicleId: number;
    vehicleNumber: string;
    slotId: number;
    slotNumber: string;
    amount: number;
  }[];
}

export interface ChargeEstimate {
  durationMinutes: number;
  hourlyRate: number;
  billingPeriod: BillingPeriod;
  billedRate: number;
  amount: number;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface DashboardMetrics {
  totalSlots: number;
  availableSlots: number;
  occupiedSlots: number;
  reservedSlots: number;
  maintenanceSlots: number;
  totalUsers: number;
  totalBookings: number;
  confirmedBookings: number;
  activeBookings: number;
  completedBookings: number;
  cancelledBookings: number;
  bookedValue: number;
  completedBookingValue: number;
}

export interface Analytics {
  from: string;
  to: string;
  groupBy: "DAY" | "WEEK" | "MONTH";
  revenueTrend: { date: string; bookings: number; amount: number }[];
  mostBookedSlots: { slotId: number; slotNumber: string; bookings: number }[];
  vehicleTypeDistribution: Record<string, number>;
  currentOccupancy: number;
  confirmedBookings: number;
}

export interface RefundAttempt {
  id: number;
  bookingReference: string | null;
  groupReference: string | null;
  amountPaise: number;
  status: string;
  providerRefundId: string | null;
  failureReason: string | null;
  createdAt: string;
}

export interface AdminUser extends User {
  createdAt: string;
}

export interface OwnerVerification {
  ownerUserId: number;
  ownerName: string;
  email: string;
  phone: string | null;
  emailVerified: boolean;
  documentUploaded: boolean;
  documentFileName: string | null;
  documentUploadedAt: string | null;
  approvalStatus: "PENDING" | "APPROVED" | "REJECTED";
  approvalMethod: "AUTO" | "MANUAL" | null;
  requestedAt: string;
  rejectionReason: string | null;
  developmentOtp?: string | null;
}

export interface OwnerApproval {
  owner: OwnerVerification;
  documentAvailable: boolean;
  reviewedBy: string | null;
  reviewedAt: string | null;
  notificationSent?: boolean;
}

export interface UnassignedVehicle {
  id: number;
  legacyOwnerName: string;
  vehicleNumber: string;
  vehicleType: string | null;
  brand: string | null;
  model: string | null;
  color: string | null;
}

export interface AdminSettings {
  facilityId: number;
  facilityName: string;
  address: string;
  totalCapacity: number;
  availableSlots: number;
  operatingStart: string;
  operatingEnd: string;
  ownerUserId: number | null;
  currencyCode: "INR";
  ownerRegistrationEnabled: boolean;
  cancellationEnabled: boolean;
  cancellationCutoffMinutes: number;
  refundPercentage: number;
  vehicleTypes: string[];
}

export interface OwnerOverview {
  facility: Facility;
  slots: ParkingSlot[];
  bookings: Booking[];
}
