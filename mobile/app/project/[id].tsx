import React, { useEffect, useState, useCallback } from "react";
import {
  View,
  Text,
  FlatList,
  StyleSheet,
  TouchableOpacity,
  Modal,
  RefreshControl,
} from "react-native";
import { useLocalSearchParams } from "expo-router";
import Toast from "react-native-toast-message";
import { useStore } from "../../store/useStore";
import { TaskCard } from "../../components/TaskCard";
import { TaskForm } from "../../components/TaskForm";
import { NLPInput } from "../../components/NLPInput";
import { ProgressBar } from "../../components/ProgressBar";
import { EmptyState } from "../../components/EmptyState";
import { SkeletonLoader } from "../../components/SkeletonLoader";
import { Colors } from "../../constants/colors";
import { parseTaskInput, Task } from "../../services/api";

export default function ProjectDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const {
    selectedProject,
    tasks,
    fetchProject,
    fetchTasks,
    createTask,
    updateTask,
    deleteTask,
    createSubtask,
    isDarkMode,
  } = useStore();
  const colors = isDarkMode ? Colors.dark : Colors.light;
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [showForm, setShowForm] = useState(false);
  const [nlpLoading, setNlpLoading] = useState(false);
  const [initialFormData, setInitialFormData] = useState<any>(null);
  const [expandedTasks, setExpandedTasks] = useState<Set<number>>(new Set());

  const projectId = Number(id);

  const load = useCallback(async () => {
    try {
      await fetchProject(projectId);
      await fetchTasks(projectId);
    } catch {
      Toast.show({ type: "error", text1: "Failed to load project" });
    }
    setLoading(false);
  }, [projectId, fetchProject, fetchTasks]);

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
    // Optimistic update
    try {
      await updateTask(task.id, {
        status: newStatus,
        completed_at:
          newStatus === "done" ? new Date().toISOString() : null,
      } as any);
      Toast.show({
        type: "success",
        text1: newStatus === "done" ? "Task completed" : "Task reopened",
      });
    } catch {
      Toast.show({ type: "error", text1: "Failed to update task" });
      load(); // Revert by reloading
    }
  };

  const handleDelete = async (task: Task) => {
    try {
      await deleteTask(task.id);
      Toast.show({ type: "success", text1: "Task deleted" });
    } catch {
      Toast.show({ type: "error", text1: "Failed to delete task" });
    }
  };

  const handleCreate = async (data: any) => {
    try {
      await createTask(projectId, data);
      Toast.show({ type: "success", text1: "Task created" });
      setShowForm(false);
      setInitialFormData(null);
    } catch {
      Toast.show({ type: "error", text1: "Failed to create task" });
    }
  };

  const handleNLPSubmit = async (text: string) => {
    setNlpLoading(true);
    try {
      const resp = await parseTaskInput(text);
      const parsed = resp.data;
      setInitialFormData({
        title: parsed.title,
        due_date: parsed.due_date,
        priority: parsed.priority || 3,
      });
      setShowForm(true);
    } catch {
      Toast.show({
        type: "info",
        text1: "Couldn't parse that",
        text2: "Opening blank form instead",
      });
      setShowForm(true);
    }
    setNlpLoading(false);
  };

  const toggleExpand = (taskId: number) => {
    setExpandedTasks((prev) => {
      const next = new Set(prev);
      next.has(taskId) ? next.delete(taskId) : next.add(taskId);
      return next;
    });
  };

  const completed = tasks.filter((t) => t.status === "done").length;

  if (loading) {
    return (
      <View style={[s.container, { backgroundColor: colors.background }]}>
        <SkeletonLoader lines={6} />
      </View>
    );
  }

  return (
    <View style={[s.container, { backgroundColor: colors.background }]}>
      {selectedProject && (
        <View style={s.projectInfo}>
          <Text style={[s.projectTitle, { color: colors.text }]}>
            {selectedProject.title}
          </Text>
          {selectedProject.description && (
            <Text
              style={[s.projectDesc, { color: colors.textSecondary }]}
            >
              {selectedProject.description}
            </Text>
          )}
          <ProgressBar completed={completed} total={tasks.length} />
        </View>
      )}

      {tasks.length === 0 ? (
        <EmptyState
          icon="✅"
          title="No tasks"
          message="Add your first task"
          actionLabel="Create Task"
          onAction={() => setShowForm(true)}
        />
      ) : (
        <FlatList
          data={tasks}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => (
            <View>
              <TaskCard
                task={item}
                onPress={() => toggleExpand(item.id)}
                onToggleComplete={() => handleToggle(item)}
                onDelete={() => handleDelete(item)}
              />
              {expandedTasks.has(item.id) &&
                item.subtasks?.map((sub) => (
                  <View key={sub.id} style={s.subtaskIndent}>
                    <TaskCard
                      task={sub}
                      onPress={() => {}}
                      onToggleComplete={() => handleToggle(sub)}
                      onDelete={() => handleDelete(sub)}
                    />
                  </View>
                ))}
            </View>
          )}
          contentContainerStyle={s.list}
          refreshControl={
            <RefreshControl
              refreshing={refreshing}
              onRefresh={onRefresh}
            />
          }
        />
      )}

      <NLPInput
        onSubmit={handleNLPSubmit}
        isLoading={nlpLoading}
        placeholder="Add a task..."
      />

      <TouchableOpacity
        style={[s.fab, { backgroundColor: colors.primary }]}
        onPress={() => {
          setInitialFormData(null);
          setShowForm(true);
        }}
      >
        <Text style={s.fabText}>+</Text>
      </TouchableOpacity>

      <Modal visible={showForm} animationType="slide" transparent>
        <View style={s.modalOverlay}>
          <View
            style={[
              s.modalContent,
              { backgroundColor: colors.card },
            ]}
          >
            <TaskForm
              initialData={initialFormData || undefined}
              onSubmit={handleCreate}
              onCancel={() => {
                setShowForm(false);
                setInitialFormData(null);
              }}
              submitLabel="Create Task"
            />
          </View>
        </View>
      </Modal>
    </View>
  );
}

const s = StyleSheet.create({
  container: { flex: 1, paddingTop: 16 },
  projectInfo: { paddingHorizontal: 20, marginBottom: 16 },
  projectTitle: { fontSize: 24, fontWeight: "700", marginBottom: 4 },
  projectDesc: { fontSize: 14, marginBottom: 8 },
  list: { paddingHorizontal: 16, paddingBottom: 140 },
  subtaskIndent: { marginLeft: 24 },
  fab: {
    position: "absolute",
    right: 20,
    bottom: 80,
    width: 56,
    height: 56,
    borderRadius: 28,
    justifyContent: "center",
    alignItems: "center",
    elevation: 4,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 4,
  },
  fabText: { color: "#fff", fontSize: 28, fontWeight: "300", marginTop: -2 },
  modalOverlay: {
    flex: 1,
    backgroundColor: "rgba(0,0,0,0.5)",
    justifyContent: "flex-end",
  },
  modalContent: {
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    padding: 24,
    paddingBottom: 40,
    maxHeight: "80%",
  },
});
