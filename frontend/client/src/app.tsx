import { lazy, Suspense, type ReactNode } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "./auth";
import type { Role } from "./types";
import { Loading, PortalLayout, PublicLayout } from "./components";

const LandingPage = lazy(() => import("./pages/public").then((pages) => ({ default: pages.LandingPage })));
const LoginPage = lazy(() => import("./pages/public").then((pages) => ({ default: pages.LoginPage })));
const RegisterPage = lazy(() => import("./pages/public").then((pages) => ({ default: pages.RegisterPage })));
const ResetPasswordPage = lazy(() => import("./pages/public").then((pages) => ({ default: pages.ResetPasswordPage })));
const UserDashboardPage = lazy(() => import("./pages/user").then((pages) => ({ default: pages.UserDashboardPage })));
const FindParkingPage = lazy(() => import("./pages/user").then((pages) => ({ default: pages.FindParkingPage })));
const VehiclesPage = lazy(() => import("./pages/user").then((pages) => ({ default: pages.VehiclesPage })));
const BookingHistoryPage = lazy(() => import("./pages/user").then((pages) => ({ default: pages.BookingHistoryPage })));
const ProfilePage = lazy(() => import("./pages/user").then((pages) => ({ default: pages.ProfilePage })));
const SlotDetailsPage = lazy(() => import("./pages/user").then((pages) => ({ default: pages.SlotDetailsPage })));
const UserBookingDetailsPage = lazy(() => import("./pages/user").then((pages) => ({ default: pages.UserBookingDetailsPage })));
const AdminDashboardPage = lazy(() => import("./pages/admin").then((pages) => ({ default: pages.AdminDashboardPage })));
const AdminOwnerApprovalsPage = lazy(() => import("./pages/admin").then((pages) => ({ default: pages.AdminOwnerApprovalsPage })));
const AdminSlotsPage = lazy(() => import("./pages/admin").then((pages) => ({ default: pages.AdminSlotsPage })));
const AdminBookingsPage = lazy(() => import("./pages/admin").then((pages) => ({ default: pages.AdminBookingsPage })));
const AdminUsersPage = lazy(() => import("./pages/admin").then((pages) => ({ default: pages.AdminUsersPage })));
const AdminAnalyticsPage = lazy(() => import("./pages/admin").then((pages) => ({ default: pages.AdminAnalyticsPage })));
const AdminBookingDetailsPage = lazy(() => import("./pages/admin").then((pages) => ({ default: pages.AdminBookingDetailsPage })));
const AdminReportsPage = lazy(() => import("./pages/admin").then((pages) => ({ default: pages.AdminReportsPage })));
const AdminRefundsPage = lazy(() => import("./pages/admin").then((pages) => ({ default: pages.AdminRefundsPage })));
const AdminSettingsPage = lazy(() => import("./pages/admin").then((pages) => ({ default: pages.AdminSettingsPage })));
const OwnerDashboardPage = lazy(() => import("./pages/owner").then((pages) => ({ default: pages.OwnerDashboardPage })));
const OwnerVerificationPage = lazy(() => import("./pages/owner").then((pages) => ({ default: pages.OwnerVerificationPage })));

function RequireRole({
  role,
  children,
}: {
  role: Role;
  children: ReactNode;
}) {
  const { user, loading } = useAuth();
  if (loading) return <Loading label="Restoring your secure session…" />;
  if (!user) return <Navigate to={role === "ADMIN" ? "/admin/login" : "/login"} replace />;
  if (user.role !== role) {
    return <Navigate to={user.role === "ADMIN" ? "/admin/dashboard" : user.role === "PARKING_OWNER" ? "/owner/dashboard" : "/app/dashboard"} replace />;
  }
  return children;
}

function HomeRedirect() {
  const { user, loading } = useAuth();
  if (loading) return <Loading />;
  if (!user) return <LandingPage />;
  return <Navigate to={user.role === "ADMIN" ? "/admin/dashboard" : user.role === "PARKING_OWNER" ? "/owner/dashboard" : "/app/dashboard"} replace />;
}

export function App() {
  return (
    <Suspense fallback={<Loading label="Loading SmartPark…" />}>
      <Routes>
        <Route element={<PublicLayout />}>
          <Route path="/" element={<HomeRedirect />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/admin/login" element={<LoginPage adminOnly />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/forgot-password" element={<ResetPasswordPage />} />
        </Route>
        <Route
          path="/app"
          element={<RequireRole role="USER"><PortalLayout /></RequireRole>}
        >
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<UserDashboardPage />} />
          <Route path="find" element={<FindParkingPage />} />
          <Route path="slots/:slotId" element={<SlotDetailsPage />} />
          <Route path="vehicles" element={<VehiclesPage />} />
          <Route path="bookings" element={<BookingHistoryPage />} />
          <Route path="bookings/:bookingId" element={<UserBookingDetailsPage />} />
          <Route path="profile" element={<ProfilePage />} />
        </Route>
        <Route
          path="/admin"
          element={<RequireRole role="ADMIN"><PortalLayout admin /></RequireRole>}
        >
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<AdminDashboardPage />} />
          <Route path="slots" element={<AdminSlotsPage />} />
          <Route path="bookings" element={<AdminBookingsPage />} />
          <Route path="bookings/:bookingId" element={<AdminBookingDetailsPage />} />
          <Route path="users" element={<AdminUsersPage />} />
          <Route path="owner-approvals" element={<AdminOwnerApprovalsPage />} />
          <Route path="analytics" element={<AdminAnalyticsPage />} />
          <Route path="reports" element={<AdminReportsPage />} />
          <Route path="refunds" element={<AdminRefundsPage />} />
          <Route path="settings" element={<AdminSettingsPage />} />
        </Route>
        <Route
          path="/owner"
          element={<RequireRole role="PARKING_OWNER"><PortalLayout owner /></RequireRole>}
        >
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<OwnerDashboardPage />} />
          <Route path="verification" element={<OwnerVerificationPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  );
}
