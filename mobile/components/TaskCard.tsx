import React, { useRef } from "react";
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Alert,
  Animated,
  PanResponder,
} from "react-native";
import { Task } from "../services/api";
import PriorityBadge from "./PriorityBadge";

interface TaskCardProps {
  task: Task;
  onPress: (task: Task) => void;
  onToggleComplete: (task: Task) => void;
  onDelete: (task: Task) => void;
}

function isOverdue(dueDate: string | null): boolean {
  if (!dueDate) return false;
  const due = new Date(dueDate);
  const now = new Date();
  now.setHours(0, 0, 0, 0);
  return due < now;
}

function formatDate(dateStr: string | null): string {
  if (!dateStr) return "";
  const date = new Date(dateStr);
  const now = new Date();
  const tomorrow = new Date(now);
  tomorrow.setDate(tomorrow.getDate() + 1);

  if (date.toDateString() === now.toDateString()) return "Today";
  if (date.toDateString() === tomorrow.toDateString()) return "Tomorrow";

  return date.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
  });
}

export default function TaskCard({
  task,
  onPress,
  onToggleComplete,
  onDelete,
}: TaskCardProps) {
  const isDone = task.status === "done" || task.completed_at !== null;
  const overdue = !isDone && isOverdue(task.due_date);
  const subtaskCount = task.subtasks?.length || 0;
  const completedSubtasks =
    task.subtasks?.filter(
      (s) => s.status === "done" || s.completed_at !== null
    ).length || 0;

  const translateX = useRef(new Animated.Value(0)).current;

  const panResponder = useRef(
    PanResponder.create({
      onMoveShouldSetPanResponder: (_, gestureState) =>
        Math.abs(gestureState.dx) > 10 && Math.abs(gestureState.dy) < 10,
      onPanResponderMove: (_, gestureState) => {
        if (gestureState.dx < 0) {
          translateX.setValue(Math.max(gestureState.dx, -100));
        }
      },
      onPanResponderRelease: (_, gestureState) => {
        if (gestureState.dx < -60) {
          Animated.spring(translateX, {
            toValue: -100,
            useNativeDriver: true,
          }).start();
          Alert.alert(
            "Delete Task",
            `Are you sure you want to delete "${task.title}"?`,
            [
              {
                text: "Cancel",
                onPress: () =>
                  Animated.spring(translateX, {
                    toValue: 0,
                    useNativeDriver: true,
                  }).start(),
              },
              {
                text: "Delete",
                style: "destructive",
                onPress: () => onDelete(task),
              },
            ]
          );
        } else {
          Animated.spring(translateX, {
            toValue: 0,
            useNativeDriver: true,
          }).start();
        }
      },
    })
  ).current;

  return (
    <View style={styles.wrapper}>
      <View style={styles.deleteBackground}>
        <Text style={styles.deleteText}>Delete</Text>
      </View>
      <Animated.View
        style={[styles.card, { transform: [{ translateX }] }]}
        {...panResponder.panHandlers}
      >
        <TouchableOpacity
          onPress={() => onPress(task)}
          activeOpacity={0.7}
          style={[
            styles.cardInner,
            task.depth > 0 && { marginLeft: task.depth * 16 },
          ]}
        >
          <TouchableOpacity
            onPress={() => onToggleComplete(task)}
            style={styles.checkboxArea}
            hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
          >
            <View
              style={[
                styles.checkbox,
                isDone && styles.checkboxChecked,
              ]}
            >
              {isDone && <Text style={styles.checkmark}>✓</Text>}
            </View>
          </TouchableOpacity>

          <View style={styles.content}>
            <Text
              style={[styles.title, isDone && styles.titleDone]}
              numberOfLines={2}
            >
              {task.title}
            </Text>

            <View style={styles.meta}>
              {task.priority > 0 && task.priority <= 4 && (
                <PriorityBadge priority={task.priority} />
              )}

              {task.due_date && (
                <Text style={[styles.dueDate, overdue && styles.dueDateOverdue]}>
                  {overdue ? "⚠ " : ""}
                  {formatDate(task.due_date)}
                </Text>
              )}

              <View
                style={[
                  styles.statusChip,
                  isDone && styles.statusChipDone,
                ]}
              >
                <Text
                  style={[
                    styles.statusText,
                    isDone && styles.statusTextDone,
                  ]}
                >
                  {isDone ? "Done" : task.status === "in_progress" ? "In Progress" : "Todo"}
                </Text>
              </View>

              {subtaskCount > 0 && (
                <Text style={styles.subtaskCount}>
                  {completedSubtasks}/{subtaskCount} subtasks
                </Text>
              )}
            </View>
          </View>
        </TouchableOpacity>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    marginBottom: 8,
    overflow: "hidden",
    borderRadius: 10,
  },
  deleteBackground: {
    position: "absolute",
    top: 0,
    bottom: 0,
    right: 0,
    width: 100,
    backgroundColor: "#EF4444",
    justifyContent: "center",
    alignItems: "center",
    borderRadius: 10,
  },
  deleteText: {
    color: "#FFFFFF",
    fontWeight: "600",
    fontSize: 14,
  },
  card: {
    backgroundColor: "#FFFFFF",
    borderRadius: 10,
    borderWidth: 1,
    borderColor: "#E5E7EB",
  },
  cardInner: {
    flexDirection: "row",
    padding: 12,
    alignItems: "flex-start",
  },
  checkboxArea: {
    paddingTop: 2,
    marginRight: 10,
  },
  checkbox: {
    width: 22,
    height: 22,
    borderRadius: 11,
    borderWidth: 2,
    borderColor: "#D1D5DB",
    justifyContent: "center",
    alignItems: "center",
  },
  checkboxChecked: {
    backgroundColor: "#10B981",
    borderColor: "#10B981",
  },
  checkmark: {
    color: "#FFFFFF",
    fontSize: 13,
    fontWeight: "700",
  },
  content: {
    flex: 1,
  },
  title: {
    fontSize: 15,
    fontWeight: "500",
    color: "#1A1A2E",
    marginBottom: 6,
  },
  titleDone: {
    textDecorationLine: "line-through",
    color: "#9CA3AF",
  },
  meta: {
    flexDirection: "row",
    flexWrap: "wrap",
    alignItems: "center",
    gap: 6,
  },
  dueDate: {
    fontSize: 12,
    color: "#6B7280",
    fontWeight: "500",
  },
  dueDateOverdue: {
    color: "#EF4444",
    fontWeight: "600",
  },
  statusChip: {
    backgroundColor: "#F3F4F6",
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 10,
  },
  statusChipDone: {
    backgroundColor: "#D1FAE5",
  },
  statusText: {
    fontSize: 11,
    color: "#6B7280",
    fontWeight: "500",
  },
  statusTextDone: {
    color: "#059669",
  },
  subtaskCount: {
    fontSize: 11,
    color: "#9CA3AF",
  },
});
