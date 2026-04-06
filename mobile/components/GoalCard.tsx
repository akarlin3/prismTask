import React from "react";
import { View, Text, StyleSheet, TouchableOpacity } from "react-native";
import { Goal } from "../services/api";

interface GoalCardProps {
  goal: Goal;
  onPress: (goal: Goal) => void;
  projectCount?: number;
}

function formatDate(dateStr: string | null): string {
  if (!dateStr) return "";
  const date = new Date(dateStr);
  return date.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

function getStatusStyle(status: string) {
  switch (status) {
    case "active":
      return { bg: "#DBEAFE", text: "#2563EB", label: "Active" };
    case "completed":
      return { bg: "#D1FAE5", text: "#059669", label: "Completed" };
    case "archived":
      return { bg: "#F3F4F6", text: "#6B7280", label: "Archived" };
    case "on_hold":
      return { bg: "#FEF3C7", text: "#D97706", label: "On Hold" };
    default:
      return { bg: "#F3F4F6", text: "#6B7280", label: status };
  }
}

export default function GoalCard({
  goal,
  onPress,
  projectCount,
}: GoalCardProps) {
  const statusStyle = getStatusStyle(goal.status);
  const count = projectCount ?? goal.projects?.length ?? 0;

  return (
    <TouchableOpacity
      style={styles.card}
      onPress={() => onPress(goal)}
      activeOpacity={0.7}
    >
      <View
        style={[
          styles.stripe,
          { backgroundColor: goal.color || "#3B82F6" },
        ]}
      />
      <View style={styles.content}>
        <View style={styles.header}>
          <Text style={styles.title} numberOfLines={2}>
            {goal.title}
          </Text>
          <View
            style={[styles.statusBadge, { backgroundColor: statusStyle.bg }]}
          >
            <Text style={[styles.statusText, { color: statusStyle.text }]}>
              {statusStyle.label}
            </Text>
          </View>
        </View>

        <View style={styles.footer}>
          {goal.target_date && (
            <Text style={styles.date}>
              Target: {formatDate(goal.target_date)}
            </Text>
          )}
          <Text style={styles.projectCount}>
            {count} {count === 1 ? "project" : "projects"}
          </Text>
        </View>
      </View>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  card: {
    flexDirection: "row",
    backgroundColor: "#FFFFFF",
    borderRadius: 10,
    borderWidth: 1,
    borderColor: "#E5E7EB",
    marginBottom: 8,
    overflow: "hidden",
  },
  stripe: {
    width: 4,
  },
  content: {
    flex: 1,
    padding: 14,
  },
  header: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
    marginBottom: 10,
  },
  title: {
    fontSize: 16,
    fontWeight: "600",
    color: "#1A1A2E",
    flex: 1,
    marginRight: 8,
  },
  statusBadge: {
    paddingHorizontal: 10,
    paddingVertical: 3,
    borderRadius: 12,
  },
  statusText: {
    fontSize: 12,
    fontWeight: "600",
  },
  footer: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  date: {
    fontSize: 12,
    color: "#6B7280",
  },
  projectCount: {
    fontSize: 12,
    color: "#9CA3AF",
  },
});
