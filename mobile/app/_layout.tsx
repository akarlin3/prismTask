import React, { useEffect } from "react";
import { ActivityIndicator, View, StyleSheet } from "react-native";
import { Stack } from "expo-router";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import Toast from "react-native-toast-message";
import { useStore } from "../store/useStore";
import { Colors } from "../constants/colors";

export default function RootLayout() {
  const { isLoading, checkAuth, isDarkMode } = useStore();
  const colors = isDarkMode ? Colors.dark : Colors.light;

  useEffect(() => {
    checkAuth();
  }, []);

  if (isLoading) {
    return (
      <View style={[styles.loading, { backgroundColor: colors.background }]}>
        <ActivityIndicator size="large" color={colors.primary} />
      </View>
    );
  }

  return (
    <GestureHandlerRootView style={styles.flex}>
      <Stack
        screenOptions={{
          headerShown: false,
          contentStyle: { backgroundColor: colors.background },
        }}
      >
        <Stack.Screen name="(auth)" />
        <Stack.Screen name="(tabs)" />
        <Stack.Screen
          name="goal/[id]"
          options={{ headerShown: true, title: "Goal" }}
        />
        <Stack.Screen
          name="project/[id]"
          options={{ headerShown: true, title: "Project" }}
        />
        <Stack.Screen
          name="today/index"
          options={{ headerShown: true, title: "Today" }}
        />
        <Stack.Screen
          name="overdue/index"
          options={{ headerShown: true, title: "Overdue" }}
        />
        <Stack.Screen
          name="upcoming/index"
          options={{ headerShown: true, title: "Upcoming" }}
        />
      </Stack>
      <Toast />
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  flex: {
    flex: 1,
  },
  loading: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
  },
});
