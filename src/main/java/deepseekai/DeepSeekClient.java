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
 * DeepSeek Chat Completions API icin kucuk bir istemci.
 * <p>
 * Harici bir kutuphane gerektirmez; Ghidra ile birlikte gelen
 * {@code java.net.http.HttpClient} ve {@code Gson} kullanilir.
 */
public class DeepSeekClient {

	/** API'den donen yanit. */
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
			return "token: " + promptTokens + " giris + " + completionTokens + " cikis = " +
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
	 * Sohbet tamamlama istegi gonderir.
	 *
	 * @param systemPrompt modelin rolu ve kurallari
	 * @param userPrompt gonderilecek asil icerik
	 * @param config ayarlar
	 * @param monitor iptal kontrolu icin (null olabilir)
	 */
	public ChatResponse chat(String systemPrompt, String userPrompt, DeepSeekConfig config,
			TaskMonitor monitor) throws IOException, CancelledException {

		if (DeepSeekConfig.isBlank(config.apiKey)) {
			throw new IOException("API anahtari tanimli degil. " +
				"Tools > DeepSeek AI > Ayarlar menusunden girin.");
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
			response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Istek kesintiye ugradi: " + e.getMessage(), e);
		}

		checkCancelled(monitor);

		String responseBody = response.body() == null ? "" : response.body();
		int status = response.statusCode();
		if (status != 200) {
			throw new IOException("DeepSeek API hatasi (HTTP " + status + "): " +
				extractErrorMessage(responseBody));
		}

		return parseResponse(responseBody);
	}

	private static JsonObject message(String role, String content) {
		JsonObject m = new JsonObject();
		m.addProperty("role", role);
		m.addProperty("content", content);
		return m;
	}

	private static void checkCancelled(TaskMonitor monitor) throws CancelledException {
		if (monitor != null) {
			monitor.checkCanceled();
		}
	}

	/** Kullanicinin girdigi adresi tam endpoint'e cevirir. */
	public static String chatCompletionsUrl(String baseUrl) {
		String base = DeepSeekConfig.isBlank(baseUrl) ? DeepSeekConfig.DEFAULT_BASE_URL
				: baseUrl.trim();
		while (base.endsWith("/")) {
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

	private static ChatResponse parseResponse(String responseBody) throws IOException {
		JsonObject root;
		try {
			root = JsonParser.parseString(responseBody).getAsJsonObject();
		}
		catch (Exception e) {
			throw new IOException("API yaniti okunamadi (gecersiz JSON): " + abbreviate(
				responseBody, 400), e);
		}

		if (root.has("error") && !root.get("error").isJsonNull()) {
			throw new IOException("DeepSeek API hatasi: " + errorMessageFrom(root.get("error")));
		}

		ChatResponse result = new ChatResponse();
		JsonArray choices = root.getAsJsonArray("choices");
		if (choices == null || choices.size() == 0) {
			throw new IOException("API yanitinda 'choices' alani yok: " + abbreviate(
				responseBody, 400));
		}

		JsonObject choice = choices.get(0).getAsJsonObject();
		if (choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()) {
			result.finishReason = choice.get("finish_reason").getAsString();
		}
		JsonObject message = choice.getAsJsonObject("message");
		if (message != null) {
			result.content = jsonString(message, "content");
			result.reasoningContent = jsonString(message, "reasoning_content");
		}

		JsonObject usage = root.getAsJsonObject("usage");
		if (usage != null) {
			result.promptTokens = jsonInt(usage, "prompt_tokens");
			result.completionTokens = jsonInt(usage, "completion_tokens");
			result.totalTokens = jsonInt(usage, "total_tokens");
		}

		if (result.content.isEmpty() && !result.reasoningContent.isEmpty()) {
			result.content = result.reasoningContent;
		}
		return result;
	}

	private static String extractErrorMessage(String responseBody) {
		try {
			JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
			if (root.has("error")) {
				return errorMessageFrom(root.get("error"));
			}
			if (root.has("message")) {
				return root.get("message").getAsString();
			}
		}
		catch (Exception e) {
			// yoksay, ham govdeyi dondur
		}
		return abbreviate(responseBody, 400);
	}

	private static String errorMessageFrom(JsonElement error) {
		if (error == null || error.isJsonNull()) {
			return "(bilinmeyen hata)";
		}
		if (error.isJsonObject()) {
			JsonObject o = error.getAsJsonObject();
			String msg = jsonString(o, "message");
			String type = jsonString(o, "type");
			if (msg.isEmpty()) {
				msg = o.toString();
			}
			return type.isEmpty() ? msg : (msg + " [" + type + "]");
		}
		return error.toString();
	}

	static String jsonString(JsonObject o, String name) {
		if (o == null || !o.has(name) || o.get(name).isJsonNull()) {
			return "";
		}
		try {
			return o.get(name).getAsString();
		}
		catch (Exception e) {
			return o.get(name).toString();
		}
	}

	static int jsonInt(JsonObject o, String name) {
		if (o == null || !o.has(name) || o.get(name).isJsonNull()) {
			return 0;
		}
		try {
			return o.get(name).getAsInt();
		}
		catch (Exception e) {
			return 0;
		}
	}

	static String abbreviate(String text, int max) {
		if (text == null) {
			return "";
		}
		String t = text.trim();
		if (t.length() <= max) {
			return t;
		}
		return t.substring(0, max) + "... (kisaltildi)";
	}
}
