import {
  ArrowUpRight,
  CarFront,
  ChartNoAxesCombined,
  CircleUserRound,
  FileChartColumn,
  FileCheck2,
  LayoutDashboard,
  LogOut,
  MapPin,
  Menu,
  ParkingSquare,
  RotateCcw,
  TicketCheck,
  UsersRound,
  X,
} from "lucide-react";
import {
  useState,
  type ButtonHTMLAttributes,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
} from "react";
import { NavLink, Outlet } from "react-router-dom";
import { useAuth, useSignOut } from "./auth";
import type { BookingStatus } from "./types";

const userNavigation = [
  { to: "/app/dashboard", label: "Overview", icon: LayoutDashboard },
  { to: "/app/find", label: "Find parking", icon: MapPin },
  { to: "/app/vehicles", label: "My vehicles", icon: CarFront },
  { to: "/app/bookings", label: "My bookings", icon: TicketCheck },
  { to: "/app/profile", label: "Profile", icon: CircleUserRound },
];

const ownerNavigation = [
  { to: "/owner/dashboard", label: "Facility overview", icon: LayoutDashboard },
  { to: "/owner/verification", label: "KYC verification", icon: FileCheck2 },
];

const adminNavigation = [
  { to: "/admin/dashboard", label: "Overview", icon: LayoutDashboard },
  { to: "/admin/slots", label: "Parking slots", icon: ParkingSquare },
  { to: "/admin/bookings", label: "Bookings", icon: TicketCheck },
  { to: "/admin/users", label: "Users", icon: UsersRound },
  { to: "/admin/owner-approvals", label: "Owner approvals", icon: FileCheck2 },
  { to: "/admin/refunds", label: "Refunds", icon: RotateCcw },
  { to: "/admin/analytics", label: "Analytics", icon: ChartNoAxesCombined },
  { to: "/admin/reports", label: "Reports", icon: FileChartColumn },
  { to: "/admin/settings", label: "Settings", icon: CircleUserRound },
];

export function Brand({ compact = false }: { compact?: boolean }) {
  return (
    <span className={`brand${compact ? " brand-compact" : ""}`}>
      <span className="brand-icon"><ParkingSquare size={19} /></span>
      <span>smart<span>park</span></span>
    </span>
  );
}

export function PublicLayout() {
  const { user } = useAuth();
  const destination = user?.role === "ADMIN" ? "/admin/dashboard" : user?.role === "PARKING_OWNER" ? "/owner/dashboard" : "/app/dashboard";
  return (
    <div className="public-layout">
      <header className="public-nav">
        <NavLink to="/" aria-label="SmartPark home"><Brand /></NavLink>
        <nav>
          <a href="/#features">Platform</a>
          <a href="/#how-it-works">How it works</a>
          {user ? (
            <NavLink className="button button-primary button-small" to={destination}>
              Open dashboard <ArrowUpRight size={15} />
            </NavLink>
          ) : (
            <>
              <NavLink className="nav-login" to="/login">Log in</NavLink>
              <NavLink className="nav-login" to="/admin/login">Admin</NavLink>
              <NavLink className="button button-primary button-small" to="/register">
                Get started <ArrowUpRight size={15} />
              </NavLink>
            </>
          )}
        </nav>
      </header>
      <Outlet />
      <footer className="public-footer">
        <Brand compact />
        <span>Parking, with a little more intelligence.</span>
        <span>© {new Date().getFullYear()} SmartPark</span>
      </footer>
    </div>
  );
}

