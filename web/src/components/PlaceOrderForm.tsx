import { useState } from 'react';
import { placeOrder } from '../api';

const PRODUCTS = [
  { id: 'p1', name: 'Mechanical Keyboard', price: 149.0 },
  { id: 'p2', name: 'USB-C Hub', price: 49.5 },
  { id: 'p3', name: 'Premium Monitor', price: 1299.0 },
  { id: 'p4', name: 'Wireless Mouse', price: 79.0 },
];

export function PlaceOrderForm({ onPlaced }: { onPlaced: () => void }) {
  const [productId, setProductId] = useState(PRODUCTS[0].id);
  const [quantity, setQuantity] = useState(1);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const product = PRODUCTS.find((p) => p.id === productId)!;
  const total = product.price * quantity;

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setErr(null);
    try {
      await placeOrder({
        productId,
        quantity,
        amount: total,
        currency: 'USD',
      });
      onPlaced();
    } catch (e: any) {
      setErr(e.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="bg-white p-6 rounded-lg shadow-sm border space-y-4">
      <h2 className="text-lg font-semibold">Place Order</h2>

      <div>
        <label className="block text-sm font-medium mb-1">Product</label>
        <select
          value={productId}
          onChange={(e) => setProductId(e.target.value)}
          className="w-full border rounded px-3 py-2"
        >
          {PRODUCTS.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name} — ${p.price.toFixed(2)}
            </option>
          ))}
        </select>
      </div>

      <div>
        <label className="block text-sm font-medium mb-1">Quantity</label>
        <input
          type="number"
          min={1}
          max={100}
          value={quantity}
          onChange={(e) => setQuantity(Number(e.target.value))}
          className="w-full border rounded px-3 py-2"
        />
      </div>

      <div className="flex justify-between items-center text-sm">
        <span className="text-slate-500">Total</span>
        <span className="font-mono font-semibold">${total.toFixed(2)} USD</span>
      </div>

      {total > 1000 && (
        <div className="text-xs text-amber-600 bg-amber-50 p-2 rounded">
          Heads up: orders &gt; $1000 are auto-declined by payment-service.
        </div>
      )}

      {err && <div className="text-red-600 text-sm">{err}</div>}

      <button
        type="submit"
        disabled={busy}
        className="w-full bg-indigo-600 text-white py-2 rounded hover:bg-indigo-700 disabled:opacity-50"
      >
        {busy ? 'Placing…' : 'Place Order'}
      </button>
    </form>
  );
}
