# SmartPark

SmartPark is a parking management demo built with React, Vite, and Spring Boot.
When no backend URL is configured, the web client runs in browser-only demo mode
and does not require a database, email provider, payment provider, or other
hosted service.

## Host the demo on Vercel

1. Import this GitHub repository into Vercel.
2. Set **Root Directory** to `frontend/client`.
3. Use `npm run build` as the build command and `dist` as the output directory.
4. Leave `VITE_API_BASE_URL` unset and deploy. The client automatically uses the
   local demo API instead of sending requests to the site origin.

The demo includes sample parking spaces in Latur, browser-local accounts,
on-screen email verification codes, vehicle management, parking search,
estimates, and bookings. To sign in as the demo administrator, use
`admin@smartpark.demo` with password `DemoAdmin123`.

## Demo limitations

- Accounts, vehicles, and bookings are stored in the current browser's local
  storage. They do not sync between browsers or devices and can be cleared by
  the user.
- Demo OTPs are shown on screen. No email or SMS is sent.
- Owner verification and approval are simulated. Do not enter real identity
  documents or other sensitive information.
- Map tiles are loaded from OpenStreetMap's public tile servers; no map API key
  is needed, but map requests do go to that third-party service.
- Payments, password resets, and other server-dependent features are disabled.
- Demo credentials are public and are not suitable for real accounts or
  sensitive data. This mode is for previews and evaluation only.

## Use a separately hosted backend

To use the Spring Boot API instead of the demo, configure `VITE_API_BASE_URL`
with its public HTTPS origin (without an `/api` suffix), set the backend CORS
allowlist to the Vercel origin, and redeploy. This requires separately hosted
backend and database services; it is not part of the free browser-only demo.
