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

		String key = null;
		String keySource = "";
		String[] args = getScriptArgs();
		if (args != null && args.length > 0 && args[0] != null && !args[0].trim().isEmpty()) {
			key = args[0].trim();
			keySource = "betik argumani";
		}
		if (key == null) {
			String env = System.getenv("DEEPSEEK_API_KEY");
			if (env != null && !env.trim().isEmpty()) {
				key = env.trim();
				keySource = "DEEPSEEK_API_KEY ortam degiskeni";
			}
		}

		if (key == null) {
			println("API anahtari bulunamadi.");
			println("Ya DEEPSEEK_API_KEY ortam degiskenini tanimlayin ya da betige");
			println("arguman olarak verin: TestDeepSeekApi.java sk-xxxxxxxx");
			return;
		}

		config.apiKey = key;
		config.temperature = 0.0;
		config.maxTokens = 32;
		config.timeoutSeconds = 60;

		println("=== DeepSeek API baglanti testi ===");
		println("Uç nokta (endpoint) : " + DeepSeekClient.chatCompletionsUrl(config.baseUrl));
		println("Model               : " + config.model);
		println("Anahtar kaynagi     : " + keySource + " (" + mask(key) + ")");
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
