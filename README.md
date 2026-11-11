# SmartPark

SmartPark is a parking management system for vehicle registration, bookings,
and slot allocation. It uses Spring Boot, MySQL, and a React/Vite client.

## Deploy the web client to Vercel

Vercel hosts the React/Vite client only. The Spring Boot API and MySQL database
must run separately; do not use the in-memory `local-h2` profile for hosted
accounts or bookings.

1. Import this GitHub repository into Vercel.
2. Set **Root Directory** to `frontend/client`. Vercel will detect Vite; use
   `npm run build` and `dist` if prompted for build settings.
3. Set the Vercel environment variable `VITE_API_BASE_URL` to the backend's
   public HTTPS origin, without an `/api` suffix.
4. On the backend, set `SMARTPARK_CORS_ALLOWED_ORIGINS` to the exact Vercel site
   origin.
5. Redeploy after setting environment variables.

`frontend/client/vercel.json` provides SPA route rewrites for paths such as
`/app/find`. The backend needs a persistent MySQL-compatible database. Owner
document uploads also need persistent/object storage; the default local
filesystem is not durable on ephemeral containers. Configure the email service
and payment provider settings on the backend separately.

### Free-tier backend option

The included `render.yaml` describes a Docker web service on Render Free. One
possible database is a MySQL-compatible TiDB Cloud Starter instance. This is a
hobby/demo setup, not production: Render may sleep after inactivity, loses
local files on sleep/redeploy, and blocks common SMTP ports.

1. Create a TiDB Cloud Starter instance and database; use its TLS-enabled JDBC
   URL for `SMARTPARK_DB_URL`, and set the database username and password.
2. Create a Resend API key and verify the sender domain. Set
   `SMARTPARK_MAIL_RESEND_API_KEY` and `SMARTPARK_MAIL_FROM`; Resend's free email
   allowance is subject to its current daily/monthly limits.
3. Create a **private** Cloudflare R2 bucket and scoped S3 credentials. Set
   `SMARTPARK_KYC_R2_ENDPOINT`, `SMARTPARK_KYC_R2_ACCESS_KEY_ID`,
   `SMARTPARK_KYC_R2_SECRET_ACCESS_KEY`, and `SMARTPARK_KYC_R2_BUCKET`.
4. In Render, create a Blueprint from the repository using `render.yaml` and
   enter the `sync: false` environment values when prompted. Set
   `SMARTPARK_CORS_ALLOWED_ORIGINS` and `SMARTPARK_RESET_URL` to the Vercel
   site's exact origin and `/forgot-password` URL. Bootstrap an admin, then
   remove the bootstrap email/password variables.
5. In Vercel, set `VITE_API_BASE_URL` to the deployed Render HTTPS origin and
   redeploy. The client must be built again after changing this variable.

Keep all API keys and database credentials in provider secret settings, never
in Git. Check each provider's current quotas, billing requirements, and region
availability before creating services. Render Free has no durable local disk,
so keep owner KYC files in the private R2 bucket; the app serves documents only
through its authenticated admin endpoint.
