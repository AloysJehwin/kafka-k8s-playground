import { useEffect, useState } from 'react';
import { fetchMe, fetchMyOrders } from './api';
import { Order, OrderCompletedEvent, User } from './types';
import { Login } from './components/Login';
import { Header } from './components/Header';
import { PlaceOrderForm } from './components/PlaceOrderForm';
import { OrderList } from './components/OrderList';

export default function App() {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [orders, setOrders] = useState<Order[]>([]);

  useEffect(() => {
    fetchMe()
      .then(setUser)
      .catch(() => setUser(null))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (!user) return;
    refresh();
    const es = new EventSource('/api/events', { withCredentials: true });
    es.addEventListener('order-completed', (ev) => {
      const data = JSON.parse((ev as MessageEvent).data) as OrderCompletedEvent;
      setOrders((prev) =>
        prev.map((o) =>
          o.id === data.orderId ? { ...o, status: data.status } : o
        )
      );
    });
    es.onerror = () => es.close();
    return () => es.close();
  }, [user]);

  const [error, setError] = useState<string | null>(null);

  async function refresh() {
    try {
      const list = await fetchMyOrders();
      setOrders(list);
      setError(null);
    } catch (e: any) {
      setError(e.message);
    }
  }

  if (loading) {
    return <div className="min-h-screen flex items-center justify-center">Loading…</div>;
  }
  if (!user) return <Login />;

  return (
    <div className="min-h-screen">
      <Header user={user} />
      <main className="max-w-5xl mx-auto px-6 py-8 grid md:grid-cols-2 gap-6">
        {error && (
          <div className="md:col-span-2 bg-red-50 text-red-700 px-4 py-2 rounded text-sm">
            {error}
          </div>
        )}
        <PlaceOrderForm onPlaced={refresh} />
        <OrderList orders={orders} />
      </main>
    </div>
  );
}
