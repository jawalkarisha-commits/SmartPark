import { Activity, CalendarDays, CheckCircle2, FileCheck2, MapPin, ParkingSquare, Pencil, Plus, RefreshCw, ShieldCheck, Trash2 } from "lucide-react";
import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { api, currency } from "../api";
import { FacilityLocationPicker } from "../FacilityMap";
import {
  Button,
  EmptyState,
  Loading,
  Notice,
  PageHeader,
  Panel,
  StatCard,
  StatusBadge,
} from "../components";
import type { Booking, Facility, OwnerOverview, OwnerVerification, ParkingSlot, SlotStatus } from "../types";

export function OwnerDashboardPage() {
  const [overview, setOverview] = useState<OwnerOverview | null>(null);
  const [facility, setFacility] = useState<OwnerOverview["facility"] | null>(null);
  const [facilities, setFacilities] = useState<Facility[]>([]);
  const [selectedFacilityId, setSelectedFacilityId] = useState<number | null>(null);
  const [creatingFacility, setCreatingFacility] = useState(false);
  const [verification, setVerification] = useState<OwnerVerification | null>(null);
  const [facilityDraft, setFacilityDraft] = useState({
    name: "", address: "", totalCapacity: "1",
    operatingStart: "00:00", operatingEnd: "23:59", latitude: "", longitude: "",
  });
  const [slotDraft, setSlotDraft] = useState({
    slotNumber: "", floorNumber: "", location: "", zone: "",
    vehicleType: "", hourlyPrice: "", dailyPrice: "", monthlyPrice: "",
    status: "AVAILABLE" as SlotStatus,
  });
  const [editingSlotId, setEditingSlotId] = useState<number | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setError("");
    try {
      const ownerStatus = await api<OwnerVerification>("/api/owner/verification");
      setVerification(ownerStatus);
      if (ownerStatus.approvalStatus === "APPROVED") {
        const ownerFacilities = await api<Facility[]>("/api/owner/facilities");
        setFacilities(ownerFacilities);
        const ownerFacility = ownerFacilities.find((item) => item.id === selectedFacilityId)
          || ownerFacilities[0] || null;
        if (ownerFacility && selectedFacilityId !== ownerFacility.id) {
          setSelectedFacilityId(ownerFacility.id);
        }
        setFacility(ownerFacility);
        if (ownerFacility) {
          setFacilityDraft({
            name: ownerFacility.name,
            address: ownerFacility.address,
            totalCapacity: String(ownerFacility.totalCapacity),
            operatingStart: ownerFacility.operatingStart.slice(0, 5),
            operatingEnd: ownerFacility.operatingEnd.slice(0, 5),
            latitude: ownerFacility.latitude == null ? "" : String(ownerFacility.latitude),
            longitude: ownerFacility.longitude == null ? "" : String(ownerFacility.longitude),
          });
          setOverview(await api<OwnerOverview>(`/api/owner/facilities/${ownerFacility.id}/overview`));
        } else {
          setOverview(null);
        }
      } else {
        setFacility(null);
        setOverview(null);
      }
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Facility information could not be loaded.");
    } finally {
      setLoading(false);
    }
  }, [selectedFacilityId]);
  useEffect(() => { void load(); }, [load]);

  async function transitionBooking(booking: Booking, action: "start" | "complete" | "cancel") {
    if (action === "cancel" && !window.confirm(`Cancel booking ${booking.bookingReference}?`)) return;
    setError("");
    setNotice("");
    try {
      const updated = await api<Booking>(`/api/owner/bookings/${booking.id}/${action}`, {
        method: "POST",
      });
      setOverview((current) => current && ({
        ...current,
        bookings: current.bookings.map((item) => item.id === updated.id ? updated : item),
      }));
      setNotice(`Booking ${booking.bookingReference} ${action === "start" ? "session started" : action === "complete" ? "completed" : "cancelled"}.`);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "The booking could not be updated.");
    }
  }

  async function saveFacility(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError("");
    setNotice("");
    try {
      const body = {
        ...facilityDraft,
        totalCapacity: Number(facilityDraft.totalCapacity),
        operatingStart: `${facilityDraft.operatingStart}:00`,
        operatingEnd: `${facilityDraft.operatingEnd}:00`,
        latitude: facilityDraft.latitude === "" ? null : Number(facilityDraft.latitude),
        longitude: facilityDraft.longitude === "" ? null : Number(facilityDraft.longitude),
      };
      const saved = await api<Facility>(
        creatingFacility || !facility
          ? "/api/owner/facilities"
          : `/api/owner/facilities/${facility.id}`,
        {
        method: creatingFacility || !facility ? "POST" : "PUT",
        body: JSON.stringify(body),
      });
      setSelectedFacilityId(saved.id);
      setCreatingFacility(false);
      setFacility(saved);
      setFacilities((current) => [
        ...current.filter((item) => item.id !== saved.id),
        saved,
      ].sort((a, b) => a.id - b.id));
      setFacilityDraft({
        name: saved.name,
        address: saved.address,
        totalCapacity: String(saved.totalCapacity),
        operatingStart: saved.operatingStart.slice(0, 5),
        operatingEnd: saved.operatingEnd.slice(0, 5),
        latitude: saved.latitude == null ? "" : String(saved.latitude),
        longitude: saved.longitude == null ? "" : String(saved.longitude),
      });
      setOverview(await api<OwnerOverview>(`/api/owner/facilities/${saved.id}/overview`));
      setNotice(creatingFacility || !facility
        ? "Facility created. Add parking spaces to this location."
        : "Facility details updated.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Facility details could not be saved.");
    } finally {
      setSaving(false);
    }
  }

  async function saveSlot(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError("");
    setNotice("");
    const body = {
      slotNumber: slotDraft.slotNumber,
      floorNumber: slotDraft.floorNumber ? Number(slotDraft.floorNumber) : null,
      location: slotDraft.location || null,
      zone: slotDraft.zone || null,
      vehicleType: slotDraft.vehicleType || null,
      hourlyPrice: Number(slotDraft.hourlyPrice),
      dailyPrice: Number(slotDraft.dailyPrice),
      monthlyPrice: Number(slotDraft.monthlyPrice),
      status: slotDraft.status,
    };
    try {
      await api<ParkingSlot>(
        editingSlotId
          ? `/api/owner/facilities/${facility?.id}/parking-slots/${editingSlotId}`
          : `/api/owner/facilities/${facility?.id}/parking-slots`,
        {
          method: editingSlotId ? "PUT" : "POST",
          body: JSON.stringify(body),
        },
      );
      setNotice(editingSlotId ? "Parking space updated." : "Parking space added to your facility.");
      setSlotDraft({
        slotNumber: "", floorNumber: "", location: "", zone: "",
        vehicleType: "", hourlyPrice: "", dailyPrice: "", monthlyPrice: "",
        status: "AVAILABLE",
      });
      setEditingSlotId(null);
      const updated = await api<OwnerOverview>(`/api/owner/facilities/${facility?.id}/overview`);
      setOverview(updated);
      setFacility(updated.facility);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Parking space could not be saved.");
    } finally {
      setSaving(false);
    }
  }

  function editSlot(slot: ParkingSlot) {
    setEditingSlotId(slot.id);
    setSlotDraft({
      slotNumber: slot.slotNumber,
      floorNumber: slot.floorNumber === null ? "" : String(slot.floorNumber),
      location: slot.location || "",
      zone: slot.zone || "",
      vehicleType: slot.vehicleType || "",
      hourlyPrice: String(slot.hourlyPrice),
      dailyPrice: String(slot.dailyPrice),
      monthlyPrice: String(slot.monthlyPrice),
      status: slot.status,
    });
  }

  async function removeSlot(slotId: number) {
    if (!window.confirm("Remove this parking space? Spaces with booking history cannot be deleted.")) return;
    setError("");
    setNotice("");
    try {
      await api<void>(`/api/owner/facilities/${facility?.id}/parking-slots/${slotId}`, { method: "DELETE" });
      setNotice("Parking space removed.");
      setOverview(await api<OwnerOverview>(`/api/owner/facilities/${facility?.id}/overview`));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Parking space could not be removed.");
    }
  }

  if (loading) return <Loading label="Loading facility operations…" />;
  if (verification?.approvalStatus !== "APPROVED") {
    const rejected = verification?.approvalStatus === "REJECTED";
    return (
      <>
        <PageHeader
          eyebrow="PARKING OWNER"
          title={rejected ? "Owner application rejected" : "Owner verification"}
          description={rejected
            ? "Your application was reviewed by an administrator. Facility operations remain unavailable."
            : "Email verification, document review, and owner approval are required before facility operations are available."}
          action={<Link className="button button-primary" to="/owner/verification">Continue verification</Link>}
        />
        <Notice>{error}</Notice>
        <Panel title={rejected ? "Application decision" : verification?.documentUploaded
          ? "Registration submitted · approval pending"
          : "Complete owner verification"}>
          {rejected ? (
            <p>{verification.rejectionReason || "The administrator did not provide a reason."}</p>
          ) : verification?.documentUploaded ? (
            <div className="verification-review-note">
              Your email is verified and your government document has been submitted for administrator review.
              Owner dashboard management will be available after approval.
            </div>
          ) : (
            <div className="verification-status-list">
              <span><span className={verification?.emailVerified ? "step-complete" : ""}>1</span>Email verification {verification?.emailVerified ? "complete" : "required"}</span>
              <span><span className={verification?.documentUploaded ? "step-complete" : ""}>2</span>Government document {verification?.documentUploaded ? "received" : "required"}</span>
              <span><span>3</span>Administrator review is required before listing parking spaces</span>
            </div>
          )}
        </Panel>
      </>
    );
  }

  const slots = overview?.slots || [];
  const bookings = overview?.bookings || [];
  return (
    <>
      <PageHeader
        eyebrow="PARKING OWNER"
        title={facility?.name || "Your parking facility"}
        description={facility ? `${facility.address} · Operating ${facility.operatingStart}–${facility.operatingEnd}` : "Set up your facility and list your parking spaces."}
        action={<Button variant="secondary" onClick={() => void load()}><RefreshCw size={15} /> Refresh</Button>}
      />
      <Notice>{error}</Notice>
      <Notice tone="success">{notice}</Notice>
      <div className="verification-approved-banner"><ShieldCheck size={18} /> Owner verification approved ({verification?.approvalMethod === "AUTO" ? "automatic KYC" : "administrator review"}).</div>
      {facilities.length > 0 && !creatingFacility && (
        <Panel title="Your locations">
          <div className="form-grid">
            <label className="field"><span>Selected facility</span><select value={selectedFacilityId ?? ""} onChange={(event) => setSelectedFacilityId(Number(event.target.value))}>
              {facilities.map((item) => <option key={item.id} value={item.id}>{item.name} · {item.address}</option>)}
            </select></label>
            <div className="form-actions"><Button type="button" onClick={() => {
              setFacilityDraft({ name: "", address: "", totalCapacity: "1", operatingStart: "00:00", operatingEnd: "23:59", latitude: "", longitude: "" });
              setCreatingFacility(true);
            }}>Add facility <Plus size={15} /></Button></div>
          </div>
        </Panel>
      )}
      {!facility || creatingFacility ? (
        <Panel title={creatingFacility ? "Add another facility" : "Create your parking facility"}>
          <p className="panel-description">Add the facility customers will see, then create and price its parking spaces.</p>
          <form className="form-grid" onSubmit={(event) => void saveFacility(event)}>
            <label className="field"><span>Facility name</span><input value={facilityDraft.name} onChange={(event) => setFacilityDraft({ ...facilityDraft, name: event.target.value })} required maxLength={160} /></label>
            <label className="field"><span>Address</span><input value={facilityDraft.address} onChange={(event) => setFacilityDraft({ ...facilityDraft, address: event.target.value })} required maxLength={500} /></label>
            <label className="field"><span>Parking space capacity</span><input type="number" min="1" value={facilityDraft.totalCapacity} onChange={(event) => setFacilityDraft({ ...facilityDraft, totalCapacity: event.target.value })} required /></label>
            <label className="field"><span>Operating hours start</span><input type="time" value={facilityDraft.operatingStart} onChange={(event) => setFacilityDraft({ ...facilityDraft, operatingStart: event.target.value })} required /></label>
            <label className="field"><span>Operating hours end</span><input type="time" value={facilityDraft.operatingEnd} onChange={(event) => setFacilityDraft({ ...facilityDraft, operatingEnd: event.target.value })} required /></label>
            <label className="field"><span>Latitude</span><input type="number" min="-90" max="90" step="any" value={facilityDraft.latitude} onChange={(event) => setFacilityDraft({ ...facilityDraft, latitude: event.target.value })} /></label>
            <label className="field"><span>Longitude</span><input type="number" min="-180" max="180" step="any" value={facilityDraft.longitude} onChange={(event) => setFacilityDraft({ ...facilityDraft, longitude: event.target.value })} /></label>
            <FacilityLocationPicker
              coordinates={facilityDraft.latitude && facilityDraft.longitude
                ? { latitude: Number(facilityDraft.latitude), longitude: Number(facilityDraft.longitude) }
                : null}
              onPick={({ latitude, longitude }) => setFacilityDraft((current) => ({
                ...current, latitude: latitude.toFixed(7), longitude: longitude.toFixed(7),
              }))}
            />
            <div className="form-actions"><Button type="submit" disabled={saving}>{saving ? "Saving facility…" : creatingFacility ? "Add facility" : "Create facility"} <Plus size={15} /></Button>{creatingFacility && <Button type="button" variant="secondary" onClick={() => { setCreatingFacility(false); void load(); }}>Cancel</Button>}</div>
          </form>
        </Panel>
      ) : (
        <>
          <Panel title="Facility profile">
            <form className="form-grid" onSubmit={(event) => void saveFacility(event)}>
              <label className="field"><span>Facility name</span><input value={facilityDraft.name} onChange={(event) => setFacilityDraft({ ...facilityDraft, name: event.target.value })} required maxLength={160} /></label>
              <label className="field"><span>Address</span><input value={facilityDraft.address} onChange={(event) => setFacilityDraft({ ...facilityDraft, address: event.target.value })} required maxLength={500} /></label>
              <label className="field"><span>Parking space capacity</span><input type="number" min={slots.length || 1} value={facilityDraft.totalCapacity} onChange={(event) => setFacilityDraft({ ...facilityDraft, totalCapacity: event.target.value })} required /></label>
              <label className="field"><span>Operating hours start</span><input type="time" value={facilityDraft.operatingStart} onChange={(event) => setFacilityDraft({ ...facilityDraft, operatingStart: event.target.value })} required /></label>
              <label className="field"><span>Operating hours end</span><input type="time" value={facilityDraft.operatingEnd} onChange={(event) => setFacilityDraft({ ...facilityDraft, operatingEnd: event.target.value })} required /></label>
              <label className="field"><span>Latitude</span><input type="number" min="-90" max="90" step="any" value={facilityDraft.latitude} onChange={(event) => setFacilityDraft({ ...facilityDraft, latitude: event.target.value })} /></label>
              <label className="field"><span>Longitude</span><input type="number" min="-180" max="180" step="any" value={facilityDraft.longitude} onChange={(event) => setFacilityDraft({ ...facilityDraft, longitude: event.target.value })} /></label>
              <FacilityLocationPicker
                coordinates={facilityDraft.latitude && facilityDraft.longitude
                  ? { latitude: Number(facilityDraft.latitude), longitude: Number(facilityDraft.longitude) }
                  : null}
                onPick={({ latitude, longitude }) => setFacilityDraft((current) => ({
                  ...current, latitude: latitude.toFixed(7), longitude: longitude.toFixed(7),
                }))}
              />
              <div className="form-actions"><Button type="submit" variant="secondary" disabled={saving}>{saving ? "Saving…" : "Save facility details"}</Button></div>
            </form>
          </Panel>
          <div className="stats-grid">
            <StatCard label="Facility capacity" value={facility.totalCapacity} hint="Configured total spaces" icon={ParkingSquare} />
            <StatCard label="Registered spaces" value={slots.length} hint="In your assigned facility" icon={MapPin} tone="blue" />
            <StatCard label="Operational spaces" value={slots.filter((slot) => slot.available).length} hint="Not in maintenance" icon={Activity} tone="green" />
            <StatCard label="Recent bookings" value={bookings.length} hint="Latest facility reservations" icon={CalendarDays} tone="amber" />
          </div>
          <Panel title={editingSlotId ? "Edit parking space" : "Add a parking space"}>
            <form className="form-grid" onSubmit={(event) => void saveSlot(event)}>
              <label className="field"><span>Space / slot number</span><input value={slotDraft.slotNumber} onChange={(event) => setSlotDraft({ ...slotDraft, slotNumber: event.target.value })} required maxLength={100} /></label>
              <label className="field"><span>Floor number</span><input type="number" min="0" value={slotDraft.floorNumber} onChange={(event) => setSlotDraft({ ...slotDraft, floorNumber: event.target.value })} /></label>
              <label className="field"><span>Location label</span><input value={slotDraft.location} onChange={(event) => setSlotDraft({ ...slotDraft, location: event.target.value })} maxLength={255} /></label>
              <label className="field"><span>Zone</span><input value={slotDraft.zone} onChange={(event) => setSlotDraft({ ...slotDraft, zone: event.target.value })} maxLength={100} /></label>
              <label className="field"><span>Vehicle type</span><input value={slotDraft.vehicleType} onChange={(event) => setSlotDraft({ ...slotDraft, vehicleType: event.target.value })} maxLength={40} placeholder="e.g. Four-wheeler" /></label>
              <label className="field"><span>Hourly rate (INR)</span><input type="number" min="0.01" step="0.01" value={slotDraft.hourlyPrice} onChange={(event) => setSlotDraft({ ...slotDraft, hourlyPrice: event.target.value })} required /></label>
              <label className="field"><span>Daily rate (INR)</span><input type="number" min="0.01" step="0.01" value={slotDraft.dailyPrice} onChange={(event) => setSlotDraft({ ...slotDraft, dailyPrice: event.target.value })} required /></label>
              <label className="field"><span>Monthly rate (INR)</span><input type="number" min="0.01" step="0.01" value={slotDraft.monthlyPrice} onChange={(event) => setSlotDraft({ ...slotDraft, monthlyPrice: event.target.value })} required /></label>
              <label className="field"><span>Space status</span><select value={slotDraft.status} onChange={(event) => setSlotDraft({ ...slotDraft, status: event.target.value as SlotStatus })}><option value="AVAILABLE">Available</option><option value="MAINTENANCE">Maintenance</option></select></label>
              <div className="form-actions">
                <Button type="submit" disabled={saving}>{saving ? "Saving…" : editingSlotId ? "Update space" : "Add parking space"} <Plus size={15} /></Button>
                {editingSlotId && <Button type="button" variant="secondary" onClick={() => { setEditingSlotId(null); setSlotDraft({ slotNumber: "", floorNumber: "", location: "", zone: "", vehicleType: "", hourlyPrice: "", dailyPrice: "", monthlyPrice: "", status: "AVAILABLE" }); }}>Cancel edit</Button>}
              </div>
            </form>
          </Panel>
          <Panel title="Parking inventory">
            {slots.length ? (
              <div className="table-wrap">
                <table>
                  <thead><tr><th>Space</th><th>Location</th><th>Vehicle type</th><th>Hourly / daily / monthly</th><th>Status</th><th>Manage</th></tr></thead>
                  <tbody>{slots.map((slot) => (
                    <tr key={slot.id}>
                      <td><strong>{slot.slotNumber}</strong></td>
                      <td>{slot.location || facility.name}<small>{slot.zone || "—"}</small></td>
                      <td>{slot.vehicleType || "All types"}</td>
                      <td>{currency(slot.hourlyPrice)} / {currency(slot.dailyPrice)} / {currency(slot.monthlyPrice)}</td>
                      <td><StatusBadge status={slot.status} /></td>
                      <td className="owner-slot-actions">
                        <Button variant="secondary" aria-label={`Edit ${slot.slotNumber}`} onClick={() => editSlot(slot)}><Pencil size={14} /></Button>
                        <Button variant="danger" aria-label={`Delete ${slot.slotNumber}`} onClick={() => void removeSlot(slot.id)}><Trash2 size={14} /></Button>
                      </td>
                    </tr>
                  ))}</tbody>
                </table>
              </div>
            ) : <EmptyState title="No spaces configured" body="Use Add a parking space above to create your first listing." />}
          </Panel>
          <Panel title="Recent facility bookings">
            {bookings.length ? (
              <div className="table-wrap">
                <table>
                  <thead><tr><th>Reference</th><th>Space / vehicle</th><th>Schedule</th><th>Amount</th><th>Status</th><th>Operations</th></tr></thead>
                  <tbody>{bookings.map((booking) => (
                    <tr key={booking.id}>
                      <td><strong>{booking.bookingReference.slice(0, 8).toUpperCase()}</strong></td>
                      <td>{booking.slotNumber}<small>{booking.vehicleNumber}</small></td>
                      <td>{new Date(booking.startTime).toLocaleString()}<small>to {new Date(booking.endTime).toLocaleString()}</small></td>
                      <td>{currency(booking.amount)}</td>
                      <td><StatusBadge status={booking.status} /></td>
                      <td><div className="row-buttons">
                        {booking.status === "CONFIRMED" && new Date(booking.startTime) <= new Date() && new Date(booking.endTime) > new Date() && (
                          <Button variant="secondary" onClick={() => void transitionBooking(booking, "start")}>Start</Button>
                        )}
                        {booking.status === "CONFIRMED" && new Date(booking.startTime) > new Date() && (
                          <Button variant="danger" onClick={() => void transitionBooking(booking, "cancel")}>Cancel</Button>
                        )}
                        {(booking.status === "ACTIVE" || booking.status === "OVERDUE") && (
                          <Button variant="secondary" onClick={() => void transitionBooking(booking, "complete")}>Complete</Button>
                        )}
                      </div></td>
                    </tr>
                  ))}</tbody>
                </table>
              </div>
            ) : <EmptyState title="No bookings yet" body="Bookings for this facility will appear here." />}
            <p className="chart-disclaimer">Booking amounts are pricing records and do not indicate payment settlement.</p>
          </Panel>
        </>
      )}
    </>
  );
}

