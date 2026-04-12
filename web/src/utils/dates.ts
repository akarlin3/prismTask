import {
  format,
  formatDistanceToNow,
  isToday,
  isTomorrow,
  isYesterday,
  isPast,
  parseISO,
  differenceInDays,
  startOfDay,
} from 'date-fns';

export function formatDate(dateStr: string | null): string {
  if (!dateStr) return '';
  const date = parseISO(dateStr);

  if (isToday(date)) return 'Today';
  if (isTomorrow(date)) return 'Tomorrow';
  if (isYesterday(date)) return 'Yesterday';

  const daysAway = differenceInDays(startOfDay(date), startOfDay(new Date()));
  if (daysAway > 0 && daysAway <= 6) {
    return format(date, 'EEEE'); // Day name
  }

  return format(date, 'MMM d, yyyy');
}

export function formatDateTime(dateStr: string | null): string {
  if (!dateStr) return '';
  return format(parseISO(dateStr), 'MMM d, yyyy h:mm a');
}

export function formatRelative(dateStr: string | null): string {
  if (!dateStr) return '';
  return formatDistanceToNow(parseISO(dateStr), { addSuffix: true });
}

export function isOverdue(dateStr: string | null): boolean {
  if (!dateStr) return false;
  const date = parseISO(dateStr);
  return isPast(startOfDay(date)) && !isToday(date);
}

export function formatTime(timeStr: string | null): string {
  if (!timeStr) return '';
  const [hours, minutes] = timeStr.split(':').map(Number);
  const period = hours >= 12 ? 'PM' : 'AM';
  const displayHour = hours % 12 || 12;
  return `${displayHour}:${String(minutes).padStart(2, '0')} ${period}`;
}
