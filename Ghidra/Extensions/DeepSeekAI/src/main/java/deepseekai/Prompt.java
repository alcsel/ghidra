/* ###
 * DeepSeek AI - Ghidra Extension
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package deepseekai;

/**
 * Builds system and user prompts sent to the DeepSeek API.
 */
public class Prompt {

	private Prompt() {
		// utility class
	}

	/** Expected JSON schema and instructions for the model. */
	public static String systemPrompt(DeepSeekConfig config) {
		String language = DeepSeekConfig.isBlank(config.language) ? "English" : config.language;
		return """
			You are a senior reverse engineering and software analysis expert.
			You will receive C pseudocode of a function decompiled by Ghidra,
			its symbol table, and address information.

			YOUR TASK
			1. Explain in detail what the function does.
			2. Identify complex, tricky, or noteworthy logic and propose technical comments.
			3. Suggest meaningful names for variables (parameters and local variables).
			4. If appropriate, suggest a concise and meaningful function name and block comment.
			5. Clearly note any uncertainties or ambiguities (do not hallucinate).

			RULES
			- Return ONLY a valid JSON object. Do not include any characters outside the JSON.
			- Do NOT wrap your response in markdown code fences (```). Put the explanation inside the JSON.
			- Lines in the decompiled code start with address tags like [0x00401020].
			  When proposing comments, set the "address" field EXACTLY to this address.
			  Do not propose comments for addresses you are not confident about.
			- Do not hallucinate or make unfounded guesses. If information is insufficient,
			  add items to "uncertainties" and lower confidence scores.
			- Variable names: valid C identifier, snake_case, 2-48 characters,
			  alphanumeric and underscores only, must not start with a digit, must not be a C keyword.
			  Do not assign the same new name to multiple variables.
			- Consider variable types, usage patterns, and called functions when choosing names.
			  Example: strlen result -> length, recv buffer -> packet_buffer.
			- confidence: a float between 0.0 (guess) and 1.0 (certain).
			- Write all explanations, comments, and reasons in %s.

			JSON SCHEMA
			{
			  "summary": "detailed paragraph explaining what the function does",
			  "function_name": "suggested_function_name or null",
			  "function_comment": "brief block comment for the function entry point",
			  "hard_parts": [
			    {
			      "address": "0x00401020 or null",
			      "explanation": "why this part is complex or significant",
			      "comment": "short comment to place on the code line"
			    }
			  ],
			  "line_comments": [
			    { "address": "0x00401020", "comment": "concise technical comment", "confidence": 0.9 }
			  ],
			  "variable_renames": [
			    {
			      "old_name": "uVar1",
			      "new_name": "packet_length",
			      "type": "int or null",
			      "reason": "rationale for this name",
			      "confidence": 0.8
			    }
			  ],
			  "uncertainties": [ "points where you are uncertain" ]
			}
			""".formatted(language);
	}

	/** User prompt containing context and decompiled code of the target function. */
	public static String userPrompt(DecompiledContext context, DeepSeekConfig config) {
		String language = DeepSeekConfig.isBlank(config.language) ? "English" : config.language;
		StringBuilder sb = new StringBuilder();

		sb.append("Analyze the following Ghidra function and provide your response in ")
				.append(language).append(".\n\n");

		sb.append("## FUNCTION\n");
		sb.append("Name: ").append(context.function.getName()).append('\n');
		sb.append("Entry address: 0x")
				.append(context.function.getEntryPoint().toString().replace(" ", ""))
				.append('\n');
		sb.append("Signature: ").append(context.signature).append('\n');
		sb.append("Called functions: ").append(context.calledFunctionsText()).append("\n\n");

		sb.append("## VARIABLES (use for renaming)\n");
		if (context.symbols.isEmpty()) {
			sb.append("(no symbol information available)\n");
		}
		else {
			for (DecompiledContext.SymbolInfo symbol : context.symbols) {
				sb.append("- ").append(symbol.describe()).append('\n');
			}
		}
		sb.append('\n');

		sb.append("## DECOMPILED CODE\n");
		sb.append("(the [0x...] tag at the beginning of each line is the address of that line)\n");
		sb.append("```c\n");
		sb.append(context.annotatedCode);
		sb.append("```\n\n");

		if (context.codeTruncated) {
			sb.append("NOTE: Code truncated due to length; only comment on the displayed portion.\n\n");
		}

		sb.append("Now return JSON only.");
		return sb.toString();
	}
}
