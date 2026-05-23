export interface User {
  sub: string;
  email: string;
  name: string;
  picture: string;
}

export interface Order {
  id: string;
  customerId: string;
  productId: string;
  quantity: number;
  amount: number;
  currency: string;
  status: 'PLACED' | 'CONFIRMED' | 'REJECTED';
  createdAt: string;
}

export interface OrderCompletedEvent {
  orderId: string;
  status: 'CONFIRMED' | 'REJECTED';
  reason: string | null;
  completedAt: string;
}
