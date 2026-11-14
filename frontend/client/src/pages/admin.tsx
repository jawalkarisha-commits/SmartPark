import {
  Activity,
  ArrowUpRight,
  CalendarDays,
  CarFront,
  CheckCircle2,
  CircleDollarSign,
  Clock3,
  Download,
  FileText,
  ParkingSquare,
  RefreshCw,
  RotateCcw,
  Search,
  ShieldCheck,
  UsersRound,
} from "lucide-react";
import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type FormEvent,
} from "react";
import { Link, useParams } from "react-router-dom";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { api, currency, downloadFile, queryString } from "../api";
import { useAuth } from "../auth";
import {
  Button,
  EmptyState,
  Field,
  Loading,
  Notice,
  PageHeader,
  Panel,
  SelectField,
  StatCard,
  StatusBadge,
} from "../components";
import type {
  AdminUser,
  AdminSettings,
  Analytics,
  Booking,
  DashboardMetrics,
  PageResponse,
  ParkingSlot,
  SlotStatus,
  UnassignedVehicle,
  User,
  OwnerApproval,
  RefundAttempt,
} from "../types";

const chartColors = ["#9a77ff", "#6a4ed1", "#c5afff", "#7d6bb5", "#cab9fb"];

function dateOnly(date: Date) {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 10);
}

function last30Days() {
  const to = new Date();
  const from = new Date();
  from.setDate(from.getDate() - 29);
  return { from: dateOnly(from), to: dateOnly(to) };
}

