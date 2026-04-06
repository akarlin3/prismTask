import React, { useCallback, useEffect, useState } from "react";
import {
  View,
  Text,
  ScrollView,
  RefreshControl,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
} from "react-native";
import { useRouter } from "expo-router";
import Toast from "react-native-toast-message";
import { useStore } from "../../store/useStore";
import { Colors } from "../../constants/colors";
import type { Task } from "../../services/api";
import { searchTasks } from "../../services/api";

// Components - these will be used if they exist, otherwise inline fallbacks
import ProgressBar from "../../components/ProgressBar";
import PriorityBadge from "../../components/PriorityBadge";

export default function DashboardScreen() {
  const router = useRouter();
  const {
    isDarkMode,
    summary,
    todayTasks,
    overdueTasks,
    fetchDashboard,
    fetchTodayTasks,
    fetchOverdueTasks,
    updateTask,
  } = useStore();
  const colors = isDarkMode ? Colors.dark : Colors.light;

  const [refreshing, setRefreshing] = useState(false);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState("");
  const [searchResults, setSearchResults] = useState<Task[]>([]);
  const [searching, setSearching] = useState(false);

  const loadData = useCallback(async () => {
    try {
      await Promise.all([
        fetchDashboard(),
        fetchTodayTasks(),
        fetchOverdueTasks(),
      ]);
    } catch (error) {
      Toast.show({
        type: "error",
        text1: "Error",
        text2: "Failed to load dashboard",
      });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const onRefresh = useCallback(async () => {
    setRefreshing(true);
    await loadData();
    setRefreshing(false);
  }, [loadData]);

  const handleSearch = useCallback(async (query: string) => {
    setSearchQuery(query);
    if (!query.trim()) {
      setSearchResults([]);
      return;
    }
    setSearching(true);
    try {
      const resp = await searchTasks(query);
      setSearchResults(resp.data.results);
    } catch {
      Toast.show({ type: "error", text1: "Search failed" });
    } finally {
      setSearching(false);
    }
  }, []);

  const handleToggleTask = useCallback(
    async (task: Task) => {
      const newStatus = task.status === "done" ? "todo" : "done";
      try {
        await updateTask(task.id, {
          status: newStatus,
          completed_at: newStatus === "done" ? new Date().toISOString() : null,
        });
        Toast.show({
          type: "success",
          text1: newStatus === "done" ? "Task completed!" : "Task reopened",
        });
        loadData();
      } catch {
        Toast.show({ type: "error", text1: "Failed to update task" });
      }
    },
    [updateTask, loadData]
  );

  if (loading) {
    return (
      <View style={[styles.centered, { backgroundColor: colors.background }]}>
        <ActivityIndicator size="large" color={colors.primary} />
        <Text style={[styles.loadingText, { color: colors.textSecondary }]}>
          Loading dashboard...
        </Text>
      </View>
    );
  }

  const isEmpty =
    todayTasks.length === 0 &&
    overdueTasks.length === 0 &&
    (!summary || summary.today_tasks === 0);

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      <ScrollView
        style={styles.scrollView}
        contentContainerStyle={styles.scrollContent}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
        }
      >
        {/* Search Bar */}
        <View
          style={[
            styles.searchBar,
            { backgroundColor: colors.surface, borderColor: colors.border },
          ]}
        >
          <Text style={styles.searchIcon}>🔍</Text>
          <View style={styles.searchInputWrapper}>
            <Text
              style={[styles.searchPlaceholder, { color: colors.textSecondary }]}
              onPress={() => router.push("/(tabs)/search")}
            >
              Search tasks...
            </Text>
          </View>
        </View>

        {/* Summary Cards */}
        {summary && (
          <View style={styles.summaryGrid}>
            <TouchableOpacity
              style={[styles.summaryCard, { backgroundColor: colors.dangerLight }]}
              onPress={() => router.push("/overdue/")}
            >
              <Text style={[styles.summaryNumber, { color: colors.danger }]}>
                {summary.overdue_tasks}
              </Text>
              <Text style={[styles.summaryLabel, { color: colors.danger }]}>
                Overdue
              </Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[styles.summaryCard, { backgroundColor: colors.primaryLight }]}
              onPress={() => router.push("/today/")}
            >
              <Text style={[styles.summaryNumber, { color: colors.primary }]}>
                {summary.today_tasks}
              </Text>
              <Text style={[styles.summaryLabel, { color: colors.primary }]}>
                Today
              </Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[styles.summaryCard, { backgroundColor: colors.grayLight }]}
              onPress={() => router.push("/upcoming/")}
            >
              <Text style={[styles.summaryNumber, { color: colors.text }]}>
                {summary.upcoming_tasks}
              </Text>
              <Text style={[styles.summaryLabel, { color: colors.textSecondary }]}>
                This Week
              </Text>
            </TouchableOpacity>

            <View
              style={[styles.summaryCard, { backgroundColor: colors.successLight }]}
            >
              <Text style={[styles.summaryNumber, { color: colors.success }]}>
                {summary.completed_tasks}
              </Text>
              <Text style={[styles.summaryLabel, { color: colors.success }]}>
                Completed
              </Text>
            </View>
          </View>
        )}

        {/* Overdue Section */}
        {overdueTasks.length > 0 && (
          <View style={styles.section}>
            <View style={styles.sectionHeader}>
              <Text style={[styles.sectionTitle, { color: colors.danger }]}>
                Overdue
              </Text>
              <TouchableOpacity onPress={() => router.push("/overdue/")}>
                <Text style={[styles.seeAll, { color: colors.primary }]}>
                  See all
                </Text>
              </TouchableOpacity>
            </View>
            {overdueTasks.slice(0, 5).map((task) => (
              <TaskCardInline
                key={task.id}
                task={task}
                colors={colors}
                onToggle={() => handleToggleTask(task)}
                accent={colors.danger}
              />
            ))}
          </View>
        )}

        {/* Due Today Section */}
        {todayTasks.length > 0 && (
          <View style={styles.section}>
            <View style={styles.sectionHeader}>
              <Text style={[styles.sectionTitle, { color: colors.text }]}>
                Due Today
              </Text>
              <TouchableOpacity onPress={() => router.push("/today/")}>
                <Text style={[styles.seeAll, { color: colors.primary }]}>
                  See all
                </Text>
              </TouchableOpacity>
            </View>
            {todayTasks.slice(0, 5).map((task) => (
              <TaskCardInline
                key={task.id}
                task={task}
                colors={colors}
                onToggle={() => handleToggleTask(task)}
              />
            ))}
          </View>
        )}

        {/* Empty State */}
        {isEmpty && (
          <View style={styles.emptyState}>
            <Text style={styles.emptyIcon}>{"✅"}</Text>
            <Text style={[styles.emptyTitle, { color: colors.text }]}>
              You're all caught up!
            </Text>
            <Text style={[styles.emptySubtitle, { color: colors.textSecondary }]}>
              No tasks due today or overdue. Great job!
            </Text>
          </View>
        )}
      </ScrollView>

      {/* NLP Input Bar */}
      <TouchableOpacity
        style={[
          styles.nlpBar,
          { backgroundColor: colors.surface, borderColor: colors.border },
        ]}
        onPress={() => router.push("/(tabs)/search")}
      >
        <Text style={styles.nlpIcon}>{"+"}</Text>
        <Text style={[styles.nlpPlaceholder, { color: colors.textSecondary }]}>
          Quick add task...
        </Text>
      </TouchableOpacity>
    </View>
  );
}

