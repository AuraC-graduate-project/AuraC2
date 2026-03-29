import React, { useState } from "react";
import { TechBackground } from "./loginui/components/TechBackground";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "./loginui/components/ui/card";
import { Input } from "./loginui/components/ui/input";
import { Label } from "./loginui/components/ui/label";
import { Button } from "./loginui/components/ui/button";
import { Lock, User } from "lucide-react";
import { loginApi } from "../services/authApi";

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
    <div className="min-h-screen bg-[#0F172A] flex flex-col items-center justify-center p-4 relative">
      <TechBackground />

      <div className="w-full max-w-md space-y-8 relative z-10">
        <div className="text-center space-y-2">
          <h1 className="text-white tracking-tight">
            <span className="text-4xl md:text-5xl">AuraC²</span>
            <span className="text-xl md:text-2xl text-gray-300"> – Contest Control</span>
          </h1>
          <p className="text-gray-400 text-sm">Secure Offline Contest Management</p>
        </div>

        <Card className="border-gray-800 bg-slate-900/90 backdrop-blur-sm shadow-2xl">
          <CardHeader className="space-y-1 pb-4">
            <CardTitle className="text-2xl text-center text-white">Welcome back</CardTitle>
            <CardDescription className="text-center text-gray-400">
              Sign in to access your dashboard
            </CardDescription>
          </CardHeader>

          <CardContent>
            <form onSubmit={handleLogin} className="space-y-4">
              {error && (
                <div className="rounded-md border border-red-900/50 bg-red-950/30 px-3 py-2 text-sm text-red-200">
                  {error}
                </div>
              )}

              <div className="space-y-2">
                <Label htmlFor="username" className="text-gray-300">Username</Label>
                <div className="relative">
                  <User className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-gray-400" />
                  <Input
                    id="username"
                    type="text"
                    placeholder="Enter your username"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    className="pl-10 bg-slate-800/50 border-gray-700 text-white placeholder:text-gray-500"
                    required
                    autoComplete="username"
                  />
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="password" className="text-gray-300">Password</Label>
                <div className="relative">
                  <Lock className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-gray-400" />
                  <Input
                    id="password"
                    type="password"
                    placeholder="Enter your password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    className="pl-10 bg-slate-800/50 border-gray-700 text-white placeholder:text-gray-500"
                    required
                    autoComplete="current-password"
                  />
                </div>
              </div>

              <Button
                type="submit"
                className="w-full bg-[#FACC15] hover:bg-[#EAB308] text-gray-900 font-semibold"
                disabled={loading}
              >
                {loading ? "Signing in..." : "Sign In"}
              </Button>

              
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
