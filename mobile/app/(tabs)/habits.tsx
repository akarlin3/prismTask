import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { useStore } from "../../store/useStore";
import { Colors } from "../../constants/colors";

export default function HabitsScreen() {
  const { isDarkMode } = useStore();
  const colors = isDarkMode ? Colors.dark : Colors.light;

  return (
    <View style={[s.container, { backgroundColor: colors.background }]}>
      <Text style={s.emoji}>{"🔄"}</Text>
      <Text style={[s.title, { color: colors.text }]}>Habits</Text>
      <Text style={[s.subtitle, { color: colors.textSecondary }]}>
        Coming soon! Track your daily and weekly habits with streaks, analytics,
        and more.
      </Text>
    </View>
  );
}

const s = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    paddingHorizontal: 32,
  },
  emoji: { fontSize: 64, marginBottom: 20 },
  title: { fontSize: 24, fontWeight: "600", marginBottom: 8 },
  subtitle: { fontSize: 16, textAlign: "center", lineHeight: 22 },
});
