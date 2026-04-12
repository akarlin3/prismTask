import { useState, useRef, useEffect, useCallback, type ReactNode } from 'react';
import { ChevronDown, Search, X, Check } from 'lucide-react';

export interface SelectOption {
  value: string;
  label: string;
  icon?: ReactNode;
}

interface BaseSelectProps {
  options: SelectOption[];
  placeholder?: string;
  searchable?: boolean;
  disabled?: boolean;
  error?: string;
  label?: string;
  className?: string;
}

interface SingleSelectProps extends BaseSelectProps {
  multi?: false;
  value: string | null;
  onChange: (value: string | null) => void;
}

interface MultiSelectProps extends BaseSelectProps {
  multi: true;
  value: string[];
  onChange: (value: string[]) => void;
}

type SelectProps = SingleSelectProps | MultiSelectProps;

export function Select(props: SelectProps) {
  const {
    options,
    placeholder = 'Select...',
    searchable = false,
    disabled = false,
    error,
    label,
    className = '',
    multi,
  } = props;

  const [isOpen, setIsOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [highlightIndex, setHighlightIndex] = useState(0);
  const containerRef = useRef<HTMLDivElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLUListElement>(null);

  const filteredOptions = search
    ? options.filter((o) => o.label.toLowerCase().includes(search.toLowerCase()))
    : options;

  const isSelected = (optionValue: string) => {
    if (multi) return (props as MultiSelectProps).value.includes(optionValue);
    return (props as SingleSelectProps).value === optionValue;
  };

  const handleSelect = useCallback((optionValue: string) => {
    if (multi) {
      const multiProps = props as MultiSelectProps;
      const current = multiProps.value;
      if (current.includes(optionValue)) {
        multiProps.onChange(current.filter((v) => v !== optionValue));
      } else {
        multiProps.onChange([...current, optionValue]);
      }
    } else {
      (props as SingleSelectProps).onChange(optionValue);
      setIsOpen(false);
    }
    setSearch('');
  }, [multi, props]);

  const removeChip = (optionValue: string) => {
    if (multi) {
      const multiProps = props as MultiSelectProps;
      multiProps.onChange(multiProps.value.filter((v) => v !== optionValue));
    }
  };

  // Close on click outside
  useEffect(() => {
    if (!isOpen) return;
    const handler = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsOpen(false);
        setSearch('');
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [isOpen]);

  // Focus search on open
  useEffect(() => {
    if (isOpen && searchable) {
      searchRef.current?.focus();
    }
  }, [isOpen, searchable]);

  // Reset highlight when search changes (derived state pattern)
  const [prevSearch, setPrevSearch] = useState(search);
  if (search !== prevSearch) {
    setPrevSearch(search);
    setHighlightIndex(0);
  }

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (!isOpen) {
        if (e.key === 'Enter' || e.key === ' ' || e.key === 'ArrowDown') {
          e.preventDefault();
          setIsOpen(true);
        }
        return;
      }

      switch (e.key) {
        case 'ArrowDown':
          e.preventDefault();
          setHighlightIndex((i) => Math.min(i + 1, filteredOptions.length - 1));
          break;
        case 'ArrowUp':
          e.preventDefault();
          setHighlightIndex((i) => Math.max(i - 1, 0));
          break;
        case 'Enter':
          e.preventDefault();
          if (filteredOptions[highlightIndex]) {
            handleSelect(filteredOptions[highlightIndex].value);
          }
          break;
        case 'Escape':
          e.preventDefault();
          setIsOpen(false);
          setSearch('');
          break;
      }
    },
    [isOpen, filteredOptions, highlightIndex, handleSelect],
  );

  // Scroll highlighted into view
  useEffect(() => {
    if (!isOpen || !listRef.current) return;
    const el = listRef.current.children[highlightIndex] as HTMLElement;
    el?.scrollIntoView({ block: 'nearest' });
  }, [highlightIndex, isOpen]);

  const getDisplayValue = () => {
    if (multi) {
      const vals = (props as MultiSelectProps).value;
      if (vals.length === 0) return null;
      return (
        <div className="flex flex-wrap gap-1">
          {vals.map((v) => {
            const opt = options.find((o) => o.value === v);
            return (
              <span
                key={v}
                className="inline-flex items-center gap-1 rounded-md bg-[var(--color-bg-secondary)] px-2 py-0.5 text-xs text-[var(--color-text-primary)]"
              >
                {opt?.label || v}
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    removeChip(v);
                  }}
                  className="text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]"
                >
                  <X className="h-3 w-3" />
                </button>
              </span>
            );
          })}
        </div>
      );
    }

    const val = (props as SingleSelectProps).value;
    if (!val) return null;
    const opt = options.find((o) => o.value === val);
    return (
      <span className="text-[var(--color-text-primary)]">{opt?.label || val}</span>
    );
  };

  const displayValue = getDisplayValue();

  return (
    <div className={`flex flex-col gap-1.5 ${className}`} ref={containerRef}>
      {label && (
        <label className="text-sm font-medium text-[var(--color-text-primary)]">
          {label}
        </label>
      )}

      {/* Trigger */}
      <button
        type="button"
        disabled={disabled}
        onClick={() => setIsOpen(!isOpen)}
        onKeyDown={handleKeyDown}
        className={`flex min-h-[38px] w-full items-center justify-between rounded-lg border px-3 py-2 text-sm transition-colors ${
          isOpen
            ? 'border-[var(--color-accent)] ring-1 ring-[var(--color-accent)]'
            : 'border-[var(--color-border)]'
        } ${error ? 'border-red-500' : ''} bg-[var(--color-bg-card)] disabled:cursor-not-allowed disabled:opacity-50`}
        aria-expanded={isOpen}
        aria-haspopup="listbox"
      >
        <div className="flex-1 text-left">
          {displayValue || (
            <span className="text-[var(--color-text-secondary)]">{placeholder}</span>
          )}
        </div>
        <ChevronDown
          className={`ml-2 h-4 w-4 shrink-0 text-[var(--color-text-secondary)] transition-transform duration-150 ${
            isOpen ? 'rotate-180' : ''
          }`}
        />
      </button>

      {error && <p className="text-xs text-red-500">{error}</p>}

      {/* Dropdown */}
      {isOpen && (
        <div className="relative z-50">
          <div className="absolute left-0 top-0 w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-card)] shadow-lg">
            {/* Search */}
            {searchable && (
              <div className="border-b border-[var(--color-border)] p-2">
                <div className="relative">
                  <Search className="absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--color-text-secondary)]" />
                  <input
                    ref={searchRef}
                    type="text"
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    onKeyDown={handleKeyDown}
                    placeholder="Search..."
                    className="w-full rounded-md border border-[var(--color-border)] bg-[var(--color-bg-secondary)] py-1.5 pl-8 pr-3 text-sm text-[var(--color-text-primary)] outline-none focus:border-[var(--color-accent)]"
                  />
                </div>
              </div>
            )}

            {/* Options list */}
            <ul
              ref={listRef}
              role="listbox"
              className="max-h-60 overflow-y-auto p-1"
              aria-multiselectable={multi}
            >
              {filteredOptions.length === 0 ? (
                <li className="px-3 py-2 text-sm text-[var(--color-text-secondary)]">
                  No options found
                </li>
              ) : (
                filteredOptions.map((option, index) => (
                  <li
                    key={option.value}
                    role="option"
                    aria-selected={isSelected(option.value)}
                    onClick={() => handleSelect(option.value)}
                    onMouseEnter={() => setHighlightIndex(index)}
                    className={`flex cursor-pointer items-center gap-2 rounded-md px-3 py-2 text-sm transition-colors ${
                      highlightIndex === index
                        ? 'bg-[var(--color-bg-secondary)]'
                        : ''
                    } ${
                      isSelected(option.value)
                        ? 'text-[var(--color-accent)]'
                        : 'text-[var(--color-text-primary)]'
                    }`}
                  >
                    {option.icon}
                    <span className="flex-1">{option.label}</span>
                    {isSelected(option.value) && (
                      <Check className="h-4 w-4 text-[var(--color-accent)]" />
                    )}
                  </li>
                ))
              )}
            </ul>
          </div>
        </div>
      )}
    </div>
  );
}