export function AdminDashboardPage() {
  const [metrics, setMetrics] = useState<DashboardMetrics | null>(null);
  const [analytics, setAnalytics] = useState<Analytics | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setError("");
    try {
      const period = last30Days();
      const [dashboard, trend] = await Promise.all([
        api<DashboardMetrics>("/api/admin/dashboard"),
        api<Analytics>(`/api/admin/analytics?${queryString({ ...period, groupBy: "DAY" })}`),
      ]);
      setMetrics(dashboard);
      setAnalytics(trend);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Admin dashboard could not be loaded.");
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => { void load(); }, [load]);

  if (loading) return <Loading label="Loading live operations data…" />;
  return (
    <>
      <PageHeader eyebrow="PARKING OPERATIONS" title="Operations overview" description="A live snapshot of your SmartPark facility." action={<Button variant="secondary" onClick={() => void load()}><RefreshCw size={15} /> Refresh</Button>} />
      <Notice>{error}</Notice>
      {metrics && (
        <>
          <div className="stats-grid admin-stats-grid">
            <StatCard label="Total spaces" value={metrics.totalSlots} hint={`${metrics.maintenanceSlots} in maintenance`} icon={ParkingSquare} />
            <StatCard label="Available now" value={metrics.availableSlots} hint="Operational and unreserved" icon={Activity} tone="green" />
            <StatCard label="Occupied" value={metrics.occupiedSlots} hint="Active sessions" icon={CarFront} tone="blue" />
            <StatCard label="Reserved" value={metrics.reservedSlots} hint="Current and upcoming" icon={CalendarDays} tone="amber" />
            <StatCard label="Registered users" value={metrics.totalUsers} hint="All customer accounts" icon={UsersRound} />
            <StatCard label="Bookings" value={metrics.totalBookings} hint={`${metrics.activeBookings} active now`} icon={Clock3} tone="blue" />
            <StatCard label="Completed" value={metrics.completedBookings} hint={`${metrics.cancelledBookings} cancelled`} icon={ShieldCheck} tone="green" />
            <StatCard label="Completed value" value={currency(metrics.completedBookingValue)} hint="Customer booking charges" icon={CircleDollarSign} tone="violet" />
            <StatCard label="Platform fees (simulated)" value={currency(metrics.platformFeeTotal)} hint="10% share after customer settlement" icon={CircleDollarSign} tone="amber" />
          </div>
          <div className="dashboard-grid">
            <Panel title="Booked value · last 30 days" className="chart-panel">
              {analytics?.revenueTrend.length ? (
                <ResponsiveContainer width="100%" height={260}>
                  <LineChart data={analytics.revenueTrend}>
                    <CartesianGrid stroke="#272b3c" strokeDasharray="3 3" vertical={false} />
                    <XAxis dataKey="date" stroke="#8288a0" tickLine={false} axisLine={false} tickFormatter={(value: string) => value.slice(5)} />
                    <YAxis stroke="#8288a0" tickLine={false} axisLine={false} />
                    <Tooltip contentStyle={{ background: "#171b2b", border: "1px solid #30354b", borderRadius: 10 }} formatter={(value) => currency(Number(value ?? 0))} />
                    <Line type="monotone" dataKey="amount" stroke="#9a77ff" strokeWidth={3} dot={{ r: 3, fill: "#bca4ff" }} />
                  </LineChart>
                </ResponsiveContainer>
              ) : <EmptyState title="No booking data for this period" body="The chart will populate as reservations are created." />}
            </Panel>
            <Panel title="Most booked spaces" className="chart-panel">
              {analytics?.mostBookedSlots.length ? (
                <ResponsiveContainer width="100%" height={260}>
                  <BarChart data={analytics.mostBookedSlots} layout="vertical" margin={{ left: 8, right: 12 }}>
                    <CartesianGrid stroke="#272b3c" strokeDasharray="3 3" horizontal={false} />
                    <XAxis type="number" stroke="#8288a0" tickLine={false} axisLine={false} />
                    <YAxis type="category" dataKey="slotNumber" stroke="#a9adc0" tickLine={false} axisLine={false} width={50} />
                    <Tooltip contentStyle={{ background: "#171b2b", border: "1px solid #30354b", borderRadius: 10 }} />
                    <Bar dataKey="bookings" fill="#8b5cf6" radius={[0, 5, 5, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              ) : <EmptyState title="No spaces booked yet" body="Popular spaces will appear here as booking history grows." />}
            </Panel>
          </div>
          <div className="admin-summary-line">
            <span>Confirmed <strong>{metrics.confirmedBookings}</strong></span>
            <span>Active <strong>{metrics.activeBookings}</strong></span>
            <span>Completed <strong>{metrics.completedBookings}</strong></span>
            <span>Booked value <strong>{currency(metrics.bookedValue)}</strong></span>
            <span>Platform fees (simulated) <strong>{currency(metrics.platformFeeTotal)}</strong></span>
            <span className="summary-caption">No real payment or fund transfer is processed.</span>
          </div>
        </>
      )}
    </>
  );
}

export function AdminOwnerApprovalsPage() {
  const [approvals, setApprovals] = useState<OwnerApproval[]>([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busyOwnerId, setBusyOwnerId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setError("");
    try {
      setApprovals(await api<OwnerApproval[]>("/api/admin/owner-approvals"));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Owner approval requests could not be loaded.");
    } finally {
      setLoading(false);
    }

  }, []);
  useEffect(() => { void load(); }, [load]);

  async function approve(ownerId: number) {
    setBusyOwnerId(ownerId);
    setError("");
    setNotice("");
    try {
      const result = await api<OwnerApproval>(`/api/admin/owner-approvals/${ownerId}/approve`, { method: "POST" });
      setNotice(result.notificationSent === false
        ? `${result.owner.ownerName} was approved. The notification email could not be sent; configure SMTP to notify the owner.`
        : `${result.owner.ownerName} was manually approved as a parking owner.`);
      await load();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "This owner could not be approved.");
    } finally {
      setBusyOwnerId(null);
    }
  }

  async function reject(ownerId: number) {
    const reason = window.prompt("Enter the reason for rejecting this application:");
    if (reason === null) return;
    if (!reason.trim()) {
      setError("A rejection reason is required.");
      return;
    }

    setBusyOwnerId(ownerId);
    setError("");
    setNotice("");
    try {
      const result = await api<OwnerApproval>(
        `/api/admin/owner-approvals/${ownerId}/reject`,
        {
          method: "POST",
          body: JSON.stringify({ reason: reason.trim() }),
        },
      );
      setNotice(result.notificationSent === false
        ? `${result.owner.ownerName}'s application was rejected. The notification email could not be sent; configure SMTP to notify the owner.`
        : `${result.owner.ownerName}'s application was rejected.`);
      await load();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "This owner could not be rejected.");
    } finally {
      setBusyOwnerId(null);
    }
  }

  async function downloadDocument(ownerId: number) {
    setError("");
    try {
      await downloadFile(`/api/admin/owner-approvals/${ownerId}/document`);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "The government document could not be downloaded.");
    }
  }

  return (
    <>
      <PageHeader
        eyebrow="OWNER ONBOARDING"
        title="Owner approval requests"
        description="Uploaded files are not treated as identity verification. Review each pending application and approve or reject it with a reason."
        action={<Button variant="secondary" onClick={() => void load()}><RefreshCw size={15} /> Refresh</Button>}
      />
      <Notice>{error}</Notice>
      <Notice tone="success">{notice}</Notice>
      <p className="panel-description">
        Approval is enabled after the owner verifies their email and submits a government document.
        A notification-email failure will not undo the administrator's decision.
      </p>
      <Panel title={`Pending requests · ${approvals.length}`}>
        {loading ? <Loading label="Loading owner requests…" /> : approvals.length ? (
          <div className="table-wrap">
            <table className="owner-approvals-table">
              <thead>
                <tr><th>Parking owner</th><th>Email verification</th><th>Government document</th><th>Request date</th><th>Admin action</th></tr>
              </thead>
              <tbody>
                {approvals.map(({ owner, documentAvailable }) => (
                  <tr key={owner.ownerUserId}>
                    <td><strong>{owner.ownerName}</strong><small>{owner.email}{owner.phone ? ` · ${owner.phone}` : ""}</small></td>
                    <td><StatusBadge status={owner.emailVerified ? "VERIFIED" : "PENDING"} /></td>
                    <td>
                      {documentAvailable ? (
                        <Button variant="secondary" className="document-download" onClick={() => void downloadDocument(owner.ownerUserId)}>
                          <Download size={14} /> {owner.documentFileName || "Download"}
                        </Button>
                      ) : <span className="muted-inline"><FileText size={14} /> Not uploaded</span>}
                    </td>
                    <td>{new Date(owner.requestedAt).toLocaleDateString()}</td>
                    <td>
                      <div className="owner-slot-actions">
                        <Button disabled={
                          busyOwnerId !== null ||
                          !owner.emailVerified ||
                          !documentAvailable
                        } onClick={() => void approve(owner.ownerUserId)}>
                          <CheckCircle2 size={15} /> {busyOwnerId === owner.ownerUserId ? "Saving…" : "Approve"}
                        </Button>
                        <Button
                          variant="secondary"
                          disabled={busyOwnerId !== null}
                          onClick={() => void reject(owner.ownerUserId)}
                        >
                          Reject
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <EmptyState title="No pending owner requests" body="Owner applications remain pending until an administrator approves or rejects them." />
        )}
      </Panel>
    </>
  );
}

export function AdminRefundsPage() {
  const [refunds, setRefunds] = useState<RefundAttempt[]>([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busyRefundId, setBusyRefundId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setError("");
    try {
      setRefunds(await api<RefundAttempt[]>("/api/admin/refunds"));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Refund reconciliation data could not be loaded.");
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => { void load(); }, [load]);

  async function retry(refund: RefundAttempt) {
    if (!window.confirm(`Retry refund ${refund.id} for ${currency(refund.amountPaise / 100)}?`)) return;
    setBusyRefundId(refund.id);
    setError("");
    setNotice("");
    try {
      await api<void>(`/api/admin/refunds/${refund.id}/retry`, { method: "POST" });
      setNotice(`Refund ${refund.id} retry was submitted. Refresh to see the latest provider status.`);
      await load();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "The refund could not be retried.");
    } finally {
      setBusyRefundId(null);
    }
  }

  return (
    <>
      <PageHeader
        eyebrow="PAYMENT OPERATIONS"
        title="Refund reconciliation"
        description="Review provider refund attempts and safely retry pending or failed refunds."
        action={<Button variant="secondary" onClick={() => void load()}><RefreshCw size={15} /> Refresh</Button>}
      />
      <Notice>{error}</Notice>
      <Notice tone="success">{notice}</Notice>
      <Panel title="Recent refund attempts">
        {loading ? <Loading /> : refunds.length ? (
          <div className="table-wrap">
            <table>
              <thead><tr><th>Reference</th><th>Amount</th><th>Status</th><th>Provider refund</th><th>Last detail</th><th>Created</th><th></th></tr></thead>
              <tbody>{refunds.map((refund) => (
                <tr key={refund.id}>
                  <td><strong>{refund.bookingReference || refund.groupReference || `Refund ${refund.id}`}</strong></td>
                  <td>{currency(refund.amountPaise / 100)}</td>
                  <td><StatusBadge status={refund.status} /></td>
                  <td>{refund.providerRefundId || "—"}</td>
                  <td>{refund.failureReason || "—"}</td>
                  <td>{new Date(refund.createdAt).toLocaleString()}</td>
                  <td>{refund.status === "FAILED" || refund.status === "PENDING" ? (
                    <Button variant="secondary" disabled={busyRefundId === refund.id} onClick={() => void retry(refund)}>
                      <RotateCcw size={14} /> {busyRefundId === refund.id ? "Retrying…" : "Retry"}
                    </Button>
                  ) : null}</td>
                </tr>
              ))}</tbody>
            </table>
          </div>
        ) : <EmptyState title="No refund attempts yet" body="Refund requests will appear here for reconciliation." />}
      </Panel>
    </>
  );
}

export function AdminSlotsPage() {
  const blank = {
    slotNumber: "", floorNumber: "", location: "", zone: "",
    vehicleType: "", hourlyPrice: "", dailyPrice: "", monthlyPrice: "",
    status: "AVAILABLE" as SlotStatus,
  };
  const [slots, setSlots] = useState<ParkingSlot[]>([]);
  const [vehicleTypes, setVehicleTypes] = useState<string[]>([]);
  const [values, setValues] = useState(blank);
  const [editing, setEditing] = useState<number | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setError("");
    try {
      const [slotData, supportedTypes] = await Promise.all([
        api<ParkingSlot[]>("/api/admin/parking-slots"),
        api<string[]>("/api/settings/vehicle-types"),
      ]);
      setSlots(slotData);
      setVehicleTypes(supportedTypes);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Parking spaces could not be loaded.");
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => { void load(); }, [load]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError("");
    const body = {
      slotNumber: values.slotNumber,
      floorNumber: values.floorNumber ? Number(values.floorNumber) : null,
      location: values.location || null,
      zone: values.zone || null,
      vehicleType: values.vehicleType || null,
      hourlyPrice: Number(values.hourlyPrice),
      dailyPrice: Number(values.dailyPrice),
      monthlyPrice: Number(values.monthlyPrice),
      status: values.status,
    };
    try {
      await api<ParkingSlot>(
        editing ? `/api/admin/parking-slots/${editing}` : "/api/admin/parking-slots",
        { method: editing ? "PATCH" : "POST", body: JSON.stringify(body) },
      );
      setValues(blank);
      setEditing(null);
      await load();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "The parking space could not be saved.");
    } finally {
      setSaving(false);
    }
  }

  async function remove(slot: ParkingSlot) {
    if (!window.confirm(`Delete space ${slot.slotNumber}? Spaces with booking history cannot be deleted.`)) return;
    try {
      await api<void>(`/api/admin/parking-slots/${slot.id}`, { method: "DELETE" });
      await load();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "The parking space could not be deleted.");
    }
  }

  function edit(slot: ParkingSlot) {
    setEditing(slot.id);
    setValues({
      slotNumber: slot.slotNumber,
      floorNumber: slot.floorNumber == null ? "" : String(slot.floorNumber),
      location: slot.location || "",
      zone: slot.zone || "",
      vehicleType: slot.vehicleType || "",
      hourlyPrice: String(slot.hourlyPrice),
      dailyPrice: String(slot.dailyPrice),
      monthlyPrice: String(slot.monthlyPrice),
      status: slot.status,
    });
  }

  return (
    <>
      <PageHeader eyebrow="FACILITY CONFIGURATION" title="Parking spaces" description="Manage slot operations, vehicle compatibility, and INR hourly/daily/monthly pricing." />
      <Notice>{error}</Notice>
      <div className="content-split admin-slot-split">
        <Panel title={editing ? "Edit space" : "Add a parking space"}>
          <form className="form-grid" onSubmit={(event) => void submit(event)}>
            <Field label="Space number" value={values.slotNumber} onChange={(e) => setValues({ ...values, slotNumber: e.target.value })} required maxLength={100} />
            <Field label="Floor" type="number" min="0" value={values.floorNumber} onChange={(e) => setValues({ ...values, floorNumber: e.target.value })} />
            <Field label="Location" value={values.location} onChange={(e) => setValues({ ...values, location: e.target.value })} />
            <Field label="Zone" value={values.zone} onChange={(e) => setValues({ ...values, zone: e.target.value })} />
            <SelectField label="Vehicle type" value={values.vehicleType} onChange={(e) => setValues({ ...values, vehicleType: e.target.value })}>
              <option value="">All types</option>{vehicleTypes.map((type) => <option key={type}>{type}</option>)}
            </SelectField>
            <Field label="Hourly rate" type="number" min="0.01" step="0.01" value={values.hourlyPrice} onChange={(e) => setValues({ ...values, hourlyPrice: e.target.value })} required />
            <Field label="Daily rate" type="number" min="0.01" step="0.01" value={values.dailyPrice} onChange={(e) => setValues({ ...values, dailyPrice: e.target.value })} required />
            <Field label="Monthly rate (30 days)" type="number" min="0.01" step="0.01" value={values.monthlyPrice} onChange={(e) => setValues({ ...values, monthlyPrice: e.target.value })} required />
            <SelectField label="Operational status" value={values.status} onChange={(e) => setValues({ ...values, status: e.target.value as SlotStatus })}>
              <option value="AVAILABLE">Available</option><option value="MAINTENANCE">Maintenance</option>
            </SelectField>
            <div className="form-actions"><Button type="submit" disabled={saving}>{saving ? "Saving…" : editing ? "Save changes" : "Add space"}</Button>{editing && <Button type="button" variant="ghost" onClick={() => { setEditing(null); setValues(blank); }}>Cancel</Button>}</div>
          </form>
        </Panel>
        <Panel title="Inventory">
          {loading ? <Loading /> : slots.length ? (
            <div className="table-wrap">
              <table>
                <thead><tr><th>Space</th><th>Location</th><th>Hourly / day / month</th><th>Status</th><th></th></tr></thead>
                <tbody>{slots.map((slot) => (
                  <tr key={slot.id}>
                    <td><strong>{slot.slotNumber}</strong><small>{slot.vehicleType || "All vehicle types"}</small></td>
                    <td>{slot.location || "—"}<small>{[slot.zone, slot.floorNumber != null ? `Floor ${slot.floorNumber}` : ""].filter(Boolean).join(" · ")}</small></td>
                    <td>{currency(slot.hourlyPrice)} / {currency(slot.dailyPrice)} / {currency(slot.monthlyPrice)}</td>
                    <td><StatusBadge status={slot.status} /></td>
                    <td><div className="row-buttons"><Button variant="ghost" onClick={() => edit(slot)}>Edit</Button><Button variant="danger" onClick={() => void remove(slot)}>Delete</Button></div></td>
                  </tr>
                ))}</tbody>
              </table>
            </div>
          ) : <EmptyState title="No parking spaces configured" body="Add the first space to begin accepting reservations." />}
        </Panel>
      </div>
    </>
  );
}

export function AdminBookingsPage() {
  const [page, setPage] = useState<PageResponse<Booking> | null>(null);
  const [status, setStatus] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [currentPage, setCurrentPage] = useState(0);

  const load = useCallback(async () => {
    setError("");
    try {
      const query = queryString({ status, page: currentPage, size: 20 });
      setPage(await api<PageResponse<Booking>>(`/api/admin/bookings?${query}`));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Bookings could not be loaded.");
    } finally {
      setLoading(false);
    }
  }, [status, currentPage]);
  useEffect(() => { void load(); }, [load]);

  async function transition(booking: Booking, action: "start" | "complete" | "cancel") {
    const messages = { start: "Start this parking session?", complete: "Mark this booking completed?", cancel: "Cancel this booking?" };
    if (!window.confirm(messages[action])) return;
    try {
      await api<Booking>(`/api/admin/bookings/${booking.id}/${action}`, { method: "POST" });
      await load();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Booking status could not be updated.");
    }
  }

  return (
    <>
      <PageHeader eyebrow="RESERVATION CONTROL" title="Booking management" description="Review reservations and update their operational status." />
      <Notice>{error}</Notice>
      <Panel title="All bookings" action={<SelectField label="Status filter" value={status} onChange={(event) => { setStatus(event.target.value); setCurrentPage(0); }}>
        <option value="">All statuses</option><option value="PENDING_PAYMENT">Pending payment</option><option value="CONFIRMED">Confirmed</option><option value="ACTIVE">Active</option><option value="OVERDUE">Overdue</option><option value="COMPLETED">Completed</option><option value="CANCELLED">Cancelled</option><option value="EXPIRED">Expired</option>
      </SelectField>}>
        {loading ? <Loading /> : page?.items.length ? (
          <>
            <div className="table-wrap">
              <table>
                <thead><tr><th>Reference</th><th>Customer / vehicle</th><th>Space</th><th>Schedule</th><th>Charge estimate</th><th>Status</th><th>Operations</th></tr></thead>
                <tbody>{page.items.map((booking) => (
                  <tr key={booking.id}>
                    <td><Link className="text-link" to={`/admin/bookings/${booking.id}`}>#{booking.bookingReference.slice(0, 8).toUpperCase()}</Link></td>
                    <td>{booking.userId}<small>{booking.vehicleNumber}</small></td>
                    <td>{booking.slotNumber}</td>
                    <td>{new Date(booking.startTime).toLocaleString()}<small>to {new Date(booking.endTime).toLocaleString()}</small></td>
                    <td>{currency(booking.amount)}</td>
                    <td><StatusBadge status={booking.status} /></td>
                    <td><div className="row-buttons">
                      {booking.status === "CONFIRMED" && <>
                        <Button variant="secondary" onClick={() => void transition(booking, "start")}>Start</Button>
                        <Button variant="danger" onClick={() => void transition(booking, "cancel")}>Cancel</Button>
                      </>}
                      {(booking.status === "ACTIVE" || booking.status === "OVERDUE") && <Button variant="secondary" onClick={() => void transition(booking, "complete")}>Complete</Button>}
                    </div></td>
                  </tr>
                ))}</tbody>
              </table>
            </div>
            <div className="pagination">
              <span>{page.totalElements} total bookings</span>
              <div><Button variant="ghost" disabled={page.page <= 0} onClick={() => setCurrentPage(page.page - 1)}>Previous</Button><span>Page {page.page + 1} of {Math.max(page.totalPages, 1)}</span><Button variant="ghost" disabled={page.page + 1 >= page.totalPages} onClick={() => setCurrentPage(page.page + 1)}>Next</Button></div>
            </div>
          </>
        ) : <EmptyState title="No bookings match this filter" body="Reservations will appear here as customers make them." />}
      </Panel>
    </>
  );
}

export function AdminUsersPage() {
  const { user: currentUser } = useAuth();
  const [users, setUsers] = useState<PageResponse<AdminUser> | null>(null);
  const [unassigned, setUnassigned] = useState<UnassignedVehicle[]>([]);
  const [unverifiedOwners, setUnverifiedOwners] = useState<AdminUser[]>([]);
  const [search, setSearch] = useState("");
  const [query, setQuery] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [owners, setOwners] = useState<Record<number, string>>({});

  const load = useCallback(async () => {
    setError("");
    try {
      const [userPage, unassignedVehicles, legacyOwners] = await Promise.all([
        api<PageResponse<AdminUser>>(`/api/admin/users?${queryString({ search, page: 0, size: 50 })}`),
        api<UnassignedVehicle[]>("/api/admin/vehicles/unassigned"),
        api<AdminUser[]>("/api/admin/users/unverified-owners"),
      ]);
      setUsers(userPage);
      setUnassigned(unassignedVehicles);
      setUnverifiedOwners(legacyOwners);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "User records could not be loaded.");
    } finally {
      setLoading(false);
    }
  }, [search]);
  useEffect(() => { void load(); }, [load]);

  async function toggle(user: AdminUser) {
    try {
      await api<AdminUser>(`/api/admin/users/${user.id}/status`, {
        method: "PATCH",
        body: JSON.stringify({ enabled: !user.enabled }),
      });
      await load();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Account status could not be changed.");
    }
  }

  async function removeUser(user: AdminUser) {
    const ownerHistory = user.role === "PARKING_OWNER"
      ? " This also permanently deletes their facilities, spaces, and all bookings and payment history tied to those facilities, including other customers’ records."
      : " This also permanently deletes their vehicles, bookings, and payment history.";
    if (!window.confirm(
      `Permanently delete ${user.fullName} (${user.email})? This cannot be undone.${ownerHistory}`,
    )) return;
    try {
      await api<void>(`/api/admin/users/${user.id}`, { method: "DELETE" });
      await load();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "The account could not be deleted.");
    }
  }

  async function assign(vehicle: UnassignedVehicle) {
    const userId = owners[vehicle.id];
    if (!userId) return;
    try {
      await api<UnassignedVehicle>(`/api/admin/vehicles/${vehicle.id}/owner`, {
        method: "PATCH",
        body: JSON.stringify({ userId: Number(userId) }),
      });
      await load();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Vehicle ownership could not be assigned.");
    }
  }

  return (
    <>
      <PageHeader eyebrow="CUSTOMER ACCOUNTS" title="User management" description="Search accounts, manage access, and resolve legacy records safely." />
      <Notice>{error}</Notice>
      <Panel title="Registered users" action={<form className="table-search" onSubmit={(event) => { event.preventDefault(); setSearch(query); }}><Search size={16} /><input placeholder="Search name or email" value={query} onChange={(event) => setQuery(event.target.value)} /><button type="submit">Search</button></form>}>
        {loading ? <Loading /> : users?.items.length ? (
          <div className="table-wrap">
            <table>
              <thead><tr><th>Name</th><th>Contact</th><th>Role</th><th>Created</th><th>Access</th><th></th></tr></thead>
              <tbody>{users.items.map((user) => (
                <tr key={user.id}>
                  <td><strong>{user.fullName}</strong></td><td>{user.email}<small>{user.phone || "No phone on file"}</small></td><td>{user.role}</td>
                  <td>{new Date(user.createdAt).toLocaleDateString()}</td>
                  <td><StatusBadge status={user.enabled ? "ACTIVE" : "DISABLED"} /></td>
                  <td><div className="row-buttons">
                    <Button variant={user.enabled ? "danger" : "secondary"} onClick={() => void toggle(user)}>{user.enabled ? "Deactivate" : "Activate"}</Button>
                    <Button variant="danger" disabled={user.id === currentUser?.id} onClick={() => void removeUser(user)}>Delete</Button>
                  </div></td>
                </tr>
              ))}</tbody>
            </table>
          </div>
        ) : <EmptyState title="No matching users" body="Try a different search term." />}
        {users && <div className="pagination"><span>{users.totalElements} registered accounts</span><span>Page {users.page + 1} of {Math.max(users.totalPages, 1)}</span></div>}
      </Panel>
      {unverifiedOwners.length > 0 && (
        <Panel title="Legacy unverified owner registrations">
          <p className="panel-description">These owner records predate staged email verification and are excluded from registered users and approval queues. Delete one to remove the incomplete record and let that person register again.</p>
          <div className="table-wrap">
            <table>
              <thead><tr><th>Name</th><th>Email</th><th>Created</th><th></th></tr></thead>
              <tbody>{unverifiedOwners.map((owner) => (
                <tr key={owner.id}>
                  <td><strong>{owner.fullName}</strong></td>
                  <td>{owner.email}</td>
                  <td>{new Date(owner.createdAt).toLocaleDateString()}</td>
                  <td><Button variant="danger" onClick={() => void removeUser(owner)}>Delete permanently</Button></td>
                </tr>
              ))}</tbody>
            </table>
          </div>
        </Panel>
      )}
      <Panel title="Unassigned legacy vehicles">
        <p className="panel-description">These existing vehicle records could not be safely matched to an account automatically. Assign an owner only after confirming the record.</p>
        {unassigned.length ? (
          <div className="table-wrap">
            <table>
              <thead><tr><th>Registration</th><th>Legacy owner label</th><th>Vehicle</th><th>Assign account</th></tr></thead>
              <tbody>{unassigned.map((vehicle) => (
                <tr key={vehicle.id}>
                  <td><strong>{vehicle.vehicleNumber}</strong></td><td>{vehicle.legacyOwnerName}</td><td>{[vehicle.brand, vehicle.model, vehicle.vehicleType].filter(Boolean).join(" · ")}</td>
                  <td><div className="assign-controls"><select aria-label={`Choose owner for ${vehicle.vehicleNumber}`} value={owners[vehicle.id] || ""} onChange={(event) => setOwners({ ...owners, [vehicle.id]: event.target.value })}><option value="">Choose account</option>{users?.items.map((user) => <option key={user.id} value={user.id}>{user.fullName} · {user.email}</option>)}</select><Button disabled={!owners[vehicle.id]} onClick={() => void assign(vehicle)}>Assign</Button></div></td>
                </tr>
              ))}</tbody>
            </table>
          </div>
        ) : <EmptyState title="No unassigned vehicles" body="All existing vehicle records have an account owner." />}
      </Panel>
    </>
  );
}

