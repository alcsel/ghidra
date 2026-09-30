/* ###
 * Ghidra AI Extension
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package deepseekai;

import java.io.File;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

import ghidra.framework.options.ToolOptions;
import ghidra.util.Msg;

/**
 * Configuration holder for the Ghidra AI Extension.
 * <p>
 * Supports multiple major AI providers (OpenAI, Anthropic Claude, Google Gemini,
 * DeepSeek, Ollama, Groq, OpenRouter, Mistral, xAI, Custom).
 * Maintains per-provider credentials, base URLs, and models so switching
 * between models is instantaneous without losing credentials.
 */
public class DeepSeekConfig {

	public static final String OWNER = "DeepSeek AI";

	public static final String OPT_PROVIDER = "AI Provider";
	public static final String OPT_API_KEY = "API Key";
	public static final String OPT_BASE_URL = "Base URL";
	public static final String OPT_MODEL = "Model";
	public static final String OPT_TEMPERATURE = "Temperature";
	public static final String OPT_MAX_TOKENS = "Max response tokens";
	public static final String OPT_TIMEOUT = "Request timeout (seconds)";
	public static final String OPT_LANGUAGE = "Response language";
	public static final String OPT_MAX_CHARS = "Max decompiled code chars";
	public static final String OPT_AUTO_COMMENTS = "Auto apply comments";
	public static final String OPT_AUTO_RENAMES = "Auto apply variable renames";
	public static final String OPT_AUTO_FUNC_NAME = "Auto apply function name";

	public static final String DEFAULT_BASE_URL = AiProvider.DEEPSEEK.getDefaultBaseUrl();
	public static final String DEFAULT_MODEL = AiProvider.DEEPSEEK.getDefaultModel();
	public static final String DEFAULT_LANGUAGE = "English";
	public static final String DEFAULT_API_KEY = "";

	/** Active AI provider. */
	public AiProvider provider = AiProvider.DEEPSEEK;

	/** Active API key for the selected provider. */
	public String apiKey = DEFAULT_API_KEY;

	/** Active Base URL for the selected provider. */
	public String baseUrl = DEFAULT_BASE_URL;

	/** Active Model identifier. */
	public String model = DEFAULT_MODEL;

	public String language = DEFAULT_LANGUAGE;
	public double temperature = 0.2;
	public int maxTokens = 8192;
	public int timeoutSeconds = 180;
	public int maxCodeChars = 24000;
	public boolean autoApplyComments = false;
	public boolean autoApplyRenames = false;
	public boolean autoApplyFunctionName = false;

	/** True if the current API key was resolved from an environment variable. */
	public boolean apiKeyFromEnvironment = false;

	/** True if the current API key was resolved from a local properties file. */
	public boolean apiKeyFromFile = false;

	/** Name of the environment variable if resolved from environment. */
	public String resolvedEnvVar = "";

	/** Per-provider saved API keys. */
	public final Map<AiProvider, String> providerKeys = new EnumMap<>(AiProvider.class);

	/** Per-provider saved Base URLs. */
	public final Map<AiProvider, String> providerUrls = new EnumMap<>(AiProvider.class);

	/** Per-provider saved models. */
	public final Map<AiProvider, String> providerModels = new EnumMap<>(AiProvider.class);

	/** Local secret key file paths. */
	public static final String KEY_FILE_NEW = ".ghidra_ai.properties";
	public static final String KEY_FILE_LEGACY = ".deepseek_ghidra.properties";

	public DeepSeekConfig() {
		// Initialize defaults for all providers
		for (AiProvider p : AiProvider.values()) {
			providerUrls.put(p, p.getDefaultBaseUrl());
			providerModels.put(p, p.getDefaultModel());
			providerKeys.put(p, "");
		}
	}

	public static File getKeyFile() {
		File primary = new File(System.getProperty("user.home", "."), KEY_FILE_NEW);
		if (primary.isFile()) {
			return primary;
		}
		File legacy = new File(System.getProperty("user.home", "."), KEY_FILE_LEGACY);
		if (legacy.isFile()) {
			return legacy;
		}
		return primary;
	}

	/** Reads key for a given provider from the local properties file. */
	public static String readKeyFromFile(AiProvider p) {
		File file = getKeyFile();
		if (!file.isFile()) {
			File legacy = new File(System.getProperty("user.home", "."), KEY_FILE_LEGACY);
			if (legacy.isFile()) {
				file = legacy;
			}
			else {
				return null;
			}
		}
		Properties props = new Properties();
		try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
			props.load(reader);
		}
		catch (Exception e) {
			Msg.warn(DeepSeekConfig.class, "Could not read key file: " + file.getAbsolutePath(), e);
			return null;
		}

