import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { useNavigate } from "react-router-dom";
import { api, tokenStorage } from "./api";
import type { AuthResponse, Role, User } from "./types";

interface AuthContextValue {
  user: User | null;
  loading: boolean;
  updateUser(user: User): void;
  signIn(email: string, password: string, requiredRole?: Role): Promise<User>;
  completeRegistration(email: string, otp: string): Promise<User>;
  signOut(): void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  const accept = useCallback((response: AuthResponse) => {
    tokenStorage.set(response.accessToken);
    const profile: User = {
      id: response.id,
      fullName: response.fullName,
      email: response.email,
      phone: response.phone,
      role: response.role,
      enabled: response.enabled,
    };
    setUser(profile);
    return profile;
  }, []);

  useEffect(() => {
    const handleUnauthorized = () => setUser(null);
    window.addEventListener("smartpark:unauthorized", handleUnauthorized);
    if (!tokenStorage.get()) {
      setLoading(false);
      return () => window.removeEventListener("smartpark:unauthorized", handleUnauthorized);
    }
    api<User>("/api/users/me")
      .then(setUser)
      .catch(() => {
        tokenStorage.clear();
        setUser(null);
      })
      .finally(() => setLoading(false));
    return () => window.removeEventListener("smartpark:unauthorized", handleUnauthorized);
  }, []);

  const signIn = useCallback(async (
    email: string,
    password: string,
    requiredRole?: Role,
  ) => {
    const response = await api<AuthResponse>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
    if (requiredRole && response.role !== requiredRole) {
      throw new Error(
        requiredRole === "ADMIN"
          ? "This sign-in is for administrator accounts. Use customer sign-in instead."
          : "This account cannot sign in to this area.",
      );
    }
    return accept(response);
  }, [accept]);

  const completeRegistration = useCallback(async (email: string, otp: string) => {
    const response = await api<AuthResponse>("/api/auth/register/verify", {
      method: "POST",
      body: JSON.stringify({ email, otp }),
    });
    return accept(response);
  }, [accept]);

  const signOut = useCallback(() => {
    tokenStorage.clear();
    setUser(null);
  }, []);

  const updateUser = useCallback((profile: User) => setUser(profile), []);

  const value = useMemo(
    () => ({ user, loading, updateUser, signIn, completeRegistration, signOut }),
    [user, loading, updateUser, signIn, completeRegistration, signOut],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used within AuthProvider");
  return context;
}

export function useSignOut() {
  const { signOut } = useAuth();
  const navigate = useNavigate();
  return () => {
    signOut();
    navigate("/");
  };
}
