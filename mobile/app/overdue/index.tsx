import React, { useEffect, useState, useCallback } from "react";
import {
  View,
  Text,
  FlatList,
  StyleSheet,
  RefreshControl,
  TouchableOpacity,
} from "react-native";
import Toast from "react-native-toast-message";
import { useStore } from "../../store/useStore";
import { TaskCard } from "../../components/TaskCard";
import { EmptyState } from "../../components/EmptyState";
import { SkeletonLoader } from "../../components/SkeletonLoader";
import { Colors } from "../../constants/colors";
import { Task } from "../../services/api";

function daysOverdue(task: Task): number {
  if (!task.due_date) return 0;
  const now = new Date();
  const due = new Date(task.due_date);
  return Math.floor((now.getTime() - due.getTime()) / 86400000);
}

function formatOverdue(task: Task): string {
  const days = daysOverdue(task);
  if (days <= 0) return "Due today";
  if (days === 1) return "1 day overdue";
  return `${days} days overdue`;
}

export default function OverdueScreen() {
  const { overdueTasks, fetchOverdueTasks, updateTask, isDarkMode } =
    useStore();
  const colors = isDarkMode ? Colors.dark : Colors.light;
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    try {
      await fetchOverdueTasks();
    } catch {
      Toast.show({ type: "error", text1: "Failed to load overdue tasks" });
    }
    setLoading(false);
  }, [fetchOverdueTasks]);

  useEffect(() => {
    load();
  }, [load]);

  const onRefresh = async () => {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  };

  const handleToggle = async (task: Task) => {
    const newStatus = task.status === "done" ? "todo" : "done";
    try {
      await updateTask(task.id, {
        status: newStatus,
        completed_at:
          newStatus === "done" ? new Date().toISOString() : null,
      } as any);
      Toast.show({
        type: "success",
        text1: newStatus === "done" ? "Task completed!" : "Task reopened",
      });
      await load();
    } catch {
      Toast.show({ type: "error", text1: "Failed to update task" });
    }
  };

  // Sort by most overdue first
  const sorted = [...overdueTasks].sort(
    (a, b) => daysOverdue(b) - daysOverdue(a)
  );

  if (loading) {
    return (
      <View style={[s.container, { backgroundColor: colors.background }]}>
        <SkeletonLoader lines={5} />
      </View>
    );
  }

  return (
    <View style={[s.container, { backgroundColor: colors.background }]}>
      <Text style={[s.header, { color: colors.danger }]}>Overdue</Text>
      {overdueTasks.length > 0 && (
        <Text style={[s.subheader, { color: colors.textSecondary }]}>
          {overdueTasks.length} task{overdueTasks.length !== 1 ? "s" : ""}{" "}
          overdue
        </Text>
      )}

      {sorted.length === 0 ? (
        <EmptyState
          icon="✅"
          title="No overdue tasks"
          message="You're all caught up!"
        />
      ) : (
        <FlatList
          data={sorted}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => (
            <View>
              <TaskCard
                task={item}
                onPress={() => {}}
                onToggleComplete={() => handleToggle(item)}
              />
              <Text style={[s.overdueLabel, { color: colors.danger }]}>
                {formatOverdue(item)}
              </Text>
            </View>
          )}
          contentContainerStyle={s.list}
          refreshControl={
            <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
          }
        />
      )}
    </View>
  );
}

const s = StyleSheet.create({
  container: { flex: 1, paddingTop: 16 },
  header: {
    fontSize: 28,
    fontWeight: "700",
    paddingHorizontal: 20,
    marginBottom: 4,
  },
  subheader: {
    fontSize: 14,
    paddingHorizontal: 20,
    marginBottom: 16,
  },
  list: { paddingHorizontal: 16, paddingBottom: 20 },
  overdueLabel: {
    fontSize: 12,
    fontWeight: "500",
    paddingHorizontal: 4,
    marginTop: -4,
    marginBottom: 8,
  },
});
