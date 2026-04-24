import { useCallback, useEffect, useState } from "react";
import type { Api, PaymentIntent, Stats, Refund } from "../lib/api.js";
import { fmtMoney, fmtShortId, fmtTime, ApiError } from "../lib/api.js";

export function Dashboard({
  api,
  merchantId,
  onLogout,
}: {
  api: Api;
  merchantId: string;
  onLogout: () => void;
}) {
  const [items, setItems] = useState<PaymentIntent[]>([]);
  const [stats, setStats] = useState<Stats | null>(null);
  const [selected, setSelected] = useState<PaymentIntent | null>(null);
  const [refundMsg, setRefundMsg] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [list, s] = await Promise.all([api.listPayments(0, 50), api.stats()]);
      setItems(list.items);
      setStats(s);
    } catch (e) {
      console.error(e);
    }
  }, [api]);

  useEffect(() => {
    load();
  }, [load]);

  const createOne = async (form: HTMLFormElement) => {
    const fd = new FormData(form);
    const amount = Number(fd.get("amount"));
    const currency = String(fd.get("currency") || "USD").toUpperCase();
    const description = String(fd.get("description") || "");
    if (!amount || amount <= 0) return;
    try {
      await api.createPayment({
        amountCents: Math.round(amount * 100),
        currency,
        description,
      });
      form.reset();
      load();
    } catch (e) {
      console.error(e);
    }
  };

  const cancel = async (id: string) => {
    try {
      await api.cancelPayment(id);
      load();
      if (selected?.id === id) setSelected(null);
    } catch (e) {
      console.error(e);
    }
  };

  const refund = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!selected) return;
    const fd = new FormData(e.currentTarget);
    const amountStr = String(fd.get("amount") || "").trim();
    const reason = String(fd.get("reason") || "").trim();
    try {
      const r = await api.createRefund({
        paymentIntentId: selected.id,
        amountCents: amountStr ? Math.round(Number(amountStr) * 100) : undefined,
        reason: reason || undefined,
      });
      setRefundMsg(
        `Refund ${fmtShortId(r.id)} · ${r.status} · ${fmtMoney(r.amountCents, selected.currency)}`,
      );
      e.currentTarget.reset();
      load();
    } catch (err) {
      setRefundMsg(err instanceof ApiError ? err.message : String(err));
    }
  };

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <span>pay<em>/</em>flow</span>
          <span className="tag">payments console</span>
        </div>
        <div className="topbar-stats">
          <div className="stat">
            volume (succeeded)
            <b>{fmtMoney(stats?.succeededAmountCents ?? 0)}</b>
          </div>
          <div className="stat">
            succeeded
            <b>{stats?.succeededCount ?? 0}</b>
          </div>
          <div className="stat">
            failed
            <b>{stats?.failedCount ?? 0}</b>
          </div>
          <button
            className="btn"
            onClick={onLogout}
            style={{ padding: "6px 14px", fontSize: 11 }}
          >
            sign out
          </button>
        </div>
      </header>

      <div className="main">
        <div className="main-left">
          <div className="section-eyebrow">Merchant · {merchantId.slice(0, 8)}…</div>
          <h1 className="section-title">
            Transactions, <em style={{ fontStyle: "italic", color: "var(--accent)" }}>live</em>
          </h1>

          <div className="stat-cards">
            <div className="stat-card">
              <div className="label">Volume</div>
              <div className="value">
                {fmtMoney(stats?.succeededAmountCents ?? 0)}
              </div>
              <div className="delta">succeeded only</div>
            </div>
            <div className="stat-card">
              <div className="label">Succeeded</div>
              <div className="value">{stats?.succeededCount ?? 0}</div>
              <div className="delta">
                all time · merchant-scoped
              </div>
            </div>
            <div className="stat-card">
              <div className="label">Failed</div>
              <div className="value" style={{ color: "var(--err)" }}>
                {stats?.failedCount ?? 0}
              </div>
              <div className="delta">mostly card_declined</div>
            </div>
            <div className="stat-card">
              <div className="label">Processing</div>
              <div className="value" style={{ color: "var(--warn)" }}>
                {stats?.processingCount ?? 0}
              </div>
              <div className="delta">awaiting reconcile</div>
            </div>
          </div>

          <form
            className="create-form"
            onSubmit={(e) => {
              e.preventDefault();
              createOne(e.currentTarget);
            }}
          >
            <div>
              <label htmlFor="amount">Amount</label>
              <input id="amount" name="amount" placeholder="29.00" inputMode="decimal" />
            </div>
            <div>
              <label htmlFor="currency">Currency</label>
              <input id="currency" name="currency" defaultValue="USD" maxLength={3} />
            </div>
            <div style={{ gridColumn: "span 2" }}>
              <label htmlFor="description">Description</label>
              <input id="description" name="description" placeholder="test order" />
            </div>
            <button type="submit" className="btn primary">
              Charge
            </button>
          </form>

          <table className="tx-table" aria-label="Transactions">
            <thead>
              <tr>
                <th>id</th>
                <th>created</th>
                <th>description</th>
                <th>status</th>
                <th className="tx-amount">amount</th>
              </tr>
            </thead>
            <tbody>
              {items.length === 0 && (
                <tr>
                  <td colSpan={5} style={{ color: "var(--fg-muted)", fontStyle: "italic" }}>
                    no transactions yet — use the form above to create one
                  </td>
                </tr>
              )}
              {items.map((pi) => (
                <tr
                  key={pi.id}
                  className={selected?.id === pi.id ? "selected" : ""}
                  onClick={() => {
                    setRefundMsg(null);
                    setSelected(pi);
                  }}
                >
                  <td className="tx-id">{fmtShortId(pi.id)}</td>
                  <td>{fmtTime(pi.createdAt)}</td>
                  <td>{pi.description || "—"}</td>
                  <td>
                    <span className={`pill ${pi.status}`}>{pi.status}</span>
                  </td>
                  <td className="tx-amount">
                    {fmtMoney(pi.amountCents, pi.currency)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <aside className="main-right">
          {!selected ? (
            <div className="detail-empty">
              Select a transaction
              <br />
              to view its details.
            </div>
          ) : (
            <>
              <div className="section-eyebrow">Transaction</div>
              <div className="detail-amount">
                {fmtMoney(selected.amountCents, selected.currency)}
                <em>{selected.currency}</em>
              </div>
              <div style={{ marginTop: 10 }}>
                <span className={`pill ${selected.status}`}>{selected.status}</span>
              </div>
              <dl className="detail-meta">
                <dt>id</dt>
                <dd>{selected.id}</dd>
                <dt>provider</dt>
                <dd>{selected.providerId || "—"}</dd>
                <dt>description</dt>
                <dd>{selected.description || "—"}</dd>
                <dt>created</dt>
                <dd>{fmtTime(selected.createdAt)}</dd>
                <dt>updated</dt>
                <dd>{fmtTime(selected.updatedAt)}</dd>
              </dl>

              <div className="detail-actions">
                {(selected.status === "processing" ||
                  selected.status === "requires_confirmation") && (
                  <button className="btn" onClick={() => cancel(selected.id)}>
                    cancel intent
                  </button>
                )}
              </div>

              {selected.status === "succeeded" && (
                <form className="refund-form" onSubmit={refund}>
                  <div className="section-eyebrow" style={{ marginBottom: 12 }}>
                    Issue refund
                  </div>
                  <label htmlFor="refund-amount">Amount (blank = full)</label>
                  <input id="refund-amount" name="amount" placeholder="leave blank for full" />
                  <label htmlFor="refund-reason">Reason</label>
                  <input id="refund-reason" name="reason" placeholder="customer request" />
                  <button type="submit" className="btn primary" style={{ width: "100%" }}>
                    refund
                  </button>
                  {refundMsg && (
                    <div
                      style={{
                        color: "var(--fg-dim)",
                        fontFamily: "var(--mono)",
                        fontSize: 11,
                        marginTop: 12,
                      }}
                    >
                      {refundMsg}
                    </div>
                  )}
                </form>
              )}
            </>
          )}
        </aside>
      </div>
    </div>
  );
}
