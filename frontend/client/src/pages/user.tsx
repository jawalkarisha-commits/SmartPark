import {
  ArrowRight,
  CalendarClock,
  CarFront,
  Clock3,
  MapPin,
  Plus,
  RefreshCw,
  TicketCheck,
  Trash2,
  Wallet,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { api, currency, queryString, toLocalDateTimeValue } from "../api";
import { useAuth } from "../auth";
import { AvailableFacilitiesMap } from "../FacilityMap";
import { bookingPaymentStatus } from "../types";
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
import type { BillingPeriod, Booking, BookingGroup, ChargeEstimate, GroupChargeEstimate, ParkingSlot, User, Vehicle } from "../types";

function defaultWindow() {
  const start = new Date(Date.now() + 60 * 60 * 1000);
  start.setSeconds(0, 0);
  const end = new Date(start.getTime() + 60 * 60 * 1000);
  return { start: toLocalDateTimeValue(start), end: toLocalDateTimeValue(end) };
}

function dateLabel(value: string) {
  return new Date(value).toLocaleString(undefined, {
    month: "short", day: "numeric", year: "numeric", hour: "numeric", minute: "2-digit",
  });
}

type BookingAction = "deposit" | "parked" | "depart" | "settle";

const ARRIVAL_WINDOW_MS = 30 * 60_000;

function isParkingWindowOpen(booking: Booking, now: number) {
  const startTime = Date.parse(booking.startTime);
  return booking.status === "CONFIRMED"
    && now >= startTime
    && now <= startTime + ARRIVAL_WINDOW_MS;
}

function bookingActionsFor(booking: Booking, now: number): BookingAction[] {
  if (booking.status === "PENDING_PAYMENT") return ["deposit"];
  if (booking.status === "CONFIRMED") {
    const startTime = Date.parse(booking.startTime);
    if (now >= startTime - ARRIVAL_WINDOW_MS && now <= startTime + ARRIVAL_WINDOW_MS) {
      return ["parked"];
    }
  }
  if (booking.status === "ACTIVE" || booking.status === "OVERDUE") return ["depart"];
  if (booking.status === "COMPLETED" && booking.balanceDue > 0) return ["settle"];
  return [];
}

function BookingActionButtons({
  booking,
  actions,
  now,
  busy,
  onAction,
}: {
  booking: Booking;
  actions: BookingAction[];
  now: number;
  busy: boolean;
  onAction: (action: BookingAction) => void;
}) {
  if (!actions.length) return null;
  return (
    <div className="booking-actions" role="group" aria-label={`Actions for booking ${booking.bookingReference}`}>
      {actions.map((action) => (
        <div className="booking-action-item" key={action}>
          <Button
            variant={action === "parked" || action === "depart" ? "secondary" : "primary"}
            className="button-small"
            disabled={busy || (action === "parked" && !isParkingWindowOpen(booking, now))}
            onClick={() => onAction(action)}
          >
            {busy
              ? action === "parked" ? "Confirming…" : action === "depart" ? "Completing booking…" : "Recording…"
              : action === "deposit"
                ? `Pay deposit · ${currency(booking.depositAmount)}`
                : action === "parked"
                  ? "Vehicle is parked"
                  : action === "depart"
                    ? "I have left the space"
                    : `Pay balance · ${currency(booking.balanceDue)}`}
          </Button>
          {action === "parked" && now < Date.parse(booking.startTime) && (
            <small className="booking-action-hint">Available at scheduled start</small>
          )}
        </div>
      ))}
    </div>
  );
}

export function UserDashboardPage() {
  const { user } = useAuth();
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [slots, setSlots] = useState<ParkingSlot[]>([]);
  const [error, setError] = useState("");
  const [bookingNotice, setBookingNotice] = useState("");
  const [paymentBusy, setPaymentBusy] = useState(false);
  const [actionBusy, setActionBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [clock, setClock] = useState(Date.now());

  useEffect(() => {
    const timer = window.setInterval(() => setClock(Date.now()), 30_000);
    return () => window.clearInterval(timer);
  }, []);

  const load = useCallback(async () => {
    setError("");
    try {
      const window = defaultWindow();
      const [bookingData, vehicleData, available] = await Promise.all([
        api<Booking[]>("/api/bookings/my"),
        api<Vehicle[]>("/api/vehicles"),
        api<ParkingSlot[]>(`/api/bookings/availability?${queryString({
          startTime: window.start, endTime: window.end,
        })}`),
      ]);
      setBookings(bookingData);
      setVehicles(vehicleData);
      setSlots(available);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Dashboard data could not be loaded.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);
  const active = bookings.filter(
    (booking) => booking.status === "ACTIVE" || booking.status === "OVERDUE",
  ).length;
  const upcoming = bookings.filter(
    (booking) => booking.status === "CONFIRMED" && new Date(booking.startTime) > new Date(),
  );
  const recent = [...bookings].slice(0, 4);

  async function pay(booking: Booking) {
    setPaymentBusy(true);
    setError("");
    setBookingNotice("");
    try {
      if (booking.status === "PENDING_PAYMENT") {
        if (booking.bookingGroupId) {
          const group = await api<BookingGroup>(
            `/api/bookings/groups/${booking.bookingGroupId}/deposit`,
            { method: "POST" },
          );
          setBookings((current) => current.map((item) =>
            group.bookings.find((updated) => updated.id === item.id) || item,
          ));
        } else {
          const updated = await api<Booking>(`/api/bookings/${booking.id}/deposit`, {
            method: "POST",
          });
          setBookings((current) => current.map((item) =>
            item.id === updated.id ? updated : item,
          ));
        }
        setBookingNotice("Demo deposit recorded. No payment provider was opened.");
      } else {
        const updated = await api<Booking>(`/api/bookings/${booking.id}/settle`, {
          method: "POST",
        });
        setBookings((current) => current.map((item) =>
          item.id === updated.id ? updated : item,
        ));
        setBookingNotice("Demo payment recorded. No payment provider was opened.");
      }
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "The payment could not be recorded.");
    } finally {
      setPaymentBusy(false);
    }
  }

  async function updateBookingStatus(booking: Booking, action: "parked" | "depart") {
    setActionBusy(true);
    setError("");
    setBookingNotice("");
    try {
      const updated = await api<Booking>(`/api/bookings/${booking.id}/${action}`, {
        method: "POST",
      });
      setBookings((current) => current.map((item) => item.id === updated.id ? updated : item));
      setBookingNotice(action === "parked"
        ? "Parking confirmed. Your booking time is based on the scheduled start."
        : "Thank you for parking with SmartPark. Your space is available for other drivers.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "The booking could not be updated.");
    } finally {
      setActionBusy(false);
    }
  }

  if (loading) return <Loading />;
  return (
    <>
      <PageHeader
        eyebrow="YOUR PARKING, AT A GLANCE"
        title={`Good to see you, ${user?.fullName.split(" ")[0] || "there"}.`}
        description="Here’s what’s happening with your parking."
        action={<Button variant="secondary" onClick={() => void load()}><RefreshCw size={15} /> Refresh</Button>}
      />
      {error && <Notice>{error}</Notice>}
      <Notice tone="success">{bookingNotice}</Notice>
      <div className="stats-grid">
        <StatCard label="Total bookings" value={bookings.length} hint="Across your account" icon={TicketCheck} />
        <StatCard label="Active now" value={active} hint="Currently in progress" icon={Clock3} tone="green" />
        <StatCard label="Upcoming" value={upcoming.length} hint="Confirmed reservations" icon={CalendarClock} tone="blue" />
        <StatCard label="Vehicles" value={vehicles.length} hint="Registered to you" icon={CarFront} tone="amber" />
      </div>
      <div className="dashboard-grid">
        <Panel title="Upcoming reservations" action={<Link className="panel-link" to="/app/bookings">All bookings <ArrowRight size={14} /></Link>}>
          {upcoming.length ? (
            <div className="booking-list">
              {upcoming.slice(0, 3).map((booking) => (
                <BookingRow key={booking.id} booking={booking} />
              ))}
            </div>
          ) : (
            <EmptyState title="Nothing booked yet" body="Find a space that works for your schedule." action={<Link className="button button-primary button-small" to="/app/find">Find parking <ArrowRight size={14} /></Link>} />
          )}
        </Panel>
        <Panel title="Quick search" className="quick-search-card">
          <div className="quick-search-icon"><MapPin size={20} /></div>
          <h3>A spot for your next trip</h3>
          <p>Search availability by time and vehicle, then get a price estimate before you book.</p>
          <div className="quick-search-meta"><span><span className="live-dot" /> Live availability</span><span>{slots.length} spaces in the next hour window</span></div>
          <Link className="button button-secondary" to="/app/find">Explore parking <ArrowRight size={15} /></Link>
        </Panel>
      </div>
      <Panel title="Recent activity" className="recent-panel">
        {recent.length ? (
          <div className="table-wrap">
            <table>
              <thead><tr><th>Reservation</th><th>Parking</th><th>Schedule</th><th>Amount</th><th>Status</th><th>Payment</th><th>Actions</th></tr></thead>
              <tbody>{recent.map((booking) => {
                const actions = bookingActionsFor(booking, clock);
                return (
                  <tr key={booking.id}>
                    <td><strong>#{booking.bookingReference.slice(0, 8).toUpperCase()}</strong></td>
                    <td>Space {booking.slotNumber}<small>{booking.vehicleNumber}</small></td>
                    <td>{dateLabel(booking.startTime)}</td>
                    <td>{currency(booking.finalAmount)}</td>
                    <td><StatusBadge status={booking.status} /></td>
                    <td><StatusBadge status={bookingPaymentStatus(booking)} /></td>
                    <td>
                      <BookingActionButtons
                        booking={booking}
                        actions={actions}
                        now={clock}
                        busy={paymentBusy || actionBusy}
                        onAction={(action) => {
                          if (action === "deposit" || action === "settle") void pay(booking);
                          else void updateBookingStatus(booking, action);
                        }}
                      />
                    </td>
                  </tr>
                );
              })}</tbody>
            </table>
          </div>
        ) : <EmptyState title="Your activity will appear here" body="Confirmed bookings are saved here for easy reference." />}
      </Panel>
    </>
  );
}

export function FindParkingPage() {
  const [time, setTime] = useState(defaultWindow);
  const [vehicleType, setVehicleType] = useState("");
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [slots, setSlots] = useState<ParkingSlot[]>([]);
  const [selected, setSelected] = useState<ParkingSlot | null>(null);
  const [vehicleId, setVehicleId] = useState("");
  const [groupMode, setGroupMode] = useState(false);
  const [groupVehicleIds, setGroupVehicleIds] = useState<number[]>([]);
  const [groupEstimate, setGroupEstimate] = useState<GroupChargeEstimate | null>(null);
  const [billingPeriod, setBillingPeriod] = useState<BillingPeriod>("HOURLY");
  const [estimate, setEstimate] = useState<ChargeEstimate | null>(null);
  const [confirmation, setConfirmation] = useState<Booking | null>(null);
  const [groupConfirmation, setGroupConfirmation] = useState<BookingGroup | null>(null);
  const [estimateLoading, setEstimateLoading] = useState(false);
  const [estimateError, setEstimateError] = useState("");
  const [nearby, setNearby] = useState({ latitude: "", longitude: "", radiusKm: "10" });
  const [locationQuery, setLocationQuery] = useState("");
  const [locationResults, setLocationResults] = useState<{ label: string; latitude: number; longitude: number }[]>([]);
  const [locationSearchBusy, setLocationSearchBusy] = useState(false);
  const [selectedLocationLabel, setSelectedLocationLabel] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [paymentBusy, setPaymentBusy] = useState(false);
  const [paymentNotice, setPaymentNotice] = useState("");
  const [clock, setClock] = useState(Date.now());
  const activeHold = groupConfirmation?.bookings[0] ?? confirmation;

  useEffect(() => {
    if (activeHold?.status !== "PENDING_PAYMENT") return;
    const timer = window.setInterval(() => setClock(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [activeHold?.status]);

  useEffect(() => {
    api<Vehicle[]>("/api/vehicles")
      .then((items) => {
        setVehicles(items);
        if (items.length) setVehicleId(String(items[0].id));
      })
      .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "Vehicles could not be loaded."));
  }, []);

  const search = useCallback(async (event?: FormEvent) => {
    event?.preventDefault();
    setError("");
    if ((nearby.latitude === "") !== (nearby.longitude === "")) {
      setError("Enter both latitude and longitude, or use the location button.");
      return;
    }
    if (nearby.latitude && nearby.longitude) {
      const latitude = Number(nearby.latitude);
      const longitude = Number(nearby.longitude);
      const radius = Number(nearby.radiusKm);
      if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90
          || !Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
        setError("Latitude must be between -90 and 90, and longitude between -180 and 180.");
        return;
      }
      if (!Number.isFinite(radius) || radius <= 0 || radius > 200) {
        setError("Search radius must be greater than 0 and no more than 200 km.");
        return;
      }
    }
    setLoading(true);
    try {
      const found = await api<ParkingSlot[]>(`/api/bookings/availability?${queryString({
        startTime: time.start, endTime: time.end, vehicleType,
        ...(locationQuery.trim()
          ? { locationQuery: locationQuery.trim() }
          : {}),
        ...(nearby.latitude && nearby.longitude
          ? {
              latitude: Number(nearby.latitude),
              longitude: Number(nearby.longitude),
              radiusKm: Number(nearby.radiusKm),
            }
          : {}),
      })}`);
      setSlots(found);
      setConfirmation(null);
      setGroupConfirmation(null);
      setPaymentNotice("");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Availability could not be checked.");
    } finally {
      setLoading(false);
    }
  }, [time, vehicleType, nearby, locationQuery]);

  async function searchLocation() {
    const query = locationQuery.trim();
    if (query.length < 3) {
      setError("Enter at least 3 characters to search for a place.");
      return;
    }
    setLocationSearchBusy(true);
    setError("");
    try {
      const results = await api<{ label: string; latitude: number; longitude: number }[]>(
        `/api/locations/search?${queryString({ query })}`,
      );
      setLocationResults(results);
      if (!results.length) setError("No matching places were found.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "The place could not be searched.");
    } finally {
      setLocationSearchBusy(false);
    }
  }

  useEffect(() => {
    if (!selected) {
      setEstimate(null);
      setGroupEstimate(null);
      setEstimateLoading(false);
      setEstimateError("");
      return;
    }
    let active = true;
    if (groupMode) {
      setEstimate(null);
      if (groupVehicleIds.length < 2) {
        setGroupEstimate(null);
        setEstimateLoading(false);
        setEstimateError(
          groupVehicleIds.length === 1
            ? "Select at least one more vehicle, or turn off multiple-vehicle booking to reserve a single space."
            : "Select at least two vehicles, or turn off multiple-vehicle booking to reserve a single space.",
        );
        return () => { active = false; };
      }
      setGroupEstimate(null);
      setEstimateLoading(true);
      setEstimateError("");
      const params = new URLSearchParams({
        facilityId: String(selected.facilityId),
        startTime: time.start,
        endTime: time.end,
        billingPeriod,
      });
      groupVehicleIds.forEach((id) => params.append("vehicleIds", String(id)));
      api<GroupChargeEstimate>(`/api/bookings/estimate/group?${params}`)
        .then((result) => {
          if (active) setGroupEstimate(result);
        })
        .catch((reason: unknown) => {
          if (active) {
            setGroupEstimate(null);
            setEstimateError(reason instanceof Error ? reason.message : "Group estimate could not be loaded.");
          }
        })
        .finally(() => {
          if (active) setEstimateLoading(false);
        });
      return () => { active = false; };
    }
    setGroupEstimate(null);
    setEstimate(null);
    setEstimateLoading(true);
    setEstimateError("");
    const query = queryString({
      slotId: selected.id, startTime: time.start, endTime: time.end,
      billingPeriod,
    });
    api<ChargeEstimate>(`/api/bookings/estimate?${query}`)
      .then((result) => {
        if (active) setEstimate(result);
      })
      .catch((reason: unknown) => {
        if (active) {
          setEstimate(null);
          setEstimateError(reason instanceof Error ? reason.message : "Price estimate could not be loaded.");
        }
      })
      .finally(() => {
        if (active) setEstimateLoading(false);
      });
    return () => { active = false; };
  }, [selected, time, billingPeriod, groupMode, groupVehicleIds]);

  async function book(event: FormEvent) {
    event.preventDefault();
    if (!selected || (!groupMode && !vehicleId) || (groupMode && groupVehicleIds.length < 2)) return;
    setError("");
    setSaving(true);
    try {
      if (groupMode) {
        const group = await api<BookingGroup>("/api/bookings/groups", {
          method: "POST",
          body: JSON.stringify({
            facilityId: selected.facilityId,
            vehicleIds: groupVehicleIds,
            startTime: time.start,
            endTime: time.end,
            billingPeriod,
          }),
        });
        setGroupConfirmation(group);
        setConfirmation(null);
        setPaymentNotice("");
        setSelected(null);
        setSlots((current) => current.filter(
          (slot) => !group.bookings.some((booking) => booking.slotId === slot.id),
        ));
        return;
      }
      const booking = await api<Booking>("/api/bookings", {
        method: "POST",
        body: JSON.stringify({
          vehicleId: Number(vehicleId),
          slotId: selected.id,
          startTime: time.start,
          endTime: time.end,
          billingPeriod,
        }),
      });
      setConfirmation(booking);
      setGroupConfirmation(null);
      setPaymentNotice("");
      setSelected(null);
      setSlots((current) => current.filter((slot) => slot.id !== booking.slotId));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "The booking could not be confirmed.");
    } finally {
      setSaving(false);
    }
  }

  async function payForBooking() {
    if (!confirmation && !groupConfirmation) return;
    const isGroup = groupConfirmation !== null;
    setError("");
    setPaymentNotice("");
    setPaymentBusy(true);
    try {
      if (isGroup) {
        const group = await api<BookingGroup>(
          `/api/bookings/groups/${groupConfirmation!.id}/deposit`,
          { method: "POST" },
        );
        setGroupConfirmation(group);
      } else {
        setConfirmation(await api<Booking>(
          `/api/bookings/${confirmation!.id}/deposit`,
          { method: "POST" },
        ));
      }
      setPaymentNotice("The 50% deposit was recorded in demo mode. No payment provider was opened.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "The deposit could not be recorded.");
    } finally {
      setPaymentBusy(false);
    }
  }

  const types = useMemo(() => Array.from(new Set(vehicles.map((vehicle) => vehicle.vehicleType))), [vehicles]);
  const holdExpired = !activeHold?.holdExpiresAt
    || Date.parse(activeHold.holdExpiresAt) <= clock;
  return (
    <>
      <PageHeader eyebrow="DISCOVER YOUR SPACE" title="Find parking" description="Choose a time window and we’ll show eligible spaces and a backend-calculated estimate." />
      <Notice>{error}</Notice>
      {(confirmation || groupConfirmation) && (
        <div className="confirmation-card">
          <span className="confirm-check"><TicketCheck size={23} /></span>
          <div><strong>{activeHold?.status === "CONFIRMED" ? "Reservation confirmed" : "Space held — 50% deposit required"}</strong>
            {groupConfirmation
              ? <p>Group {groupConfirmation.groupReference} · {groupConfirmation.bookings.length} vehicles · {currency(groupConfirmation.totalAmount)} · deposit {currency(groupConfirmation.bookings.reduce((total, item) => total + item.depositAmount, 0))}</p>
              : confirmation && <p>Reference {confirmation.bookingReference} · Space {confirmation.slotNumber} · {currency(confirmation.amount)} · 50% deposit {currency(confirmation.depositAmount)}</p>}
          </div>
          <div className="confirmation-actions">
            {activeHold?.status === "PENDING_PAYMENT" && <Button onClick={() => void payForBooking()} disabled={paymentBusy || holdExpired}>{paymentBusy ? "Recording deposit…" : holdExpired ? "Hold expired" : "Pay 50% deposit (demo)"} <Wallet size={15} /></Button>}
            {activeHold && <Link to={`/app/bookings/${activeHold.id}`} className="text-link">Booking details <ArrowRight size={14} /></Link>}
          </div>
          {groupConfirmation && <ul className="panel-description">{groupConfirmation.bookings.map((item) => <li key={item.id}>{item.vehicleNumber} → Space {item.slotNumber}: {currency(item.amount)}</li>)}</ul>}
          {paymentNotice && <Notice tone="success">{paymentNotice}</Notice>}
        </div>
      )}
      <Panel title="Your parking schedule">
        <form className="search-form" onSubmit={(event) => void search(event)}>
          <Field label="Start date & time" type="datetime-local" value={time.start} onChange={(event) => setTime((current) => ({ ...current, start: event.target.value }))} required />
          <Field label="End date & time" type="datetime-local" value={time.end} onChange={(event) => setTime((current) => ({ ...current, end: event.target.value }))} required />
          <SelectField label="Vehicle type" value={vehicleType} onChange={(event) => setVehicleType(event.target.value)}>
            <option value="">All vehicle types</option>
            {types.map((type) => <option key={type} value={type}>{type}</option>)}
          </SelectField>
          <Field label="Latitude (optional)" type="number" min="-90" max="90" step="any" value={nearby.latitude} onChange={(event) => setNearby((current) => ({ ...current, latitude: event.target.value }))} />
          <Field label="Longitude (optional)" type="number" min="-180" max="180" step="any" value={nearby.longitude} onChange={(event) => setNearby((current) => ({ ...current, longitude: event.target.value }))} />
          <Field label="Search radius (km)" type="number" min="0.1" max="200" step="any" value={nearby.radiusKm} onChange={(event) => setNearby((current) => ({ ...current, radiusKm: event.target.value }))} />
          <label className="field location-search-field">
            <span>Search an address or place</span>
            <input
              value={locationQuery}
              onChange={(event) => {
                setLocationQuery(event.target.value);
                setLocationResults([]);
                setSelectedLocationLabel("");
              }}
              maxLength={120}
              placeholder="City, address, or landmark"
            />
          </label>
          <Button type="button" variant="secondary" disabled={locationSearchBusy} onClick={() => void searchLocation()}>
            <MapPin size={15} /> {locationSearchBusy ? "Searching…" : "Find place"}
          </Button>
          <Button type="button" variant="secondary" onClick={() => {
            if (!navigator.geolocation) { setError("Location access is not available in this browser."); return; }
            navigator.geolocation.getCurrentPosition(
              (position) => setNearby((current) => ({
                ...current,
                latitude: position.coords.latitude.toFixed(6),
                longitude: position.coords.longitude.toFixed(6),
              })),
              () => setError("Your location could not be read. You can enter coordinates instead."),
              { timeout: 10000, maximumAge: 60000 },
            );
          }}><MapPin size={15} /> Use my location</Button>
          <Button type="submit" disabled={loading}>{loading ? "Searching…" : "Search spaces"} <ArrowRight size={15} /></Button>
        </form>
        <p className="location-search-disclosure">
          Address searches are sent to <a href="https://nominatim.openstreetmap.org" target="_blank" rel="noreferrer">OpenStreetMap Nominatim</a>. Select a result to set your nearby-search center.
        </p>
        {selectedLocationLabel && <p className="location-selected">Search center: {selectedLocationLabel}</p>}
        {locationResults.length > 0 && (
          <div className="location-search-results" aria-label="Address search results">
            {locationResults.map((result) => (
              <button
                type="button"
                key={`${result.latitude}:${result.longitude}:${result.label}`}
                onClick={() => {
                  setNearby((current) => ({
                    ...current,
                    latitude: String(result.latitude),
                    longitude: String(result.longitude),
                  }));
                  setSelectedLocationLabel(result.label);
                  setLocationResults([]);
                }}
              >
                <MapPin size={15} /><span>{result.label}</span>
              </button>
            ))}
          </div>
        )}
      </Panel>
      <div className="result-heading"><div><h2>Available spaces</h2><p>{loading ? "Checking live availability…" : `${slots.length} spaces match your selected schedule`}</p></div></div>
      {!loading && (
        <Panel title="Explore on map">
          <AvailableFacilitiesMap
            slots={slots}
            searchCenter={nearby.latitude && nearby.longitude
              ? { latitude: Number(nearby.latitude), longitude: Number(nearby.longitude) }
              : null}
            radiusKm={nearby.latitude && nearby.longitude ? Number(nearby.radiusKm) : null}
            onSelect={(slot) => {
              const compatible = vehicles.find((vehicle) =>
                !slot.vehicleType || vehicle.vehicleType.toLowerCase() === slot.vehicleType.toLowerCase(),
              );
              if (compatible) setVehicleId(String(compatible.id));
              setSelected(slot);
            }}
          />
        </Panel>
      )}
      {loading ? <Loading label="Searching available spaces…" /> : slots.length ? (
        <div className="slot-grid">
          {slots.map((slot) => (
            <article key={slot.id} className="slot-card">
              <div className="slot-card-top"><span className="slot-icon"><MapPin size={18} /></span><span className="availability-pill"><span /> Available</span></div>
              <div className="slot-number">Space {slot.slotNumber}</div>
              <div className="slot-location">{slot.location || "Parking facility"}{slot.floorNumber != null ? ` · Floor ${slot.floorNumber}` : ""}{slot.zone ? ` · ${slot.zone}` : ""}</div>
              <div className="slot-location"><strong>{slot.facilityName || "Parking facility"}</strong> · {slot.facilityAddress}</div>
              <div className="slot-card-bottom"><div><strong>{currency(slot.hourlyPrice)}</strong><small>per hour</small></div>              <Button onClick={() => {
                const compatible = vehicles.find((vehicle) =>
                  !slot.vehicleType || vehicle.vehicleType.toLowerCase() === slot.vehicleType.toLowerCase(),
                );
                if (compatible) setVehicleId(String(compatible.id));
                setSelected(slot);
              }}>Choose space <ArrowRight size={14} /></Button></div>
              <Link className="text-link slot-details-link" to={`/app/slots/${slot.id}`}>Space details <ArrowRight size={14} /></Link>
              {slot.vehicleType && <div className="slot-type">{slot.vehicleType} compatible</div>}
              {slot.latitude != null && slot.longitude != null && <a className="text-link slot-details-link" href={`https://www.openstreetmap.org/?mlat=${slot.latitude}&mlon=${slot.longitude}#map=16/${slot.latitude}/${slot.longitude}`} target="_blank" rel="noreferrer">Open map <MapPin size={14} /></a>}
            </article>
          ))}
        </div>
      ) : (
        <EmptyState title="No spaces found for this time" body="Try another time window or vehicle type, then search again." action={<Button variant="secondary" onClick={() => void search()}><RefreshCw size={15} /> Refresh availability</Button>} />
      )}
      {selected && (
        <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setSelected(null); }}>
          <section className="booking-modal" role="dialog" aria-modal="true" aria-labelledby="booking-modal-title">
            <button className="modal-close" aria-label="Close dialog" onClick={() => setSelected(null)}>×</button>
            <div className="eyebrow">REVIEW YOUR RESERVATION</div>
            <h2 id="booking-modal-title">Space {selected.slotNumber}</h2>
            <p className="modal-subtitle">{selected.facilityName} · {selected.location || "Parking facility"} · {dateLabel(time.start)} – {dateLabel(time.end)}</p>
            <label className="field-inline"><input type="checkbox" checked={groupMode} onChange={(event) => {
              setGroupMode(event.target.checked);
              setGroupVehicleIds([]);
            }} /> Reserve spaces for multiple vehicles at this facility</label>
            {!groupMode && !vehicles.some((vehicle) => !selected.vehicleType || vehicle.vehicleType.toLowerCase() === selected.vehicleType.toLowerCase()) ? (
              <EmptyState title="No compatible vehicle found" body="Add a vehicle compatible with this parking space before booking." action={<Link to="/app/vehicles" className="button button-primary">Manage vehicles</Link>} />
            ) : (
              <form className="auth-form" onSubmit={(event) => void book(event)}>
                {groupMode ? (
                  <fieldset className="field">
                    <legend>Choose vehicles (2–10)</legend>
                    {vehicles.map((vehicle) => <label key={vehicle.id} className="field-inline">
                      <input type="checkbox" checked={groupVehicleIds.includes(vehicle.id)} onChange={(event) => setGroupVehicleIds((current) =>
                        event.target.checked
                          ? [...current, vehicle.id]
                          : current.filter((id) => id !== vehicle.id),
                      )} />
                      {vehicle.vehicleNumber} · {vehicle.vehicleType}
                    </label>)}
                  </fieldset>
                ) : <SelectField label="Choose your vehicle" value={vehicleId} onChange={(event) => setVehicleId(event.target.value)} required>
                  {vehicles.filter((vehicle) => !selected.vehicleType || vehicle.vehicleType.toLowerCase() === selected.vehicleType.toLowerCase()).map((vehicle) => <option key={vehicle.id} value={vehicle.id}>{vehicle.vehicleNumber} · {vehicle.vehicleType}</option>)}
                </SelectField>}
                <SelectField label="Billing rate" value={billingPeriod} onChange={(event) => setBillingPeriod(event.target.value as BillingPeriod)}>
                  <option value="HOURLY">Hourly · prorated by minute</option>
                  <option value="DAILY">Daily · prorated by minute</option>
                  <option value="MONTHLY">Monthly · prorated over 30 days</option>
                </SelectField>
                <div className="estimate-box">
                  <div><span>Vehicles</span><strong>{groupMode ? groupEstimate?.vehicles.length ?? "Select vehicles" : 1}</strong></div>
                  <div><span>{billingPeriod.toLowerCase()} rate</span><strong>{estimateLoading ? "Calculating…" : groupMode ? groupEstimate ? `${currency(groupEstimate.totalAmount)} total` : "—" : estimate ? `${currency(estimate.billedRate)} / ${billingPeriod.toLowerCase()}` : "—"}</strong></div>
                  <div className="estimate-total"><span>Estimated charge</span><strong>{groupMode ? groupEstimate ? currency(groupEstimate.totalAmount) : "—" : estimate ? currency(estimate.amount) : "—"}</strong></div>
                  {groupMode && groupEstimate?.vehicles.map((item) => <small key={item.vehicleId}>{item.vehicleNumber} → Space {item.slotNumber}: {currency(item.amount)}</small>)}
                  {estimateError && <small role="alert">{estimateError}</small>}
                  <small>Calculated by SmartPark. The final amount is recomputed and saved by the server when you confirm.</small>
                </div>
                <Button type="submit" disabled={saving || (groupMode ? !groupEstimate : !estimate)}>{saving ? "Confirming…" : groupMode ? "Hold all spaces" : "Hold space"} <ArrowRight size={15} /></Button>
              </form>
            )}
          </section>
        </div>
      )}
    </>
  );
}

export function SlotDetailsPage() {
  const { slotId } = useParams();
  const [slot, setSlot] = useState<ParkingSlot | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!slotId) return;
    api<ParkingSlot>(`/api/parking-slots/${slotId}`)
      .then(setSlot)
      .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "Parking space details could not be loaded."))
      .finally(() => setLoading(false));
  }, [slotId]);

  if (loading) return <Loading label="Loading parking space…" />;
  return (
    <>
      <PageHeader eyebrow="PARKING SPACE" title={slot ? `Space ${slot.slotNumber}` : "Space details"} description="Review location, vehicle compatibility, and the current hourly rate." action={<Link className="button button-secondary" to="/app/find">Back to search</Link>} />
      <Notice>{error}</Notice>
      {slot && (
        <div className="content-split">
          <Panel title="Space information">
            <dl className="detail-list">
              <div><dt>Availability status</dt><dd><StatusBadge status={slot.status} /> {slot.available ? "Available to reserve" : "Not currently available"}</dd></div>
              <div><dt>Location</dt><dd>{slot.location || "Parking facility"}</dd></div>
              <div><dt>Floor</dt><dd>{slot.floorNumber ?? "Not specified"}</dd></div>
              <div><dt>Zone</dt><dd>{slot.zone || "Not specified"}</dd></div>
              <div><dt>Vehicle compatibility</dt><dd>{slot.vehicleType || "All vehicle types"}</dd></div>
              <div><dt>Hourly rate</dt><dd>{currency(slot.hourlyPrice)} / hour</dd></div>
            </dl>
          </Panel>
          <Panel title="Reserve this space">
            <p className="panel-description">Choose your arrival time and vehicle in the parking search. Your final charge is calculated securely by the server.</p>
            <Link className="button button-primary" to="/app/find">Check schedule and book <ArrowRight size={15} /></Link>
          </Panel>
        </div>
      )}
    </>
  );
}