export function PortalLayout({ admin = false, owner = false }: { admin?: boolean; owner?: boolean }) {
  const { user } = useAuth();
  const signOut = useSignOut();
  const [menuOpen, setMenuOpen] = useState(false);
  const links = admin ? adminNavigation : owner ? ownerNavigation : userNavigation;
  return (
    <div className={`portal ${admin ? "portal-admin" : owner ? "portal-owner" : ""}`}>
      <aside className={`sidebar${menuOpen ? " sidebar-open" : ""}`}>
        <NavLink to={admin ? "/admin/dashboard" : owner ? "/owner/dashboard" : "/app/dashboard"} className="sidebar-brand">
          <Brand />
        </NavLink>
        <div className="side-label">{admin ? "WORKSPACE" : owner ? "FACILITY" : "YOUR SPACE"}</div>
        <nav className="side-nav">
          {links.map(({ to, label, icon: Icon }) => (
            <NavLink key={to} to={to} onClick={() => setMenuOpen(false)}>
              <Icon size={17} strokeWidth={1.8} /><span>{label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="user-mini">
            <div className="avatar">{user?.fullName?.slice(0, 1).toUpperCase() || "S"}</div>
            <div className="user-mini-copy">
              <strong>{user?.fullName || "SmartPark user"}</strong>
              <span>{admin ? "Administrator" : owner ? "Parking owner" : user?.email}</span>
            </div>
          </div>
          <button className="side-signout" onClick={signOut}>
            <LogOut size={16} /> Sign out
          </button>
        </div>
      </aside>
      <div className="portal-main">
        <header className="portal-topbar">
          <button className="mobile-menu" aria-label="Toggle navigation" onClick={() => setMenuOpen(!menuOpen)}>
            {menuOpen ? <X size={20} /> : <Menu size={20} />}
          </button>
          <div className="topbar-context">{admin ? "Operations console" : owner ? "Facility portal" : "Customer portal"}</div>
          <div className="topbar-user">
            <span className="online-indicator" />
            <span>{user?.fullName}</span>
            <div className="avatar avatar-small">{user?.fullName?.slice(0, 1).toUpperCase()}</div>
          </div>
        </header>
        <main className="page-content"><Outlet /></main>
      </div>
      {menuOpen && <button className="sidebar-scrim" aria-label="Close navigation" onClick={() => setMenuOpen(false)} />}
    </div>
  );
}

export function PageHeader({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow: string;
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="page-header">
      <div>
        <div className="eyebrow">{eyebrow}</div>
        <h1>{title}</h1>
        {description && <p>{description}</p>}
      </div>
      {action && <div className="page-header-action">{action}</div>}
    </div>
  );
}

export function StatCard({
  label,
  value,
  hint,
  icon: Icon,
  tone = "violet",
}: {
  label: string;
  value: string | number;
  hint?: string;
  icon: typeof LayoutDashboard;
  tone?: "violet" | "green" | "blue" | "amber";
}) {
  return (
    <article className="stat-card">
      <div className={`stat-icon tone-${tone}`}><Icon size={19} /></div>
      <div className="stat-copy">
        <span>{label}</span>
        <strong>{value}</strong>
        {hint && <small>{hint}</small>}
      </div>
    </article>
  );
}

export function Panel({
  title,
  action,
  children,
  className = "",
}: {
  title?: string;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={`panel-card ${className}`}>
      {(title || action) && (
        <div className="panel-heading">
          {title && <h2>{title}</h2>}
          {action}
        </div>
      )}
      {children}
    </section>
  );
}

export function Field({
  label,
  className = "",
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label: string; className?: string }) {
  return (
    <label className={`field ${className}`}>
      <span>{label}</span>
      <input {...props} />
    </label>
  );
}

export function SelectField({
  label,
  children,
  className = "",
  ...props
}: SelectHTMLAttributes<HTMLSelectElement> & { label: string; children: ReactNode; className?: string }) {
  return (
    <label className={`field ${className}`}>
      <span>{label}</span>
      <select {...props}>{children}</select>
    </label>
  );
}

export function Notice({
  children,
  tone = "error",
}: {
  children: ReactNode;
  tone?: "error" | "success" | "info";
}) {
  if (!children) return null;
  return <div className={`notice notice-${tone}`} role="status">{children}</div>;
}

export function Loading({ label = "Loading your data…" }: { label?: string }) {
  return <div className="loading-state"><span className="spinner" />{label}</div>;
}

export function EmptyState({
  title,
  body,
  action,
}: {
  title: string;
  body?: string;
  action?: ReactNode;
}) {
  return (
    <div className="empty-state">
      <span className="empty-mark"><ParkingSquare size={22} /></span>
      <strong>{title}</strong>
      {body && <p>{body}</p>}
      {action}
    </div>
  );
}

export function StatusBadge({ status }: { status: BookingStatus | string }) {
  return <span className={`status-badge status-${status.toLowerCase()}`}>{status.replaceAll("_", " ")}</span>;
}

export function Button({
  variant = "primary",
  className = "",
  children,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "secondary" | "danger" | "ghost";
}) {
  return (
    <button className={`button button-${variant} ${className}`} {...props}>
      {children}
    </button>
  );
}
