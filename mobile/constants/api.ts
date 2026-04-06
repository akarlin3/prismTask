import { Platform } from "react-native";

const DEV_URL = Platform.select({
  android: "http://10.0.2.2:8000/api/v1",
  ios: "http://localhost:8000/api/v1",
  default: "http://localhost:8000/api/v1",
});

const PROD_URL = "https://averytask-api.up.railway.app/api/v1";

const __DEV__ = process.env.NODE_ENV !== "production";

export const API_BASE_URL = __DEV__ ? DEV_URL : PROD_URL;