export function AdminAnalyticsPage() {
  const initial = last30Days();
  const [from, setFrom] = useState(initial.from);
  const [to, setTo] = useState(initial.to);
  const [groupBy, setGroupBy] = useState<Analytics["groupBy"]>("DAY");
  const [analytics, setAnalytics] = useState<Analytics | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const load = useCallback(async (event?: FormEvent) => {
    event?.preventDefault();
    setError("");
    setLoading(true);
    try {
      setAnalytics(await api<Analytics>(`/api/admin/analytics?${queryString({ from, to, groupBy })}`));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Analytics could not be loaded.");
    } finally {
      setLoading(false);
    }
  }, [from, to, groupBy]);
  useEffect(() => { void load(); }, [load]);

  const types = useMemo(
    () => Object.entries(analytics?.vehicleTypeDistribution || {}).map(([name, value]) => ({ name, value })),
    [analytics],
  );
  return (
    <>
      <PageHeader eyebrow="PERFORMANCE & TRENDS" title="Analytics" description="Explore booking value, space utilization, and vehicle mix from persisted reservations." />
      <Notice>{error}</Notice>
      <Panel className="analytics-filter-panel">
        <form className="analytics-filter" onSubmit={(event) => void load(event)}>
          <Field label="From" type="date" value={from} onChange={(event) => setFrom(event.target.value)} required />
          <Field label="To (exclusive)" type="date" value={to} onChange={(event) => setTo(event.target.value)} required />
          <SelectField label="Group results by" value={groupBy} onChange={(event) => setGroupBy(event.target.value as Analytics["groupBy"])}>
            <option value="DAY">Day</option><option value="WEEK">Week</option><option value="MONTH">Month</option>
          </SelectField>
          <Button type="submit" disabled={loading}><RefreshCw size={14} /> Update</Button>
        </form>
      </Panel>
      {loading ? <Loading label="Aggregating database analytics…" /> : analytics && (
        <>
          <div className="stats-grid analytics-summary">
            <StatCard label="Active occupancy" value={analytics.currentOccupancy} hint="Spaces in an active session now" icon={CarFront} tone="blue" />
            <StatCard label="Confirmed bookings" value={analytics.confirmedBookings} hint="Across the facility" icon={CalendarDays} />
            <StatCard label="Popular spaces" value={analytics.mostBookedSlots.length} hint="With booking history in range" icon={ParkingSquare} tone="violet" />
            <StatCard label="Vehicle categories" value={types.length} hint="Observed in booking records" icon={UsersRound} tone="green" />
          </div>
          <div className="dashboard-grid">
            <Panel title="Estimated booking value" className="chart-panel">
              {analytics.revenueTrend.length ? <ResponsiveContainer width="100%" height={300}>
                <LineChart data={analytics.revenueTrend}>
                  <CartesianGrid stroke="#272b3c" strokeDasharray="3 3" vertical={false} />
                  <XAxis dataKey="date" stroke="#8288a0" tickLine={false} axisLine={false} tickFormatter={(value: string) => value.slice(5)} />
                  <YAxis stroke="#8288a0" tickLine={false} axisLine={false} />
                  <Tooltip contentStyle={{ background: "#171b2b", border: "1px solid #30354b", borderRadius: 10 }} formatter={(value) => currency(Number(value ?? 0))} />
                  <Line type="monotone" dataKey="amount" stroke="#9a77ff" strokeWidth={3} dot={{ r: 3, fill: "#bca4ff" }} />
                </LineChart>
              </ResponsiveContainer> : <EmptyState title="No bookings for this date range" body="Try a wider range or revisit once bookings are available." />}
              <p className="chart-disclaimer">Calculated booking charges. Simulated platform and owner shares are shown in settlement totals; no real funds move.</p>
            </Panel>
            <Panel title="Vehicle category mix" className="chart-panel">
              {types.length ? <ResponsiveContainer width="100%" height={300}>
                <PieChart>
                  <Pie data={types} dataKey="value" nameKey="name" innerRadius={66} outerRadius={105} paddingAngle={5}>
                    {types.map((item, index) => <Cell key={item.name} fill={chartColors[index % chartColors.length]} />)}
                  </Pie>
                  <Tooltip contentStyle={{ background: "#171b2b", border: "1px solid #30354b", borderRadius: 10 }} />
                </PieChart>
              </ResponsiveContainer> : <EmptyState title="No vehicle data yet" body="Vehicle distribution appears after reservations are made." />}
              <div className="legend-list">{types.map((item, index) => <span key={item.name}><i style={{ backgroundColor: chartColors[index % chartColors.length] }} />{item.name}<strong>{item.value}</strong></span>)}</div>
            </Panel>
          </div>
          <Panel title="Most frequently booked spaces">
            {analytics.mostBookedSlots.length ? (
              <div className="popular-slot-list">{analytics.mostBookedSlots.map((slot, index) => (
                <div className="popular-slot" key={slot.slotId}><span className="rank">0{index + 1}</span><div><strong>Space {slot.slotNumber}</strong><span>Booking activity in selected range</span></div><strong>{slot.bookings} bookings</strong><ArrowUpRight size={15} /></div>
              ))}</div>
            ) : <EmptyState title="No popular spaces yet" body="This report will populate as bookings are stored." />}
          </Panel>
        </>
      )}
    </>
  );
}

