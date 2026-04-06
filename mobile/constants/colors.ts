export const Colors = {
  light: {
    background: "#FFFFFF",
    surface: "#F8F9FA",
    text: "#1A1A2E",
    textSecondary: "#6B7280",
    border: "#E5E7EB",
    primary: "#3B82F6",
    primaryLight: "#DBEAFE",
    success: "#10B981",
    successLight: "#D1FAE5",
    warning: "#F59E0B",
    warningLight: "#FEF3C7",
    danger: "#EF4444",
    dangerLight: "#FEE2E2",
    gray: "#9CA3AF",
    grayLight: "#F3F4F6",
    card: "#FFFFFF",
  },
  dark: {
    background: "#1A1A2E",
    surface: "#16213E",
    text: "#E5E7EB",
    textSecondary: "#9CA3AF",
    border: "#374151",
    primary: "#60A5FA",
    primaryLight: "#1E3A5F",
    success: "#34D399",
    successLight: "#064E3B",
    warning: "#FBBF24",
    warningLight: "#78350F",
    danger: "#F87171",
    dangerLight: "#7F1D1D",
    gray: "#6B7280",
    grayLight: "#1F2937",
    card: "#16213E",
  },
};

export const PriorityColors: Record<number, string> = {
  1: "#EF4444", // urgent - red
  2: "#F97316", // high - orange
  3: "#3B82F6", // medium - blue
  4: "#9CA3AF", // low - gray
};

export const PriorityLabels: Record<number, string> = {
  1: "Urgent",
  2: "High",
  3: "Medium",
  4: "Low",
};
