import React from "react";
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Switch,
  Alert,
} from "react-native";
import { useRouter } from "expo-router";
import { useStore } from "../../store/useStore";
import { Colors } from "../../constants/colors";

export default function SettingsScreen() {
  const router = useRouter();
  const { user, logout, isDarkMode, toggleDarkMode } = useStore();
  const colors = isDarkMode ? Colors.dark : Colors.light;

  const handleLogout = () => {
    Alert.alert("Logout", "Are you sure you want to logout?", [
      { text: "Cancel", style: "cancel" },
      {
        text: "Logout",
        style: "destructive",
        onPress: async () => {
          await logout();
          router.replace("/(auth)/login");
        },
      },
    ]);
  };

  return (
    <View style={[s.container, { backgroundColor: colors.background }]}>
      <Text style={[s.header, { color: colors.text }]}>Settings</Text>

      <View
        style={[
          s.section,
          { backgroundColor: colors.card, borderColor: colors.border },
        ]}
      >
        <Text style={[s.sectionTitle, { color: colors.textSecondary }]}>
          Account
        </Text>
        <Text style={[s.label, { color: colors.text }]}>
          {user?.name || "User"}
        </Text>
        <Text style={[s.sublabel, { color: colors.textSecondary }]}>
          {user?.email || ""}
        </Text>
      </View>

      <View
        style={[
          s.section,
          { backgroundColor: colors.card, borderColor: colors.border },
        ]}
      >
        <Text style={[s.sectionTitle, { color: colors.textSecondary }]}>
          Appearance
        </Text>
        <View style={s.row}>
          <Text style={[s.label, { color: colors.text }]}>Dark Mode</Text>
          <Switch value={isDarkMode} onValueChange={toggleDarkMode} />
        </View>
      </View>

      <TouchableOpacity
        style={[s.logoutBtn, { borderColor: colors.danger }]}
        onPress={handleLogout}
      >
        <Text style={[s.logoutText, { color: colors.danger }]}>Logout</Text>
      </TouchableOpacity>

      <Text style={[s.version, { color: colors.textSecondary }]}>
        AveryTask v1.0.0
      </Text>
    </View>
  );
}

const s = StyleSheet.create({
  container: { flex: 1, paddingTop: 60, paddingHorizontal: 20 },
  header: { fontSize: 28, fontWeight: "700", marginBottom: 24 },
  section: {
    borderWidth: 1,
    borderRadius: 12,
    padding: 16,
    marginBottom: 16,
  },
  sectionTitle: {
    fontSize: 12,
    fontWeight: "600",
    textTransform: "uppercase",
    marginBottom: 8,
  },
  label: { fontSize: 16, fontWeight: "500" },
  sublabel: { fontSize: 14, marginTop: 2 },
  row: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  logoutBtn: {
    borderWidth: 1,
    borderRadius: 12,
    padding: 16,
    alignItems: "center",
    marginTop: 16,
  },
  logoutText: { fontSize: 16, fontWeight: "600" },
  version: { textAlign: "center", marginTop: 24, fontSize: 12 },
});
