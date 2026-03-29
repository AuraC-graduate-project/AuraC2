import React, { createContext, useContext } from "react";

type Role = "ADMIN" | "TEAM";

type AuthContextType = {
    user: string;
    role: Role;
    logout: () => Promise<void>;
};

export const AuthContext = createContext<AuthContextType | null>(null);

export function useAuth() {
    const ctx = useContext(AuthContext);
    if (!ctx) {
        throw new Error("useAuth must be used inside AuthContext.Provider");
    }
    return ctx;
}
