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
  Alert,
  ActivityIndicator,
} from "react-native";
import { useRouter } from "expo-router";
import Toast from "react-native-toast-message";
import { useStore } from "../../store/useStore";
import { GoalCard } from "../../components/GoalCard";
import { EmptyState } from "../../components/EmptyState";
import { SkeletonLoader } from "../../components/SkeletonLoader";
import { Colors } from "../../constants/colors";

const GOAL_COLORS = [
  "#3B82F6",
  "#EF4444",
  "#10B981",
  "#F59E0B",
  "#8B5CF6",
  "#EC4899",
  "#06B6D4",
  "#F97316",
];

export default function GoalsScreen() {
  const router = useRouter();
  const { goals, fetchGoals, createGoal, isDarkMode } = useStore();
  const colors = isDarkMode ? Colors.dark : Colors.light;
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [showForm, setShowForm] = useState(false);
  const [creating, setCreating] = useState(false);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [targetDate, setTargetDate] = useState("");
  const [selectedColor, setSelectedColor] = useState(GOAL_COLORS[0]);

  const load = useCallback(async () => {
    try {
      await fetchGoals();
    } catch {
      Toast.show({ type: "error", text1: "Failed to load goals" });
    }
    setLoading(false);
  }, [fetchGoals]);

  useEffect(() => {
    load();
  }, [load]);

  const onRefresh = async () => {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  };

  const resetForm = () => {
    setTitle("");
    setDescription("");
    setTargetDate("");
    setSelectedColor(GOAL_COLORS[0]);
  };

  const handleCreate = async () => {
    if (!title.trim()) {
      Alert.alert("Error", "Title is required");
      return;
    }
    setCreating(true);
    try {
      await createGoal({
        title: title.trim(),
        description: description.trim() || undefined,
        target_date: targetDate.trim() || undefined,
        color: selectedColor,
      });
      Toast.show({ type: "success", text1: "Goal created" });
      setShowForm(false);
      resetForm();
    } catch {
      Toast.show({ type: "error", text1: "Failed to create goal" });
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

  return (
    <View style={[s.container, { backgroundColor: colors.background }]}>
      <Text style={[s.header, { color: colors.text }]}>Goals</Text>
      {goals.length === 0 ? (
        <EmptyState
          icon="🎯"
          title="No goals yet"
          message="Set your first goal to get started"
          actionLabel="Create Goal"
          onAction={() => setShowForm(true)}
        />
      ) : (
        <FlatList
          data={goals}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => (
            <GoalCard
              goal={item}
              onPress={() => router.push(`/goal/${item.id}`)}
              projectCount={item.projects?.length}
            />
          )}
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
            <Text style={[s.modalTitle, { color: colors.text }]}>New Goal</Text>

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
            <TextInput
              style={[
                s.input,
                { borderColor: colors.border, color: colors.text },
              ]}
              placeholder="Target date (YYYY-MM-DD)"
              placeholderTextColor={colors.textSecondary}
              value={targetDate}
              onChangeText={setTargetDate}
            />

            <Text
              style={[
                s.colorLabel,
                { color: colors.text, marginTop: 4, marginBottom: 8 },
              ]}
            >
              Color
            </Text>
            <View style={s.colorPicker}>
              {GOAL_COLORS.map((c) => (
                <TouchableOpacity
                  key={c}
                  style={[
                    s.colorOption,
                    { backgroundColor: c },
                    selectedColor === c && s.colorSelected,
                  ]}
                  onPress={() => setSelectedColor(c)}
                />
              ))}
            </View>

            <View style={s.modalButtons}>
              <TouchableOpacity
                style={s.cancelBtn}
                onPress={() => {
                  setShowForm(false);
                  resetForm();
                }}
              >
                <Text style={{ color: colors.textSecondary }}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[s.createBtn, { backgroundColor: colors.primary }]}
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
  container: { flex: 1, paddingTop: 60 },
  header: {
    fontSize: 28,
    fontWeight: "700",
    paddingHorizontal: 20,
    marginBottom: 16,
  },
  list: { paddingHorizontal: 16, paddingBottom: 80 },
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
  colorLabel: { fontSize: 15, fontWeight: "500" },
  colorPicker: { flexDirection: "row", gap: 10, marginBottom: 12 },
  colorOption: { width: 32, height: 32, borderRadius: 16 },
  colorSelected: {
    borderWidth: 3,
    borderColor: "#FFFFFF",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.3,
    shadowRadius: 2,
    elevation: 3,
  },
  modalButtons: {
    flexDirection: "row",
    justifyContent: "flex-end",
    gap: 12,
    marginTop: 8,
  },
  cancelBtn: { padding: 12 },
  createBtn: {
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 8,
    minWidth: 80,
    alignItems: "center",
  },
  createBtnText: { color: "#fff", fontWeight: "600", fontSize: 16 },
});