export function VehiclesPage() {
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [vehicleTypes, setVehicleTypes] = useState<string[]>([]);
  const [values, setValues] = useState({ vehicleNumber: "", vehicleType: "", brand: "", model: "", color: "" });
  const [editing, setEditing] = useState<number | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setError("");
    try {
      const [items, supportedTypes] = await Promise.all([
        api<Vehicle[]>("/api/vehicles"),
        api<string[]>("/api/settings/vehicle-types"),
      ]);
      setVehicles(items);
      setVehicleTypes(supportedTypes);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Vehicles could not be loaded.");
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => { void load(); }, [load]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError("");
    try {
      await api<Vehicle>(editing ? `/api/vehicles/${editing}` : "/api/vehicles", {
        method: editing ? "PUT" : "POST",
        body: JSON.stringify(values),
      });
      setValues({ vehicleNumber: "", vehicleType: "", brand: "", model: "", color: "" });
      setEditing(null);
      await load();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Vehicle could not be saved.");
    }
  }

  function edit(vehicle: Vehicle) {
    setEditing(vehicle.id);
    setValues({
      vehicleNumber: vehicle.vehicleNumber,
      vehicleType: vehicle.vehicleType,
      brand: vehicle.brand || "",
      model: vehicle.model || "",
      color: vehicle.color || "",
    });
  }

  async function remove(vehicle: Vehicle) {
    if (!window.confirm(`Remove ${vehicle.vehicleNumber}? Vehicles with booking history are retained.`)) return;
    try {
      await api<void>(`/api/vehicles/${vehicle.id}`, { method: "DELETE" });
      await load();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Vehicle could not be removed.");
    }
  }

  return (
    <>
      <PageHeader eyebrow="YOUR GARAGE" title="My vehicles" description="Manage the vehicles you use for SmartPark reservations." />
      <Notice>{error}</Notice>
      <div className="content-split">
        <Panel title={editing ? "Edit vehicle" : "Add a vehicle"}>
          <form className="form-grid" onSubmit={(event) => void submit(event)}>
            <Field label="Registration number" value={values.vehicleNumber} onChange={(event) => setValues({ ...values, vehicleNumber: event.target.value.toUpperCase() })} required minLength={3} maxLength={20} />
            <SelectField label="Vehicle type" value={values.vehicleType} onChange={(event) => setValues({ ...values, vehicleType: event.target.value })} required>
              <option value="">Select type</option>{vehicleTypes.map((type) => <option key={type}>{type}</option>)}
            </SelectField>
            <Field label="Brand" value={values.brand} onChange={(event) => setValues({ ...values, brand: event.target.value })} maxLength={100} />
            <Field label="Model" value={values.model} onChange={(event) => setValues({ ...values, model: event.target.value })} maxLength={100} />
            <Field label="Color" value={values.color} onChange={(event) => setValues({ ...values, color: event.target.value })} maxLength={80} />
            <div className="form-actions"><Button type="submit">{editing ? "Save changes" : <><Plus size={15} /> Add vehicle</>}</Button>{editing && <Button variant="ghost" type="button" onClick={() => { setEditing(null); setValues({ vehicleNumber: "", vehicleType: "", brand: "", model: "", color: "" }); }}>Cancel</Button>}</div>
          </form>
        </Panel>
        <Panel title="Registered vehicles">
          {loading ? <Loading /> : vehicles.length ? (
            <div className="vehicle-list">
              {vehicles.map((vehicle) => (
                <article className="vehicle-row" key={vehicle.id}>
                  <span className="vehicle-icon"><CarFront size={21} /></span>
                  <div className="vehicle-copy"><strong>{vehicle.vehicleNumber}</strong><span>{[vehicle.brand, vehicle.model, vehicle.vehicleType, vehicle.color].filter(Boolean).join(" · ")}</span></div>
                  <div className="row-buttons"><Button variant="ghost" onClick={() => edit(vehicle)}>Edit</Button><button className="icon-button danger-icon" aria-label={`Delete ${vehicle.vehicleNumber}`} onClick={() => void remove(vehicle)}><Trash2 size={16} /></button></div>
                </article>
              ))}
            </div>
          ) : <EmptyState title="No vehicles added" body="Add your first vehicle to make a reservation." />}
        </Panel>
      </div>
    </>
  );
}

