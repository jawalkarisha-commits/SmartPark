import {
  ArrowDownRight,
  ArrowRight,
  ArrowUpRight,
  Check,
  CircleParking,
  Clock3,
  LockKeyhole,
  MapPin,
  ShieldCheck,
  Sparkles,
  Zap,
} from "lucide-react";
import { useState, type FormEvent, type ReactNode } from "react";
import { Link, Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
import { Button, Field, Notice, SelectField } from "../components";

export function LandingPage() {
  const { user } = useAuth();
  const action = user?.role === "ADMIN" ? "/admin/dashboard" : user?.role === "PARKING_OWNER" ? "/owner/dashboard" : "/app/dashboard";
  return (
    <main className="landing">
      <section className="hero-section">
        <div className="hero-copy">
          <div className="hero-kicker"><span /> SMARTER PARKING STARTS HERE</div>
          <h1>Find your spot.<br /><span>Park smarter.</span></h1>
          <p>Discover available parking, reserve with confidence, and keep every journey moving from one intelligent platform.</p>
          <div className="hero-actions">
            <Link to={user ? action : "/register"} className="button button-primary">
              Find a parking spot <ArrowRight size={17} />
            </Link>
            {!user && <Link to="/login" className="button button-secondary">Sign in</Link>}
          </div>
          <div className="hero-assurance">
            <span><Check size={14} /> Real-time availability</span>
            <span><Check size={14} /> Secure reservations</span>
          </div>
        </div>
        <div className="hero-art" aria-label="Illustration of a reserved parking space">
          <div className="orbit orbit-one" />
          <div className="orbit orbit-two" />
          <div className="art-glow" />
          <div className="parking-board">
            <div className="board-top"><span className="live-dot" /> LIVE PARKING</div>
            <div className="board-title">Your next spot<br />is right here.</div>
            <div className="parking-map">
              <div className="map-street" />
              <div className="map-slot slot-muted"><span>P</span></div>
              <div className="map-slot slot-muted"><span>P</span></div>
              <div className="map-slot slot-active"><CircleParking size={28} /><b>YOUR SPOT</b></div>
              <div className="map-car"><div className="car-window" /><div className="car-light" /></div>
              <div className="map-route" />
            </div>
            <div className="board-location"><MapPin size={14} /> Downtown · Level 2 <span>AVAILABLE</span></div>
          </div>
          <div className="art-float float-time"><Clock3 size={15} /><span>Book on your schedule</span></div>
          <div className="art-float float-safe"><ShieldCheck size={16} /><span>Reservation protected</span></div>
        </div>
        <a className="scroll-cue" href="#features">SCROLL TO EXPLORE <ArrowDownRight size={14} /></a>
      </section>

      <section className="feature-section" id="features">
        <div className="section-intro">
          <div className="eyebrow">THE SMARTPARK DIFFERENCE</div>
          <h2>Less circling.<br /><span>More arriving.</span></h2>
          <p>A simpler parking experience from the first search to the final checkout.</p>
        </div>
        <div className="feature-grid">
          <article className="feature-card">
            <span className="feature-icon"><MapPin size={20} /></span>
            <h3>Know before you go</h3>
            <p>Explore available spaces and find a fit for your vehicle and schedule.</p>
          </article>
          <article className="feature-card">
            <span className="feature-icon"><Zap size={20} /></span>
            <h3>Reserve in a few steps</h3>
            <p>Choose your vehicle, see a server-calculated estimate, and confirm your booking.</p>
          </article>
          <article className="feature-card">
            <span className="feature-icon"><LockKeyhole size={20} /></span>
            <h3>Your details stay yours</h3>
            <p>Personal vehicles and bookings are protected behind your secure account.</p>
          </article>
        </div>
      </section>

      <section className="how-section" id="how-it-works">
        <div className="how-heading">
          <div className="eyebrow">A BETTER WAY TO PARK</div>
          <h2>From search to spot<br />in <span>three easy steps.</span></h2>
          <Link to={user ? action : "/register"} className="text-link">Start your journey <ArrowUpRight size={15} /></Link>
        </div>
        <div className="steps">
          <div className="step"><span>01</span><div><h3>Choose your time</h3><p>Search parking that fits your arrival and departure.</p></div></div>
          <div className="step"><span>02</span><div><h3>Pick your space</h3><p>Compare compatible spaces and review your estimated charge.</p></div></div>
          <div className="step"><span>03</span><div><h3>Arrive with a plan</h3><p>Your reservation details stay available in your SmartPark account.</p></div></div>
        </div>
      </section>

      <section className="landing-cta">
        <div className="cta-spark"><Sparkles size={17} /></div>
        <div><h2>Make parking the easy part.</h2><p>Your next spot is waiting to be found.</p></div>
        <Link to={user ? action : "/register"} className="button button-primary">Get started <ArrowRight size={16} /></Link>
      </section>
    </main>
  );
}

export function LoginPage({ adminOnly = false }: { adminOnly?: boolean }) {
  const { signIn, signOut, user } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={user.role === "ADMIN" ? "/admin/dashboard" : user.role === "PARKING_OWNER" ? "/owner/dashboard" : "/app/dashboard"} replace />;

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      const signedIn = await signIn(email, password);
      if (adminOnly && signedIn.role !== "ADMIN") {
        signOut();
        setError("This sign-in is for administrator accounts. Use customer sign-in instead.");
        return;
      }
      navigate(signedIn.role === "ADMIN" ? "/admin/dashboard" : signedIn.role === "PARKING_OWNER" ? "/owner/dashboard" : "/app/dashboard", { replace: true });
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Unable to sign in.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthFrame
      title={adminOnly ? "Administrator sign in" : "Welcome back"}
      subtitle={adminOnly ? "Sign in to manage SmartPark operations." : "Sign in to pick up where you left off."}
    >
      <Notice>{error}</Notice>
      {adminOnly && (
        <p className="password-help">
          Administrator accounts are provisioned by the application operator; public sign-up cannot create an admin account.
          Set SMARTPARK_ADMIN_BOOTSTRAP_EMAIL and SMARTPARK_ADMIN_BOOTSTRAP_PASSWORD before starting the server.
          Set SMARTPARK_ADMIN_BOOTSTRAP_RESET_PASSWORD=true to replace an existing admin password.
        </p>
      )}
      <form className="auth-form" onSubmit={submit}>
        <Field label="Email address" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        <Field label="Password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        <div className="form-meta"><Link to="/forgot-password">Forgot password?</Link></div>
        <Button type="submit" disabled={busy}>{busy ? "Signing in…" : "Sign in"} <ArrowRight size={16} /></Button>
      </form>
      {adminOnly ? (
        <p className="auth-switch-copy"><Link to="/login">Customer sign in</Link></p>
      ) : (
        <p className="auth-switch-copy">New to SmartPark? <Link to="/register">Create an account</Link></p>
      )}
      <div className="secure-caption"><ShieldCheck size={14} /> Your account is protected with secure sign-in.</div>
    </AuthFrame>
  );
}

