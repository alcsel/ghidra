# DeepSeek AI - Ghidra Extension

[![Ghidra](https://img.shields.io/badge/Ghidra-12.1.2%2B-blue.svg)](https://ghidra-sre.org/)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)
[![DeepSeek](https://img.shields.io/badge/API-DeepSeek-blueviolet.svg)](https://platform.deepseek.com)

Ghidra extension integrating **DeepSeek API** (`deepseek-chat` and `deepseek-reasoner`) directly into the Ghidra decompiler.

*For Turkish documentation, see [README_TR.md](README_TR.md).*

---

### Core Capabilities

| Capability | Description |
| :--- | :--- |
| **Function Explanation** | Provides detailed natural-language explanations of decompiled logic and algorithms. |
| **Inline Comments** | Detects complex or obfuscated expressions and suggests technical comments mapped to code addresses. |
| **Variable Renaming** | Replaces compiler artifacts (`uVar1`, `param_1`, `iVar3`) with meaningful semantic names (`packet_len`, `buffer_ptr`). |
| **Function Renaming** | Suggests descriptive function names and structured block comments for entry points. |
| **Uncertainty Audit** | Outlines ambiguous areas in a dedicated tab without guessing or hallucinating. |
| **Interactive Review** | All suggestions are reviewed in an interactive dialog before committing to the program. |
| **Batch Analysis** | Iterates over entire binaries, applies naming/comments, and exports enriched C pseudocode. |
| **Zero Dependencies** | Built using Ghidra's embedded libraries (`java.net.http`, `Gson`). No external jar runtime dependencies. |

---

## 1. Requirements

- **Ghidra**: `12.1.2` (or `11.x`+)
- **JDK**: `21+` (e.g. Eclipse Adoptium Temurin 21, Microsoft OpenJDK 21)
- **DeepSeek API Key**: `sk-...` from [platform.deepseek.com](https://platform.deepseek.com)

---

## 2. Installation

### Method A - PowerShell Script (Recommended, Offline)

No Gradle installation or internet connection required. Compiles directly against Ghidra's internal jars:

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

If Ghidra is in a non-standard directory, supply the path explicitly:

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1 -GhidraDir "C:\Tools\ghidra_12.1.2_PUBLIC"
```

What the script does:
1. Compiles sources with `javac` against Ghidra framework jars.
2. Derives the `DeepSeekAI.tool` template from Ghidra's `CodeBrowser.tool` with `deepseekai.DeepSeekAIPlugin` included.
3. Produces a distribution zip under `dist/ghidra_<version>_<date>_DeepSeekAI.zip`.
4. Copies the staged extension into `Ghidra/Extensions/DeepSeekAI` and installs tool templates to the user profile.

### Method B - Gradle (Standard Build)

Requires internet access to download the Gradle wrapper:

```bash
# Set your Ghidra installation path
export GHIDRA_INSTALL_DIR="/path/to/ghidra_12.1.2_PUBLIC"

# Build the extension archive
./gradlew buildExtension
```

On Windows:
```cmd
set GHIDRA_INSTALL_DIR=C:\path\to\ghidra_12.1.2_PUBLIC
gradlew.bat -PGHIDRA_INSTALL_DIR=%GHIDRA_INSTALL_DIR% buildExtension
```

Output archive is generated in: `dist/ghidra_<version>_<date>_DeepSeekAI.zip`.

### Method C - Ghidra UI (Manual Archive Install)

1. Launch Ghidra.
2. Navigate to **File > Install Extensions...**
3. Click the green **`+`** icon in the top right.
4. Select the generated `dist/ghidra_*_DeepSeekAI.zip` file.
5. Check the box next to **DeepSeekAI**.
6. Restart Ghidra.

### Post-Installation Verification

1. Launch Ghidra and open a project.
2. Double-click the **DeepSeekAI** tool in the Project Manager Tool Chest (or open standard **CodeBrowser**).
3. Verify that the **Tools > DeepSeek AI** menu appears.
   - If missing: **File > Configure... > Configure Plugins > search 'DeepSeek' > enable DeepSeekAI**.

---

## 3. Usage

### 3.1 Single Function Analysis

1. Open any binary in CodeBrowser / DeepSeekAI tool.
2. Wait for auto-analysis to finish.
3. Position your cursor inside any decompiled function in the **Decompiler** or **Listing** window.
4. Run:
   - **Tools > DeepSeek AI > Analyze Function**
5. A modal dialog will present:
   - **Summary Tab**: Complete algorithmic summary of the function.
   - **Variable Renames Tab**: Checkbox list of suggested variable renames with old name, new name, type, and confidence score.
   - **Line Comments Tab**: Proposed address-mapped inline comments.
   - **Complex Blocks Tab**: Sections flagged as tricky or important.
   - **Uncertainties Tab**: Points the AI is unsure about.
   - **Raw Response Tab**: Unmodified model JSON output.
6. Check or uncheck items as desired, then click **Apply Selected**. Changes are committed in a single undoable transaction (`Ctrl+Z` to undo).

### 3.2 Auto-Apply Shortcut

If you prefer applying results automatically without the review dialog, configure:
- **Tools > DeepSeek AI > Settings (API Key)...**
- Check:
  - *Auto-apply comments without prompting*
  - *Auto-apply variable renames without prompting*
  - *Auto-apply function name without prompting*

---

## 4. Batch Analysis - Whole Binary Processing

Batch mode sequentially analyzes functions in a program, applies AI-suggested renames, adds inline technical comments, and exports enriched C pseudocode.

To launch:
- **Tools > DeepSeek AI > Batch Analyze Functions...**

### Scope & Filter Options:
- **All functions in program**: Full binary sweep.
- **Functions in current selection**: Targeted block.
- **Only current function**: Single function batch runner.
- **Only undefined function names (`FUN_xxxx`, `sub_xxxx`)**: Skips functions already named.
- **Skip external and thunk functions**: Focuses on actual application logic.
- **Min / max function size (bytes)**: Filters out tiny stubs or giant functions.
- **Max functions to process**: Limits token usage per run (0 = unlimited).
- **Delay between requests**: Paces API requests to respect rate limits.

### Caching System:
- Automatically maintains `*.cache.json` in the user home directory.
- Avoids redundant API calls when re-running analysis on identical functions.

### Enriched C Source Export:
- Generates `_ai_decompiled.c` containing:
  - Header with metadata (program, language, model, timestamp).
  - Decompiled functions with AI summaries, signatures, and variable mapping comments.
  - Complete alphabetical function index at the bottom.

---

## 5. Configuration & Key Management

Access preferences via **Tools > DeepSeek AI > Settings (API Key)...** or **Edit > Tool Options > DeepSeek AI**.

| Setting | Default | Description |
| :--- | :--- | :--- |
| `API Key` | `""` | DeepSeek API key (`sk-...`). |
| `Base URL` | `https://api.deepseek.com` | API endpoint (supports local OpenAI-compatible proxies). |
| `Model` | `deepseek-chat` | Choose `deepseek-chat` (fast) or `deepseek-reasoner` (deep reasoning). |
| `Temperature` | `0.2` | Controls randomness (lower is more deterministic). |
| `Max Tokens` | `8192` | Maximum token limit for completions. |
| `Timeout` | `180` | Request timeout in seconds. |
| `Response Language` | `English` | Language for AI explanations and comments. |
| `Max Code Characters` | `24000` | Decompiled code length cap sent to the model. |

### Where is the API Key Stored?

To maintain security and prevent accidental commits, keys are resolved in this priority order:

1. **Environment Variable**: `DEEPSEEK_API_KEY`
   ```bash
   # Windows PowerShell
   [System.Environment]::SetEnvironmentVariable("DEEPSEEK_API_KEY", "sk-your-key", "User")
   
   # Linux/macOS
   export DEEPSEEK_API_KEY="sk-your-key"
   ```
2. **Local Properties File**: `~/.deepseek_ghidra.properties`
   Create this file in your user home directory:
   ```properties
   apiKey=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
   ```
3. **Ghidra Settings GUI**: `Edit > Tool Options > DeepSeek AI`.

---

## 6. How It Works (Pipeline)

```
[ Cursor in Function ]
          │
          ▼
[ Ghidra Decompiler AST ]
  ├── C Pseudocode Extraction
  ├── Clang Node Token Walk (Address prefix tags [0x...])
  └── HighFunction Symbol Map (Parameters & Local variables)
          │
          ▼
[ DeepSeek API Client ]
  ├── System Prompt (Reverse Engineering persona & strict JSON schema)
  └── User Prompt (Context, symbols, annotated pseudocode)
          │
          ▼
[ AnalysisOutcome Parser ]
  ├── JSON extraction & validation
  ├── Address verification in active program memory
  └── Identifier validation (C keywords & naming sanity)
          │
          ▼
[ OutcomeApplier ]
  ├── HighFunctionDBUtil.updateDBVariable (Variables)
  ├── Listing.setComment PRE (Line comments)
  ├── Listing.setComment PLATE (Function comment)
  └── Function.setName (Function renaming)
          │
          ▼
[ Atomic Transaction Commit ]
```

---

## 7. Diagnostic Scripts

Located in `ghidra_scripts/` and accessible from **Window > Script Manager**:

| Script | Purpose |
| :--- | :--- |
| `CheckDeepSeekInstall.java` | Verifies classpath, dependencies (Gson, HttpClient), and tool template registration. |
| `CheckPluginRegistration.java` | Inspects tool configurations and active plugin manager state. |
| `DumpFunctionContext.java` | Dumps the exact prompt, annotated lines, and symbols prepared for the model (dry-run). |
| `TestBatchAnalysis.java` | Tests batch pipeline on 2 sample functions with dry-run or live API test (`api` argument). |
| `TestDeepSeekApi.java` | Tests connectivity and credentials with a 30-token sanity probe. |

---

## 8. Project Structure

```
DeepSeekGhidra/
├── .github/
│   └── workflows/
│       └── build.yml               # GitHub Actions CI/CD release workflow
├── ghidra_scripts/                 # Diagnostic and verification scripts
│   ├── CheckDeepSeekInstall.java
│   ├── CheckPluginRegistration.java
│   ├── DumpFunctionContext.java
│   ├── TestBatchAnalysis.java
│   └── TestDeepSeekApi.java
├── gradle/wrapper/                 # Gradle wrapper binaries
├── src/main/java/deepseekai/       # Core extension source code
│   ├── AnalysisOutcome.java        # Structured response model & JSON parser
│   ├── BatchAiDialog.java          # Batch analysis setup dialog
│   ├── BatchAiEngine.java          # Batch execution & C exporter engine
│   ├── BatchAiOptions.java         # Batch configuration & scope filters
│   ├── BatchAiTask.java            # Background task for batch execution
│   ├── DecompiledContext.java      # Context container for code & symbols
│   ├── DecompilerHelper.java       # AST token tree walker & decompilation helpers
│   ├── DeepSeekAIPlugin.java       # Ghidra ProgramPlugin entry point & menu actions
│   ├── DeepSeekAnalyzeTask.java    # Single function background worker
│   ├── DeepSeekApplyTask.java      # Modification applier task
│   ├── DeepSeekClient.java         # HttpClient client for DeepSeek API
│   ├── DeepSeekConfig.java         # Options registration & key resolver
│   ├── DeepSeekOptionsDialog.java  # Preferences dialog & connection probe
│   ├── DeepSeekResultDialog.java   # Suggestions review dialog
│   ├── OutcomeApplier.java         # Transactional database applier
│   └── Prompt.java                 # System & user prompt templates
├── src/main/resources/
│   └── defaultTools/
│       └── DeepSeekAI.tool         # Pre-configured tool template
├── .gitattributes                  # EOL normalization rules
├── .gitignore                      # Git exclusion rules
├── build.gradle                    # Gradle build definition
├── build.ps1                       # Offline PowerShell build script
├── extension.properties            # Ghidra extension manifest
├── LICENSE                         # Apache 2.0 License
├── Module.manifest                 # Module manifest
├── README.md                       # English documentation
└── README_TR.md                    # Turkish documentation
```

---

## 9. GitHub CI/CD & Releases

The project includes an automated GitHub Actions workflow (`.github/workflows/build.yml`):
- Runs automated Gradle builds on every `push` and `pull_request`.
- Automatically publishes extension zip archives to **GitHub Releases** whenever a release tag is pushed:

```bash
git tag v1.0.0
git push origin v1.0.0
```

---

## 10. License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
