/**
 * Browser notification support for task reminders.
 * Uses the Web Notifications API with setTimeout for scheduling.
 *
 * Limitations:
 * - Notifications only fire while the tab is open
 * - For reliable reminders, users should install as PWA
 */

const scheduledTimers = new Map<number, ReturnType<typeof setTimeout>>();

export function isNotificationSupported(): boolean {
  return 'Notification' in window;
}

export async function requestNotificationPermission(): Promise<NotificationPermission> {
  if (!isNotificationSupported()) return 'denied';
  if (Notification.permission === 'granted') return 'granted';
  if (Notification.permission === 'denied') return 'denied';
  return Notification.requestPermission();
}

export function getNotificationPermission(): NotificationPermission {
  if (!isNotificationSupported()) return 'denied';
  return Notification.permission;
}

export function showNotification(
  title: string,
  options?: NotificationOptions,
): Notification | null {
  if (!isNotificationSupported() || Notification.permission !== 'granted') {
    return null;
  }
  return new Notification(title, {
    icon: '/favicon.svg',
    badge: '/favicon.svg',
    ...options,
  });
}

export function scheduleReminder(
  taskId: number,
  title: string,
  dueDate: string,
  offsetMs: number,
): void {
  cancelReminder(taskId);

  const dueTime = new Date(dueDate).getTime();
  const notifyAt = dueTime - offsetMs;
  const delay = notifyAt - Date.now();

  if (delay <= 0) return; // Already past

  const timer = setTimeout(() => {
    showNotification(`Task Reminder: ${title}`, {
      body: `Due at ${new Date(dueDate).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`,
      tag: `task-reminder-${taskId}`,
      requireInteraction: true,
    });
    scheduledTimers.delete(taskId);
  }, delay);

  scheduledTimers.set(taskId, timer);
}

export function cancelReminder(taskId: number): void {
  const existing = scheduledTimers.get(taskId);
  if (existing) {
    clearTimeout(existing);
    scheduledTimers.delete(taskId);
  }
}

export function cancelAllReminders(): void {
  for (const timer of scheduledTimers.values()) {
    clearTimeout(timer);
  }
  scheduledTimers.clear();
}