export function OwnerVerificationPage() {
  const [searchParams] = useSearchParams();
  const [verification, setVerification] = useState<OwnerVerification | null>(null);
  const [otp, setOtp] = useState("");
  const [document, setDocument] = useState<File | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const initializeOnce = useRef(false);

  const load = useCallback(async () => {
    const current = await api<OwnerVerification>("/api/owner/verification");
    setVerification(current);
  }, []);

  useEffect(() => {
    if (initializeOnce.current) return;
    initializeOnce.current = true;
    const initialize = async () => {
      setError("");
      try {
        if (searchParams.get("sendCode") === "1") {
          const updated = await api<OwnerVerification>("/api/owner/verification/otp", { method: "POST" });
          setVerification(updated);
          setNotice(updated.developmentOtp
            ? "Local testing code created. Enter the code displayed below."
            : "A 6-digit verification code was sent to your email.");
        } else {
          await load();
        }
      } catch (reason) {
        setError(reason instanceof Error ? reason.message : "Owner verification could not be loaded.");
        try {
          await load();
        } catch {
          setVerification(null);
        }
      }
    };
    void initialize();
  }, [load, searchParams]);

  async function sendOtp() {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const updated = await api<OwnerVerification>("/api/owner/verification/otp", { method: "POST" });
      setVerification(updated);
      setNotice(updated.developmentOtp
        ? "Local testing code created. Enter the code displayed below; it expires in 10 minutes."
        : "A 6-digit verification code was sent to your email. It expires in 10 minutes.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "The verification email could not be sent.");
    } finally {
      setBusy(false);
    }
  }

  async function refreshStatus() {
    setError("");
    try {
      await load();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Verification status could not be refreshed.");
    }
  }

  async function verifyEmail(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const updated = await api<OwnerVerification>("/api/owner/verification/otp/verify", {
        method: "POST",
        body: JSON.stringify({ otp }),
      });
      setVerification(updated);
      setOtp("");
      setNotice("Email verified. Upload a government document for administrator review.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Email verification failed.");
    } finally {
      setBusy(false);
    }
  }

  async function uploadDocument(event: FormEvent) {
    event.preventDefault();
    if (!document) return;
    const form = new FormData();
    form.append("document", document);
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const updated = await api<OwnerVerification>("/api/owner/verification/document", {
        method: "POST",
        body: form,
      });
      setVerification(updated);
      setNotice("Your document was uploaded and is awaiting administrator review.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "The document could not be uploaded.");
    } finally {
      setBusy(false);
    }
  }

  const approved = verification?.approvalStatus === "APPROVED";
  const rejected = verification?.approvalStatus === "REJECTED";
  const pending = verification?.approvalStatus === "PENDING";
  const submittedForReview = pending && verification?.documentUploaded === true;
  return (
    <div className="owner-verification-page">
      <PageHeader eyebrow="OWNER ONBOARDING" title="Verify your parking-owner account" description="Verify your email and submit a government document for administrator review." />
      <Notice>{error}</Notice>
      <Notice tone="success">{notice}</Notice>
      {verification && (
        <div className="modal-backdrop owner-verification-backdrop">
          <section className="booking-modal verification-modal" role="dialog" aria-modal="true" aria-labelledby="owner-verify-title">
            <div className="verification-icon"><FileCheck2 size={21} /></div>
            <div className="eyebrow">PARKING OWNER KYC</div>
            <h2 id="owner-verify-title">
              {approved
                ? "Owner application approved"
                : rejected
                  ? "Owner application rejected"
                  : submittedForReview
                    ? "Registration submitted · approval pending"
                    : verification.emailVerified
                      ? "Upload your government document"
                      : "Verify your email"}
            </h2>
            <p className="modal-subtitle">
              {approved
                ? "An administrator approved your account. You can now manage your parking listings."
                : rejected
                  ? verification.rejectionReason || "An administrator rejected your application."
                  : verification.emailVerified
                    ? verification.documentUploaded
                      ? "Your email is verified and your government document was submitted. An administrator must review and approve your owner registration before you can manage a facility."
                      : "Upload a government-issued PDF, PNG, JPG, or JPEG document (up to 10 MB). Uploading a file does not automatically verify your identity; an administrator will review it."
                    : verification.developmentOtp
                      ? "Use the local testing code shown below to verify this email address."
                      : `We sent a 6-digit verification code to ${verification.email}.`}
            </p>
            <div className="verification-status-list">
              <span><span className={verification.emailVerified ? "step-complete" : ""}><CheckCircle2 size={13} /></span>Email {verification.emailVerified ? "verified" : "verification pending"}</span>
              <span><span className={verification.documentUploaded ? "step-complete" : ""}><FileCheck2 size={13} /></span>Government document {verification.documentUploaded ? "uploaded" : "not uploaded"}</span>
            </div>
            {pending && !verification.emailVerified && (
              <form className="auth-form verification-form" onSubmit={(event) => void verifyEmail(event)}>
                {verification.developmentOtp && (
                  <Notice tone="info">
                    Local testing OTP: <strong>{verification.developmentOtp}</strong>
                  </Notice>
                )}
                <label className="field">
                  <span>Email verification code</span>
                  <input value={otp} onChange={(event) => setOtp(event.target.value)} inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6} required />
                </label>
                <Button type="submit" disabled={busy || otp.length !== 6}>{busy ? "Verifying…" : "Verify email"}</Button>
                <Button type="button" variant="secondary" disabled={busy} onClick={() => void sendOtp()}>Send a new code</Button>
              </form>
            )}
            {pending && verification.emailVerified && !verification.documentUploaded && (
              <form className="auth-form verification-form" onSubmit={(event) => void uploadDocument(event)}>
                <label className="field">
                  <span>Government-issued identity document</span>
                  <input type="file" accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg" onChange={(event) => setDocument(event.target.files?.[0] || null)} required />
                </label>
                <p className="panel-description">Accepted: PDF, PNG, JPG, JPEG · Maximum 10 MB. Your file is stored privately and is available only to administrators.</p>
                <Button type="submit" disabled={busy || !document}>{busy ? "Uploading securely…" : "Upload for administrator review"}</Button>
              </form>
            )}
            {submittedForReview && (
              <Notice tone="info">
                Submitted for administration review. Your owner dashboard will unlock after approval.
              </Notice>
            )}
            {approved && <div className="verification-approved-banner"><ShieldCheck size={18} /> You can now access your owner dashboard.</div>}
            {rejected && <div className="verification-review-note">Your owner dashboard and listing controls remain locked while this application is rejected.</div>}
            {approved ? (
              <Link className="button button-primary verification-continue" to="/owner/dashboard">Continue to owner dashboard</Link>
            ) : pending ? (
              <>
                <Button type="button" variant="secondary" className="verification-continue" onClick={() => void refreshStatus()}>Refresh approval status</Button>
                <p className="verification-review-note">Administrators review submitted documents and decide whether to approve or reject the application.</p>
              </>
            ) : null}
          </section>
        </div>
      )}
    </div>
  );
}
