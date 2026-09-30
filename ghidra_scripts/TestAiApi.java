/* ###
 * Ghidra AI Extension
 *
 * Universal Connectivity Test Script for AI Providers.
 * Supports OpenAI, Anthropic Claude, Google Gemini, DeepSeek, Ollama, Groq, OpenRouter, Mistral, xAI.
 *
 * In Ghidra : Window > Script Manager > TestAiApi > Run
 * Headless  : analyzeHeadless ... -postScript TestAiApi.java [provider] [model] [apiKey]
 *
 * Examples:
 *   analyzeHeadless ... -postScript TestAiApi.java
 *   analyzeHeadless ... -postScript TestAiApi.java openai gpt-4o sk-...
 *   analyzeHeadless ... -postScript TestAiApi.java anthropic claude-3-7-sonnet-20250219 sk-ant-...
 *   analyzeHeadless ... -postScript TestAiApi.java ollama llama3.3
 *   analyzeHeadless ... -postScript TestAiApi.java gemini gemini-2.5-flash AIzaSy...
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
import deepseekai.AiProvider;
import deepseekai.DeepSeekClient;
import deepseekai.DeepSeekConfig;
import ghidra.app.script.GhidraScript;

public class TestAiApi extends GhidraScript {

	@Override
	protected void run() throws Exception {
		DeepSeekConfig config = new DeepSeekConfig();

		String[] args = getScriptArgs();
		if (args != null && args.length > 0 && args[0] != null && !args[0].trim().isEmpty()) {
			config.provider = AiProvider.fromName(args[0].trim());
			config.baseUrl = config.provider.getDefaultBaseUrl();
			config.model = config.provider.getDefaultModel();
		}
		if (args != null && args.length > 1 && args[1] != null && !args[1].trim().isEmpty()) {
			config.model = args[1].trim();
		}
		if (args != null && args.length > 2 && args[2] != null && !args[2].trim().isEmpty()) {
			config.apiKey = args[2].trim();
		}
		else {
			config.loadApiKeyOnly();
		}

		println("==========================================================");
		println(" AI Provider Connectivity Test");
		println("==========================================================");
		println("Provider     : " + config.provider.getDisplayName());
		println("Protocol     : " + config.provider.getProtocol());
		println("Model        : " + config.model);
		println("Base URL     : " + config.baseUrl);
		println("Key Source   : " + config.apiKeySource() + " (" + mask(config.apiKey) + ")");
		println("");

		if (config.provider.requiresApiKey() && !config.hasApiKey()) {
			println("[ERROR] API key not found. Please provide key via:");
			println("  1) Script argument: TestAiApi.java " + config.provider.name().toLowerCase() + " <model> <apiKey>");
			println("  2) Environment variable: " + String.join(" / ", config.provider.getEnvVarNames()));
			println("  3) Local file: " + DeepSeekConfig.getKeyFile().getAbsolutePath());
			return;
		}

		config.temperature = 0.0;
		config.maxTokens = 32;
		config.timeoutSeconds = 45;

		println("Sending test prompt to " + config.provider.getDisplayName() + "...");
		long start = System.currentTimeMillis();
		try {
			DeepSeekClient.ChatResponse response = new DeepSeekClient().chat(
				"You are a connectivity test assistant. Reply strictly with 'OK'.",
				"Ping",
				config,
				monitor
			);
			long elapsed = System.currentTimeMillis() - start;
			println("");
			println("RESULT       : SUCCESS (" + elapsed + " ms)");
			println("Response     : " + response.content.trim());
			if (!response.reasoningContent.isEmpty()) {
				println("Reasoning    : " + DeepSeekClient.abbreviate(response.reasoningContent, 80));
			}
			if (!response.usageText().isEmpty()) {
				println("Token Usage  : " + response.usageText());
			}
		}
		catch (Throwable t) {
			long elapsed = System.currentTimeMillis() - start;
			println("");
			println("RESULT       : FAILED (" + elapsed + " ms)");
			println("Error        : " + t.getMessage());
		}
	}

	private String mask(String key) {
		if (key == null || key.isEmpty()) {
			return "(empty)";
		}
		if (key.length() < 8) {
			return "***";
		}
		return key.substring(0, 6) + "..." + key.substring(key.length() - 4);
	}
}