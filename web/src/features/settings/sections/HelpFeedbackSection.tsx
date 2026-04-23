import { Bug, HelpCircle, Mail, MessageSquare } from 'lucide-react';

/**
 * Help & Feedback — links to bug tracker, email, keyboard-shortcuts
 * summary. Client-only; no backend form submission yet.
 */
export function HelpFeedbackSection() {
  return (
    <ul className="flex flex-col gap-2 text-sm">
      <LinkItem
        href="https://github.com/akarlin3/prismTask/issues/new?labels=bug,web"
        icon={<Bug className="h-4 w-4" aria-hidden="true" />}
        title="Report a bug"
        sub="Opens a new GitHub issue pre-tagged for the web client."
      />
      <LinkItem
        href="https://github.com/akarlin3/prismTask/issues/new?labels=enhancement,web"
        icon={<MessageSquare className="h-4 w-4" aria-hidden="true" />}
        title="Request a feature"
        sub="Suggest an improvement. Phase G parity work is tracked in docs/WEB_PARITY_GAP_ANALYSIS.md."
      />
      <LinkItem
        href="mailto:support@prismtask.app"
        icon={<Mail className="h-4 w-4" aria-hidden="true" />}
        title="Email support"
        sub="Account, billing, or private feedback."
      />
      <LinkItem
        href="https://github.com/akarlin3/prismTask#keyboard-shortcuts"
        icon={<HelpCircle className="h-4 w-4" aria-hidden="true" />}
        title="Keyboard shortcuts"
        sub="Full list of `/`, Ctrl+K, etc. The in-app modal is also on `?`."
      />
    </ul>
  );
}

function LinkItem({
  href,
  icon,
  title,
  sub,
}: {
  href: string;
  icon: React.ReactNode;
  title: string;
  sub: string;
}) {
  return (
    <li>
      <a
        href={href}
        target="_blank"
        rel="noreferrer noopener"
        className="flex items-start gap-3 rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] p-3 transition-colors hover:border-[var(--color-accent)]/40"
      >
        <span className="mt-0.5 text-[var(--color-accent)]">{icon}</span>
        <span className="flex-1">
          <span className="block font-medium text-[var(--color-text-primary)]">
            {title}
          </span>
          <span className="block text-xs text-[var(--color-text-secondary)]">
            {sub}
          </span>
        </span>
      </a>
    </li>
  );
}
