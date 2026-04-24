import { useState } from "react";
import { Api, ApiError } from "../lib/api.js";

const DEMO_KEY = "demo-api-key";

export function LoginScreen({
  onLoggedIn,
}: {
  onLoggedIn: (token: string, merchantId: string) => void;
}) {
  const [apiKey, setApiKey] = useState(DEMO_KEY);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    setError(null);
    setBusy(true);
    try {
      const res = await Api.login(apiKey);
      onLoggedIn(res.token, res.merchantId);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="login">
      <div className="login-card">
        <h1>
          pay<em>/</em>flow
        </h1>
        <p>Sign in to the payments console with your merchant API key.</p>
        <label htmlFor="api-key">API key</label>
        <input
          id="api-key"
          value={apiKey}
          onChange={(e) => setApiKey(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && submit()}
          type="password"
          autoComplete="off"
          spellCheck={false}
        />
        <button
          className="btn primary"
          onClick={submit}
          disabled={busy || !apiKey}
        >
          {busy ? "Signing in…" : "Enter console"}
        </button>
        {error && (
          <div
            style={{
              color: "var(--err)",
              fontFamily: "var(--mono)",
              fontSize: 11.5,
              marginTop: 12,
            }}
          >
            {error}
          </div>
        )}
        <div className="hint">
          Demo key is pre-filled (<code>{DEMO_KEY}</code>). In production the
          key is exchanged for a short-lived JWT and never stored beyond the
          session.
        </div>
      </div>
    </div>
  );
}
