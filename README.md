# Ghidra AI Extension - Multi-Provider AI Assistant (v2.0)

[![Ghidra](https://img.shields.io/badge/Ghidra-12.1.2%2B-blue.svg)](https://ghidra-sre.org/)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)
[![Multi-AI](https://img.shields.io/badge/AI-OpenAI%20%7C%20Claude%20%7C%20Gemini%20%7C%20DeepSeek%20%7C%20Ollama-blueviolet.svg)](https://github.com/ghidra-ai)

A state-of-the-art Ghidra extension integrating major Large Language Model (LLM) providers directly into Ghidra's decompiler. Analyze binary functions, deobfuscate logic, rename variables, generate comments, and perform batch reverse engineering using your choice of cloud AI or 100% offline local models.

*Türkçe dokümantasyon için [README_TR.md](README_TR.md) dosyasına bakınız.*

---

### Supported AI Providers & Models

| Provider | Supported Models | Protocol | Key Required? | Environment Variable | Best Use Case |
| :--- | :--- | :--- | :---: | :--- | :--- |
| **OpenAI** | `gpt-4o`, `gpt-4o-mini`, `o1`, `o3-mini`, `gpt-4.5` | OpenAI Chat | Yes | `OPENAI_API_KEY` | General reverse engineering, complex algorithm analysis |
| **Anthropic Claude** | `claude-3-7-sonnet-20250219`, `claude-3-5-sonnet`, `claude-3-5-haiku` | Anthropic Messages | Yes | `ANTHROPIC_API_KEY` | Deep code reasoning, hybrid thinking, large context functions |
| **Google Gemini** | `gemini-2.5-pro`, `gemini-2.5-flash`, `gemini-2.0-flash` | OpenAI-compatible | Yes | `GEMINI_API_KEY`, `GOOGLE_API_KEY` | High speed, large binary context, economical analysis |
| **DeepSeek** | `deepseek-chat` (V3), `deepseek-reasoner` (R1) | OpenAI Chat | Yes | `DEEPSEEK_API_KEY` | High-accuracy reverse engineering at fraction of cost |
| **Ollama** *(Local / Offline)* | `qwen2.5-coder:32b`, `llama3.3:70b`, `deepseek-r1:14b`, `codellama` | OpenAI Chat | **No (Free/Offline)** | `OLLAMA_API_KEY` *(optional)* | **100% confidential, air-gapped reverse engineering (zero data leakage)** |
| **Groq** | `llama-3.3-70b-versatile`, `deepseek-r1-distill-llama-70b` | OpenAI Chat | Yes | `GROQ_API_KEY` | Ultra-fast inference (hundreds of tokens/second) |
| **OpenRouter** | `anthropic/claude-3.7-sonnet`, `deepseek/deepseek-r1`, `openai/gpt-4o` | OpenAI Chat | Yes | `OPENROUTER_API_KEY` | Single API key for 200+ models from all top AI labs |
| **Mistral AI** | `codestral-latest`, `mistral-large-latest` | OpenAI Chat | Yes | `MISTRAL_API_KEY` | Fine-tuned code reasoning models |
| **xAI** | `grok-2`, `grok-2-mini`, `grok-beta` | OpenAI Chat | Yes | `XAI_API_KEY` | Grok reasoning models |
| **Custom Endpoint** | `local-model`, `custom-model` | OpenAI Chat | Optional | `CUSTOM_API_KEY`, `AI_API_KEY` | Private LM Studio, vLLM, TextGen, or Azure OpenAI servers |

---

### Core Capabilities

| Capability | Description |
| :--- | :--- |
| **Multi-Provider Hub** | Switch between OpenAI, Claude, Gemini, DeepSeek, Ollama, Groq, OpenRouter with a single click. |
| **Dynamic Model Discovery** | Click **Fetch Models 🔄** in Settings to query Ollama locally or cloud APIs for available models. |
| **Per-Provider Credential Memory** | Switching providers preserves your API key, custom base URL, and selected model for each provider. |
| **Function Explanation** | Provides detailed natural-language explanations of decompiled algorithms and intent. |
| **Inline Comments** | Detects complex or obfuscated expressions and suggests technical comments mapped to code addresses. |
| **Semantic Variable Renaming** | Replaces compiler artifacts (`uVar1`, `param_1`, `iVar3`) with meaningful semantic names (`packet_len`, `buffer_ptr`). |
| **Function Renaming** | Suggests descriptive function names and structured block comments for entry points. |
| **Uncertainty Audit** | Outlines ambiguous areas in a dedicated tab without guessing or hallucinating. |
| **Interactive Review Dialog** | Review, filter, and double-click addresses before committing changes to the database. |
| **Batch Analysis** | Iterates over entire binaries, applies naming/comments, and exports enriched C pseudocode. |
| **Zero External Dependencies** | Built using Ghidra's embedded libraries (`java.net.http`, `Gson`). No external jar dependencies required. |

---

## 1. Requirements

- **Ghidra**: `12.1.2` (or `11.x`+)
- **JDK**: `21+` (e.g. Eclipse Adoptium Temurin 21, Microsoft OpenJDK 21)
- **AI Credentials**: API key from your chosen provider, or local **Ollama** instance.

---

## 2. Installation

### Method A - PowerShell Script (Recommended, Offline)

No Gradle installation or internet connection required. Compiles directly against Ghidra's internal jars:

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

If Ghidra is in a custom directory, supply the path explicitly:

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1 -GhidraDir "C:\Tools\ghidra_12.1.2_PUBLIC"
```

What the script does:
1. Compiles sources with `javac` against Ghidra framework jars.
2. Derives the tool template from Ghidra's `CodeBrowser.tool` with `deepseekai.DeepSeekAIPlugin` included.
3. Produces a distribution zip under `dist/ghidra_<version>_<date>_DeepSeekAI.zip`.
4. Copies the staged extension into `Ghidra/Extensions/DeepSeekAI` and installs tool templates to the user profile.

### Method B - Manual ZIP Installation

1. In Ghidra: `File > Install Extensions...`
2. Click the `+` (green plus) icon in the top right.
3. Select `dist/ghidra_12.1.2_PUBLIC_YYYYMMDD_DeepSeekAI.zip`.
4. Restart Ghidra.

---

## 3. Configuration & API Keys

### Option A - Interactive Settings Dialog

1. Open Ghidra CodeBrowser.
2. Navigate to `Tools > AI Assistant > Settings (Providers & Keys)...`
3. Select your desired AI provider from the dropdown.
4. Enter your API key (or click `Get Key ↗` to open the provider console in browser).
5. (Optional) For Ollama or OpenAI, click `Fetch Models 🔄` to automatically load available models.
6. Click `Test Connection` to verify connectivity, latency, and credentials.
7. Click `Save & Apply`.

### Option B - Environment Variables

The extension automatically reads provider-specific environment variables:

| Provider | Environment Variable |
| :--- | :--- |
| **OpenAI** | `OPENAI_API_KEY` |
| **Anthropic Claude** | `ANTHROPIC_API_KEY` |
| **Google Gemini** | `GEMINI_API_KEY` or `GOOGLE_API_KEY` |
| **DeepSeek** | `DEEPSEEK_API_KEY` |
| **Groq** | `GROQ_API_KEY` |
| **OpenRouter** | `OPENROUTER_API_KEY` |
| **Mistral** | `MISTRAL_API_KEY` |
| **xAI Grok** | `XAI_API_KEY` |
| **Custom** | `CUSTOM_API_KEY` or `AI_API_KEY` |

Windows PowerShell:
```powershell
[System.Environment]::SetEnvironmentVariable("OPENAI_API_KEY", "sk-...", "User")
[System.Environment]::SetEnvironmentVariable("ANTHROPIC_API_KEY", "sk-ant-...", "User")
[System.Environment]::SetEnvironmentVariable("DEEPSEEK_API_KEY", "sk-...", "User")
```

### Option C - Local Properties File

Create `~/.ghidra_ai.properties` (or `~/.deepseek_ghidra.properties`) in your user home directory:

```properties
# Active provider: OPENAI, ANTHROPIC, GEMINI, DEEPSEEK, OLLAMA, GROQ, OPENROUTER, MISTRAL, XAI
provider=OPENAI

# Per-provider API keys
openai.apiKey=sk-...
anthropic.apiKey=sk-ant-...
gemini.apiKey=AIzaSy...
deepseek.apiKey=sk-...
groq.apiKey=gsk_...
openrouter.apiKey=sk-or-...
mistral.apiKey=...
xai.apiKey=...
```

---

## 4. Usage

### 4.1 Single Function Analysis

1. Place your cursor inside any decompiled function in the Decompiler or Listing window.
2. Navigate to `Tools > AI Assistant > Analyze Function` (or right-click menu).
3. The AI reviews the pseudocode, symbols, and calls.
4. An interactive review dialog will open:
   - **Summary Tab**: Natural language explanation of the function.
   - **Variable Renames Tab**: Suggested variable names, types, reasons, and confidence.
   - **Line Comments Tab**: Suggested technical comments mapped to specific instruction addresses.
   - **Complex Blocks Tab**: Identification of tricky algorithms, crypto, or obfuscation.
   - **Uncertainties Tab**: Areas where the AI recommends human verification.
5. Check or uncheck items, then click **Apply Confirmed Changes** to commit them atomically.

### 4.2 Quick Provider Switching

Need to compare GPT-4o with Claude 3.7 or local Ollama?
- Click `Tools > AI Assistant > Quick Switch Provider / Model...`
- Select the new provider from the dialog — credentials and models are switched instantly!

### 4.3 100% Offline Reverse Engineering with Ollama

For air-gapped environments or proprietary firmware where code cannot leave your machine:
1. Install [Ollama](https://ollama.com/) and run:
   ```bash
   ollama run qwen2.5-coder:32b
   # or
   ollama run deepseek-r1:14b
   ```
2. In Ghidra: `Tools > AI Assistant > Settings (Providers & Keys)...`
3. Select **Ollama (Local / Offline RE)**.
4. Base URL defaults to `http://localhost:11434/v1`. No API key required!
5. Click `Fetch Models 🔄` to automatically select your installed model.
6. Click `Test Connection` and `Save & Apply`.

### 4.4 Batch Analysis

Analyze entire binaries or selected function sets sequentially:
1. `Tools > AI Assistant > Batch Analyze Functions...`
2. Configure scope (All functions, Selection, or Current).
3. Filter out library/thunk functions or set size bounds.
4. Enable auto-renaming, comments, caching, and enriched `.c` pseudocode export.
5. Click **Start Batch Analysis**.

---

## 5. Diagnostic Scripts

Included in `ghidra_scripts/`:

| Script | Purpose |
| :--- | :--- |
| `CheckDeepSeekInstall.java` | Verifies installation, dependencies, and all 10 provider integrations. |
| `TestAiApi.java` | Tests live connectivity and latency for any AI provider in GUI or headless mode. |
| `TestBatchAnalysis.java` | Tests batch function selection and execution from script manager. |
| `CheckPluginRegistration.java` | Diagnoses plugin registration status in active tool. |

Run via Ghidra Headless Analyzer:
```powershell
analyzeHeadless.bat C:\Temp TempProj -postScript TestAiApi.java openai gpt-4o sk-...
analyzeHeadless.bat C:\Temp TempProj -postScript TestAiApi.java anthropic claude-3-7-sonnet-20250219 sk-ant-...
analyzeHeadless.bat C:\Temp TempProj -postScript TestAiApi.java ollama qwen2.5-coder:32b
```

---

## 6. License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.