export function BookingHistoryPage() {
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [filter, setFilter] = useState("ALL");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [actionBusy, setActionBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [clock, setClock] = useState(Date.now());

  useEffect(() => {
    const timer = window.setInterval(() => setClock(Date.now()), 30_000);
    return () => window.clearInterval(timer);
  }, []);

  const load = useCallback(async () => {
    setError("");
    try {
      setBookings(await api<Booking[]>("/api/bookings/my"));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Bookings could not be loaded.");
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => { void load(); }, [load]);

  const visible = filter === "ALL" ? bookings : bookings.filter((booking) => booking.status === filter);

  async function runBookingAction(booking: Booking, action: BookingAction) {
    setActionBusy(true);
    setError("");
    setNotice("");
    try {
      if (action === "deposit" && booking.bookingGroupId) {
        const group = await api<BookingGroup>(
          `/api/bookings/groups/${booking.bookingGroupId}/deposit`,
          { method: "POST" },
        );
        setBookings((current) => current.map((item) =>
          group.bookings.find((updated) => updated.id === item.id) || item,
        ));
      } else {
        const updated = await api<Booking>(`/api/bookings/${booking.id}/${action}`, {
          method: "POST",
        });
        setBookings((current) => current.map((item) => item.id === updated.id ? updated : item));
      }
      setNotice(action === "parked"
        ? "Parking confirmed. Your booking time is based on the scheduled start."
        : action === "depart"
          ? "Thank you for parking with SmartPark. Your space is available for other drivers."
          : action === "deposit"
            ? "Demo deposit recorded. No payment provider was opened."
            : "Demo payment recorded. No payment provider was opened.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "The booking could not be updated.");
    } finally {
      setActionBusy(false);
    }
  }

  async function cancel(booking: Booking) {
    if (!window.confirm(booking.bookingGroupId
      ? `Cancel all vehicles in booking group ${booking.bookingGroupId}?`
      : `Cancel reservation ${booking.bookingReference}?`)) return;
    setError("");
    try {
      if (booking.bookingGroupId) {
        await api<BookingGroup>(`/api/bookings/groups/${booking.bookingGroupId}/cancel`, { method: "POST" });
      } else {
        await api<Booking>(`/api/bookings/${booking.id}/cancel`, { method: "POST" });
      }
      await load();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "This booking could not be cancelled.");
    }
  }

  return (
    <>
      <PageHeader eyebrow="YOUR RESERVATIONS" title="My bookings" description="Review upcoming plans and your parking history." action={<Link className="button button-primary" to="/app/find">Book a space <ArrowRight size={15} /></Link>} />
      <Notice>{error}</Notice>
      <Notice tone="success">{notice}</Notice>
      <div className="filter-bar">
        {["ALL", "PENDING_PAYMENT", "CONFIRMED", "ACTIVE", "OVERDUE", "COMPLETED", "CANCELLED", "EXPIRED"].map((item) => (
          <button key={item} className={filter === item ? "filter-active" : ""} onClick={() => setFilter(item)}>{item === "ALL" ? "All bookings" : item}</button>
        ))}
      </div>
      {loading ? <Loading /> : visible.length ? (
        <div className="booking-card-list">
          {visible.map((booking) => {
            const actions = bookingActionsFor(booking, clock);
            const canCancel = (booking.status === "CONFIRMED" || booking.status === "PENDING_PAYMENT")
              && new Date(booking.startTime) > new Date();
            return (
              <article className="booking-card" key={booking.id}>
                <div className="booking-card-main">
                  <div className="booking-icon"><TicketCheck size={20} /></div>
                  <div><Link className="booking-ref" to={`/app/bookings/${booking.id}`}>#{booking.bookingReference}</Link><h3>Space {booking.slotNumber}</h3><span>{booking.vehicleNumber} · {dateLabel(booking.startTime)}</span></div>
                </div>
                <div className="booking-card-schedule"><span>ARRIVAL</span><strong>{dateLabel(booking.startTime)}</strong><span>DEPARTURE</span><strong>{dateLabel(booking.endTime)}</strong></div>
                <div className="booking-card-total"><span>Final charge</span><strong>{currency(booking.finalAmount)}</strong><StatusBadge status={booking.status} /><small>Payment: <StatusBadge status={bookingPaymentStatus(booking)} /></small></div>
                {(actions.length > 0 || canCancel) && (
                  <div className="booking-card-actions">
                    <BookingActionButtons
                      booking={booking}
                      actions={actions}
                      now={clock}
                      busy={actionBusy}
                      onAction={(action) => void runBookingAction(booking, action)}
                    />
                    {canCancel && <Button variant="danger" onClick={() => void cancel(booking)}>Cancel booking</Button>}
                  </div>
                )}
              </article>
            );
          })}
        </div>
      ) : <EmptyState title="No bookings in this view" body="Try another filter or find a parking space for your next trip." />}
    </>
  );
}

export function UserBookingDetailsPage() {
  const { bookingId } = useParams();
  const [booking, setBooking] = useState<Booking | null>(null);
  const [error, setError] = useState("");
  const [actionBusy, setActionBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(true);
  const [clock, setClock] = useState(Date.now());

  useEffect(() => {
    const timer = window.setInterval(() => setClock(Date.now()), 30_000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    if (!bookingId) return;
    api<Booking>(`/api/bookings/${bookingId}`)
      .then(setBooking)
      .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : "Booking details could not be loaded."))
      .finally(() => setLoading(false));
  }, [bookingId]);

  async function runBookingAction(action: "deposit" | "parked" | "depart" | "settle") {
    if (!booking) return;
    setActionBusy(true);
    setError("");
    setNotice("");
    try {
      const path = `/api/bookings/${booking.id}/${action}`;
      if (action === "deposit" && booking.bookingGroupId) {
        const group = await api<BookingGroup>(
          `/api/bookings/groups/${booking.bookingGroupId}/deposit`,
          { method: "POST" },
        );
        setBooking(group.bookings.find((item) => item.id === booking.id) || booking);
      } else {
        setBooking(await api<Booking>(path, { method: "POST" }));
      }
      setNotice(action === "depart"
        ? "Thank you for parking with SmartPark. Your space is available for other drivers."
        : action === "settle"
          ? "Demo payment recorded. No payment provider was opened."
          : action === "parked"
            ? "Parking confirmed. Your booking time is based on the scheduled start."
            : "The 50% deposit was recorded in demo mode. No payment provider was opened.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "The booking could not be updated.");
    } finally {
      setActionBusy(false);
    }
  }

  async function cancel() {
    if (!booking || !window.confirm(booking.bookingGroupId
      ? `Cancel all vehicles in booking group ${booking.bookingGroupId}?`
      : `Cancel reservation ${booking.bookingReference}?`)) return;
    setError("");
    try {
      if (booking.bookingGroupId) {
        const group = await api<BookingGroup>(`/api/bookings/groups/${booking.bookingGroupId}/cancel`, { method: "POST" });
        setBooking(group.bookings.find((item) => item.id === booking.id) || booking);
      } else {
        setBooking(await api<Booking>(`/api/bookings/${booking.id}/cancel`, { method: "POST" }));
      }
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "This booking could not be cancelled.");
    }
  }

  if (loading) return <Loading label="Loading booking…" />;
  return (
    <>
      <PageHeader eyebrow="RESERVATION DETAILS" title={booking ? `Booking ${booking.bookingReference.slice(0, 8).toUpperCase()}` : "Booking details"} description="Review the schedule, vehicle, status, and saved charge for your reservation." action={<Link className="button button-secondary" to="/app/bookings">Back to bookings</Link>} />
      <Notice>{error}</Notice>
      <Notice tone="success">{notice}</Notice>
      {booking && (
        <div className="content-split">
          <Panel title="Reservation">
            <dl className="detail-list">
              <div><dt>Reference</dt><dd>{booking.bookingReference}</dd></div>
              <div><dt>Status</dt><dd><StatusBadge status={booking.status} /></dd></div>
              <div><dt>Payment status</dt><dd><StatusBadge status={bookingPaymentStatus(booking)} /></dd></div>
              <div><dt>Parking space</dt><dd><Link className="text-link" to={`/app/slots/${booking.slotId}`}>Space {booking.slotNumber}</Link></dd></div>
              <div><dt>Vehicle</dt><dd>{booking.vehicleNumber}</dd></div>
              <div><dt>Arrival</dt><dd>{dateLabel(booking.startTime)}</dd></div>
              <div><dt>Departure</dt><dd>{dateLabel(booking.endTime)}</dd></div>
              <div><dt>Hourly rate snapshot</dt><dd>{currency(booking.hourlyRate)} / hour</dd></div>
              <div><dt>Scheduled booking charge</dt><dd>{currency(booking.amount)}</dd></div>
              <div><dt>50% deposit</dt><dd>{currency(booking.depositAmount)}</dd></div>
              <div><dt>Amount paid</dt><dd>{currency(booking.paidAmount)}</dd></div>
              {booking.actualEntryTime && <div><dt>Actual entry</dt><dd>{dateLabel(booking.actualEntryTime)}</dd></div>}
              {booking.actualExitTime && <div><dt>Actual exit</dt><dd>{dateLabel(booking.actualExitTime)}</dd></div>}
              <div><dt>Late fee ({booking.lateHours} hours)</dt><dd>{currency(booking.lateFeeAmount)}</dd></div>
              <div><dt>Final charge</dt><dd>{currency(booking.finalAmount)}</dd></div>
              <div><dt>Balance due</dt><dd>{currency(booking.balanceDue)}</dd></div>
              {booking.depositRefundAmount > 0 && <div><dt>Amount refunded</dt><dd>{currency(booking.depositRefundAmount)}</dd></div>}
              {booking.settlementPaidAt && <>
                <div><dt>Simulated SmartPark fee (10%)</dt><dd>{currency(booking.platformFeeAmount)}</dd></div>
                <div><dt>Simulated owner share (90%)</dt><dd>{currency(booking.ownerEarnings)}</dd></div>
              </>}
            </dl>
          </Panel>
          {booking.status === "PENDING_PAYMENT" && (
            <Panel title="Confirm this reservation">
              <p className="panel-description">Pay the 50% deposit to confirm your space. This is a demo payment and will not open a payment gateway.</p>
              <Button onClick={() => void runBookingAction("deposit")} disabled={actionBusy}>
                {actionBusy ? "Recording deposit…" : `Pay deposit · ${currency(booking.depositAmount)}`} <Wallet size={15} />
              </Button>
            </Panel>
          )}
          {booking.status === "CONFIRMED" && clock >= Date.parse(booking.startTime)
              && clock <= Date.parse(booking.startTime) + 30 * 60_000 && (
            <Panel title="Vehicle arrival">
              <p className="panel-description">Confirm when the vehicle is parked. Your booking time starts at the scheduled start, with a 30-minute arrival window.</p>
              <Button onClick={() => void runBookingAction("parked")} disabled={actionBusy}>
                {actionBusy ? "Confirming…" : "Vehicle is parked"}
              </Button>
            </Panel>
          )}
          {(booking.status === "ACTIVE" || booking.status === "OVERDUE") && (
            <Panel title="Leaving the parking space">
              <p className="panel-description">Confirm your departure to release the space immediately. A 15-minute grace period applies after the scheduled end; then late fees are ₹30/hour for four-wheelers or ₹15/hour for two-wheelers, prorated by the minute.</p>
              <Button onClick={() => void runBookingAction("depart")} disabled={actionBusy}>
                {actionBusy ? "Completing booking…" : "I have left the space"}
              </Button>
            </Panel>
          )}
          {booking.status === "COMPLETED" && booking.balanceDue > 0 && (
            <Panel title="Final payment">
              <p className="panel-description">Your final charge includes the scheduled booking amount and any late fee. This demo payment records the balance without opening a payment gateway.</p>
              <Button onClick={() => void runBookingAction("settle")} disabled={actionBusy}>
                {actionBusy ? "Recording payment…" : `Pay balance · ${currency(booking.balanceDue)}`} <Wallet size={15} />
              </Button>
            </Panel>
          )}
          {booking.status === "COMPLETED" && booking.settlementPaidAt && (
            <Panel title="Payment complete">
              <p className="panel-description">Thank you for parking with SmartPark. Your demo payment is recorded and the space is available again.</p>
            </Panel>
          )}
          {(booking.status === "CONFIRMED" || booking.status === "PENDING_PAYMENT") && new Date(booking.startTime) > new Date() && (
            <Panel title="Need to change your plans?">
              <p className="panel-description">You can cancel this reservation before it starts.</p>
              <Button variant="danger" onClick={() => void cancel()}>Cancel booking</Button>
            </Panel>
          )}
        </div>
      )}
    </>
  );
}

export function ProfilePage() {
  const { user } = useAuth();
  const [profile, setProfile] = useState({ fullName: user?.fullName || "", phone: user?.phone || "" });
  const [passwords, setPasswords] = useState({ currentPassword: "", newPassword: "" });
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");

  async function saveProfile(event: FormEvent) {
    event.preventDefault();
    setError("");
    setNotice("");
    try {
      const updated = await api<User>("/api/users/me", {
        method: "PATCH",
        body: JSON.stringify(profile),
      });
      setProfile({ fullName: updated.fullName, phone: updated.phone || "" });
      setNotice("Profile updated.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Profile could not be updated.");
    }
  }

  async function changePassword(event: FormEvent) {
    event.preventDefault();
    setError("");
    setNotice("");
    try {
      await api<void>("/api/users/me/password", {
        method: "PUT",
        body: JSON.stringify(passwords),
      });
      setPasswords({ currentPassword: "", newPassword: "" });
      setNotice("Password changed successfully.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Password could not be changed.");
    }
  }

  return (
    <>
      <PageHeader eyebrow="ACCOUNT SETTINGS" title="Your profile" description="Keep your account details up to date." />
      <Notice>{error}</Notice>
      <Notice tone="success">{notice}</Notice>
      <div className="content-split profile-split">
        <Panel title="Personal information">
          <form className="auth-form" onSubmit={(event) => void saveProfile(event)}>
            <Field label="Full name" value={profile.fullName} onChange={(event) => setProfile({ ...profile, fullName: event.target.value })} required maxLength={255} />
            <Field label="Email address" value={user?.email || ""} readOnly />
            <Field label="Phone number" type="tel" value={profile.phone} onChange={(event) => setProfile({ ...profile, phone: event.target.value })} />
            <Button type="submit">Save profile</Button>
          </form>
        </Panel>
        <Panel title="Change password">
          <form className="auth-form" onSubmit={(event) => void changePassword(event)}>
            <Field label="Current password" type="password" autoComplete="current-password" value={passwords.currentPassword} onChange={(event) => setPasswords({ ...passwords, currentPassword: event.target.value })} required />
            <Field label="New password" type="password" autoComplete="new-password" value={passwords.newPassword} onChange={(event) => setPasswords({ ...passwords, newPassword: event.target.value })} required minLength={6} pattern="(?=.*[A-Z])(?=.*[0-9]).{6,}" />
            <Button type="submit" variant="secondary">Update password</Button>
          </form>
          <div className="profile-footnote"><Wallet size={15} /> Your booking history remains attached to your account.</div>
        </Panel>
      </div>
    </>
  );
}

function BookingRow({ booking }: { booking: Booking }) {
  return (
    <Link className="booking-row" to={`/app/bookings/${booking.id}`}>
      <div className="booking-row-icon"><MapPin size={17} /></div>
      <div className="booking-row-copy"><strong>Space {booking.slotNumber}</strong><span>{dateLabel(booking.startTime)} · {booking.vehicleNumber}</span></div>
      <div className="booking-row-side"><StatusBadge status={booking.status} /><strong>{currency(booking.finalAmount)}</strong></div>
    </Link>
  );
}
