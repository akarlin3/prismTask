import React, { useEffect, useState, useCallback } from "react";
import {
  View,
  Text,
  FlatList,
  StyleSheet,
  TouchableOpacity,
  Modal,
  TextInput,
  RefreshControl,
  ActivityIndicator,
} from "react-native";
import { useLocalSearchParams, useRouter } from "expo-router";
import Toast from "react-native-toast-message";
import { useStore } from "../../store/useStore";
import { ProgressBar } from "../../components/ProgressBar";
import { EmptyState } from "../../components/EmptyState";
import { SkeletonLoader } from "../../components/SkeletonLoader";
import { Colors } from "../../constants/colors";

export default function GoalDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const { selectedGoal, projects, fetchGoal, createProject, isDarkMode } =
    useStore();
  const colors = isDarkMode ? Colors.dark : Colors.light;
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [showForm, setShowForm] = useState(false);
  const [creating, setCreating] = useState(false);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");

  const load = useCallback(async () => {
    try {
      await fetchGoal(Number(id));
    } catch {
      Toast.show({ type: "error", text1: "Failed to load goal" });
    }
    setLoading(false);
  }, [id, fetchGoal]);

  useEffect(() => {
    load();
  }, [load]);

  const onRefresh = async () => {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  };

  const handleCreate = async () => {
    if (!title.trim()) return;
    setCreating(true);
    try {
      await createProject(Number(id), {
        title: title.trim(),
        description: description.trim() || undefined,
      });
      Toast.show({ type: "success", text1: "Project created" });
      setShowForm(false);
      setTitle("");
      setDescription("");
      load();
    } catch {
      Toast.show({ type: "error", text1: "Failed to create project" });
    } finally {
      setCreating(false);
    }
  };

  if (loading) {
    return (
      <View style={[s.container, { backgroundColor: colors.background }]}>
        <SkeletonLoader lines={5} />
      </View>
    );
  }

  const goal = selectedGoal;

  return (
    <View style={[s.container, { backgroundColor: colors.background }]}>
      {goal && (
        <View style={s.goalInfo}>
          <Text style={[s.goalTitle, { color: colors.text }]}>
            {goal.title}
          </Text>
          {goal.description ? (
            <Text style={[s.goalDesc, { color: colors.textSecondary }]}>
              {goal.description}
            </Text>
          ) : null}
          <View style={s.metaRow}>
            <View
              style={[
                s.statusBadge,
                { backgroundColor: colors.primaryLight },
              ]}
            >
              <Text style={[s.statusText, { color: colors.primary }]}>
                {goal.status}
              </Text>
            </View>
            {goal.target_date && (
              <Text style={[s.metaText, { color: colors.textSecondary }]}>
                Target: {new Date(goal.target_date).toLocaleDateString()}
              </Text>
            )}
          </View>
        </View>
      )}

      <Text style={[s.sectionTitle, { color: colors.text }]}>
        Projects ({projects.length})
      </Text>

      {projects.length === 0 ? (
        <EmptyState
          icon="📁"
          title="No projects"
          message="Break this goal into projects"
          actionLabel="Create Project"
          onAction={() => setShowForm(true)}
        />
      ) : (
        <FlatList
          data={projects}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => {
            const taskCount = item.tasks?.length || 0;
            const doneCount =
              item.tasks?.filter((t) => t.status === "done").length || 0;
            return (
              <TouchableOpacity
                style={[
                  s.projectCard,
                  {
                    backgroundColor: colors.card,
                    borderColor: colors.border,
                  },
                ]}
                onPress={() => router.push(`/project/${item.id}`)}
              >
                <View style={s.projectHeader}>
                  <Text
                    style={[s.projectTitle, { color: colors.text }]}
                    numberOfLines={1}
                  >
                    {item.title}
                  </Text>
                  <View
                    style={[
                      s.statusSmall,
                      { backgroundColor: colors.grayLight },
                    ]}
                  >
                    <Text
                      style={{ fontSize: 11, color: colors.textSecondary }}
                    >
                      {item.status}
                    </Text>
                  </View>
                </View>
                {item.description ? (
                  <Text
                    style={[
                      s.projectDesc,
                      { color: colors.textSecondary },
                    ]}
                    numberOfLines={2}
                  >
                    {item.description}
                  </Text>
                ) : null}
                {item.due_date && (
                  <Text
                    style={[
                      s.projectDueDate,
                      { color: colors.textSecondary },
                    ]}
                  >
                    Due: {new Date(item.due_date).toLocaleDateString()}
                  </Text>
                )}
                <ProgressBar completed={doneCount} total={taskCount} />
              </TouchableOpacity>
            );
          }}
          contentContainerStyle={s.list}
          refreshControl={
            <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
          }
        />
      )}

      <TouchableOpacity
        style={[s.fab, { backgroundColor: colors.primary }]}
        onPress={() => setShowForm(true)}
      >
        <Text style={s.fabText}>+</Text>
      </TouchableOpacity>

      <Modal visible={showForm} animationType="slide" transparent>
        <View style={s.modalOverlay}>
          <View style={[s.modalContent, { backgroundColor: colors.card }]}>
            <Text style={[s.modalTitle, { color: colors.text }]}>
              New Project
            </Text>
            <TextInput
              style={[
                s.input,
                { borderColor: colors.border, color: colors.text },
              ]}
              placeholder="Title"
              placeholderTextColor={colors.textSecondary}
              value={title}
              onChangeText={setTitle}
              autoFocus
            />
            <TextInput
              style={[
                s.input,
                s.textArea,
                { borderColor: colors.border, color: colors.text },
              ]}
              placeholder="Description (optional)"
              placeholderTextColor={colors.textSecondary}
              value={description}
              onChangeText={setDescription}
              multiline
            />
            <View style={s.modalButtons}>
              <TouchableOpacity
                onPress={() => {
                  setShowForm(false);
                  setTitle("");
                  setDescription("");
                }}
              >
                <Text
                  style={{ color: colors.textSecondary, padding: 12 }}
                >
                  Cancel
                </Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[
                  s.createBtn,
                  { backgroundColor: colors.primary },
                ]}
                onPress={handleCreate}
                disabled={creating}
              >
                {creating ? (
                  <ActivityIndicator size="small" color="#FFFFFF" />
                ) : (
                  <Text style={s.createBtnText}>Create</Text>
                )}
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
    </View>
  );
}

