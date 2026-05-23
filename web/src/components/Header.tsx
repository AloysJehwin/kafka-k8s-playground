import { User } from '../types';
import { logout } from '../api';

export function Header({ user }: { user: User }) {
  return (
    <header className="bg-white border-b sticky top-0 z-10">
      <div className="max-w-5xl mx-auto px-6 py-3 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 bg-gradient-to-br from-indigo-500 to-purple-600 rounded" />
          <span className="font-semibold">Eventflow</span>
          <span className="text-xs text-slate-400 hidden sm:inline">
            Kafka order saga demo
          </span>
        </div>
        <div className="flex items-center gap-3">
          <img
            src={user.picture}
            alt={user.name}
            className="w-8 h-8 rounded-full"
            referrerPolicy="no-referrer"
          />
          <div className="hidden sm:block text-right">
            <div className="text-sm font-medium">{user.name}</div>
            <div className="text-xs text-slate-500">{user.email}</div>
          </div>
          <button
            onClick={logout}
            className="text-sm text-slate-600 hover:text-slate-900 px-3 py-1 border rounded"
          >
            Sign out
          </button>
        </div>
      </div>
    </header>
  );
}
