// Thin PayFlow API client.

export interface PaymentIntent {
  id: string;
  status: "requires_confirmation" | "processing" | "succeeded" | "failed" | "canceled";
  amountCents: number;
  currency: string;
  description?: string;
  providerId?: string;
  createdAt: string;
  updatedAt: string;
}

export interface Refund {
  id: string;
  paymentIntentId: string;
  amountCents: number;
  status: "pending" | "succeeded" | "failed";
  reason?: string;
  providerRefundId?: string;
  createdAt: string;
  updatedAt: string;
}

export interface Stats {
  succeededCount: number;
  failedCount: number;
  processingCount: number;
  succeededAmountCents: number;
}

export interface ListResponse {
  page: number;
  size: number;
  total: number;
  items: PaymentIntent[];
}

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

function randomId() {
  return "req_" + Math.random().toString(36).slice(2, 12) + Date.now().toString(36);
}

export class Api {
  constructor(private readonly token: string) {}

  private async req(path: string, init: RequestInit = {}): Promise<any> {
    const res = await fetch(path, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        Authorization: "Bearer " + this.token,
        ...(init.headers || {}),
      },
    });
    const text = await res.text();
    const body = text ? JSON.parse(text) : null;
    if (!res.ok) throw new ApiError(res.status, body?.error ?? body?.title ?? res.statusText);
    return body;
  }

  static async login(apiKey: string): Promise<{ token: string; merchantId: string }> {
    const res = await fetch("/auth/token", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ apiKey }),
    });
    const body = await res.json();
    if (!res.ok) throw new ApiError(res.status, body?.error ?? res.statusText);
    return body;
  }

  listPayments(page = 0, size = 25): Promise<ListResponse> {
    return this.req(`/v1/payment-intents?page=${page}&size=${size}`);
  }

  getPayment(id: string): Promise<PaymentIntent> {
    return this.req(`/v1/payment-intents/${id}`);
  }

  createPayment(body: {
    amountCents: number;
    currency: string;
    description?: string;
  }): Promise<PaymentIntent> {
    return this.req("/v1/payment-intents", {
      method: "POST",
      headers: { "Idempotency-Key": randomId() },
      body: JSON.stringify(body),
    });
  }

  cancelPayment(id: string): Promise<PaymentIntent> {
    return this.req(`/v1/payment-intents/${id}/cancel`, { method: "POST" });
  }

  stats(): Promise<Stats> {
    return this.req("/v1/payment-intents/stats");
  }

  createRefund(body: {
    paymentIntentId: string;
    amountCents?: number;
    reason?: string;
  }): Promise<Refund> {
    return this.req("/v1/refunds", {
      method: "POST",
      headers: { "Idempotency-Key": randomId() },
      body: JSON.stringify(body),
    });
  }
}

export function fmtMoney(cents: number, currency = "USD"): string {
  return new Intl.NumberFormat(undefined, {
    style: "currency",
    currency,
    minimumFractionDigits: 2,
  }).format(cents / 100);
}

export function fmtShortId(id: string): string {
  return id.slice(0, 8) + "…";
}

export function fmtTime(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}
