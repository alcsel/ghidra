/* ###
 * DeepSeek AI - Ghidra Extension
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package deepseekai;

/**
 * Builds system and user prompts sent to the AI API.
 */
public class Prompt {

	private Prompt() {
		// utility class
	}

	/** Expected JSON schema and instructions for the model. */
	public static String systemPrompt(DeepSeekConfig config) {
		String language = DeepSeekConfig.isBlank(config.language) ? "English" : config.language;
		return """
			You are an elite reverse engineering specialist and expert C/C++ systems programmer.
			You will receive decompiled C pseudocode of a binary function extracted by Ghidra,
			along with its symbol table, parameters, calls, and address mapping.

			PRIMARY MISSION: RECONSTRUCT CLEAN, HUMAN-WRITTEN C SOURCE CODE ("clean_c_code")
			Decompiled binary pseudocode is cluttered with raw stack variables, compiler register
			artifacts, and machine-generated placeholder names (uVar1, iVar2, param_1, local_10, etc.).
			Your primary task is to completely reconstruct this into idiomatic, human-written C/C++
			source code that looks as if it was authored by an experienced human developer from original source.

			CRITICAL RULES FOR "clean_c_code":
			1. ABSOLUTELY ZERO DECOMPILER ARTIFACTS:
			   - NEVER output synthetic decompiler variable names such as:
			     'uVar1', 'uVar2', 'iVar', 'bVar', 'lVar', 'puVar', 'param_1', 'param_2',
			     'local_', 'unaff_', 'in_register', 'extraout_', 'CONCAT', 'SUB', etc.
			   - Every single parameter, local variable, buffer, and counter MUST be given a clean,
			     meaningful, contextual human identifier (e.g., 'packet_len', 'socket_fd', 'buffer',
			     'status', 'user_ctx', 'error_code', 'is_authenticated', 'retry_count').
			2. ELIMINATE RAW POINTER ARITHMETIC AND CAST GIBBERISH:
			   - Replace raw offset dereferences like '*(int *)(param_1 + 0x18)' or '*(undefined4 *)ptr'
			     with clean struct member access ('config->timeout_ms'), typed pointer access, or array indexing.
			3. IDIOMATIC HUMAN CONTROL FLOW:
			   - Eliminate artificial compiler goto loops, dummy loop counters, and stack canary checks (__stack_chk_fail).
			   - Write clean, idiomatic 'for', 'while', 'switch/case', 'if/else', and early returns.
			4. MODERN STANDARD C TYPES:
			   - Use standard C types ('int', 'uint32_t', 'uint8_t', 'size_t', 'bool', 'char *', struct pointers)
			     instead of Ghidra types ('undefined4', 'undefined8', 'byte', 'dword', 'qword', 'longlong').
			5. READY-TO-COMPILE AND FORMATTED:
			   - Do NOT include any address tags like '[0x00401020]' in 'clean_c_code'.
			   - Provide the complete, compilable function definition with clear indentation and helpful inline comments.

			EXHAUSTIVE VARIABLE RENAMING ("variable_renames"):
			- You MUST include an exhaustive mapping of EVERY single decompiler placeholder variable
			  from the decompiled code (every uVar, iVar, bVar, lVar, puVar, param_, local_, unaff_, etc.)
			  to its new human-written name.
			  This allows Ghidra's symbol table and decompiler window to be updated directly.
			- Every variable rename MUST be a valid C identifier (snake_case), alphanumeric + underscore.

			ANALYSIS TASKS:
			- summary: Clear, high-level technical summary of what the function accomplishes.
			- function_name: Concise, meaningful function name (e.g., parse_http_header, decrypt_payload).
			- function_comment: Technical block/Doxygen comment describing purpose, inputs, and outputs.
			- hard_parts: Highlight tricky bitwise logic, cryptographic routines, protocol parsing, or non-obvious algorithms.
			- line_comments: Technical comments tied to specific hex addresses from the decompilation.
			- uncertainties: Note any ambiguous behaviors, unresolved pointer structures, or assumptions.
			- Write all explanations, summaries, and comments in %s.

			OUTPUT FORMAT:
			- Return ONLY a valid JSON object matching the JSON schema below.
			- Do NOT wrap your response in markdown code fences (```). Return pure JSON.

			JSON SCHEMA:
			{
			  "summary": "Detailed technical explanation of what the function does",
			  "function_name": "suggested_clean_function_name",
			  "function_comment": "Block comment for the function entry point",
			  "clean_c_code": "/* Reconstructed clean, human-written C code */\\nint suggested_clean_function_name(char *buffer, size_t length) {\\n    /* Implementation with zero uVar / param_ artifacts */\\n}",
			  "hard_parts": [
			    {
			      "address": "0x00401020",
			      "explanation": "Why this section is complex or significant",
			      "comment": "Short line comment"
			    }
			  ],
			  "line_comments": [
			    { "address": "0x00401020", "comment": "concise technical comment", "confidence": 0.95 }
			  ],
			  "variable_renames": [
			    {
			      "old_name": "uVar1",
			      "new_name": "packet_length",
			      "type": "uint32_t",
			      "reason": "Calculates incoming packet byte length",
			      "confidence": 0.95
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

		sb.append("Analyze the following Ghidra function and reconstruct it into clean, human-written C/C++ code without any decompilation artifacts (no uVar, iVar, param_, local_, etc.). Respond in ")
				.append(language).append(".\n\n");

		sb.append("## FUNCTION\n");
		sb.append("Name: ").append(context.function.getName()).append('\n');
		sb.append("Entry address: 0x")
				.append(context.function.getEntryPoint().toString().replace(" ", ""))
				.append('\n');
		sb.append("Signature: ").append(context.signature).append('\n');
		sb.append("Called functions: ").append(context.calledFunctionsText()).append("\n\n");

		sb.append("## VARIABLES (Rename every single placeholder below into a meaningful human name in 'variable_renames')\n");
		if (context.symbols.isEmpty()) {
			sb.append("(no symbol information available)\n");
		}
		else {
			for (DecompiledContext.SymbolInfo symbol : context.symbols) {
				sb.append("- ").append(symbol.describe()).append('\n');
			}
		}
		sb.append('\n');

		sb.append("## RAW DECOMPILED CODE\n");
		sb.append("(The [0x...] tag at the beginning of each line is the address of that line. In 'clean_c_code', strip all address tags and produce clean, human-written C code.)\n");
		sb.append("```c\n");
		sb.append(context.annotatedCode);
		sb.append("```\n\n");

		if (context.codeTruncated) {
			sb.append("NOTE: Code truncated due to length; only comment on the displayed portion.\n\n");
		}

		sb.append("REMINDER: In 'clean_c_code', provide 100% human-written C code with ZERO uVar or param_ artifacts. Rename every variable.\n");
		sb.append("Now return JSON only.");
		return sb.toString();
	}
}
