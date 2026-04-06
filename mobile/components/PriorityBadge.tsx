import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { PriorityColors, PriorityLabels } from "../constants/colors";

interface PriorityBadgeProps {
  priority: number;
}

export default function PriorityBadge({ priority }: PriorityBadgeProps) {
  const color = PriorityColors[priority] || "#9CA3AF";
  const label = PriorityLabels[priority] || "None";

  return (
    <View style={[styles.badge, { backgroundColor: color + "20", borderColor: color }]}>
      <View style={[styles.dot, { backgroundColor: color }]} />
      <Text style={[styles.label, { color }]}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 12,
    borderWidth: 1,
    alignSelf: "flex-start",
  },
  dot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    marginRight: 4,
  },
  label: {
    fontSize: 12,
    fontWeight: "600",
  },
});