export function AdminBookingDetailsPage() {
  const { bookingId } = useParams();
  const [booking, setBooking] = useState<Booking | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    if (!bookingId) return;
    setError("");
    try {
      setBooking(await api<Booking>(`/api/admin/bookings/${bookingId}`));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Booking details could not be loaded.");
    } finally {
      setLoading(false);
    }
  }, [bookingId]);
  useEffect(() => { void load(); }, [load]);

  async function transition(action: "start" | "complete" | "cancel") {
    if (!booking) return;
    const prompts = {
      start: "Start this parking session?",
      complete: "Mark this booking completed?",
      cancel: "Cancel this booking?",
    };
    if (!window.confirm(prompts[action])) return;
    setError("");
    try {
      setBooking(await api<Booking>(`/api/admin/bookings/${booking.id}/${action}`, { method: "POST" }));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Booking status could not be updated.");
    }
  }

  if (loading) return <Loading label="Loading booking record…" />;
  return (
    <>
      <PageHeader eyebrow="RESERVATION CONTROL" title={booking ? `Booking ${booking.bookingReference.slice(0, 8).toUpperCase()}` : "Booking details"} description="Inspect the saved reservation details and perform eligible operations." action={<Link className="button button-secondary" to="/admin/bookings">Back to bookings</Link>} />
      <Notice>{error}</Notice>
      {booking && (
        <div className="content-split">
          <Panel title="Reservation record">
            <dl className="detail-list">
              <div><dt>Reference</dt><dd>{booking.bookingReference}</dd></div>
              <div><dt>Status</dt><dd><StatusBadge status={booking.status} /></dd></div>
              <div><dt>Customer ID</dt><dd>{booking.userId}</dd></div>
              <div><dt>Vehicle</dt><dd>{booking.vehicleNumber}</dd></div>
              <div><dt>Parking space</dt><dd>{booking.slotNumber}</dd></div>
              <div><dt>Arrival</dt><dd>{new Date(booking.startTime).toLocaleString()}</dd></div>
              <div><dt>Departure</dt><dd>{new Date(booking.endTime).toLocaleString()}</dd></div>
              <div><dt>Hourly rate snapshot</dt><dd>{currency(booking.hourlyRate)} / hour</dd></div>
              <div><dt>Saved charge</dt><dd>{currency(booking.amount)}</dd></div>
              {booking.actualEntryTime && <div><dt>Actual entry</dt><dd>{new Date(booking.actualEntryTime).toLocaleString()}</dd></div>}
              {booking.actualExitTime && <div><dt>Actual exit</dt><dd>{new Date(booking.actualExitTime).toLocaleString()}</dd></div>}
              {booking.lateFeeAmount > 0 && <div><dt>Late fee ({booking.lateHours} hours)</dt><dd>{currency(booking.lateFeeAmount)}</dd></div>}
            </dl>
          </Panel>
          <Panel title="Booking operations">
            <p className="panel-description">Updates are subject to the server’s booking lifecycle rules.</p>
            <div className="row-buttons">
              {booking.status === "CONFIRMED" && <>
                <Button variant="secondary" onClick={() => void transition("start")}>Start session</Button>
                <Button variant="danger" onClick={() => void transition("cancel")}>Cancel booking</Button>
              </>}
              {(booking.status === "ACTIVE" || booking.status === "OVERDUE") && <Button variant="secondary" onClick={() => void transition("complete")}>Complete session</Button>}
              {booking.status === "COMPLETED" && <p className="panel-description">This booking is complete.</p>}
              {booking.status === "CANCELLED" && <p className="panel-description">This booking was cancelled.</p>}
            </div>
          </Panel>
        </div>
      )}
    </>
  );
}

