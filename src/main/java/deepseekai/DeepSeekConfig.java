/* ###
 * DeepSeek AI - Ghidra Extension
 *
 * DeepSeek API kullanarak decompile edilmis kodu aciklayan, anlasilmasi zor
 * kisimlara yorum yazan ve degiskenleri isimlendiren Ghidra eklentisi.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package deepseekai;

import ghidra.framework.options.ToolOptions;
import ghidra.util.Msg;

/**
 * Eklenti ayarlari.
 * <p>
 * Ayarlar Ghidra arac (tool) opsiyonlarinda saklanir; boylece
 * <b>Edit -&gt; Tool Options -&gt; DeepSeek AI</b> menusunden de gorulup
 * degistirilebilir ve Ghidra yeniden baslatildiginda korunur.
 * <p>
 * API anahtari ayrica {@code DEEPSEEK_API_KEY} ortam degiskeninden de
 * okunur; bu degisken tanimli ise ve opsiyon bos birakilmissa ortam
 * degiskeni kullanilir.
 */
public class DeepSeekConfig {

	/** Opsiyon grubunun (owner) adi. */
	public static final String OWNER = "DeepSeek AI";

	private static final String OPT_API_KEY = "API Key";
	private static final String OPT_BASE_URL = "API Base URL";
	private static final String OPT_MODEL = "Model";
	private static final String OPT_TEMPERATURE = "Temperature";
	private static final String OPT_MAX_TOKENS = "Max Tokens";
	private static final String OPT_TIMEOUT = "Request Timeout (seconds)";
	private static final String OPT_LANGUAGE = "Response Language";
	private static final String OPT_MAX_CHARS = "Max decompiled code characters";
	private static final String OPT_AUTO_COMMENTS = "Auto apply comments";
	private static final String OPT_AUTO_RENAMES = "Auto apply variable renames";
	private static final String OPT_AUTO_FUNC_NAME = "Auto apply function name";

	public static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
	public static final String DEFAULT_MODEL = "deepseek-chat";
	public static final String DEFAULT_LANGUAGE = "Turkce";

	/**
	 * API anahtari kaynak onceligi:
	 * <ol>
	 * <li>{@code DEEPSEEK_API_KEY} ortam degiskeni (bos degilse)</li>
	 * <li>Ayarlar penceresi / Edit &gt; Tool Options &gt; DeepSeek AI</li>
	 * </ol>
	 * Gizli anahtar bilerek kaynak koda gomulmemistir; boylece depo paylasima
	 * acik olsa bile sizinti olmaz.
	 */
	public static final String DEFAULT_API_KEY = "";

	public String apiKey = DEFAULT_API_KEY;
	public String baseUrl = DEFAULT_BASE_URL;
	public String model = DEFAULT_MODEL;
	public String language = DEFAULT_LANGUAGE;
	public double temperature = 0.2;
	public int maxTokens = 8192;
	public int timeoutSeconds = 180;
	public int maxCodeChars = 24000;
	public boolean autoApplyComments = false;
	public boolean autoApplyRenames = false;
	public boolean autoApplyFunctionName = false;

	/** true ise API anahtari ortam degiskeninden gelmistir. */
	public boolean apiKeyFromEnvironment = false;

	/** Opsiyonlari Ghidra'ya kaydeder. */
	public void register(ToolOptions options) {
		registerSafely(options, OPT_API_KEY, DEFAULT_API_KEY,
			"DeepSeek API anahtari (sk-...). DeepSeek API'sinden alinir.");
		registerSafely(options, OPT_BASE_URL, DEFAULT_BASE_URL,
			"DeepSeek API sunucusu (orn. https://api.deepseek.com)");
		registerSafely(options, OPT_MODEL, DEFAULT_MODEL,
			"Kullanilacak model: deepseek-chat veya deepseek-reasoner");
		registerSafely(options, OPT_TEMPERATURE, Double.valueOf(0.2),
			"Modelin yaraticiligi (0.0 = kararli, 2.0 = yaratici)");
		registerSafely(options, OPT_MAX_TOKENS, Integer.valueOf(8192),
			"Yanit icin en fazla token sayisi");
		registerSafely(options, OPT_TIMEOUT, Integer.valueOf(180),
			"API istegi zaman asimi (saniye)");
		registerSafely(options, OPT_LANGUAGE, DEFAULT_LANGUAGE,
			"Aciklamalarin ve yorumlarin dili");
		registerSafely(options, OPT_MAX_CHARS, Integer.valueOf(24000),
			"API'ye gonderilecek en fazla decompile edilmis kod karakteri");
		registerSafely(options, OPT_AUTO_COMMENTS, Boolean.FALSE,
			"Analiz bittikten sonra yorumlari sormadan uygula");
		registerSafely(options, OPT_AUTO_RENAMES, Boolean.FALSE,
			"Analiz bittikten sonra degisken isimlendirmelerini sormadan uygula");
		registerSafely(options, OPT_AUTO_FUNC_NAME, Boolean.FALSE,
			"Analiz bittikten sonra fonksiyon adini sormadan uygula");
	}