export function RegisterPage() {
  const { signUp, completeRegistration, user } = useAuth();
  const navigate = useNavigate();
  const [values, setValues] = useState({ fullName: "", email: "", phone: "", password: "", confirmPassword: "", role: "USER" as "USER" | "PARKING_OWNER" });
  const [pendingEmail, setPendingEmail] = useState("");
  const [developmentOtp, setDevelopmentOtp] = useState("");
  const [otp, setOtp] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={user.role === "ADMIN" ? "/admin/dashboard" : user.role === "PARKING_OWNER" ? "/owner/dashboard" : "/app/dashboard"} replace />;

  function update(field: keyof typeof values, value: string) {
    setValues((current) => ({ ...current, [field]: value }));
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError("");
    if (values.password !== values.confirmPassword) {
      setError("Passwords do not match.");
      return;
    }
    setBusy(true);
    try {
      if (values.role === "USER") {
        const pending = await api<{ email: string; developmentOtp?: string | null }>("/api/auth/register", {
          method: "POST",
          body: JSON.stringify({
            fullName: values.fullName,
            email: values.email,
            phone: values.phone,
            password: values.password,
          }),
        });
        setPendingEmail(pending.email);
        setDevelopmentOtp(pending.developmentOtp || "");
        setOtp("");
        return;
      }
      const signedUp = await signUp({
        fullName: values.fullName,
        email: values.email,
        phone: values.phone,
        password: values.password,
      });
      navigate(
        signedUp.role === "ADMIN"
          ? "/admin/dashboard"
          : signedUp.role === "PARKING_OWNER"
            ? "/owner/verification?sendCode=1"
            : "/app/dashboard",
        { replace: true },
      );
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Unable to create your account.");
    } finally {
      setBusy(false);
    }
  }

  async function verifyRegistration(event: FormEvent) {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      const signedUp = await completeRegistration(pendingEmail, otp);
      navigate(
        signedUp.role === "ADMIN" ? "/admin/dashboard" : "/app/dashboard",
        { replace: true },
      );
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Email verification failed.");
    } finally {
      setBusy(false);
    }
  }

  async function resendRegistrationCode() {
    setError("");
    setBusy(true);
    try {
      const pending = await api<{ email: string; developmentOtp?: string | null }>("/api/auth/register/resend", {
        method: "POST",
        body: JSON.stringify({ email: pendingEmail }),
      });
      setDevelopmentOtp(pending.developmentOtp || "");
      setNotice(pending.developmentOtp
        ? "A new local testing code was created. Enter the code shown below."
        : "A new verification code was sent to your email.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "A new verification code could not be sent.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthFrame
      title={pendingEmail ? "Verify your email" : "Create your account"}
      subtitle={pendingEmail
        ? developmentOtp
          ? `Enter the local testing code below to finish creating ${pendingEmail}.`
          : `Enter the 6-digit code sent to ${pendingEmail}.`
        : "A smoother parking experience starts here."}
    >
      <Notice>{error}</Notice>
      {pendingEmail && notice && <Notice tone="success">{notice}</Notice>}
      {pendingEmail ? (
        <form className="auth-form" onSubmit={verifyRegistration}>
          {developmentOtp && (
            <Notice tone="info">
              Local testing OTP: <strong>{developmentOtp}</strong>
            </Notice>
          )}
          <Field
            label="Email verification code"
            inputMode="numeric"
            autoComplete="one-time-code"
            value={otp}
            onChange={(event) => setOtp(event.target.value.replace(/\D/g, "").slice(0, 6))}
            required
            pattern="[0-9]{6}"
            maxLength={6}
          />
          <Button type="submit" disabled={busy || otp.length !== 6}>
            {busy ? "Verifying…" : "Verify email and open dashboard"} <ArrowRight size={16} />
          </Button>
          <Button type="button" variant="secondary" disabled={busy} onClick={() => void resendRegistrationCode()}>
            Resend verification code
          </Button>
          <Button type="button" variant="secondary" disabled={busy} onClick={() => { setPendingEmail(""); setDevelopmentOtp(""); setOtp(""); setNotice(""); }}>
            Use a different email
          </Button>
        </form>
      ) : (
        <form className="auth-form" onSubmit={submit}>
          <Field label="Full name" autoComplete="name" value={values.fullName} onChange={(e) => update("fullName", e.target.value)} required maxLength={255} />
          <Field label="Email address" type="email" autoComplete="email" value={values.email} onChange={(e) => update("email", e.target.value)} required />
          <Field label="Phone number" type="tel" autoComplete="tel" value={values.phone} onChange={(e) => update("phone", e.target.value)} required minLength={7} maxLength={30} pattern="[+() .\-0-9]{7,30}" />
          <SelectField label="Account type" value={values.role} onChange={(e) => setValues((current) => ({ ...current, role: e.target.value as "USER" | "PARKING_OWNER" }))}>
            <option value="USER">Parking user</option>
            <option value="PARKING_OWNER">Parking owner</option>
          </SelectField>
          {values.role === "PARKING_OWNER" && <p className="password-help">Owner dashboard access requires administrator approval after document review.</p>}
          <Field label="Password" type="password" autoComplete="new-password" value={values.password} onChange={(e) => update("password", e.target.value)} required minLength={6} pattern="(?=.*[A-Z])(?=.*[0-9]).{6,}" title="At least 6 characters, with an uppercase letter and a number." />
          <Field label="Confirm password" type="password" autoComplete="new-password" value={values.confirmPassword} onChange={(e) => update("confirmPassword", e.target.value)} required />
          <p className="password-help">Use at least 6 characters, including an uppercase letter and a number.</p>
          <Button type="submit" disabled={busy}>{busy ? "Creating account…" : values.role === "USER" ? "Continue to email verification" : "Create owner account"} <ArrowRight size={16} /></Button>
        </form>
      )}
      <p className="auth-switch-copy">Already have an account? <Link to="/login">Sign in</Link></p>
    </AuthFrame>
  );
}

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get("resetToken");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError("");
    setMessage("");
    setBusy(true);
    try {
      if (token) {
        const result = await api<{ message: string }>("/api/users/reset-password", {
          method: "POST",
          body: JSON.stringify({ token, newPassword: password }),
        });
        setMessage(result.message);
      } else {
        const result = await api<{ message: string }>("/api/users/forgot-password", {
          method: "POST",
          body: JSON.stringify({ email }),
        });
        setMessage(result.message);
      }
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "The request could not be completed.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthFrame
      title={token ? "Choose a new password" : "Reset your password"}
      subtitle={token ? "Set a new password for your SmartPark account." : "We’ll send a reset link if the account exists."}
    >
      <Notice>{error}</Notice>
      {message && <Notice tone="success">{message}</Notice>}
      <form className="auth-form" onSubmit={submit}>
        {token ? (
          <Field label="New password" type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={6} pattern="(?=.*[A-Z])(?=.*[0-9]).{6,}" />
        ) : (
          <Field label="Email address" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        )}
        <Button type="submit" disabled={busy}>{busy ? "Please wait…" : token ? "Reset password" : "Send reset link"} <ArrowRight size={16} /></Button>
      </form>
      <p className="auth-switch-copy"><Link to="/login">Back to sign in</Link></p>
    </AuthFrame>
  );
}

function AuthFrame({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <main className="auth-page">
      <div className="auth-decoration auth-decoration-one" />
      <div className="auth-decoration auth-decoration-two" />
      <section className="auth-card">
        <div className="auth-card-mark"><CircleParking size={23} /></div>
        <div className="eyebrow">SMARTPARK ACCOUNT</div>
        <h1>{title}</h1>
        <p className="auth-subtitle">{subtitle}</p>
        {children}
        <div className="auth-privacy"><LockKeyhole size={13} /> Your information is private and protected.</div>
      </section>
    </main>
  );
}
