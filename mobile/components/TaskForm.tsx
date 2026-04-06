import React, { useState } from "react";
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  ScrollView,
  StyleSheet,
} from "react-native";
import { PriorityColors, PriorityLabels } from "../constants/colors";

interface TaskFormData {
  title: string;
  description: string;
  priority: number;
  due_date: string | null;
  status: string;
}

interface TaskFormProps {
  initialData?: Partial<TaskFormData>;
  onSubmit: (data: TaskFormData) => void;
  onCancel: () => void;
  submitLabel?: string;
}

function getNextMonday(): Date {
  const d = new Date();
  const day = d.getDay();
  const diff = day === 0 ? 1 : 8 - day;
  d.setDate(d.getDate() + diff);
  d.setHours(0, 0, 0, 0);
  return d;
}

function getNextFriday(): Date {
  const d = new Date();
  const day = d.getDay();
  const diff = day <= 5 ? 5 - day : 12 - day;
  const add = diff === 0 ? 7 : diff;
  d.setDate(d.getDate() + add);
  d.setHours(0, 0, 0, 0);
  return d;
}

function formatDateDisplay(dateStr: string | null): string {
  if (!dateStr) return "No date";
  const d = new Date(dateStr);
  return d.toLocaleDateString("en-US", {
    weekday: "short",
    month: "short",
    day: "numeric",
  });
}

const PRIORITIES = [1, 2, 3, 4];
const STATUSES = [
  { value: "todo", label: "Todo" },
  { value: "in_progress", label: "In Progress" },
  { value: "done", label: "Done" },
];