		String prefix = p.name().toLowerCase();
		String[] candidateKeys = new String[] {
			prefix + ".apiKey",
			prefix + ".api_key",
			prefix + "_api_key",
			prefix + "_key",
			prefix + ".key"
		};
		for (String k : candidateKeys) {
			String v = props.getProperty(k);
			if (!isBlank(v)) {
				return v.trim();
			}
		}

		// Also check provider's envVarName inside properties file
		for (String env : p.getEnvVarNames()) {
			String v = props.getProperty(env);
			if (!isBlank(v)) {
				return v.trim();
			}
		}

		// Fallback for default/global keys
		if (p == AiProvider.DEEPSEEK) {
			for (String k : new String[] { "apiKey", "api_key", "DEEPSEEK_API_KEY", "key" }) {
				String v = props.getProperty(k);
				if (!isBlank(v)) {
					return v.trim();
				}
			}
		}
		return null;
	}

	/** Switches the active provider, persisting the current provider settings in memory. */
	public void switchProvider(AiProvider newProvider) {
		if (newProvider == null) {
			return;
		}
		// Save current provider state
		providerKeys.put(provider, apiKey == null ? "" : apiKey);
		providerUrls.put(provider, baseUrl == null ? provider.getDefaultBaseUrl() : baseUrl);
		providerModels.put(provider, model == null ? provider.getDefaultModel() : model);

		this.provider = newProvider;

		// Load new provider state
		this.baseUrl = providerUrls.getOrDefault(newProvider, newProvider.getDefaultBaseUrl());
		this.model = providerModels.getOrDefault(newProvider, newProvider.getDefaultModel());
		this.apiKey = providerKeys.getOrDefault(newProvider, "");

		resolveApiKeyFallback();
	}

	/** Registers options with Ghidra's tool options. */
	public void register(ToolOptions options) {
		registerSafely(options, OPT_PROVIDER, provider.name(),
			"Active AI provider (DeepSeek, OpenAI, Anthropic, Gemini, Ollama, Groq, OpenRouter, Mistral, xAI, Custom)");
		registerSafely(options, OPT_API_KEY, DEFAULT_API_KEY,
			"Active API key. If left blank, environment variable or ~/.ghidra_ai.properties is used.");
		registerSafely(options, OPT_BASE_URL, DEFAULT_BASE_URL,
			"Active AI API server URL");
		registerSafely(options, OPT_MODEL, DEFAULT_MODEL,
			"Active model identifier (e.g. gpt-4o, claude-3-7-sonnet, gemini-2.5-flash, deepseek-chat)");
		registerSafely(options, OPT_TEMPERATURE, Double.valueOf(0.2),
			"Model temperature (0.0 = deterministic, 2.0 = creative)");
		registerSafely(options, OPT_MAX_TOKENS, Integer.valueOf(8192),
			"Maximum response tokens");
		registerSafely(options, OPT_TIMEOUT, Integer.valueOf(180),
			"API request timeout in seconds");
		registerSafely(options, OPT_LANGUAGE, DEFAULT_LANGUAGE,
			"Language for explanations and comments");
		registerSafely(options, OPT_MAX_CHARS, Integer.valueOf(24000),
			"Maximum characters of decompiled code sent to the API");
		registerSafely(options, OPT_AUTO_COMMENTS, Boolean.FALSE,
			"Automatically apply comments without prompting");
		registerSafely(options, OPT_AUTO_RENAMES, Boolean.FALSE,
			"Automatically apply variable renames without prompting");
		registerSafely(options, OPT_AUTO_FUNC_NAME, Boolean.FALSE,
			"Automatically apply suggested function name without prompting");

		// Register provider-specific slots
		for (AiProvider p : AiProvider.values()) {
			registerSafely(options, p.name() + "_KEY", "", "API key for " + p.getDisplayName());
			registerSafely(options, p.name() + "_URL", p.getDefaultBaseUrl(), "Base URL for " + p.getDisplayName());
			registerSafely(options, p.name() + "_MODEL", p.getDefaultModel(), "Model for " + p.getDisplayName());
		}
	}

	private static void registerSafely(ToolOptions options, String name, Object value, String description) {
		try {
			options.registerOption(name, value, null, description);
		}
		catch (Throwable t) {
			Msg.trace(DeepSeekConfig.class, "Could not register option: " + name, t);
		}
	}

	/** Loads settings from Ghidra options. */
	public void load(ToolOptions options) {
		String providerStr = options.getString(OPT_PROVIDER, AiProvider.DEEPSEEK.name());
		this.provider = AiProvider.fromName(providerStr);

		// Load provider slots
		for (AiProvider p : AiProvider.values()) {
			String savedKey = options.getString(p.name() + "_KEY", "");
			String savedUrl = options.getString(p.name() + "_URL", p.getDefaultBaseUrl());
			String savedModel = options.getString(p.name() + "_MODEL", p.getDefaultModel());
			providerKeys.put(p, savedKey);
			providerUrls.put(p, savedUrl);
			providerModels.put(p, savedModel);
		}

		apiKey = options.getString(OPT_API_KEY, DEFAULT_API_KEY);
		baseUrl = options.getString(OPT_BASE_URL, provider.getDefaultBaseUrl());
		model = options.getString(OPT_MODEL, provider.getDefaultModel());

		// If per-provider slot has a key and active apiKey was empty, use provider slot
		if (isBlank(apiKey) && !isBlank(providerKeys.get(provider))) {
			apiKey = providerKeys.get(provider);
		}

		language = options.getString(OPT_LANGUAGE, DEFAULT_LANGUAGE);
		temperature = options.getDouble(OPT_TEMPERATURE, 0.2);
		maxTokens = options.getInt(OPT_MAX_TOKENS, 8192);
		timeoutSeconds = options.getInt(OPT_TIMEOUT, 180);
		maxCodeChars = options.getInt(OPT_MAX_CHARS, 24000);
		autoApplyComments = options.getBoolean(OPT_AUTO_COMMENTS, false);
		autoApplyRenames = options.getBoolean(OPT_AUTO_RENAMES, false);
		autoApplyFunctionName = options.getBoolean(OPT_AUTO_FUNC_NAME, false);

		resolveApiKeyFallback();

		// Bounds check
		if (temperature < 0.0) temperature = 0.0;
		if (temperature > 2.0) temperature = 2.0;
		if (maxTokens <= 0) maxTokens = 8192;
		if (timeoutSeconds < 10) timeoutSeconds = 10;
		if (maxCodeChars < 2000) maxCodeChars = 2000;
	}

	/** Resolves API key for headless scripts or diagnostics without full GUI options. */
	public void loadApiKeyOnly() {
		resolveApiKeyFallback();
	}

	/** Resolves API key fallback from environment variables or properties file. */
	public void resolveApiKeyFallback() {
		apiKeyFromEnvironment = false;
		apiKeyFromFile = false;
		resolvedEnvVar = "";

		if (!provider.requiresApiKey()) {
			return;
		}

		if (isBlank(apiKey)) {
			// Check provider-specific environment variables
			for (String envName : provider.getEnvVarNames()) {
				String envVal = System.getenv(envName);
				if (!isBlank(envVal)) {
					apiKey = envVal.trim();
					apiKeyFromEnvironment = true;
					resolvedEnvVar = envName;
					return;
				}
			}
			// Also check generic AI_API_KEY
			String genericEnv = System.getenv("AI_API_KEY");
			if (!isBlank(genericEnv)) {
				apiKey = genericEnv.trim();
				apiKeyFromEnvironment = true;
				resolvedEnvVar = "AI_API_KEY";
				return;
			}
		}

		if (isBlank(apiKey)) {
			String fromFile = readKeyFromFile(provider);
			if (!isBlank(fromFile)) {
				apiKey = fromFile;
				apiKeyFromFile = true;
			}
		}
	}

	/** Saves settings to Ghidra options. */
	public void save(ToolOptions options) {
		options.setString(OPT_PROVIDER, provider.name());
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

		// Save current into map
		providerKeys.put(provider, apiKey == null ? "" : apiKey);
		providerUrls.put(provider, baseUrl);
		providerModels.put(provider, model);

		// Save per-provider options
		for (AiProvider p : AiProvider.values()) {
			String k = providerKeys.get(p);
			if (k != null) {
				options.setString(p.name() + "_KEY", k);
			}
			String u = providerUrls.get(p);
			if (u != null) {
				options.setString(p.name() + "_URL", u);
			}
			String m = providerModels.get(p);
			if (m != null) {
				options.setString(p.name() + "_MODEL", m);
			}
		}

		if ((apiKeyFromEnvironment || apiKeyFromFile) && !hasExplicitApiKey(options)) {
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
		if (!provider.requiresApiKey()) {
			return true;
		}
		return !isBlank(apiKey);
	}

	/** Describes the source of the current API key. */
	public String apiKeySource() {
		if (!provider.requiresApiKey() && isBlank(apiKey)) {
			return "Not required (" + provider.getDisplayName() + ")";
		}
		if (apiKeyFromEnvironment) {
			return resolvedEnvVar.isEmpty() ? "Environment variable" : resolvedEnvVar + " environment variable";
		}
		if (apiKeyFromFile) {
			return getKeyFile().getAbsolutePath();
		}
		return "Ghidra options (Edit > Tool Options > DeepSeek AI)";
	}

	public static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	@Override
	public String toString() {
		return "provider=" + provider.name() + ", model=" + model + ", baseUrl=" + baseUrl +
			", temperature=" + temperature + ", maxTokens=" + maxTokens;
	}
}