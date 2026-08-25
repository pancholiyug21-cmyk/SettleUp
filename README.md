# SETTLE
SETTLE is a modern, Splitwise-style Android application designed to help users track shared expenses, split bills, and settle debts seamlessly with friends and groups. It features a robust offline-first architecture with real-time cloud synchronization.
## Features
- **User Authentication**: Secure email-based authentication powered by Firebase Auth.
- **Group Management**: Create shared groups or securely join existing ones using invite codes.
- **Expense Tracking**: Add expenses with various split methods (Equal, Exact Amounts, Percentages, Shares).
- **Real-time Sync**: Groups and expenses synchronize instantly across all group members via Firestore snapshot listeners.
- **Offline-First**: Fully functional offline caching using Room database, ensuring you can view and interact with your data even without an active internet connection.
- **Intelligent Balances**: Automated debt simplification and balance calculation.
- **Secure Backend**: Financial data is securely handled with strict Firestore security rules and backend Cloud Functions.
## Architecture & Tech Stack
- **Platform**: Android (Kotlin)
- **UI Framework**: Jetpack Compose
- **Dependency Injection**: Dagger Hilt
- **Local Persistence**: Room Database
- **Cloud Backend**: 
  - Firebase Authentication
  - Cloud Firestore (Real-time NoSQL Database)
  - Firebase Cloud Functions (TypeScript/Node.js)
- **Architecture Pattern**: MVVM (Model-View-ViewModel) with Clean Architecture principles (Domain, Data, and UI layers).
## Project Structure
- `app/` - The main Android application code (Jetpack Compose, Room, Hilt, ViewModels, Repositories).
- `functions/` - Firebase Cloud Functions backend logic (e.g., secure group joining via invite codes).
- `firestore.rules` - Strict Firestore security rules to protect user profiles, groups, and financial data.
## Getting Started
### Prerequisites
- Android Studio (Latest version recommended)
- Node.js (for Firebase Cloud Functions deployment)
- Firebase CLI (`npm install -g firebase-tools`)
### Firebase Setup
To run this project with cloud synchronization, you must connect it to your own Firebase project:
1. Create a new project in the [Firebase Console](https://console.firebase.google.com/).
2. Add an Android app with the package name `com.example.splitwsie`.
3. Download the `google-services.json` file and place it in the `app/` directory (replacing the placeholder).
4. Enable **Email/Password** sign-in under the Authentication tab.
5. Create a **Firestore Database**.
6. Initialize Firebase locally and deploy the security rules and functions:
   ```bash
   firebase login
