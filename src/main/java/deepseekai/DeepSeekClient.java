/* ###
 * DeepSeek AI - Ghidra Extension
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package deepseekai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ghidra.util.exception.CancelledException;
import ghidra.util.task.TaskMonitor;

/**
 * Lightweight client for the DeepSeek Chat Completions API.
 * <p>
 * Uses Ghidra's built-in {@code java.net.http.HttpClient} and {@code Gson},
 * requiring no additional external libraries.
 */
public class DeepSeekClient {

	/** Parsed response returned by the API. */
	public static class ChatResponse {
		public String content = "";
		public String reasoningContent = "";
		public String finishReason = "";
		public int promptTokens;
		public int completionTokens;
		public int totalTokens;

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
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
	}

	/**
	 * Sends a chat completion request to the DeepSeek API.
	 *
	 * @param systemPrompt system instructions and schema requirements
	 * @param userPrompt user prompt with decompiled code context
	 * @param config active configuration
	 * @param monitor cancellation monitor (can be null)
	 */
	public ChatResponse chat(String systemPrompt, String userPrompt, DeepSeekConfig config,
			TaskMonitor monitor) throws IOException, CancelledException {

		if (DeepSeekConfig.isBlank(config.apiKey)) {
			throw new IOException("API key is not configured. " +
				"Configure it via Tools > DeepSeek AI > Settings.");
		}

		JsonObject payload = new JsonObject();
		payload.addProperty("model", config.model);
		payload.addProperty("temperature", config.temperature);
		payload.addProperty("max_tokens", config.maxTokens);
		payload.addProperty("stream", false);

		JsonArray messages = new JsonArray();
		messages.add(message("system", systemPrompt));
		messages.add(message("user", userPrompt));
		payload.add("messages", messages);

		String body = payload.toString();
		String url = chatCompletionsUrl(config.baseUrl);

		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(Math.max(10, config.timeoutSeconds)))
				.header("Content-Type", "application/json; charset=utf-8")
				.header("Accept", "application/json")
				.header("Authorization", "Bearer " + config.apiKey.trim())
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
				.build();

		checkCancelled(monitor);

		HttpResponse<String> response;
		try {
			response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new CancelledException("Request cancelled.");
		}
		catch (java.net.http.HttpTimeoutException e) {
			throw new IOException("API request timed out (" + config.timeoutSeconds + " seconds).", e);
		}
		catch (Exception e) {
			throw new IOException("API request failed: " + e.getMessage(), e);
		}

		checkCancelled(monitor);

		int status = response.statusCode();
		String responseBody = response.body();

		if (status < 200 || status >= 300) {
			String message = extractErrorMessage(responseBody);
			throw new IOException("HTTP " + status + " (" + message + ")");
		}

		return parseResponse(responseBody);
	}

	public static String chatCompletionsUrl(String baseUrl) {
		String base = DeepSeekConfig.isBlank(baseUrl) ? DeepSeekConfig.DEFAULT_BASE_URL
				: baseUrl.trim();
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

	private static ChatResponse parseResponse(String json) throws IOException {
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
				res.reasoningContent = jsonString(msg, "reasoning_content");
			}

			if (root.has("usage") && root.get("usage").isJsonObject()) {
				JsonObject usage = root.getAsJsonObject("usage");
				res.promptTokens = jsonInt(usage, "prompt_tokens");
				res.completionTokens = jsonInt(usage, "completion_tokens");
				res.totalTokens = jsonInt(usage, "total_tokens");
			}

			return res;
		}
		catch (IOException e) {
			throw e;
		}
		catch (Exception e) {
			throw new IOException("Could not parse API response: " + e.getMessage(), e);
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
							if (!type.isEmpty()) {
								sb.append("type=").append(type);
							}
							if (!code.isEmpty()) {
								if (!type.isEmpty()) {
									sb.append(", ");
								}
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
		}
		catch (Exception ignored) {
			// fall back to truncated raw response
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
