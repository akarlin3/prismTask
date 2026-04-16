import { useRef, useState } from 'react';
import { Upload, Loader2 } from 'lucide-react';
import { toast } from 'sonner';
import { ProUpgradeModal } from '@/components/shared/ProUpgradeModal';
import { useProFeature } from '@/hooks/useProFeature';
import { parseSyllabus } from './syllabusApi';
import type { SyllabusParseResult } from './syllabusTypes';

const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

interface SyllabusImportProps {
  onParsed: (result: SyllabusParseResult) => void;
}

export function SyllabusImport({ onParsed }: SyllabusImportProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [loading, setLoading] = useState(false);
  const { isPro, showUpgrade, setShowUpgrade, gatedAction } = useProFeature();

  const handleFileSelect = async (file: File) => {
    if (file.size > MAX_FILE_SIZE) {
      toast.error('PDF must be under 10MB');
      return;
    }
    if (file.type !== 'application/pdf') {
      toast.error('Only PDF files are supported');
      return;
    }

    setLoading(true);
    try {
      const result = await parseSyllabus(file);
      if (
        result.tasks.length === 0 &&
        result.events.length === 0 &&
        result.recurring_schedule.length === 0
      ) {
        toast.error('No deadlines or events found in this syllabus');
        return;
      }
      onParsed(result);
    } catch (err) {
      const message =
        err instanceof Error ? err.message : 'Upload failed - check your connection';
      toast.error(message);
    } finally {
      setLoading(false);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    }
  };

  const handleClick = () => {
    gatedAction(() => {
      fileInputRef.current?.click();
    });
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      handleFileSelect(file);
    }
  };

  return (
    <>
      <input
        ref={fileInputRef}
        type="file"
        accept=".pdf,application/pdf"
        onChange={handleInputChange}
        className="hidden"
      />

      {loading ? (
        <div className="flex flex-col items-center gap-3 rounded-xl border-2 border-dashed border-amber-300 bg-amber-50/30 p-8">
          <Loader2 className="h-8 w-8 animate-spin text-amber-500" />
          <p className="text-sm font-medium text-[var(--color-text-secondary)]">
            Analyzing Your Syllabus...
          </p>
        </div>
      ) : (
        <button
          onClick={handleClick}
          className="flex w-full flex-col items-center gap-3 rounded-xl border-2 border-dashed border-[var(--color-border)] p-8 transition-colors hover:border-amber-400 hover:bg-amber-50/20"
        >
          <div className="flex h-12 w-12 items-center justify-center rounded-full bg-amber-100">
            <Upload className="h-6 w-6 text-amber-600" />
          </div>
          <div className="text-center">
            <p className="text-sm font-semibold text-[var(--color-text-primary)]">
              Import Syllabus
            </p>
            <p className="text-xs text-[var(--color-text-secondary)]">
              Upload a PDF to auto-create tasks, events, and schedules
            </p>
          </div>
          {!isPro && (
            <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-semibold text-amber-700">
              PRO
            </span>
          )}
        </button>
      )}

      <ProUpgradeModal
        isOpen={showUpgrade}
        onClose={() => setShowUpgrade(false)}
        featureName="Syllabus Import"
        featureDescription="Import your course syllabus and auto-create tasks, events, and schedules"
      />
    </>
  );
}
