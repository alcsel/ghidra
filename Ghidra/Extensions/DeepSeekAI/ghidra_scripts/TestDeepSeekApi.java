/* ###
 * DeepSeek AI - Ghidra Extension
 *
 * Connectivity test script for DeepSeek API.
 *
 * In Ghidra : Window > Script Manager > TestDeepSeekApi > Run
 * Headless  : analyzeHeadless ... -postScript TestDeepSeekApi.java
 *             (API key can be passed as argument: ... TestDeepSeekApi.java sk-xxx)
 *
 * Key lookup order:
 *   1. Script argument (scriptArgs[0])
 *   2. DEEPSEEK_API_KEY environment variable
 *   3. Local properties file (~/.deepseek_ghidra.properties)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
import deepseekai.DeepSeekClient;
import deepseekai.DeepSeekConfig;
import ghidra.app.script.GhidraScript;

public class TestDeepSeekApi extends GhidraScript {

	@Override
	protected void run() throws Exception {
		DeepSeekConfig config = new DeepSeekConfig();

		String[] args = getScriptArgs();
		String keySource;
		if (args != null && args.length > 0 && args[0] != null && !args[0].trim().isEmpty()) {
			config.apiKey = args[0].trim();
			keySource = "script argument";
		}
		else {
			config.loadApiKeyOnly();
			keySource = config.apiKeySource();
		}

		if (!config.hasApiKey()) {
			println("API key not found. Tried in order:");
			println("  1) Script argument");
			println("  2) DEEPSEEK_API_KEY environment variable");
			println("  3) " + DeepSeekConfig.getKeyFile().getAbsolutePath() + "  (apiKey=sk-...)");
			println("");
			println("Example usage: TestDeepSeekApi.java sk-xxxxxxxx");
			return;
		}

		config.temperature = 0.0;
		config.maxTokens = 32;
		config.timeoutSeconds = 60;

		println("=== DeepSeek API Connectivity Test ===");
		println("Endpoint     : " + DeepSeekClient.chatCompletionsUrl(config.baseUrl));
		println("Model        : " + config.model);
		println("Key source   : " + keySource + " (" + mask(config.apiKey) + ")");
		println("");

		try {
			DeepSeekClient.ChatResponse response =
				new DeepSeekClient().chat("Give a short answer.", "Reply with only 'ok'.", config, null);
			println("RESULT : SUCCESS");
			println("Reply  : " + response.content.trim());
			if (!response.usageText().isEmpty()) {
				println("Usage  : " + response.usageText());
			}
		}
		catch (Throwable t) {
			println("RESULT : FAILED");
			println("Error  : " + t);
		}
	}

	private String mask(String key) {
		if (key == null || key.length() < 8) {
			return "(undefined)";
		}
		return key.substring(0, 6) + "..." + key.substring(key.length() - 4);
	}
}
