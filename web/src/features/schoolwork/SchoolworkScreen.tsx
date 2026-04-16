import { useState } from 'react';
import { ArrowLeft } from 'lucide-react';
import { SyllabusImport } from './SyllabusImport';
import { SyllabusReviewPanel } from './SyllabusReviewPanel';
import type { SyllabusParseResult } from './syllabusTypes';

export function SchoolworkScreen() {
  const [parseResult, setParseResult] = useState<SyllabusParseResult | null>(null);

  return (
    <div className="mx-auto max-w-2xl p-4">
      {/* Header */}
      <div className="mb-6">
        {parseResult ? (
          <button
            onClick={() => setParseResult(null)}
            className="mb-2 inline-flex items-center gap-1 text-sm text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]"
          >
            <ArrowLeft className="h-4 w-4" />
            Back
          </button>
        ) : null}
        <h1 className="text-2xl font-bold text-[var(--color-text-primary)]">
          Schoolwork
        </h1>
        {!parseResult && (
          <p className="text-sm text-[var(--color-text-secondary)]">
            Import a syllabus PDF to auto-create tasks, events, and recurring schedules
          </p>
        )}
      </div>

      {/* Content */}
      {parseResult ? (
        <SyllabusReviewPanel result={parseResult} onDone={() => setParseResult(null)} />
      ) : (
        <SyllabusImport onParsed={setParseResult} />
      )}
    </div>
  );
}
