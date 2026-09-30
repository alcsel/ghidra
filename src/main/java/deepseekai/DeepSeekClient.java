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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ghidra.util.exception.CancelledException;
import ghidra.util.task.TaskMonitor;

/**
 * Universal AI client supporting major LLM providers:
 * <ul>
 * <li>OpenAI (GPT-4o, o1, o3-mini)</li>
 * <li>Anthropic Claude (Claude 3.7 / 3.5 Sonnet Messages API)</li>
 * <li>Google Gemini (Gemini 2.5 Pro / Flash)</li>
 * <li>DeepSeek (V3 and R1 reasoning)</li>
 * <li>Ollama (Local offline RE, no key required)</li>
 * <li>Groq (Ultra-fast Llama 3.3)</li>
 * <li>OpenRouter (Universal gateway)</li>
 * <li>Mistral AI (Codestral)</li>
 * <li>xAI Grok</li>
 * <li>Custom / Local OpenAI-compatible servers (vLLM, LM Studio)</li>
 * </ul>
 */
public class DeepSeekClient {

	/** Parsed response returned by any AI provider. */
	public static class ChatResponse {
		public String content = "";
		public String reasoningContent = "";
		public String finishReason = "";
		public int promptTokens;
		public int completionTokens;
		public int totalTokens;
		public String providerName = "";
		public String modelName = "";

		public String usageText() {
			if (totalTokens <= 0) {
				return "";
			}
			return "tokens: " + promptTokens + " prompt + " + completionTokens + " completion = " +
				totalTokens;
		}
	}

	private final HttpClient httpClient;

