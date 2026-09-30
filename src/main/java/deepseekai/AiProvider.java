/* ###
 * Ghidra AI Extension
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package deepseekai;

import java.util.Arrays;
import java.util.List;

/**
 * Major AI providers supported by the Ghidra AI Extension.
 */
public enum AiProvider {

	DEEPSEEK(
		"DeepSeek",
		"https://api.deepseek.com",
		"deepseek-chat",
		new String[] { "deepseek-chat", "deepseek-reasoner" },
		new String[] { "DEEPSEEK_API_KEY" },
		"https://platform.deepseek.com/api_keys",
		"DeepSeek V3 (fast & economical) and R1 (deep reasoning) models.",
		true,
		ApiProtocol.OPENAI_COMPATIBLE
	),

	OPENAI(
		"OpenAI (ChatGPT / GPT-4o / o1 / o3)",
		"https://api.openai.com/v1",
		"gpt-4o",
		new String[] { "gpt-4o", "gpt-4o-mini", "o1", "o3-mini", "gpt-4.5-preview", "gpt-4-turbo" },
		new String[] { "OPENAI_API_KEY" },
		"https://platform.openai.com/api-keys",
		"Industry-standard OpenAI models: GPT-4o, GPT-4o-mini, o1, and o3-mini.",
		true,
		ApiProtocol.OPENAI_COMPATIBLE
	),

	ANTHROPIC(
		"Anthropic Claude (Claude 3.7 / 3.5 Sonnet)",
		"https://api.anthropic.com/v1",
		"claude-3-7-sonnet-20250219",
		new String[] {
			"claude-3-7-sonnet-20250219",
			"claude-3-5-sonnet-20241022",
			"claude-3-5-haiku-20241022",
			"claude-3-opus-20240229"
		},
		new String[] { "ANTHROPIC_API_KEY" },
		"https://console.anthropic.com/settings/keys",
		"Claude 3.7 Sonnet (hybrid reasoning), Claude 3.5 Sonnet, and Haiku via Messages API.",
		true,
		ApiProtocol.ANTHROPIC_MESSAGES
	),

	GEMINI(
		"Google Gemini (Gemini 2.5 Pro / Flash)",
		"https://generativelanguage.googleapis.com/v1beta/openai",
		"gemini-2.5-flash",
		new String[] {
			"gemini-2.5-pro",
			"gemini-2.5-flash",
			"gemini-2.0-flash",
			"gemini-1.5-pro",
			"gemini-1.5-flash"
		},
		new String[] { "GEMINI_API_KEY", "GOOGLE_API_KEY" },
		"https://aistudio.google.com/app/apikey",
		"Google Gemini 2.5 and 2.0 models via official OpenAI-compatible endpoint.",
		true,
		ApiProtocol.OPENAI_COMPATIBLE
	),

	OLLAMA(
		"Ollama (Local / Offline RE)",
		"http://localhost:11434/v1",
		"qwen2.5-coder:32b",
		new String[] {
			"qwen2.5-coder:32b",
			"qwen2.5-coder:7b",
			"llama3.3:70b",
			"deepseek-r1:14b",
			"deepseek-r1:8b",
			"codellama",
			"mistral",
			"phi4"
		},
		new String[] { "OLLAMA_API_KEY" },
		"http://localhost:11434",
		"Run models locally on your machine for 100% offline, confidential reverse engineering. No key required!",
		false,
		ApiProtocol.OPENAI_COMPATIBLE
	),

	GROQ(
		"Groq (Ultra-Fast LPU)",
		"https://api.groq.com/openai/v1",
		"llama-3.3-70b-versatile",
		new String[] {
			"llama-3.3-70b-versatile",
			"deepseek-r1-distill-llama-70b",
			"llama-3.1-8b-instant",
			"mixtral-8x7b-32768"
		},
		new String[] { "GROQ_API_KEY" },
		"https://console.groq.com/keys",
		"Ultra-fast inference (hundreds of tokens/sec) for Llama 3.3 and DeepSeek R1 distillations.",
		true,
		ApiProtocol.OPENAI_COMPATIBLE
	),

