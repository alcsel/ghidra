/* ###
 * DeepSeek AI - Ghidra Extension
 *
 * DeepSeek API baglantisini test eden betik.
 *
 * Ghidra'da : Window > Script Manager > TestDeepSeekApi > Run
 * Headless  : analyzeHeadless ... -postScript TestDeepSeekApi.java
 *             (anahtari arguman olarak da verebilirsiniz: ... TestDeepSeekApi.java sk-xxx)
 *
 * Anahtar su sirayla aranir:
 *   1. Betik argumani (scriptArgs[0])
 *   2. DEEPSEEK_API_KEY ortam degiskeni
 *
 * Not: Bu betik gercek bir API istegi gonderir (cok kucuk: ~30 token).
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
			keySource = "betik argumani";
		}
		else {
			// DEEPSEEK_API_KEY ortam degiskeni -> ~/.deepseek_ghidra.properties
			config.loadApiKeyOnly();
			keySource = config.apiKeySource();
		}

		if (!config.hasApiKey()) {
			println("API anahtari bulunamadi. Sirayla denendi:");
			println("  1) betik argumani");
			println("  2) DEEPSEEK_API_KEY ortam degiskeni");
			println("  3) " + DeepSeekConfig.getKeyFile().getAbsolutePath() + "  (apiKey=sk-...)");
			println("");
			println("Ornek: TestDeepSeekApi.java sk-xxxxxxxx");
			return;
		}

		config.temperature = 0.0;
		config.maxTokens = 32;
		config.timeoutSeconds = 60;

		println("=== DeepSeek API baglanti testi ===");
		println("Uç nokta (endpoint) : " + DeepSeekClient.chatCompletionsUrl(config.baseUrl));
		println("Model               : " + config.model);
		println("Anahtar kaynagi     : " + keySource + " (" + mask(config.apiKey) + ")");
		println("");

		try {
			DeepSeekClient.ChatResponse response =
				new DeepSeekClient().chat("Kisa cevap ver.", "Sadece 'ok' yaz.", config, null);
			println("SONUC    : BASARILI");
			println("Yanit    : " + response.content.trim());
			if (!response.usageText().isEmpty()) {
				println("Kullanim : " + response.usageText());
			}
		}
		catch (Throwable t) {
			println("SONUC    : BASARISIZ");
			println("Hata     : " + t);
		}
	}

	private String mask(String key) {
		if (key == null || key.length() < 8) {
			return "(tanimsiz)";
		}
		return key.substring(0, 6) + "..." + key.substring(key.length() - 4);
	}
}
