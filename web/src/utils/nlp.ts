import type { TaskPriority } from '@/types/task';

interface LocalParseResult {
  title: string;
  priority: TaskPriority | null;
  dueDate: string | null;
  tags: string[];
  project: string | null;
}

/**
 * Local NLP fallback parser for quick-add input.
 * Extracts priority (!), tags (#), project (@), and basic date keywords.
 */
export function parseQuickAdd(input: string): LocalParseResult {
  let text = input.trim();
  let priority: TaskPriority | null = null;
  const tags: string[] = [];
  let project: string | null = null;
  let dueDate: string | null = null;

  // Extract priority markers: !1, !2, !3, !4 or !urgent, !high, !medium, !low
  const priorityMatch = text.match(
    /!(\d|urgent|high|medium|low)\b/i,
  );
  if (priorityMatch) {
    const val = priorityMatch[1].toLowerCase();
    const priorityMap: Record<string, TaskPriority> = {
      '1': 1, urgent: 1,
      '2': 2, high: 2,
      '3': 3, medium: 3,
      '4': 4, low: 4,
    };
    priority = priorityMap[val] ?? null;
    text = text.replace(priorityMatch[0], '').trim();
  }

  // Extract tags: #tagname
  const tagMatches = text.matchAll(/#(\w+)/g);
  for (const match of tagMatches) {
    tags.push(match[1]);
  }
  text = text.replace(/#\w+/g, '').trim();

  // Extract project: @projectname
  const projectMatch = text.match(/@(\w+)/);
  if (projectMatch) {
    project = projectMatch[1];
    text = text.replace(projectMatch[0], '').trim();
  }

  // Extract basic date keywords
  const today = new Date();
  const dateKeywords: Record<string, () => Date> = {
    today: () => today,
    tomorrow: () => {
      const d = new Date(today);
      d.setDate(d.getDate() + 1);
      return d;
    },
    'next week': () => {
      const d = new Date(today);
      d.setDate(d.getDate() + 7);
      return d;
    },
  };

  for (const [keyword, getDate] of Object.entries(dateKeywords)) {
    const regex = new RegExp(`\\b${keyword}\\b`, 'i');
    if (regex.test(text)) {
      dueDate = getDate().toISOString().split('T')[0];
      text = text.replace(regex, '').trim();
      break;
    }
  }

  return {
    title: text.replace(/\s+/g, ' ').trim(),
    priority,
    dueDate,
    tags,
    project,
  };
}
