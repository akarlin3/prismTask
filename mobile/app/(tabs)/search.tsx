import React, { useState, useCallback } from "react";
import {
  View,
  Text,
  FlatList,
  StyleSheet,
  TextInput,
  TouchableOpacity,
  ActivityIndicator,
} from "react-native";
import { useRouter } from "expo-router";
import Toast from "react-native-toast-message";
import { searchTasks, Task } from "../../services/api";
import { TaskCard } from "../../components/TaskCard";
import { EmptyState } from "../../components/EmptyState";
import { useStore } from "../../store/useStore";
import { Colors } from "../../constants/colors";

export default function SearchScreen() {
  const router = useRouter();
  const { isDarkMode, updateTask } = useStore();
  const colors = isDarkMode ? Colors.dark : Colors.light;
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<Task[]>([]);
  const [searched, setSearched] = useState(false);
  const [loading, setLoading] = useState(false);

  const handleSearch = useCallback(async () => {
    if (!query.trim()) return;
    setLoading(true);
    try {
      const resp = await searchTasks(query.trim());
      setResults(resp.data.results || []);
      setSearched(true);
    } catch {
      Toast.show({ type: "error", text1: "Search failed" });
    }
    setLoading(false);
  }, [query]);

  const handleToggle = async (task: Task) => {
    const newStatus = task.status === "done" ? "todo" : "done";
    try {
      await updateTask(task.id, {
        status: newStatus,
        completed_at:
          newStatus === "done" ? new Date().toISOString() : null,
      } as any);
      setResults((prev) =>
        prev.map((t) => (t.id === task.id ? { ...t, status: newStatus } : t))
      );
      Toast.show({
        type: "success",
        text1: newStatus === "done" ? "Task completed!" : "Task reopened",
      });
    } catch {
      Toast.show({ type: "error", text1: "Failed to update task" });
    }
  };

  // Group results by project_id
  const grouped: Record<number, Task[]> = {};
  results.forEach((task) => {
    if (!grouped[task.project_id]) grouped[task.project_id] = [];
    grouped[task.project_id].push(task);
  });
  const sections = Object.entries(grouped).map(([projectId, tasks]) => ({
    projectId: Number(projectId),
    tasks,
  }));

  return (
    <View style={[s.container, { backgroundColor: colors.background }]}>
      <Text style={[s.header, { color: colors.text }]}>Search</Text>

      <View style={s.searchRow}>
        <TextInput
          style={[
            s.input,
            {
              borderColor: colors.border,
              color: colors.text,
              backgroundColor: colors.surface,
            },
          ]}
          placeholder="Search tasks..."
          placeholderTextColor={colors.textSecondary}
          value={query}
          onChangeText={setQuery}
          onSubmitEditing={handleSearch}
          returnKeyType="search"
          autoFocus
        />
        <TouchableOpacity
          style={[s.searchBtn, { backgroundColor: colors.primary }]}
          onPress={handleSearch}
        >
          <Text style={s.searchBtnText}>{"🔍"}</Text>
        </TouchableOpacity>
      </View>

      {loading ? (
        <View style={s.centered}>
          <ActivityIndicator size="large" color={colors.primary} />
          <Text style={[s.loadingText, { color: colors.textSecondary }]}>
            Searching...
          </Text>
        </View>
      ) : !searched ? (
        <EmptyState
          icon="🔍"
          title="Search tasks"
          message="Type a query to search across all your tasks"
        />
      ) : results.length === 0 ? (
        <EmptyState
          icon="📭"
          title="No results"
          message={`No tasks matching "${query}"`}
        />
      ) : (
        <FlatList
          data={sections}
          keyExtractor={(item) => String(item.projectId)}
          renderItem={({ item: section }) => (
            <View style={s.groupSection}>
              <Text
                style={[s.groupTitle, { color: colors.textSecondary }]}
              >
                Project #{section.projectId}
              </Text>
              {section.tasks.map((task) => (
                <TaskCard
                  key={task.id}
                  task={task}
                  onPress={() =>
                    router.push(`/project/${task.project_id}`)
                  }
                  onToggleComplete={() => handleToggle(task)}
                />
              ))}
            </View>
          )}
          contentContainerStyle={s.list}
        />
      )}
    </View>
  );
}

const s = StyleSheet.create({
  container: { flex: 1, paddingTop: 60 },
  header: {
    fontSize: 28,
    fontWeight: "700",
    paddingHorizontal: 20,
    marginBottom: 16,
  },
  searchRow: {
    flexDirection: "row",
    paddingHorizontal: 16,
    marginBottom: 16,
    gap: 8,
  },
  input: {
    flex: 1,
    borderWidth: 1,
    borderRadius: 8,
    padding: 12,
    fontSize: 16,
  },
  searchBtn: {
    width: 48,
    borderRadius: 8,
    justifyContent: "center",
    alignItems: "center",
  },
  searchBtnText: { fontSize: 20 },
  centered: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
  },
  loadingText: { marginTop: 12, fontSize: 14 },
  list: { paddingHorizontal: 16, paddingBottom: 20 },
  groupSection: { marginBottom: 20 },
  groupTitle: {
    fontSize: 13,
    fontWeight: "600",
    textTransform: "uppercase",
    letterSpacing: 0.5,
    marginBottom: 8,
  },
});
