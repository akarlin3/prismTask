import React, { useEffect, useState, useCallback } from "react";
import {
  View,
  Text,
  SectionList,
  StyleSheet,
  RefreshControl,
} from "react-native";
import Toast from "react-native-toast-message";
import { useStore } from "../../store/useStore";
import { TaskCard } from "../../components/TaskCard";
import { EmptyState } from "../../components/EmptyState";
import { SkeletonLoader } from "../../components/SkeletonLoader";
import { Colors } from "../../constants/colors";
import { Task } from "../../services/api";

export default function TodayScreen() {
  const { todayTasks, fetchTodayTasks, updateTask, isDarkMode } = useStore();
  const colors = isDarkMode ? Colors.dark : Colors.light;
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    try {
      await fetchTodayTasks();
    } catch {
      Toast.show({ type: "error", text1: "Failed to load" });
    }
    setLoading(false);
  }, [fetchTodayTasks]);

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

  // Group by project
  const grouped: Record<number, Task[]> = {};
  todayTasks.forEach((task) => {
    if (!grouped[task.project_id]) grouped[task.project_id] = [];
    grouped[task.project_id].push(task);
  });
  const sections = Object.entries(grouped).map(([projectId, data]) => ({
    title: `Project #${projectId}`,
    data,
  }));

  if (loading) {
    return (
      <View style={[s.container, { backgroundColor: colors.background }]}>
        <SkeletonLoader lines={5} />
      </View>
    );
  }

  return (
    <View style={[s.container, { backgroundColor: colors.background }]}>
      <Text style={[s.header, { color: colors.text }]}>Due Today</Text>

      {todayTasks.length === 0 ? (
        <EmptyState
          icon="🎉"
          title="All clear!"
          message="No tasks due today"
        />
      ) : (
        <SectionList
          sections={sections}
          keyExtractor={(item) => String(item.id)}
          renderSectionHeader={({ section: { title } }) => (
            <Text
              style={[s.sectionTitle, { color: colors.textSecondary }]}
            >
              {title}
            </Text>
          )}
          renderItem={({ item }) => (
            <TaskCard
              task={item}
              onPress={() => {}}
              onToggleComplete={() => handleToggle(item)}
            />
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
    marginBottom: 16,
  },
  sectionTitle: {
    fontSize: 13,
    fontWeight: "600",
    textTransform: "uppercase",
    letterSpacing: 0.5,
    paddingHorizontal: 16,
    paddingVertical: 8,
  },
  list: { paddingHorizontal: 16, paddingBottom: 20 },
});