const s = StyleSheet.create({
  container: { flex: 1, paddingTop: 16 },
  goalInfo: { paddingHorizontal: 20, marginBottom: 20 },
  goalTitle: { fontSize: 24, fontWeight: "700", marginBottom: 4 },
  goalDesc: { fontSize: 14, marginBottom: 8 },
  metaRow: { flexDirection: "row", alignItems: "center", gap: 12 },
  statusBadge: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 12,
  },
  statusText: { fontSize: 12, fontWeight: "600", textTransform: "capitalize" },
  metaText: { fontSize: 13 },
  sectionTitle: {
    fontSize: 18,
    fontWeight: "600",
    paddingHorizontal: 20,
    marginBottom: 12,
  },
  list: { paddingHorizontal: 16, paddingBottom: 80 },
  projectCard: {
    borderWidth: 1,
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
  },
  projectHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 4,
  },
  projectTitle: { fontSize: 16, fontWeight: "600", flex: 1 },
  statusSmall: {
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 8,
  },
  projectDesc: { fontSize: 13, marginBottom: 8 },
  projectDueDate: { fontSize: 12, marginBottom: 8 },
  fab: {
    position: "absolute",
    right: 20,
    bottom: 24,
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
  },
  modalTitle: { fontSize: 20, fontWeight: "600", marginBottom: 16 },
  input: {
    borderWidth: 1,
    borderRadius: 8,
    padding: 12,
    fontSize: 16,
    marginBottom: 12,
  },
  textArea: { height: 80, textAlignVertical: "top" },
  modalButtons: {
    flexDirection: "row",
    justifyContent: "flex-end",
    gap: 12,
    marginTop: 8,
  },
  createBtn: {
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 8,
    minWidth: 80,
    alignItems: "center",
  },
  createBtnText: { color: "#fff", fontWeight: "600", fontSize: 16 },
});
