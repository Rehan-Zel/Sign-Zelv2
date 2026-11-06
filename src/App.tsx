const files = [
  "settings.gradle.kts",
  "build.gradle.kts",
  "gradle.properties",
  "app/build.gradle.kts",
  "app/src/main/AndroidManifest.xml",
  "app/src/main/res/drawable/ic_launcher.xml",
  "app/src/main/java/com/zelsigns/app/MainActivity.kt",
  "app/src/main/java/com/zelsigns/app/AppViewModel.kt",
  "app/src/main/java/com/zelsigns/app/ui/Theme.kt",
  "app/src/main/java/com/zelsigns/app/ui/Screens.kt",
  "app/src/main/java/com/zelsigns/app/data/ApiClient.kt",
  "app/src/main/java/com/zelsigns/app/data/AiAssistant.kt",
  "app/src/main/java/com/zelsigns/app/data/ProjectFiles.kt",
  ".github/workflows/build-apk.yml",
];

export default function App() {
  return (
    <main className="min-h-screen bg-slate-950 text-slate-100 flex items-center justify-center p-6">
      <div className="max-w-xl w-full">
        <h1 className="text-4xl font-black tracking-widest bg-gradient-to-r from-cyan-300 to-pink-500 bg-clip-text text-transparent">
          SIGN ZEL
        </h1>
        <p className="mt-2 text-slate-400">
          Native Android (Kotlin + Jetpack Compose) project. Build it with Gradle 8.7 / JDK 17 or via the included
          GitHub Actions workflow — this page is only a placeholder for the web sandbox.
        </p>
        <ul className="mt-6 space-y-1 font-mono text-sm text-emerald-300 bg-black/50 rounded-lg p-4">
          {files.map((f) => (
            <li key={f}>📄 {f}</li>
          ))}
        </ul>
      </div>
    </main>
  );
}