function TaskCardInline({
  task,
  colors,
  onToggle,
  accent,
}: {
  task: Task;
  colors: typeof Colors.light;
  onToggle: () => void;
  accent?: string;
}) {
  const isDone = task.status === "done";

  return (
    <TouchableOpacity
      style={[styles.taskCard, { backgroundColor: colors.card, borderColor: colors.border }]}
      activeOpacity={0.7}
    >
      <TouchableOpacity style={styles.checkbox} onPress={onToggle}>
        <View
          style={[
            styles.checkboxInner,
            {
              borderColor: isDone ? colors.success : colors.border,
              backgroundColor: isDone ? colors.success : "transparent",
            },
          ]}
        >
          {isDone && <Text style={styles.checkmark}>{"✓"}</Text>}
        </View>
      </TouchableOpacity>

      <View style={styles.taskContent}>
        <Text
          style={[
            styles.taskTitle,
            { color: isDone ? colors.textSecondary : colors.text },
            isDone && styles.taskDone,
          ]}
          numberOfLines={1}
        >
          {task.title}
        </Text>
        {task.due_date && (
          <Text
            style={[
              styles.taskDueDate,
              { color: accent || colors.textSecondary },
            ]}
          >
            {new Date(task.due_date).toLocaleDateString()}
          </Text>
        )}
      </View>

      {task.priority > 0 && task.priority <= 4 && (
        <PriorityBadge priority={task.priority} />
      )}
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  centered: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
  },
  loadingText: {
    marginTop: 12,
    fontSize: 14,
  },
  scrollView: {
    flex: 1,
  },
  scrollContent: {
    padding: 16,
    paddingBottom: 80,
  },
  searchBar: {
    flexDirection: "row",
    alignItems: "center",
    borderRadius: 12,
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 10,
    marginBottom: 16,
  },
  searchIcon: {
    fontSize: 16,
    marginRight: 8,
  },
  searchInputWrapper: {
    flex: 1,
  },
  searchPlaceholder: {
    fontSize: 15,
  },
  summaryGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 10,
    marginBottom: 20,
  },
  summaryCard: {
    flex: 1,
    minWidth: "45%",
    borderRadius: 12,
    padding: 16,
    alignItems: "center",
  },
  summaryNumber: {
    fontSize: 28,
    fontWeight: "700",
  },
  summaryLabel: {
    fontSize: 12,
    fontWeight: "500",
    marginTop: 4,
  },
  section: {
    marginBottom: 20,
  },
  sectionHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 10,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: "600",
  },
  seeAll: {
    fontSize: 14,
    fontWeight: "500",
  },
  taskCard: {
    flexDirection: "row",
    alignItems: "center",
    padding: 12,
    borderRadius: 10,
    borderWidth: 1,
    marginBottom: 8,
  },
  checkbox: {
    marginRight: 10,
  },
  checkboxInner: {
    width: 22,
    height: 22,
    borderRadius: 6,
    borderWidth: 2,
    justifyContent: "center",
    alignItems: "center",
  },
  checkmark: {
    color: "#FFFFFF",
    fontSize: 13,
    fontWeight: "700",
  },
  taskContent: {
    flex: 1,
    marginRight: 8,
  },
  taskTitle: {
    fontSize: 15,
    fontWeight: "500",
  },
  taskDone: {
    textDecorationLine: "line-through",
  },
  taskDueDate: {
    fontSize: 12,
    marginTop: 2,
  },
  emptyState: {
    alignItems: "center",
    paddingVertical: 60,
  },
  emptyIcon: {
    fontSize: 48,
    marginBottom: 16,
  },
  emptyTitle: {
    fontSize: 20,
    fontWeight: "600",
    marginBottom: 8,
  },
  emptySubtitle: {
    fontSize: 14,
    textAlign: "center",
  },
  nlpBar: {
    position: "absolute",
    bottom: 0,
    left: 0,
    right: 0,
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 16,
    paddingVertical: 14,
    borderTopWidth: 1,
  },
  nlpIcon: {
    fontSize: 20,
    fontWeight: "600",
    marginRight: 10,
    color: "#3B82F6",
  },
  nlpPlaceholder: {
    fontSize: 15,
  },
});
