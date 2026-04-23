import { Code2, ExternalLink } from 'lucide-react';

/**
 * About — static version info + policy / repo links. Matches the
 * Android `AboutSection` shape. Version is hard-coded on web; when
 * we start auto-versioning the web build a build-time replacement
 * would swap this for the real value.
 */
export function AboutSection() {
  return (
    <div className="flex flex-col gap-3 text-sm text-[var(--color-text-primary)]">
      <Row label="Web client" value="prismtask-web" />
      <Row label="Backend" value="averytask-production.up.railway.app" />
      <LinkRow
        label="Privacy policy"
        href="https://github.com/akarlin3/prismTask/blob/main/docs/PRIVACY_POLICY.md"
      />
      <LinkRow
        label="Terms of service"
        href="https://github.com/akarlin3/prismTask/blob/main/docs/TERMS_OF_SERVICE.md"
      />
      <LinkRow
        label="Source"
        href="https://github.com/akarlin3/prismTask"
        icon={<Code2 className="h-4 w-4" aria-hidden="true" />}
      />
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-[var(--color-text-secondary)]">{label}</span>
      <span className="font-mono text-xs">{value}</span>
    </div>
  );
}

function LinkRow({
  label,
  href,
  icon,
}: {
  label: string;
  href: string;
  icon?: React.ReactNode;
}) {
  return (
    <a
      href={href}
      target="_blank"
      rel="noreferrer noopener"
      className="flex items-center justify-between rounded-md hover:bg-[var(--color-bg-secondary)]"
    >
      <span className="flex items-center gap-2 text-[var(--color-text-primary)]">
        {icon}
        {label}
      </span>
      <ExternalLink
        className="h-4 w-4 text-[var(--color-text-secondary)]"
        aria-hidden="true"
      />
    </a>
  );
}
