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

function groupByDay(tasks: Task[]) {
  const groups: Record<string, Task[]> = {};
  const now = new Date();
  const todayStr = now.toDateString();
  const tomorrow = new Date(now);
  tomorrow.setDate(tomorrow.getDate() + 1);
  const tomorrowStr = tomorrow.toDateString();
  const days = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

  tasks.forEach((t) => {
    if (!t.due_date) {
      const key = "No date";
      if (!groups[key]) groups[key] = [];
      groups[key].push(t);
      return;
    }

    const d = new Date(t.due_date);
    const dStr = d.toDateString();

    let label: string;
    if (dStr === todayStr) {
      label = "Today";
    } else if (dStr === tomorrowStr) {
      label = "Tomorrow";
    } else {
      label =
        days[d.getDay()] +
        ", " +
        d.toLocaleDateString("en-US", { month: "short", day: "numeric" });
    }

    if (!groups[label]) groups[label] = [];
    groups[label].push(t);
  });

  return Object.entries(groups).map(([title, data]) => ({ title, data }));
}

export default function UpcomingScreen() {
  const { upcomingTasks, fetchUpcomingTasks, updateTask, isDarkMode } =
    useStore();
  const colors = isDarkMode ? Colors.dark : Colors.light;
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    try {
      await fetchUpcomingTasks(7);
    } catch {
      Toast.show({ type: "error", text1: "Failed to load upcoming tasks" });
    }
    setLoading(false);
  }, [fetchUpcomingTasks]);

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

  const sections = groupByDay(upcomingTasks);

  if (loading) {
    return (
      <View style={[s.container, { backgroundColor: colors.background }]}>
        <SkeletonLoader lines={5} />
      </View>
    );
  }

  return (
    <View style={[s.container, { backgroundColor: colors.background }]}>
      <Text style={[s.header, { color: colors.text }]}>Upcoming</Text>
      <Text style={[s.subheader, { color: colors.textSecondary }]}>
        Next 7 days
      </Text>

      {sections.length === 0 ? (
        <EmptyState
          icon="📅"
          title="Nothing upcoming"
          message="No tasks due in the next 7 days"
        />
      ) : (
        <SectionList
          sections={sections}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => (
            <TaskCard
              task={item}
              onPress={() => {}}
              onToggleComplete={() => handleToggle(item)}
            />
          )}
          renderSectionHeader={({ section }) => (
            <View
              style={[
                s.sectionHeaderContainer,
                { backgroundColor: colors.background },
              ]}
            >
              <Text style={[s.sectionHeader, { color: colors.text }]}>
                {section.title}
              </Text>
              <Text
                style={[
                  s.sectionCount,
                  { color: colors.textSecondary },
                ]}
              >
                {section.data.length} task
                {section.data.length !== 1 ? "s" : ""}
              </Text>
            </View>
          )}
          contentContainerStyle={s.list}
          refreshControl={
            <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
          }
          stickySectionHeadersEnabled
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
  sectionHeaderContainer: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingHorizontal: 20,
    paddingVertical: 10,
  },
  sectionHeader: {
    fontSize: 16,
    fontWeight: "600",
  },
  sectionCount: {
    fontSize: 13,
  },
  list: { paddingHorizontal: 16, paddingBottom: 20 },
});
