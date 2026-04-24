# Firebase setup note

1. Open Firebase Console: https://console.firebase.google.com/
2. Create/select your project.
3. Go to Project settings > General.
4. Under Your apps, register your Android app (use your applicationId, e.g. com.example.aibalance).
5. Download the `google-services.json` file from that app card.
6. Place `google-services.json` in `app/google-services.json`.
7. In Firebase Console > Authentication, enable Email/Password and Google providers.
8. For Google sign-in to work, ensure SHA-1 and SHA-256 fingerprints are added in the Firebase Android app settings.
