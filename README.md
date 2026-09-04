# DroidLlama

DroidLlama is an Android app for running GGUF language models directly on a device through `llama.cpp`.

The app provides a Jetpack Compose chat interface, a public Hugging Face model browser, saved conversations, document context, and optional web search. Model inference runs locally, so downloaded models can be used offline with web search disabled. No hosted inference server or API key is required by the current implementation.

## Features

- Download, load, and delete models from the in-app catalog, with download progress and cancellation.
- Stream responses, stop generation, and return to saved conversations.
- Attach a document to a message or store reference documents in **File Manager** for retrieval during chat.
- Enable web search from the chat composer to supply search results to the local model.
- Configure CPU, Vulkan, or OpenCL inference, generation settings, reasoning mode, personality, and appearance.

## Development setup

### Requirements

Use Android Studio with support for the Android Gradle Plugin version configured in this repository. Select a compatible Gradle JDK in Android Studio's Gradle settings; the app's Java source/target level of 11 is separate from the JDK needed to run Gradle.

| Component | Repository configuration |
| --- | --- |
| Android Gradle Plugin | 9.3.1 |
| Gradle wrapper | 9.5.0 |
| Kotlin Compose plugin | 2.2.10 |
| Android compile / target SDK | 37 |
| Android NDK | 27.1.12297006 |
| CMake | 3.22.1 |
| Minimum device version | Android 7.0 / API 24 |
| Packaged architectures | `arm64-v8a`, `x86_64` |

Install the SDK platform, NDK, and CMake versions above through **SDK Manager**. Enable **Show Package Details** to select exact tool versions. A compatible physical device or emulator is needed to run the app. Available RAM and storage determine which models can load; model download size alone does not represent total runtime memory use.

### Native dependencies

The build expects these repositories under `app/src/main/cpp/third_party/`:

| Directory | Upstream repository |
| --- | --- |
| `llama.cpp` | `https://github.com/ggml-org/llama.cpp.git` |
| `OpenCL-Headers` | `https://github.com/KhronosGroup/OpenCL-Headers.git` |
| `OpenCL-ICD-Loader` | `https://github.com/KhronosGroup/OpenCL-ICD-Loader.git` |
| `Vulkan-Headers` | `https://github.com/KhronosGroup/Vulkan-Headers.git` |
| `SPIRV-Headers` | `https://github.com/KhronosGroup/SPIRV-Headers.git` |

These directories are tracked as Git links, but the repository currently has no `.gitmodules` file. A fresh clone therefore needs the dependencies populated manually; `git submodule update --init --recursive` alone cannot restore them.

For a **fresh clone**, run this PowerShell snippet from the project root. It clones missing dependencies and checks out the commit recorded by the parent repository. Existing populated directories are left alone.

```powershell
$nativeDependencies = @{
    'llama.cpp' = 'https://github.com/ggml-org/llama.cpp.git'
    'OpenCL-Headers' = 'https://github.com/KhronosGroup/OpenCL-Headers.git'
    'OpenCL-ICD-Loader' = 'https://github.com/KhronosGroup/OpenCL-ICD-Loader.git'
    'Vulkan-Headers' = 'https://github.com/KhronosGroup/Vulkan-Headers.git'
    'SPIRV-Headers' = 'https://github.com/KhronosGroup/SPIRV-Headers.git'
}

foreach ($dependency in $nativeDependencies.GetEnumerator()) {
    $dependencyPath = "app/src/main/cpp/third_party/$($dependency.Key)"
    if (Test-Path "$dependencyPath/CMakeLists.txt") { continue }

    $treeEntry = git ls-tree HEAD -- $dependencyPath
    if ($LASTEXITCODE -ne 0 -or $treeEntry -notmatch '^160000 commit ([0-9a-f]+)') {
        throw "Could not find the pinned commit for $dependencyPath"
    }
    $pinnedCommit = $Matches[1]
    git clone $dependency.Value $dependencyPath
    if ($LASTEXITCODE -ne 0) { throw "Clone failed for $dependencyPath" }
    git -C $dependencyPath checkout --detach $pinnedCommit
    if ($LASTEXITCODE -ne 0) { throw "Checkout failed for $dependencyPath" }
}
```

On other platforms, clone each upstream into its matching directory and check out the commit shown by `git ls-tree HEAD -- <directory>`. Use the recorded commits instead of updating dependencies to their latest branches.

### Build and run

1. Open the project root in Android Studio.
2. Install the required SDK tools and populate the native dependencies.
3. Allow Gradle sync to finish. Android Studio normally creates the ignored `local.properties` file with your SDK location; command-line builds also need a configured SDK location and a compatible JDK on `JAVA_HOME`.
4. Select the **app** run configuration and a supported device, then click **Run**.

Alternatively, from PowerShell at the project root:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

`installDebug` requires a connected device or running emulator. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. On macOS/Linux, use `./gradlew` instead of `.\gradlew.bat`.

The first build downloads Gradle dependencies and compiles the native inference libraries and GPU shaders. Models are downloaded separately inside the app; they are not bundled in the APK.

