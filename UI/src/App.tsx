import React, { useEffect, useMemo, useState } from "react";
import { LoginPage } from "./auth/LoginPage";
import { getStoredToken, storeToken, clearToken } from "./auth/tokenStore";
import { decodeJwtRole } from "./auth/jwt";
import { refreshApi, logoutApi } from "./services/authApi";
import AdminApp from "./admin/App";
import TeamApp from "./team/App";

type Role = "ADMIN" | "TEAM" | "UNKNOWN";

export default function App() {
  const [token, setToken] = useState<string | null>(() => getStoredToken());
  const [booting, setBooting] = useState(true);

  const role: Role = useMemo(
    () => (token ? decodeJwtRole(token) : "UNKNOWN"),
    [token]
  );

  useEffect(() => {
    (async () => {
      try {
        if (!token) {
          const r = await refreshApi();
          if (r?.accessToken) {
            storeToken(r.accessToken);
            setToken(r.accessToken);
          }
        }
      } finally {
        setBooting(false);
      }
    })();
  }, []);

  const onLoginSuccess = (accessToken: string) => {
    storeToken(accessToken);
    setToken(accessToken);
  };

  const onLogout = async () => {
    try {
      await logoutApi();
    } finally {
      clearToken();
      setToken(null);
    }
  };

  if (booting) return <div>Loading…</div>;
  if (!token) return <LoginPage onLoginSuccess={onLoginSuccess} />;
  if (role === "UNKNOWN") return <LoginPage onLoginSuccess={onLoginSuccess} />;

  return role === "ADMIN"
    ? <AdminApp onLogout={onLogout} />
    : <TeamApp onLogout={onLogout} />;
}
