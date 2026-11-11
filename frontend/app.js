const API_STORAGE_KEY = "smartpark-api-base";
const apiInput = document.querySelector("#apiBase");
const toast = document.querySelector("#toast");
const connectionText = document.querySelector("#connectionText");
const connectionDot = document.querySelector("#connectionDot");

apiInput.value = localStorage.getItem(API_STORAGE_KEY) || apiInput.value;

function apiBase() {
  return apiInput.value.trim().replace(/\/+$/, "");
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (character) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;",
  })[character]);
}

function showToast(message, isError = false) {
  toast.textContent = message;
  toast.classList.toggle("error", isError);
  toast.classList.add("visible");
  window.clearTimeout(showToast.timeout);
  showToast.timeout = window.setTimeout(() => toast.classList.remove("visible"), 5000);
}

async function request(path, options = {}) {
  const response = await fetch(`${apiBase()}${path}`, {
    ...options,
    headers: {
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...options.headers,
    },
  });
  const body = await response.text();
  let data;
  try {
    data = body ? JSON.parse(body) : null;
  } catch {
    data = body;
  }
  if (!response.ok) {
    const message = data?.message || data?.detail || data?.error || body || `HTTP ${response.status}`;
    throw new Error(`${response.status}: ${message}`);
  }
  return data;
}

function formValues(form) {
  return Object.fromEntries(new FormData(form).entries());
}

function setConnection(online, message) {
  connectionText.textContent = message;
  connectionDot.classList.toggle("online", online);
  connectionDot.classList.toggle("offline", !online);
}

function goToTab(name) {
  document.querySelectorAll(".tab").forEach((tab) => {
    tab.classList.toggle("active", tab.dataset.tab === name);
  });
  document.querySelectorAll(".panel").forEach((panel) => {
    panel.classList.toggle("active", panel.id === `panel-${name}`);
  });
}

function showAuthView(name) {
  const formId = {
    login: "loginForm",
    register: "registerForm",
    forgot: "forgotForm",
    reset: "resetForm",
  }[name];
  document.querySelectorAll(".auth-form").forEach((form) => {
    form.classList.toggle("hidden", form.id !== formId);
  });
  document.querySelectorAll(".auth-tab").forEach((tab) => {
    tab.classList.toggle("active", tab.dataset.authView === name);
  });
  document.querySelector("#authHeading").textContent = ({
    login: "Welcome back",
    register: "Create your account",
    forgot: "Reset your password",
    reset: "Choose a new password",
  })[name];
}

function showEmpty(element, message) {
  element.innerHTML = `<p class="empty">${escapeHtml(message)}</p>`;
}

async function refreshOverview() {
  try {
    const [vehicles, slots] = await Promise.all([
      request("/api/vehicles"),
      request("/api/parking-slots"),
    ]);
    document.querySelector("#vehicleCount").textContent = vehicles.length;
    document.querySelector("#slotCount").textContent = slots.length;
    setConnection(true, "Backend connected");
    renderVehicles(vehicles);
    renderSlots(slots);
    renderOverviewSlots(slots);
    showEmpty(
      document.querySelector("#overviewBookings"),
      "Load bookings by user ID in the Bookings tab.",
    );
  } catch (error) {
    setConnection(false, "Backend unavailable");
    showToast(error.message, true);
  }
}

function renderVehicles(vehicles) {
  const list = document.querySelector("#vehiclesList");
  if (!vehicles.length) return showEmpty(list, "No vehicles registered yet.");
  list.innerHTML = vehicles.map((vehicle) => `
    <div class="list-row">
      <div class="row-main">
        <span class="row-title">${escapeHtml(vehicle.vehicleNumber)} · ${escapeHtml(vehicle.ownerName)}</span>
        <span class="row-detail">${escapeHtml(vehicle.vehicleType || "Type not set")} · ${escapeHtml(vehicle.brand || "Brand not set")} · ID ${escapeHtml(vehicle.id)}</span>
      </div>
      <button class="button button-danger" data-delete-vehicle="${escapeHtml(vehicle.id)}" type="button">Delete</button>
    </div>
  `).join("");
}

function renderSlots(slots) {
  const list = document.querySelector("#slotsList");
  if (!slots.length) return showEmpty(list, "No parking slots added yet.");
  list.innerHTML = slots.map((slot) => `
    <div class="list-row">
      <div class="row-main">
        <span class="row-title">${escapeHtml(slot.slotNumber)} · Floor ${escapeHtml(slot.floorNumber ?? "—")}</span>
        <span class="row-detail">ID ${escapeHtml(slot.id)} · ${escapeHtml(slot.vehicleType || "Any vehicle")} · ${slot.hourlyPrice == null ? "Price not set" : `${escapeHtml(slot.hourlyPrice)} / hour`}</span>
      </div>
      <div class="row-actions">
        <span class="badge">${slot.available ? "Available" : "Unavailable"}</span>
        <button class="button button-danger" data-delete-slot="${escapeHtml(slot.id)}" type="button">Delete</button>
      </div>
    </div>
  `).join("");
}

