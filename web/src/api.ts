import { Order, User } from './types';

export async function fetchMe(): Promise<User | null> {
  const res = await fetch('/api/me', { credentials: 'include' });
  if (res.status === 401) return null;
  if (!res.ok) throw new Error(`me failed: ${res.status}`);
  return res.json();
}

export async function fetchMyOrders(): Promise<Order[]> {
  const res = await fetch('/api/orders', { credentials: 'include' });
  if (!res.ok) throw new Error(`list failed: ${res.status}`);
  return res.json();
}

export async function placeOrder(payload: {
  productId: string;
  quantity: number;
  amount: number;
  currency: string;
}): Promise<{ id: string; status: string }> {
  const res = await fetch('/api/orders', {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  if (!res.ok) throw new Error(`place failed: ${res.status}`);
  return res.json();
}

export function login() {
  window.location.href = '/oauth2/authorization/google';
}

export async function logout() {
  await fetch('/logout', { method: 'POST', credentials: 'include' });
  window.location.reload();
}