	public DeepSeekClient() {
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(30))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
	}

	/**
	 * Dispatches chat request to the active provider and protocol.
	 */
	public ChatResponse chat(String systemPrompt, String userPrompt, DeepSeekConfig config,
			TaskMonitor monitor) throws IOException, CancelledException {

		if (config.provider.requiresApiKey() && DeepSeekConfig.isBlank(config.apiKey)) {
			throw new IOException(config.provider.getDisplayName() + " API key is not configured. " +
				"Configure it via Tools > DeepSeek AI > Settings (or set " +
				String.join(" / ", config.provider.getEnvVarNames()) + ").");
		}

		if (config.provider.getProtocol() == ApiProtocol.ANTHROPIC_MESSAGES) {
			return chatAnthropic(systemPrompt, userPrompt, config, monitor);
		}

		return chatOpenAiCompatible(systemPrompt, userPrompt, config, monitor);
	}

	// ------------------------------------------------------------------
	// OpenAI-Compatible Protocol (OpenAI, DeepSeek, Ollama, Gemini, Groq, etc.)
	// ------------------------------------------------------------------

	private ChatResponse chatOpenAiCompatible(String systemPrompt, String userPrompt,
			DeepSeekConfig config, TaskMonitor monitor) throws IOException, CancelledException {

		JsonObject payload = new JsonObject();
		payload.addProperty("model", config.model);

		boolean isReasoning = AiProvider.isReasoningModel(config.model);
		boolean isOpenAiO = config.model.toLowerCase().startsWith("o1") ||
			config.model.toLowerCase().startsWith("o3");

		// OpenAI o1/o3 reasoning models use max_completion_tokens and do not support custom temperature
		if (isOpenAiO) {
			payload.addProperty("max_completion_tokens", config.maxTokens);
		}
		else {
			payload.addProperty("temperature", config.temperature);
			payload.addProperty("max_tokens", config.maxTokens);
		}
		payload.addProperty("stream", false);

		JsonArray messages = new JsonArray();
		messages.add(message("system", systemPrompt));
		messages.add(message("user", userPrompt));
		payload.add("messages", messages);

		String body = payload.toString();
		String url = chatCompletionsUrl(config.baseUrl);

		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(Math.max(10, config.timeoutSeconds)))
				.header("Content-Type", "application/json; charset=utf-8")
				.header("Accept", "application/json");

		if (!DeepSeekConfig.isBlank(config.apiKey)) {
			builder.header("Authorization", "Bearer " + config.apiKey.trim());
		}

		// OpenRouter attribution headers
		if (config.provider == AiProvider.OPENROUTER || url.contains("openrouter.ai")) {
			builder.header("HTTP-Referer", "https://github.com/ghidra-ai/ghidra-ai-extension");
			builder.header("X-Title", "Ghidra AI Extension");
		}

		HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();

		checkCancelled(monitor);
		HttpResponse<String> response = sendRequest(request, config.timeoutSeconds);
		checkCancelled(monitor);

		int status = response.statusCode();
		String responseBody = response.body();

		if (status < 200 || status >= 300) {
			String message = extractErrorMessage(responseBody);
			throw new IOException("HTTP " + status + " (" + message + ")");
		}

		ChatResponse res = parseOpenAiResponse(responseBody);
		res.providerName = config.provider.getDisplayName();
		res.modelName = config.model;
		return res;
	}

	// ------------------------------------------------------------------
	// Anthropic Claude Messages Protocol (/v1/messages)
	// ------------------------------------------------------------------

	private ChatResponse chatAnthropic(String systemPrompt, String userPrompt,
			DeepSeekConfig config, TaskMonitor monitor) throws IOException, CancelledException {

		JsonObject payload = new JsonObject();
		payload.addProperty("model", config.model);
		payload.addProperty("max_tokens", config.maxTokens);
		payload.addProperty("temperature", config.temperature);
		payload.addProperty("system", systemPrompt);

		JsonArray messages = new JsonArray();
		JsonObject userMsg = new JsonObject();
		userMsg.addProperty("role", "user");
		userMsg.addProperty("content", userPrompt);
		messages.add(userMsg);
		payload.add("messages", messages);

		String body = payload.toString();
		String url = anthropicMessagesUrl(config.baseUrl);

		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(Math.max(10, config.timeoutSeconds)))
				.header("Content-Type", "application/json; charset=utf-8")
				.header("Accept", "application/json")
				.header("x-api-key", config.apiKey.trim())
				.header("anthropic-version", "2023-06-01")
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
				.build();

		checkCancelled(monitor);
		HttpResponse<String> response = sendRequest(request, config.timeoutSeconds);
		checkCancelled(monitor);

		int status = response.statusCode();
		String responseBody = response.body();

		if (status < 200 || status >= 300) {
			String message = extractErrorMessage(responseBody);
			throw new IOException("Anthropic HTTP " + status + " (" + message + ")");
		}

		ChatResponse res = parseAnthropicResponse(responseBody);
		res.providerName = config.provider.getDisplayName();
		res.modelName = config.model;
		return res;
	}

	// ------------------------------------------------------------------
	// Dynamic Model List Fetching
	// ------------------------------------------------------------------

	/**
	 * Queries the active provider to fetch currently installed or available models.
	 * Supports Ollama (local tags), OpenAI, Groq, OpenRouter, Mistral, and Custom.
	 */
	public List<String> fetchModels(DeepSeekConfig config, TaskMonitor monitor)
			throws IOException, CancelledException {
		List<String> models = new ArrayList<>();

		if (config.provider == AiProvider.OLLAMA || config.baseUrl.contains("11434")) {
			// Ollama: GET http://localhost:11434/api/tags
			String tagsUrl = ollamaTagsUrl(config.baseUrl);
			HttpRequest request = HttpRequest.newBuilder(URI.create(tagsUrl))
					.timeout(Duration.ofSeconds(10))
					.header("Accept", "application/json")
					.GET()
					.build();
			checkCancelled(monitor);
			HttpResponse<String> response = sendRequest(request, 10);
			if (response.statusCode() == 200) {
				JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
				if (root.has("models") && root.get("models").isJsonArray()) {
					for (JsonElement el : root.getAsJsonArray("models")) {
						if (el.isJsonObject()) {
							String name = jsonString(el.getAsJsonObject(), "name");
							if (!name.isEmpty()) {
								models.add(name);
							}
						}
					}
				}
			}
		}
		else {
			// Standard OpenAI/Groq/OpenRouter/Mistral: GET /models
			String modelsUrl = openAiModelsUrl(config.baseUrl);
			HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(modelsUrl))
					.timeout(Duration.ofSeconds(15))
					.header("Accept", "application/json");
			if (!DeepSeekConfig.isBlank(config.apiKey)) {
				builder.header("Authorization", "Bearer " + config.apiKey.trim());
			}
			checkCancelled(monitor);
			HttpResponse<String> response = sendRequest(builder.GET().build(), 15);
			if (response.statusCode() == 200) {
				JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
				if (root.has("data") && root.get("data").isJsonArray()) {
					for (JsonElement el : root.getAsJsonArray("data")) {
						if (el.isJsonObject()) {
							String id = jsonString(el.getAsJsonObject(), "id");
							if (!id.isEmpty()) {
								models.add(id);
							}
						}
					}
				}
			}
		}

		Collections.sort(models);
		return models;
	}

	// ------------------------------------------------------------------
	// HTTP & Helpers
	// ------------------------------------------------------------------

	private HttpResponse<String> sendRequest(HttpRequest request, int timeoutSeconds)
			throws IOException, CancelledException {
		try {
			return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new CancelledException("Request cancelled.");
		}
		catch (java.net.http.HttpTimeoutException e) {
			throw new IOException("API request timed out (" + timeoutSeconds + " seconds).", e);
		}
		catch (Exception e) {
			throw new IOException("API request failed: " + e.getMessage(), e);
		}
	}

	public static String chatCompletionsUrl(String baseUrl) {
		String base = DeepSeekConfig.isBlank(baseUrl) ? DeepSeekConfig.DEFAULT_BASE_URL : baseUrl.trim();
		if (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		if (base.endsWith("/chat/completions")) {
			return base;
		}
		if (base.endsWith("/v1")) {
			return base + "/chat/completions";
		}
		return base + "/chat/completions";
	}

	public static String anthropicMessagesUrl(String baseUrl) {
		String base = DeepSeekConfig.isBlank(baseUrl) ? AiProvider.ANTHROPIC.getDefaultBaseUrl() : baseUrl.trim();
		if (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		if (base.endsWith("/messages")) {
			return base;
		}
		if (base.endsWith("/v1")) {
			return base + "/messages";
		}
		return base + "/v1/messages";
	}

	public static String ollamaTagsUrl(String baseUrl) {
		String base = DeepSeekConfig.isBlank(baseUrl) ? "http://localhost:11434" : baseUrl.trim();
		if (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		if (base.endsWith("/v1")) {
			base = base.substring(0, base.length() - 3);
		}
		return base + "/api/tags";
	}

	public static String openAiModelsUrl(String baseUrl) {
		String base = DeepSeekConfig.isBlank(baseUrl) ? DeepSeekConfig.DEFAULT_BASE_URL : baseUrl.trim();
		if (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		if (base.endsWith("/chat/completions")) {
			base = base.substring(0, base.length() - "/chat/completions".length());
		}
		if (base.endsWith("/v1")) {
			return base + "/models";
		}
		return base + "/v1/models";
	}

	private static JsonObject message(String role, String content) {
		JsonObject m = new JsonObject();
		m.addProperty("role", role);
		m.addProperty("content", content);
		return m;
	}

	private static void checkCancelled(TaskMonitor monitor) throws CancelledException {
		if (monitor != null && monitor.isCancelled()) {
			throw new CancelledException();
		}
	}

	private static ChatResponse parseOpenAiResponse(String json) throws IOException {
		try {
			JsonObject root = JsonParser.parseString(json).getAsJsonObject();
			ChatResponse res = new ChatResponse();

			if (!root.has("choices") || !root.get("choices").isJsonArray()) {
				throw new IOException("Invalid API response (missing 'choices' array).");
			}
			JsonArray choices = root.getAsJsonArray("choices");
			if (choices.isEmpty()) {
				throw new IOException("Invalid API response (empty 'choices' array).");
			}
			JsonObject firstChoice = choices.get(0).getAsJsonObject();
			if (firstChoice.has("finish_reason") && !firstChoice.get("finish_reason").isJsonNull()) {
				res.finishReason = firstChoice.get("finish_reason").getAsString();
			}
			if (firstChoice.has("message") && firstChoice.get("message").isJsonObject()) {
				JsonObject msg = firstChoice.getAsJsonObject("message");
				res.content = jsonString(msg, "content");
				// Reasoning / thinking tokens
				String reasoning = jsonString(msg, "reasoning_content");
				if (reasoning.isEmpty()) {
					reasoning = jsonString(msg, "reasoning");
				}
				if (reasoning.isEmpty()) {
					reasoning = jsonString(msg, "thinking");
				}
				res.reasoningContent = reasoning;
			}

			if (root.has("usage") && root.get("usage").isJsonObject()) {
				JsonObject usage = root.getAsJsonObject("usage");
				res.promptTokens = jsonInt(usage, "prompt_tokens");
				res.completionTokens = jsonInt(usage, "completion_tokens");
				res.totalTokens = jsonInt(usage, "total_tokens");
				if (res.totalTokens == 0 && (res.promptTokens > 0 || res.completionTokens > 0)) {
					res.totalTokens = res.promptTokens + res.completionTokens;
				}
			}

			return res;
		}
		catch (IOException e) {
			throw e;
		}
		catch (Exception e) {
			throw new IOException("Could not parse OpenAI-compatible API response: " + e.getMessage(), e);
		}
	}

	private static ChatResponse parseAnthropicResponse(String json) throws IOException {
		try {
			JsonObject root = JsonParser.parseString(json).getAsJsonObject();
			ChatResponse res = new ChatResponse();

			if (root.has("stop_reason") && !root.get("stop_reason").isJsonNull()) {
				res.finishReason = root.get("stop_reason").getAsString();
			}

			StringBuilder textBuilder = new StringBuilder();
			StringBuilder thinkBuilder = new StringBuilder();

			if (root.has("content") && root.get("content").isJsonArray()) {
				for (JsonElement el : root.getAsJsonArray("content")) {
					if (el.isJsonObject()) {
						JsonObject block = el.getAsJsonObject();
						String type = jsonString(block, "type");
						if ("text".equals(type)) {
							textBuilder.append(jsonString(block, "text"));
						}
						else if ("thinking".equals(type)) {
							thinkBuilder.append(jsonString(block, "thinking"));
						}
					}
				}
			}

			res.content = textBuilder.toString();
			res.reasoningContent = thinkBuilder.toString();

			if (root.has("usage") && root.get("usage").isJsonObject()) {
				JsonObject usage = root.getAsJsonObject("usage");
				res.promptTokens = jsonInt(usage, "input_tokens");
				res.completionTokens = jsonInt(usage, "output_tokens");
				res.totalTokens = res.promptTokens + res.completionTokens;
			}

			return res;
		}
		catch (Exception e) {
			throw new IOException("Could not parse Anthropic API response: " + e.getMessage(), e);
		}
	}

	private static String extractErrorMessage(String body) {
		if (body == null || body.isBlank()) {
			return "Empty error response";
		}
		try {
			JsonObject root = JsonParser.parseString(body).getAsJsonObject();
			if (root.has("error")) {
				JsonElement error = root.get("error");
				if (error.isJsonObject()) {
					JsonObject obj = error.getAsJsonObject();
					String msg = jsonString(obj, "message");
					if (!msg.isEmpty()) {
						String type = jsonString(obj, "type");
						String code = jsonString(obj, "code");
						StringBuilder sb = new StringBuilder(msg);
						if (!type.isEmpty() || !code.isEmpty()) {
							sb.append(" [");
							if (!type.isEmpty()) sb.append("type=").append(type);
							if (!code.isEmpty()) {
								if (!type.isEmpty()) sb.append(", ");
								sb.append("code=").append(code);
							}
							sb.append("]");
						}
						return sb.toString();
					}
				}
				else if (error.isJsonPrimitive()) {
					return error.getAsString();
				}
			}
			// Anthropic error format {"type": "error", "error": {"type": ..., "message": ...}}
			if (root.has("message") && root.get("message").isJsonPrimitive()) {
				return root.get("message").getAsString();
			}
		}
		catch (Exception ignored) {
		}
		return abbreviate(body.replaceAll("\\s+", " ").trim(), 200);
	}

	public static String jsonString(JsonObject o, String key) {
		if (o != null && o.has(key) && !o.get(key).isJsonNull()) {
			return o.get(key).getAsString();
		}
		return "";
	}

	public static int jsonInt(JsonObject o, String key) {
		if (o != null && o.has(key) && !o.get(key).isJsonNull()) {
			try {
				return o.get(key).getAsInt();
			}
			catch (Exception e) {
				return 0;
			}
		}
		return 0;
	}

	public static String abbreviate(String text, int max) {
		if (text == null) {
			return "";
		}
		if (text.length() <= max) {
			return text;
		}
		return text.substring(0, Math.max(0, max - 3)) + "...";
	}
}