import { Component, type ReactNode, type ErrorInfo } from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('ErrorBoundary caught:', error, errorInfo);
  }

  handleReload = () => {
    window.location.reload();
  };

  handleReset = () => {
    this.setState({ hasError: false, error: null });
  };

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <div className="flex min-h-screen items-center justify-center bg-[var(--color-bg-primary)] p-4">
          <div className="max-w-md text-center">
            <div className="mx-auto mb-6 flex h-16 w-16 items-center justify-center rounded-full bg-red-500/10">
              <AlertTriangle className="h-8 w-8 text-red-500" />
            </div>
            <h1 className="mb-2 text-xl font-semibold text-[var(--color-text-primary)]">
              Something Went Wrong
            </h1>
            <p className="mb-6 text-sm text-[var(--color-text-secondary)]">
              An unexpected error occurred. Please try reloading the page.
            </p>
            {import.meta.env.DEV && this.state.error && (
              <pre className="mb-6 max-h-40 overflow-auto rounded-lg bg-[var(--color-bg-secondary)] p-3 text-left text-xs text-red-400">
                {this.state.error.message}
                {'\n'}
                {this.state.error.stack}
              </pre>
            )}
            <div className="flex justify-center gap-3">
              <button
                onClick={this.handleReset}
                className="rounded-lg border border-[var(--color-border)] px-4 py-2 text-sm font-medium text-[var(--color-text-primary)] transition-colors hover:bg-[var(--color-bg-secondary)]"
              >
                Try Again
              </button>
              <button
                onClick={this.handleReload}
                className="flex items-center gap-2 rounded-lg bg-[var(--color-accent)] px-4 py-2 text-sm font-medium text-white transition-colors hover:opacity-90"
              >
                <RefreshCw className="h-4 w-4" />
                Reload Page
              </button>
            </div>
            <p className="mt-6 text-xs text-[var(--color-text-secondary)]">
              If this problem persists, please{' '}
              <a
                href="https://github.com/akarlin3/prismtask/issues"
                target="_blank"
                rel="noopener noreferrer"
                className="text-[var(--color-accent)] hover:underline"
              >
                report an issue
              </a>
              .
            </p>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
