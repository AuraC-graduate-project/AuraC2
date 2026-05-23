AuraC² merged src (integrated with your Spring Boot backend)

Auth endpoints (backend):
- POST /auth/login   -> {accessToken,message}   + sets HttpOnly cookie refresh_token
- POST /auth/refresh -> {accessToken}           (cookie-based)
- POST /auth/logout  -> {message}               + clears refresh cookie

Frontend behavior:
- Login stores accessToken in localStorage
- Every API call attaches Authorization: Bearer <token>
- On 401, frontend auto-calls POST /auth/refresh once and retries the request
- On first load, frontend attempts silent refresh if accessToken is missing

Config:
- VITE_API_BASE_URL (optional):
  - If you use Vite proxy: set to '' (empty) and proxy /auth + /api/* to backend
  - If you hit backend directly: set to 'http://localhost:8080' (and configure CORS + credentials)

Dev proxy example (vite.config.ts):
  server: { proxy: { '/auth': 'http://localhost:8080', '/api': 'http://localhost:8080' } }

Dependencies you may need:
- lucide-react (for login icons)

This zip contains ONLY the src/ folder + helper README.
