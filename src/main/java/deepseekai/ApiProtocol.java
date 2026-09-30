/* ###
 * Ghidra AI Extension
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package deepseekai;

/**
 * Protocol family supported by the AI client.
 */
public enum ApiProtocol {
	/**
	 * Standard OpenAI Chat Completions API (/v1/chat/completions).
	 * Supported by OpenAI, DeepSeek, Ollama, OpenRouter, Groq, Mistral, xAI,
	 * Gemini (OpenAI endpoint), vLLM, and LM Studio.
	 */
	OPENAI_COMPATIBLE("OpenAI-Compatible"),

	/**
	 * Anthropic Messages API (/v1/messages).
	 * Used by Anthropic Claude (Claude 3, 3.5, 3.7).
	 */
	ANTHROPIC_MESSAGES("Anthropic Messages"),

	/**
	 * Google Gemini Native REST API (/v1beta/models/{model}:generateContent).
	 */
	GEMINI_NATIVE("Google Gemini Native");

	private final String displayName;

	ApiProtocol(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}

	@Override
	public String toString() {
		return displayName;
	}
}