	private static void registerSafely(ToolOptions options, String name, Object value,
			String description) {
		try {
			options.registerOption(name, value, null, description);
		}
		catch (Throwable t) {
			// Opsiyon zaten kayitli olabilir; bu durumda sorun degil.
			Msg.trace(DeepSeekConfig.class, "Opsiyon kaydedilemedi: " + name, t);
		}
	}

	/** Opsiyonlardan ayarlari okur. */
	public void load(ToolOptions options) {
		apiKey = options.getString(OPT_API_KEY, DEFAULT_API_KEY);
		baseUrl = options.getString(OPT_BASE_URL, DEFAULT_BASE_URL);
		model = options.getString(OPT_MODEL, DEFAULT_MODEL);
		language = options.getString(OPT_LANGUAGE, DEFAULT_LANGUAGE);
		temperature = options.getDouble(OPT_TEMPERATURE, 0.2);
		maxTokens = options.getInt(OPT_MAX_TOKENS, 8192);
		timeoutSeconds = options.getInt(OPT_TIMEOUT, 180);
		maxCodeChars = options.getInt(OPT_MAX_CHARS, 24000);
		autoApplyComments = options.getBoolean(OPT_AUTO_COMMENTS, false);
		autoApplyRenames = options.getBoolean(OPT_AUTO_RENAMES, false);
		autoApplyFunctionName = options.getBoolean(OPT_AUTO_FUNC_NAME, false);

		apiKeyFromEnvironment = false;
		if (isBlank(apiKey)) {
			String env = System.getenv("DEEPSEEK_API_KEY");
			if (!isBlank(env)) {
				apiKey = env.trim();
				apiKeyFromEnvironment = true;
			}
		}

		// Makul araliklara kirp
		if (temperature < 0.0) {
			temperature = 0.0;
		}
		if (temperature > 2.0) {
			temperature = 2.0;
		}
		if (maxTokens <= 0) {
			maxTokens = 8192;
		}
		if (timeoutSeconds < 10) {
			timeoutSeconds = 10;
		}
		if (maxCodeChars < 2000) {
			maxCodeChars = 2000;
		}
	}

	/** Ayarlari Ghidra'ya yazar. Ortam degiskeninden gelen anahtar yazilmaz. */
	public void save(ToolOptions options) {
		options.setString(OPT_BASE_URL, baseUrl);
		options.setString(OPT_MODEL, model);
		options.setString(OPT_LANGUAGE, language);
		options.setDouble(OPT_TEMPERATURE, temperature);
		options.setInt(OPT_MAX_TOKENS, maxTokens);
		options.setInt(OPT_TIMEOUT, timeoutSeconds);
		options.setInt(OPT_MAX_CHARS, maxCodeChars);
		options.setBoolean(OPT_AUTO_COMMENTS, autoApplyComments);
		options.setBoolean(OPT_AUTO_RENAMES, autoApplyRenames);
		options.setBoolean(OPT_AUTO_FUNC_NAME, autoApplyFunctionName);

		if (apiKeyFromEnvironment && !hasExplicitApiKey(options)) {
			// Kullanici ortam degiskenini kullaniyor; opsiyonu kirletmeyelim.
			return;
		}
		options.setString(OPT_API_KEY, apiKey == null ? "" : apiKey);
	}

	private static boolean hasExplicitApiKey(ToolOptions options) {
		try {
			return !isBlank(options.getString(OPT_API_KEY, ""));
		}
		catch (Throwable t) {
			return false;
		}
	}

	public boolean hasApiKey() {
		return !isBlank(apiKey);
	}

	public static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	@Override
	public String toString() {
		return "model=" + model + ", baseUrl=" + baseUrl + ", temperature=" + temperature +
			", maxTokens=" + maxTokens;
	}
}
