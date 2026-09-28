/* ###
 * DeepSeek AI - Ghidra Extension
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package deepseekai;

import ghidra.framework.options.ToolOptions;
import ghidra.util.Msg;

/**
 * Configuration and persistent options for the DeepSeek AI extension.
 * <p>
 * Options are stored under Ghidra's {@code Edit > Tool Options > DeepSeek AI}.
 * In addition, API keys can be supplied via the {@code DEEPSEEK_API_KEY}
 * environment variable or a local properties file ({@code ~/.deepseek_ghidra.properties}).
 */
public class DeepSeekConfig {

	public static final String OWNER = "DeepSeek AI";

	public static final String OPT_API_KEY = "API Key";
	public static final String OPT_BASE_URL = "Base URL";
	public static final String OPT_MODEL = "Model";
	public static final String OPT_TEMPERATURE = "Temperature";
	public static final String OPT_MAX_TOKENS = "Max Tokens";
	public static final String OPT_TIMEOUT = "Timeout (seconds)";
	public static final String OPT_LANGUAGE = "Response Language";
	public static final String OPT_MAX_CHARS = "Max Code Characters";
	private static final String OPT_AUTO_COMMENTS = "Auto apply comments";
	private static final String OPT_AUTO_RENAMES = "Auto apply variable renames";
	private static final String OPT_AUTO_FUNC_NAME = "Auto apply function name";

	public static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
	public static final String DEFAULT_MODEL = "deepseek-chat";
	public static final String DEFAULT_LANGUAGE = "English";

	/**
	 * API key resolution order:
	 * <ol>
	 * <li>{@code DEEPSEEK_API_KEY} environment variable (if non-empty)</li>
	 * <li>Local properties file ({@code ~/.deepseek_ghidra.properties})</li>
	 * <li>Settings dialog / {@code Edit > Tool Options > DeepSeek AI}</li>
	 * </ol>
	 * The secret key is never embedded in source code so public repositories
	 * remain secure.
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

	/** True if the API key was resolved from the environment variable. */
	public boolean apiKeyFromEnvironment = false;

	/** True if the API key was resolved from the local properties file. */
	public boolean apiKeyFromFile = false;

	/**
	 * Local secret key file. Never committed to git (excluded by {@code .gitignore}).
	 */
	public static final String KEY_FILE_NAME = ".deepseek_ghidra.properties";

	/** Full path to the local key file in the user's home directory. */
	public static java.io.File getKeyFile() {
		return new java.io.File(System.getProperty("user.home", "."), KEY_FILE_NAME);
	}

	/** Reads apiKey from the local key file. Returns null if missing or unreadable. */
	private static String readKeyFromFile() {
		java.io.File file = getKeyFile();
		if (!file.isFile()) {
			return null;
		}
		java.util.Properties props = new java.util.Properties();
		try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(file.toPath(),
			java.nio.charset.StandardCharsets.UTF_8)) {
			props.load(reader);
		}
		catch (Exception e) {
			Msg.warn(DeepSeekConfig.class,
				"Could not read local key file: " + file.getAbsolutePath(), e);
			return null;
		}
		for (String keyName : new String[] { "apiKey", "api_key", "DEEPSEEK_API_KEY", "key" }) {
			String value = props.getProperty(keyName);
			if (!isBlank(value)) {
				return value.trim();
			}
		}
		return null;
	}

	/** Registers options with Ghidra's tool options. */
	public void register(ToolOptions options) {
		registerSafely(options, OPT_API_KEY, DEFAULT_API_KEY,
			"DeepSeek API key (sk-...). If left blank, DEEPSEEK_API_KEY environment " +
				"variable or ~/.deepseek_ghidra.properties is used.");
		registerSafely(options, OPT_BASE_URL, DEFAULT_BASE_URL,
			"DeepSeek API server URL (e.g. https://api.deepseek.com)");
		registerSafely(options, OPT_MODEL, DEFAULT_MODEL,
			"Model name: deepseek-chat or deepseek-reasoner");
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
	}

	private static void registerSafely(ToolOptions options, String name, Object value,
			String description) {
		try {
			options.registerOption(name, value, null, description);
		}
		catch (Throwable t) {
			// Option may already be registered; this is non-fatal.
			Msg.trace(DeepSeekConfig.class, "Could not register option: " + name, t);
		}
	}

	/** Loads settings from Ghidra options. */
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

		resolveApiKeyFallback();

		// Clamp values to reasonable bounds
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

	/**
	 * Resolves only the API key without loading full Ghidra options.
	 * Used by diagnostic scripts and headless runs.
	 */
	public void loadApiKeyOnly() {
		apiKey = DEFAULT_API_KEY;
		resolveApiKeyFallback();
	}

	/** Tries environment variable first, then the local key file if key is empty. */
	private void resolveApiKeyFallback() {
		apiKeyFromEnvironment = false;
		apiKeyFromFile = false;
		if (isBlank(apiKey)) {
			String env = System.getenv("DEEPSEEK_API_KEY");
			if (!isBlank(env)) {
				apiKey = env.trim();
				apiKeyFromEnvironment = true;
			}
		}
		if (isBlank(apiKey)) {
			String fromFile = readKeyFromFile();
			if (!isBlank(fromFile)) {
				apiKey = fromFile;
				apiKeyFromFile = true;
			}
		}
	}

	/** Saves settings to Ghidra options. Keys resolved from env or file are not persisted. */
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

		if ((apiKeyFromEnvironment || apiKeyFromFile) && !hasExplicitApiKey(options)) {
			// User is providing key via env var or local file; avoid writing to Ghidra settings
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

	/** Describes the source of the current API key. */
	public String apiKeySource() {
		if (apiKeyFromEnvironment) {
			return "DEEPSEEK_API_KEY environment variable";
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
		return "model=" + model + ", baseUrl=" + baseUrl + ", temperature=" + temperature +
			", maxTokens=" + maxTokens;
	}
}
