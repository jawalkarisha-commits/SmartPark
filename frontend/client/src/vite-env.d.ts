/// <reference types="vite/client" />

interface RazorpayCheckoutResult {
  razorpay_order_id: string;
  razorpay_payment_id: string;
  razorpay_signature: string;
}

interface RazorpayCheckoutOptions {
  key: string;
  amount: number;
  currency: string;
  name: string;
  description: string;
  order_id: string;
  prefill: { name?: string; email?: string; contact?: string };
  handler(response: RazorpayCheckoutResult): void | Promise<void>;
  modal: { ondismiss(): void };
}

interface Window {
  Razorpay?: new (options: RazorpayCheckoutOptions) => {
    open(): void;
  };
}