function renderOverviewSlots(slots) {
  const list = document.querySelector("#overviewSlots");
  if (!slots.length) return showEmpty(list, "No parking slots have been added.");
  list.innerHTML = slots.slice(0, 5).map((slot) => `
    <div class="list-row">
      <div class="row-main">
        <span class="row-title">${escapeHtml(slot.slotNumber)}</span>
        <span class="row-detail">Floor ${escapeHtml(slot.floorNumber ?? "—")} · ${escapeHtml(slot.vehicleType || "Any vehicle")}</span>
      </div>
      <span class="badge">${slot.available ? "Available" : "Unavailable"}</span>
    </div>
  `).join("");
}

function renderBookings(bookings, list) {
  if (!bookings.length) return showEmpty(list, "No bookings found for that user.");
  list.innerHTML = bookings.map((booking) => `
    <div class="list-row">
      <div class="row-main">
        <span class="row-title">${escapeHtml(booking.slotNumber)} · ${escapeHtml(booking.vehicleNumber)}</span>
        <span class="row-detail">Booking ${escapeHtml(booking.id)} · ${escapeHtml(booking.startTime)} → ${escapeHtml(booking.endTime)}</span>
      </div>
      <div class="row-actions">
        <span class="badge ${booking.status === "CANCELLED" ? "cancelled" : ""}">${escapeHtml(booking.status)}</span>
        ${booking.status === "CONFIRMED" ? `<button class="button button-danger" data-cancel-booking="${escapeHtml(booking.id)}" data-owner="${escapeHtml(booking.userId)}" type="button">Cancel</button>` : ""}
      </div>
    </div>
  `).join("");
}

document.querySelectorAll(".tab").forEach((tab) => {
  tab.addEventListener("click", () => goToTab(tab.dataset.tab));
});

document.querySelectorAll("[data-go]").forEach((button) => {
  button.addEventListener("click", () => goToTab(button.dataset.go));
});

document.querySelectorAll("[data-auth-view]").forEach((button) => {
  button.addEventListener("click", () => showAuthView(button.dataset.authView));
});

document.querySelectorAll("[data-refresh]").forEach((button) => {
  button.addEventListener("click", refreshOverview);
});

document.querySelector("#saveApi").addEventListener("click", async () => {
  localStorage.setItem(API_STORAGE_KEY, apiBase());
  await refreshOverview();
});

document.querySelector("#registerForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  try {
    const user = await request("/api/users/register", {
      method: "POST",
      body: JSON.stringify(formValues(form)),
    });
    const result = document.querySelector("#registeredUser");
    result.textContent = `Registered ${user.fullName} · ${user.email} · User ID ${user.id}`;
    result.classList.remove("hidden");
    document.querySelector("#userCount").textContent = user.id;
    form.reset();
    showAuthView("login");
    showToast("User registered successfully.");
  } catch (error) {
    showToast(error.message, true);
  }
});

document.querySelector("#loginForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    const user = await request("/api/users/login", {
      method: "POST",
      body: JSON.stringify(formValues(event.currentTarget)),
    });
    document.querySelector("#userCount").textContent = user.id;
    showToast(`Welcome, ${user.fullName}. Demo login succeeded; no secure session was created.`);
  } catch (error) {
    showToast(error.message, true);
  }
});

document.querySelector("#forgotForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    const result = await request("/api/users/forgot-password", {
      method: "POST",
      body: JSON.stringify(formValues(event.currentTarget)),
    });
    showToast(result.message);
  } catch (error) {
    showToast(error.message, true);
  }
});

document.querySelector("#resetForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  const token = new URLSearchParams(window.location.search).get("resetToken");
  if (!token) {
    showToast("Reset link is missing its token. Request a new reset email.", true);
    return;
  }
  try {
    const values = formValues(event.currentTarget);
    const result = await request("/api/users/reset-password", {
      method: "POST",
      body: JSON.stringify({ token, newPassword: values.newPassword }),
    });
    window.history.replaceState({}, "", window.location.pathname);
    event.currentTarget.reset();
    showAuthView("login");
    showToast(result.message);
  } catch (error) {
    showToast(error.message, true);
  }
});

document.querySelector("#vehicleForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  const values = formValues(form);
  try {
    await request("/api/vehicles", {
      method: "POST",
      body: JSON.stringify({
        ...values,
        vehicleType: values.vehicleType || null,
        brand: values.brand || null,
      }),
    });
    form.reset();
    showToast("Vehicle added successfully.");
    await refreshOverview();
  } catch (error) {
    showToast(error.message, true);
  }
});