The current release build uses the debug signing configuration. Configure your own release signing before distributing a production build.

## Using the app

### Download a model and start chatting

1. Open the navigation drawer and choose **Models**.
2. Browse or search the catalog and select **Download** for a model that fits your device's storage and memory.
3. When the download finishes, load the model and wait for loading to complete.
4. Choose **New Chat**, type a message, and tap **Send**. Tap **Stop** to interrupt generation.
5. Use the drawer to reopen saved conversations or start another chat.

The catalog currently includes public, ungated GGUF models with reported parameter counts up to 9 billion. Split GGUF files and `mmproj` files are excluded. Catalog visibility does not guarantee that a model is compatible with the bundled runtime or fits your device.

Downloaded models persist in app storage; loading a model puts it into the inference runtime. Load a model again when the app reports **Load a model first**.

### Work with documents

- **For an individual message:** tap the paperclip, choose a document, preview its extracted text, and ask a question about the file.
- **For reusable reference material:** open **File Manager**, tap **Add files**, and import documents. Relevant excerpts from stored files are included automatically during chat. Files can be renamed or deleted there.
- **For custom retrieval behavior:** use **Instructions** in File Manager to import a JSON configuration. Importing a new configuration replaces the active one.

Supported extraction includes text-based PDFs, plain text, Markdown, JSON, CSV/TSV, XML, YAML, HTML, and common source-code files. Scanned PDFs need a text layer; the app does not perform OCR. Word documents and images are not supported by the current extractor.

Stored-document retrieval currently ranks chunks by query-term overlap. It does not use a separate embedding model. Extracted document text is limited to 2,000,000 characters, and the model's context window further limits how much reference material can be used in a response.

Example instruction file (`instructions.json`):

```json
{
  "name": "Document assistant",
  "version": 1,
  "systemPrompt": "Use relevant stored documents as the primary factual reference for the answer.",
  "noContextResponse": "The stored documents do not contain enough information.",
  "chunkSize": 700,
  "chunkOverlap": 100,
  "topK": 5,
  "minimumScore": 0.25,
  "includeCitations": true
}
```

`name` and `systemPrompt` are required. The supported version is 1. `chunkSize` accepts 100–4000, `chunkOverlap` must be nonnegative and smaller than `chunkSize`, `topK` accepts 1–50, and `minimumScore` accepts 0–1. Instruction files must fit within the 256 KB import limit.

### Web search and local data

Tap the globe in the chat composer to enable or disable web search. When a search runs, the app sends the message text as a search query to DuckDuckGo and supplies results to the local model. Web search requires internet access and is disabled by default. Model discovery and downloads connect to Hugging Face.

Conversations, downloaded models, settings, and File Manager copies are stored locally in the app's storage. Chat attachments retain access to the selected document through its Android content URI. Clearing app data or uninstalling removes local app data; Android backup behavior follows the app's manifest and backup rules.

### Adjust inference settings

Open **Settings** to configure:

- **Compute backend:** CPU is the default. Vulkan and OpenCL require compatible device drivers; use the displayed backend availability information. GPU acceleration also requires a nonzero GPU offload layer count.
- **Generation:** model defaults, temperature and sampling controls, maximum output tokens, context size, and reasoning mode. Reasoning behavior depends on model support.
- **Assistant behavior:** personality, custom system prompt, streaming, conversation context, and stored-source citations.
- **Appearance:** system, light, or dark theme.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| Gradle sync or SDK errors | Verify the Gradle JDK and the exact SDK, NDK, and CMake versions above. |
| CMake cannot find native sources | Populate all five dependency directories at their recorded commits. |
| Shader compiler missing | Check that the configured NDK includes `shader-tools/<host>/glslc`; CMake uses that executable. |
| Model download fails | Check internet access, free storage, and the displayed Hugging Face error, then retry. |
| Model fails to load or generation runs out of memory | Try a smaller model, reduce the context window, or switch to CPU with fewer GPU layers. |
| GPU backend is unavailable | Check backend status in Settings and use CPU on unsupported devices. |
| Document context is missing | Preview the extracted text, check the file format, and ask using terms present in the document. Scanned PDFs require a text layer. |

## Project layout and checks

```text
app/src/main/java/com/example/androidllama/
  ui/          Compose screens, navigation, and view models
  inference/   Kotlin runtime bridge and conversation context handling
  data/        Chat storage, models, settings, retrieval, and web search
  rag/         Document import, extraction, chunking, and instructions
app/src/main/cpp/
  llama_jni.cpp   Native inference bridge
  CMakeLists.txt Native build and backend configuration
  third_party/   Pinned native dependencies
app/src/test/         JVM unit tests
app/src/androidTest/  Device instrumentation tests
```

Run the existing checks from the project root:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
```

Instrumentation tests require a device/emulator. The download-service test accesses Hugging Face. The native token-streaming smoke test is skipped unless `stories260K.gguf` is present in the app's private `files/smoke/` directory; see `LlamaRuntimeInstrumentedTest.kt` for the fixture details.
