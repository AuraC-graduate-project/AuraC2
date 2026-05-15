import React, { useState } from "react";
import { Input } from "./loginui/components/ui/input";
import { Label } from "./loginui/components/ui/label";
import { Button } from "./loginui/components/ui/button";
import { AlertCircle, Lock, ShieldCheck, User } from "lucide-react";
import { loginApi } from "../services/authApi";
import { ThemeToggle } from "../components/ThemeToggle";
import { TechBackground } from "./loginui/components/TechBackground";

export function LoginPage({
  onLoginSuccess,
  initialError,
}: {
  onLoginSuccess: (token: string) => void;
  initialError?: string;
}) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(initialError ?? null);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await loginApi(username.trim(), password);
      if (!res.accessToken) throw new Error("Backend did not return accessToken");
      onLoginSuccess(res.accessToken);
    } catch (err: any) {
      setError(err?.message || "Login failed");
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="aura-login-screen relative min-h-screen overflow-hidden bg-background px-4 py-10 text-on-surface">
      <TechBackground />
      <div className="absolute right-4 top-4 z-20">
        <ThemeToggle />
      </div>
      <div className="relative z-10 mx-auto flex min-h-[calc(100vh-5rem)] max-w-6xl items-center justify-center">
        <section className="aura-login-card grid w-full max-w-5xl overflow-hidden rounded-xl bg-surface-container md:grid-cols-[1fr_1.05fr]">
          <div className="aura-brand-panel relative hidden min-h-[560px] overflow-hidden p-10 md:flex md:flex-col md:justify-center">
            <div className="aura-brand-panel-grid" aria-hidden="true" />
            <div className="relative">
              <div className="mb-8 inline-flex h-14 w-14 items-center justify-center rounded-xl bg-primary text-on-primary dark:bg-[image:var(--primary-gradient)]">
                <ShieldCheck className="h-6 w-6" />
              </div>
              <h1 className="font-display text-5xl font-semibold tracking-tight text-on-surface">AuraC²</h1>
              <p className="mt-3 text-lg font-medium text-on-surface-variant">Aura Contest Control</p>
              <p className="mt-12 text-xs uppercase tracking-[0.22em] text-on-surface-soft">
                Precision · Authority · Logic
              </p>
            </div>
          </div>

          <div className="p-6 sm:p-10">
            <div className="mb-8 md:hidden">
              <h1 className="font-display text-3xl font-semibold tracking-tight">AuraC²</h1>
              <p className="text-sm text-on-surface-variant">Aura Contest Control</p>
            </div>

            <div className="mb-8">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-primary">Sign in</p>
              <h2 className="mt-2 font-display text-3xl font-semibold tracking-tight text-on-surface">Welcome back</h2>
              <p className="mt-1 text-sm text-on-surface-variant">
                Use your administrator or team credentials to continue.
              </p>
            </div>

            <form onSubmit={handleLogin} className="space-y-5">
              {error && (
                <div className="flex gap-3 rounded-xl bg-error-container px-4 py-3 text-sm text-on-error-container">
                  <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
                  <span>{error}</span>
                </div>
              )}

              <div className="space-y-2">
                <Label htmlFor="username" className="text-xs font-medium uppercase tracking-[0.14em] text-on-surface-variant">
                  Username
                </Label>
                <div className="relative">
                  <User className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-on-surface-soft" />
                  <Input
                    id="username"
                    type="text"
                    placeholder="Enter your username"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    className="h-11 pl-10"
                    required
                    autoComplete="username"
                    disabled={loading}
                  />
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="password" className="text-xs font-medium uppercase tracking-[0.14em] text-on-surface-variant">
                  Password
                </Label>
                <div className="relative">
                  <Lock className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-on-surface-soft" />
                  <Input
                    id="password"
                    type="password"
                    placeholder="Enter your password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    className="h-11 pl-10"
                    required
                    autoComplete="current-password"
                    disabled={loading}
                  />
                </div>
              </div>

              <Button
                type="submit"
                size="lg"
                className="h-11 w-full"
                disabled={loading}
              >
                {loading ? "Signing in..." : "Sign In"}
              </Button>
            </form>
          </div>
        </section>
      </div>
    </main>
  );
}