document.querySelector("#slotForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  const values = formValues(form);
  try {
    await request("/api/parking-slots", {
      method: "POST",
      body: JSON.stringify({
        ...values,
        floorNumber: values.floorNumber ? Number(values.floorNumber) : null,
        hourlyPrice: values.hourlyPrice ? Number(values.hourlyPrice) : null,
        vehicleType: values.vehicleType || null,
        available: true,
      }),
    });
    form.reset();
    showToast("Parking slot added successfully.");
    await refreshOverview();
  } catch (error) {
    showToast(error.message, true);
  }
});

document.querySelector("#availabilityForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  const values = formValues(event.currentTarget);
  const query = new URLSearchParams({
    startTime: values.startTime,
    endTime: values.endTime,
  });
  if (values.vehicleType.trim()) query.set("vehicleType", values.vehicleType.trim());
  try {
    const slots = await request(`/api/bookings/availability?${query}`);
    const results = document.querySelector("#availabilityResults");
    if (!slots.length) return showEmpty(results, "No slots available for this time window.");
    results.innerHTML = slots.map((slot) => `
      <div class="list-row">
        <div class="row-main">
          <span class="row-title">${escapeHtml(slot.slotNumber)} · Floor ${escapeHtml(slot.floorNumber ?? "—")}</span>
          <span class="row-detail">Slot ID ${escapeHtml(slot.id)} · ${escapeHtml(slot.vehicleType || "Any vehicle")}</span>
        </div>
        <button class="text-button" data-select-slot="${escapeHtml(slot.id)}" type="button">Use this slot →</button>
      </div>
    `).join("");
    showToast(`${slots.length} available slot${slots.length === 1 ? "" : "s"} found.`);
  } catch (error) {
    showToast(error.message, true);
  }
});

document.querySelector("#bookingForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  const values = formValues(event.currentTarget);
  try {
    const booking = await request("/api/bookings", {
      method: "POST",
      body: JSON.stringify({
        userId: Number(values.userId),
        vehicleId: Number(values.vehicleId),
        slotId: Number(values.slotId),
        startTime: values.startTime,
        endTime: values.endTime,
      }),
    });
    showToast(`Booking ${booking.id} created for slot ${booking.slotNumber}.`);
    await loadBookings(Number(values.userId));
  } catch (error) {
    showToast(error.message, true);
  }
});

async function loadBookings(userId) {
  const bookings = await request(`/api/bookings?userId=${encodeURIComponent(userId)}`);
  renderBookings(bookings, document.querySelector("#bookingsList"));
  return bookings;
}

document.querySelector("#bookingLookupForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  const values = formValues(event.currentTarget);
  try {
    const bookings = await loadBookings(Number(values.userId));
    showToast(`${bookings.length} booking${bookings.length === 1 ? "" : "s"} loaded.`);
  } catch (error) {
    showToast(error.message, true);
  }
});

document.addEventListener("click", async (event) => {
  const target = event.target;
  if (!(target instanceof HTMLElement)) return;
  if (target.dataset.selectSlot) {
    document.querySelector("#bookingForm [name=slotId]").value = target.dataset.selectSlot;
    const availability = formValues(document.querySelector("#availabilityForm"));
    document.querySelector("#bookingForm [name=startTime]").value = availability.startTime;
    document.querySelector("#bookingForm [name=endTime]").value = availability.endTime;
    goToTab("bookings");
    return;
  }
  if (target.dataset.deleteVehicle) {
    if (!window.confirm("Delete this vehicle?")) return;
    try {
      await request(`/api/vehicles/${target.dataset.deleteVehicle}`, { method: "DELETE" });
      showToast("Vehicle deleted.");
      await refreshOverview();
    } catch (error) {
      showToast(error.message, true);
    }
  }
  if (target.dataset.deleteSlot) {
    if (!window.confirm("Delete this parking slot?")) return;
    try {
      await request(`/api/parking-slots/${target.dataset.deleteSlot}`, { method: "DELETE" });
      showToast("Parking slot deleted.");
      await refreshOverview();
    } catch (error) {
      showToast(error.message, true);
    }
  }
  if (target.dataset.cancelBooking) {
    const userId = target.dataset.owner;
    try {
      const booking = await request(
        `/api/bookings/${target.dataset.cancelBooking}/cancel?userId=${encodeURIComponent(userId)}`,
        { method: "POST" },
      );
      showToast(`Booking ${booking.id} cancelled.`);
      await loadBookings(Number(userId));
    } catch (error) {
      showToast(error.message, true);
    }
  }
});

const resetToken = new URLSearchParams(window.location.search).get("resetToken");
if (resetToken) {
  goToTab("users");
  showAuthView("reset");
}
refreshOverview();