function csvCell(value: string | number) {
  return `"${String(value).replaceAll('"', '""')}"`;
}

export function AdminReportsPage() {
  const initial = last30Days();
  const [from, setFrom] = useState(initial.from);
  const [to, setTo] = useState(initial.to);
  const [groupBy, setGroupBy] = useState<Analytics["groupBy"]>("DAY");
  const [analytics, setAnalytics] = useState<Analytics | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const load = useCallback(async (event?: FormEvent) => {
    event?.preventDefault();
    setError("");
    setLoading(true);
    try {
      setAnalytics(await api<Analytics>(`/api/admin/analytics?${queryString({ from, to, groupBy })}`));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "The report could not be loaded.");
    } finally {
      setLoading(false);
    }
  }, [from, to, groupBy]);
  useEffect(() => { void load(); }, [load]);

  function downloadCsv() {
    if (!analytics) return;
    const rows = [
      ["Report", "Period", "Group", "Metric", "Value", "Amount"],
      ...analytics.revenueTrend.map((point) => ["Booking value", point.date, groupBy, "Bookings", point.bookings, point.amount]),
      ...analytics.mostBookedSlots.map((slot) => ["Popular spaces", `${from} to ${to}`, groupBy, slot.slotNumber, slot.bookings, ""]),
    ];
    const content = rows.map((row) => row.map(csvCell).join(",")).join("\r\n");
    const objectUrl = URL.createObjectURL(new Blob([content], { type: "text/csv;charset=utf-8" }));
    const anchor = document.createElement("a");
    anchor.href = objectUrl;
    anchor.download = `smartpark-report-${from}-to-${to}.csv`;
    anchor.click();
    URL.revokeObjectURL(objectUrl);
  }

  return (
    <>
      <PageHeader eyebrow="OPERATIONS REPORTING" title="Reports" description="Review persisted booking activity and download a CSV summary for the selected period." action={<Button variant="secondary" disabled={!analytics} onClick={downloadCsv}><Download size={15} /> Export CSV</Button>} />
      <Notice>{error}</Notice>
      <Panel className="analytics-filter-panel">
        <form className="analytics-filter" onSubmit={(event) => void load(event)}>
          <Field label="From" type="date" value={from} onChange={(event) => setFrom(event.target.value)} required />
          <Field label="To (exclusive)" type="date" value={to} onChange={(event) => setTo(event.target.value)} required />
          <SelectField label="Group results by" value={groupBy} onChange={(event) => setGroupBy(event.target.value as Analytics["groupBy"])}>
            <option value="DAY">Day</option><option value="WEEK">Week</option><option value="MONTH">Month</option>
          </SelectField>
          <Button type="submit" disabled={loading}><RefreshCw size={14} /> Generate report</Button>
        </form>
      </Panel>
      {loading ? <Loading label="Generating report from booking records…" /> : analytics && (
        <>
          <div className="stats-grid analytics-summary">
            <StatCard label="Active occupancy" value={analytics.currentOccupancy} hint="Spaces in active sessions now" icon={CarFront} tone="blue" />
            <StatCard label="Confirmed bookings" value={analytics.confirmedBookings} hint="Current confirmed reservations" icon={CalendarDays} />
            <StatCard label="Spaces in report" value={analytics.mostBookedSlots.length} hint="With booking activity in range" icon={ParkingSquare} />
            <StatCard label="Vehicle categories" value={Object.keys(analytics.vehicleTypeDistribution).length} hint="Observed in records" icon={UsersRound} tone="green" />
          </div>
          <Panel title="Booking activity report">
            {analytics.revenueTrend.length ? (
              <div className="table-wrap">
                <table>
                  <thead><tr><th>Period</th><th>Bookings</th><th>Estimated charge value</th></tr></thead>
                  <tbody>{analytics.revenueTrend.map((point) => <tr key={point.date}><td>{point.date}</td><td>{point.bookings}</td><td>{currency(point.amount)}</td></tr>)}</tbody>
                </table>
              </div>
            ) : <EmptyState title="No activity in this period" body="Choose a wider period to include booking history." />}
            <p className="chart-disclaimer">Amounts are saved booking estimates, not collected payments. CSV export contains this activity and popular-space summary.</p>
          </Panel>
          <Panel title="Most booked spaces">
            {analytics.mostBookedSlots.length ? (
              <div className="table-wrap">
                <table><thead><tr><th>Space</th><th>Bookings in period</th></tr></thead>
                  <tbody>{analytics.mostBookedSlots.map((slot) => <tr key={slot.slotId}><td>{slot.slotNumber}</td><td>{slot.bookings}</td></tr>)}</tbody>
                </table>
              </div>
            ) : <EmptyState title="No space activity to report" />}
          </Panel>
        </>
      )}
    </>
  );
}

