import { initializeApp } from 'firebase/app';
import { getAuth, GoogleAuthProvider } from 'firebase/auth';
import { getFirestore } from 'firebase/firestore';

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || "AIzaSyCr8PY_DJh00LmpW8nS3_fnsqttlUr__3g",
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || "averytask-50dc5.firebaseapp.com",
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || "averytask-50dc5",
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || "averytask-50dc5.firebasestorage.app",
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || "403186103462",
  appId: import.meta.env.VITE_FIREBASE_APP_ID || "1:403186103462:web:70dccb94955d5d2647b067",
};

export const firebaseApp = initializeApp(firebaseConfig);
export const firebaseAuth = getAuth(firebaseApp);
export const firestore = getFirestore(firebaseApp);
export const googleProvider = new GoogleAuthProvider();