	OPENROUTER(
		"OpenRouter (Universal AI Aggregator)",
		"https://openrouter.ai/api/v1",
		"anthropic/claude-3.7-sonnet",
		new String[] {
			"anthropic/claude-3.7-sonnet",
			"deepseek/deepseek-r1",
			"deepseek/deepseek-chat",
			"google/gemini-2.5-pro",
			"openai/gpt-4o",
			"meta-llama/llama-3.3-70b-instruct"
		},
		new String[] { "OPENROUTER_API_KEY" },
		"https://openrouter.ai/keys",
		"Access hundreds of models from OpenAI, Anthropic, Google, Meta with a single API key.",
		true,
		ApiProtocol.OPENAI_COMPATIBLE
	),

	MISTRAL(
		"Mistral AI (Codestral)",
		"https://api.mistral.ai/v1",
		"codestral-latest",
		new String[] { "codestral-latest", "mistral-large-latest", "mistral-small-latest" },
		new String[] { "MISTRAL_API_KEY" },
		"https://console.mistral.ai/api-keys",
		"Mistral models including Codestral, fine-tuned specifically for code reasoning.",
		true,
		ApiProtocol.OPENAI_COMPATIBLE
	),

	XAI(
		"xAI (Grok)",
		"https://api.x.ai/v1",
		"grok-2",
		new String[] { "grok-2", "grok-2-mini", "grok-beta" },
		new String[] { "XAI_API_KEY" },
		"https://console.x.ai/",
		"xAI Grok models.",
		true,
		ApiProtocol.OPENAI_COMPATIBLE
	),

	CUSTOM(
		"Custom / Local OpenAI-Compatible",
		"http://localhost:1234/v1",
		"local-model",
		new String[] { "local-model", "custom-model" },
		new String[] { "CUSTOM_API_KEY", "AI_API_KEY" },
		"http://localhost:1234",
		"Any custom OpenAI-compatible server (LM Studio, vLLM, TextGenWebUI, Azure OpenAI, etc.).",
		false,
		ApiProtocol.OPENAI_COMPATIBLE
	);

	private final String displayName;
	private final String defaultBaseUrl;
	private final String defaultModel;
	private final String[] recommendedModels;
	private final String[] envVarNames;
	private final String apiKeyUrl;
	private final String description;
	private final boolean requiresApiKey;
	private final ApiProtocol protocol;

	AiProvider(String displayName, String defaultBaseUrl, String defaultModel,
			String[] recommendedModels, String[] envVarNames, String apiKeyUrl,
			String description, boolean requiresApiKey, ApiProtocol protocol) {
		this.displayName = displayName;
		this.defaultBaseUrl = defaultBaseUrl;
		this.defaultModel = defaultModel;
		this.recommendedModels = recommendedModels;
		this.envVarNames = envVarNames;
		this.apiKeyUrl = apiKeyUrl;
		this.description = description;
		this.requiresApiKey = requiresApiKey;
		this.protocol = protocol;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getDefaultBaseUrl() {
		return defaultBaseUrl;
	}

	public String getDefaultModel() {
		return defaultModel;
	}

	public List<String> getRecommendedModels() {
		return Arrays.asList(recommendedModels);
	}

	public String[] getEnvVarNames() {
		return envVarNames;
	}

	public String getApiKeyUrl() {
		return apiKeyUrl;
	}

	public String getDescription() {
		return description;
	}

	public boolean requiresApiKey() {
		return requiresApiKey;
	}

	public ApiProtocol getProtocol() {
		return protocol;
	}

	public static AiProvider fromName(String name) {
		if (name == null || name.trim().isEmpty()) {
			return DEEPSEEK;
		}
		String clean = name.trim();
		for (AiProvider p : values()) {
			if (p.name().equalsIgnoreCase(clean) || p.displayName.equalsIgnoreCase(clean)) {
				return p;
			}
		}
		for (AiProvider p : values()) {
			if (p.displayName.toLowerCase().contains(clean.toLowerCase()) ||
				p.name().toLowerCase().contains(clean.toLowerCase())) {
				return p;
			}
		}
		return DEEPSEEK;
	}

	public static boolean isReasoningModel(String model) {
		if (model == null) {
			return false;
		}
		String lower = model.toLowerCase();
		return lower.contains("reasoner") || lower.contains("-r1") || lower.startsWith("o1") ||
			lower.startsWith("o3") || lower.contains("claude-3-7");
	}

	public boolean supportsDynamicModelList() {
		return this == OLLAMA || this == OPENAI || this == GROQ ||
			this == OPENROUTER || this == MISTRAL || this == CUSTOM;
	}

	@Override
	public String toString() {
		return displayName;
	}
}