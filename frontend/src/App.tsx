import { useEffect, useState } from "react";
import { Api, type PaymentIntent, type Stats, fmtMoney } from "./lib/api.js";
import { Dashboard } from "./pages/Dashboard.js";
import { LoginScreen } from "./pages/LoginScreen.js";

export function App() {
  const [api, setApi] = useState<Api | null>(null);
  const [merchantId, setMerchantId] = useState<string | null>(null);

  useEffect(() => {
    const saved = localStorage.getItem("payflow.token");
    const savedMerchant = localStorage.getItem("payflow.merchantId");
    if (saved && savedMerchant) {
      setApi(new Api(saved));
      setMerchantId(savedMerchant);
    }
  }, []);

  const onLoggedIn = (token: string, merchant: string) => {
    localStorage.setItem("payflow.token", token);
    localStorage.setItem("payflow.merchantId", merchant);
    setApi(new Api(token));
    setMerchantId(merchant);
  };

  const onLogout = () => {
    localStorage.removeItem("payflow.token");
    localStorage.removeItem("payflow.merchantId");
    setApi(null);
    setMerchantId(null);
  };

  if (!api || !merchantId) return <LoginScreen onLoggedIn={onLoggedIn} />;
  return <Dashboard api={api} merchantId={merchantId} onLogout={onLogout} />;
}