export function AdminSettingsPage() {
  const { user, updateUser } = useAuth();
  const [profile, setProfile] = useState({ fullName: user?.fullName || "", phone: user?.phone || "" });
  const [passwords, setPasswords] = useState({ currentPassword: "", newPassword: "" });
  const [settings, setSettings] = useState<AdminSettings | null>(null);
  const [ownerAccounts, setOwnerAccounts] = useState<AdminUser[]>([]);
  const [vehicleTypesText, setVehicleTypesText] = useState("");
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  const loadSettings = useCallback(async () => {
    setError("");
    try {
      const [current, accountPage, pendingApprovals] = await Promise.all([
        api<AdminSettings>("/api/admin/settings"),
        api<PageResponse<AdminUser>>("/api/admin/users?size=100"),
        api<OwnerApproval[]>("/api/admin/owner-approvals"),
      ]);
      setSettings(current);
      setVehicleTypesText(current.vehicleTypes.join("\n"));
      const pendingOwnerIds = new Set(pendingApprovals.map(({ owner }) => owner.ownerUserId));
      setOwnerAccounts(accountPage.items.filter((account) =>
        account.role === "PARKING_OWNER" && account.enabled && !pendingOwnerIds.has(account.id)
      ));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "System settings could not be loaded.");
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => { void loadSettings(); }, [loadSettings]);

  async function saveSettings(event: FormEvent) {
    event.preventDefault();
    if (!settings) return;
    setError("");
    setNotice("");
    try {
      const updated = await api<AdminSettings>("/api/admin/settings", {
        method: "PUT",
        body: JSON.stringify({
          ...settings,
          vehicleTypes: vehicleTypesText.split("\n").map((type) => type.trim()).filter(Boolean),
        }),
      });
      setSettings(updated);
      setVehicleTypesText(updated.vehicleTypes.join("\n"));
      setNotice("Facility and operating policies saved.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "System settings could not be saved.");
    }
  }

  async function saveProfile(event: FormEvent) {
    event.preventDefault();
    setNotice("");
    setError("");
    try {
      const updated = await api<User>("/api/users/me", { method: "PATCH", body: JSON.stringify(profile) });
      updateUser(updated);
      setNotice("Administrator profile updated.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Profile settings could not be saved.");
    }
  }

  async function changePassword(event: FormEvent) {
    event.preventDefault();
    setNotice("");
    setError("");
    try {
      await api<void>("/api/users/me/password", { method: "PUT", body: JSON.stringify(passwords) });
      setPasswords({ currentPassword: "", newPassword: "" });
      setNotice("Password updated successfully.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Password could not be changed.");
    }
  }

  return (
    <>
      <PageHeader eyebrow="WORKSPACE PREFERENCES" title="Admin settings" description="Manage your administrator profile and secure your account." />
      <Notice>{error}</Notice>
      <Notice tone="success">{notice}</Notice>
      {loading ? <Loading label="Loading facility configuration…" /> : settings && (
        <Panel title="Facility profile and operating policy">
          <form className="form-grid" onSubmit={(event) => void saveSettings(event)}>
            <Field label="Facility name" value={settings.facilityName} onChange={(event) => setSettings({ ...settings, facilityName: event.target.value })} required maxLength={160} />
            <Field label="Facility address" value={settings.address} onChange={(event) => setSettings({ ...settings, address: event.target.value })} required maxLength={500} />
            <Field label="Total parking capacity" type="number" min="0" value={settings.totalCapacity} onChange={(event) => setSettings({ ...settings, totalCapacity: Number(event.target.value) })} required />
            <Field label="Available operational spaces (live)" type="number" value={settings.availableSlots} readOnly />
            <Field label="Operating hours start" type="time" value={settings.operatingStart} onChange={(event) => setSettings({ ...settings, operatingStart: event.target.value })} required />
            <Field label="Operating hours end" type="time" value={settings.operatingEnd} onChange={(event) => setSettings({ ...settings, operatingEnd: event.target.value })} required />
            <SelectField label="Assigned parking owner" value={settings.ownerUserId ?? ""} onChange={(event) => setSettings({ ...settings, ownerUserId: event.target.value ? Number(event.target.value) : null })}>
              <option value="">No owner assigned</option>
              {ownerAccounts.map((account) => <option key={account.id} value={account.id}>{account.fullName} · {account.email}</option>)}
            </SelectField>
            <Field label="Billing currency" value="INR (₹)" readOnly />
            <Field label="Cancellation cutoff before start (minutes)" type="number" min="0" value={settings.cancellationCutoffMinutes} onChange={(event) => setSettings({ ...settings, cancellationCutoffMinutes: Number(event.target.value) })} required />
            <Field label="Refund percentage for paid cancellations" type="number" min="0" max="100" step="0.01" value={settings.refundPercentage} onChange={(event) => setSettings({ ...settings, refundPercentage: Number(event.target.value) })} required />
            <label className="setting-toggle"><input type="checkbox" checked={settings.cancellationEnabled} onChange={(event) => setSettings({ ...settings, cancellationEnabled: event.target.checked })} /><span>Allow cancellation before the configured cutoff</span></label>
            <label className="setting-toggle"><input type="checkbox" checked={settings.ownerRegistrationEnabled} onChange={(event) => setSettings({ ...settings, ownerRegistrationEnabled: event.target.checked })} /><span>Allow parking-owner account registration</span></label>
            <label className="field vehicle-types-field"><span>Supported vehicle types (one per line)</span><textarea rows={4} value={vehicleTypesText} onChange={(event) => setVehicleTypesText(event.target.value)} required /></label>
            <div className="form-actions"><Button type="submit">Save system settings</Button></div>
          </form>
          <p className="panel-description">Default policy: full refund for an eligible paid cancellation before the session starts. Razorpay credentials stay in server environment variables.</p>
        </Panel>
      )}
      <div className="content-split">
        <Panel title="Administrator profile">
          <form className="auth-form" onSubmit={(event) => void saveProfile(event)}>
            <Field label="Full name" value={profile.fullName} onChange={(event) => setProfile({ ...profile, fullName: event.target.value })} required maxLength={255} />
            <Field label="Email address" value={user?.email || ""} readOnly />
            <Field label="Phone number" type="tel" value={profile.phone} onChange={(event) => setProfile({ ...profile, phone: event.target.value })} />
            <Button type="submit">Save profile</Button>
          </form>
        </Panel>
        <Panel title="Security">
          <form className="auth-form" onSubmit={(event) => void changePassword(event)}>
            <Field label="Current password" type="password" autoComplete="current-password" value={passwords.currentPassword} onChange={(event) => setPasswords({ ...passwords, currentPassword: event.target.value })} required />
            <Field label="New password" type="password" autoComplete="new-password" value={passwords.newPassword} onChange={(event) => setPasswords({ ...passwords, newPassword: event.target.value })} required minLength={6} pattern="(?=.*[A-Z])(?=.*[0-9]).{6,}" />
            <Button type="submit" variant="secondary">Update password</Button>
          </form>
          <p className="panel-description">Admin permissions are assigned by the backend and cannot be changed from this page.</p>
        </Panel>
      </div>
    </>
  );
}