export default function TaskForm({
  initialData,
  onSubmit,
  onCancel,
  submitLabel = "Save",
}: TaskFormProps) {
  const [title, setTitle] = useState(initialData?.title || "");
  const [description, setDescription] = useState(
    initialData?.description || ""
  );
  const [priority, setPriority] = useState(initialData?.priority || 4);
  const [dueDate, setDueDate] = useState<string | null>(
    initialData?.due_date || null
  );
  const [status, setStatus] = useState(initialData?.status || "todo");
  const [showDateInput, setShowDateInput] = useState(false);
  const [customDateText, setCustomDateText] = useState("");

  const handleSubmit = () => {
    if (!title.trim()) return;
    onSubmit({
      title: title.trim(),
      description: description.trim(),
      priority,
      due_date: dueDate,
      status,
    });
  };

  const setQuickDate = (date: Date | null) => {
    if (!date) {
      setDueDate(null);
    } else {
      setDueDate(date.toISOString().split("T")[0]);
    }
    setShowDateInput(false);
  };

  const handleCustomDateSubmit = () => {
    const parsed = new Date(customDateText);
    if (!isNaN(parsed.getTime())) {
      setDueDate(parsed.toISOString().split("T")[0]);
      setShowDateInput(false);
      setCustomDateText("");
    }
  };

  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const tomorrow = new Date(today);
  tomorrow.setDate(tomorrow.getDate() + 1);

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={styles.contentContainer}
      keyboardShouldPersistTaps="handled"
    >
      {/* Title */}
      <Text style={styles.label}>Title *</Text>
      <TextInput
        style={styles.input}
        value={title}
        onChangeText={setTitle}
        placeholder="Task title"
        placeholderTextColor="#9CA3AF"
        autoFocus
      />

      {/* Description */}
      <Text style={styles.label}>Description</Text>
      <TextInput
        style={[styles.input, styles.textArea]}
        value={description}
        onChangeText={setDescription}
        placeholder="Add details..."
        placeholderTextColor="#9CA3AF"
        multiline
        numberOfLines={4}
        textAlignVertical="top"
      />

      {/* Priority */}
      <Text style={styles.label}>Priority</Text>
      <View style={styles.chipRow}>
        {PRIORITIES.map((p) => {
          const active = priority === p;
          const color = PriorityColors[p];
          return (
            <TouchableOpacity
              key={p}
              style={[
                styles.chip,
                active && { backgroundColor: color + "20", borderColor: color },
              ]}
              onPress={() => setPriority(p)}
            >
              <View
                style={[styles.chipDot, { backgroundColor: color }]}
              />
              <Text
                style={[
                  styles.chipText,
                  active && { color, fontWeight: "600" },
                ]}
              >
                {PriorityLabels[p]}
              </Text>
            </TouchableOpacity>
          );
        })}
      </View>

      {/* Due Date */}
      <Text style={styles.label}>Due Date</Text>
      <Text style={styles.dateDisplay}>{formatDateDisplay(dueDate)}</Text>
      <View style={styles.chipRow}>
        <TouchableOpacity
          style={styles.dateChip}
          onPress={() => setQuickDate(today)}
        >
          <Text style={styles.dateChipText}>Today</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={styles.dateChip}
          onPress={() => setQuickDate(tomorrow)}
        >
          <Text style={styles.dateChipText}>Tomorrow</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={styles.dateChip}
          onPress={() => setQuickDate(getNextMonday())}
        >
          <Text style={styles.dateChipText}>Next Mon</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={styles.dateChip}
          onPress={() => setQuickDate(getNextFriday())}
        >
          <Text style={styles.dateChipText}>Next Fri</Text>
        </TouchableOpacity>
      </View>
      <View style={styles.chipRow}>
        <TouchableOpacity
          style={styles.dateChip}
          onPress={() => setQuickDate(null)}
        >
          <Text style={styles.dateChipText}>No date</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={styles.dateChip}
          onPress={() => setShowDateInput(!showDateInput)}
        >
          <Text style={styles.dateChipText}>Pick date...</Text>
        </TouchableOpacity>
      </View>

      {showDateInput && (
        <View style={styles.customDateRow}>
          <TextInput
            style={styles.customDateInput}
            value={customDateText}
            onChangeText={setCustomDateText}
            placeholder="YYYY-MM-DD"
            placeholderTextColor="#9CA3AF"
            keyboardType="numbers-and-punctuation"
            returnKeyType="done"
            onSubmitEditing={handleCustomDateSubmit}
          />
          <TouchableOpacity
            style={styles.customDateButton}
            onPress={handleCustomDateSubmit}
          >
            <Text style={styles.customDateButtonText}>Set</Text>
          </TouchableOpacity>
        </View>
      )}

      {/* Status */}
      <Text style={styles.label}>Status</Text>
      <View style={styles.chipRow}>
        {STATUSES.map((s) => {
          const active = status === s.value;
          return (
            <TouchableOpacity
              key={s.value}
              style={[styles.chip, active && styles.chipActive]}
              onPress={() => setStatus(s.value)}
            >
              <Text
                style={[styles.chipText, active && styles.chipTextActive]}
              >
                {s.label}
              </Text>
            </TouchableOpacity>
          );
        })}
      </View>

      {/* Actions */}
      <View style={styles.actions}>
        <TouchableOpacity style={styles.cancelButton} onPress={onCancel}>
          <Text style={styles.cancelText}>Cancel</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[
            styles.submitButton,
            !title.trim() && styles.submitButtonDisabled,
          ]}
          onPress={handleSubmit}
          disabled={!title.trim()}
        >
          <Text style={styles.submitText}>{submitLabel}</Text>
        </TouchableOpacity>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#FFFFFF",
  },
  contentContainer: {
    padding: 20,
  },
  label: {
    fontSize: 13,
    fontWeight: "600",
    color: "#374151",
    marginBottom: 6,
    marginTop: 16,
  },
  input: {
    borderWidth: 1,
    borderColor: "#E5E7EB",
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 15,
    color: "#1A1A2E",
    backgroundColor: "#FFFFFF",
  },
  textArea: {
    minHeight: 80,
  },
  chipRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    marginTop: 4,
  },
  chip: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 12,
    paddingVertical: 7,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: "#E5E7EB",
    backgroundColor: "#F9FAFB",
  },
  chipActive: {
    backgroundColor: "#DBEAFE",
    borderColor: "#3B82F6",
  },
  chipDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    marginRight: 6,
  },
  chipText: {
    fontSize: 13,
    color: "#6B7280",
  },
  chipTextActive: {
    color: "#2563EB",
    fontWeight: "600",
  },
  dateDisplay: {
    fontSize: 14,
    color: "#1A1A2E",
    marginBottom: 8,
  },
  dateChip: {
    paddingHorizontal: 12,
    paddingVertical: 7,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: "#E5E7EB",
    backgroundColor: "#F9FAFB",
  },
  dateChipText: {
    fontSize: 13,
    color: "#6B7280",
  },
  customDateRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    marginTop: 8,
  },
  customDateInput: {
    flex: 1,
    borderWidth: 1,
    borderColor: "#E5E7EB",
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
    fontSize: 14,
    color: "#1A1A2E",
  },
  customDateButton: {
    backgroundColor: "#3B82F6",
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 8,
  },
  customDateButtonText: {
    color: "#FFFFFF",
    fontSize: 14,
    fontWeight: "600",
  },
  actions: {
    flexDirection: "row",
    justifyContent: "flex-end",
    gap: 12,
    marginTop: 30,
    paddingBottom: 20,
  },
  cancelButton: {
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: "#E5E7EB",
  },
  cancelText: {
    fontSize: 14,
    color: "#6B7280",
    fontWeight: "500",
  },
  submitButton: {
    paddingHorizontal: 24,
    paddingVertical: 10,
    borderRadius: 8,
    backgroundColor: "#3B82F6",
  },
  submitButtonDisabled: {
    backgroundColor: "#D1D5DB",
  },
  submitText: {
    fontSize: 14,
    color: "#FFFFFF",
    fontWeight: "600",
  },
});
