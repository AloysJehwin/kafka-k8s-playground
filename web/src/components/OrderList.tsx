import { Order } from '../types';

const STATUS_STYLES: Record<string, string> = {
  PLACED: 'bg-blue-100 text-blue-700 ring-blue-600/20',
  CONFIRMED: 'bg-emerald-100 text-emerald-700 ring-emerald-600/20',
  REJECTED: 'bg-red-100 text-red-700 ring-red-600/20',
};

export function OrderList({ orders }: { orders: Order[] }) {
  if (orders.length === 0) {
    return (
      <div className="bg-white p-6 rounded-lg shadow-sm border text-center text-slate-500">
        No orders yet — place one to start the saga.
      </div>
    );
  }

  return (
    <div className="bg-white rounded-lg shadow-sm border overflow-hidden">
      <div className="px-6 py-4 border-b">
        <h2 className="text-lg font-semibold">My Orders</h2>
        <p className="text-xs text-slate-500">Live updates from orders.completed Kafka topic</p>
      </div>
      <ul className="divide-y">
        {orders.map((o) => (
          <li key={o.id} className="px-6 py-4 flex items-center justify-between">
            <div>
              <div className="font-mono text-xs text-slate-500">{o.id.slice(0, 8)}…</div>
              <div className="text-sm">
                {o.productId} × {o.quantity} —{' '}
                <span className="font-mono">
                  ${Number(o.amount).toFixed(2)} {o.currency}
                </span>
              </div>
              <div className="text-xs text-slate-400 mt-1">
                {new Date(o.createdAt).toLocaleString()}
              </div>
            </div>
            <span
              className={`text-xs font-medium px-2.5 py-1 rounded-full ring-1 ring-inset ${
                STATUS_STYLES[o.status] || 'bg-slate-100 text-slate-700'
              }`}
            >
              {o.status}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
