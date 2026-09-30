/* ###
 * DeepSeek AI - Ghidra Extension
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package deepseekai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ghidra.program.model.address.Address;
import ghidra.program.model.address.AddressFormatException;
import ghidra.program.model.address.AddressSpace;
import ghidra.program.model.listing.Program;

/**
 * Structured outcome returned by the AI analysis.
 * <p>
 * Parses the JSON response received from the model and validates addresses,
 * variable names, clean reconstructed C code, and confidence values.
 */
public class AnalysisOutcome {

	/** Line comment suggestion. */
	public static class LineComment {
		public String rawAddress = "";
		public Address address;
		public String comment = "";
		public double confidence = 0.5;
		public boolean apply = true;
		public String note = "";
	}

	/** Variable rename suggestion. */
	public static class VarRename {
		public String oldName = "";
		public String newName = "";
		public String type = "";
		public String reason = "";
		public double confidence = 0.5;
		public boolean apply = true;
		public String note = "";
	}

	/** Complex section identified by the model. */
	public static class HardPart {
		public String address = "";
		public String explanation = "";
		public String comment = "";
	}

	public String summary = "";
	public String functionName = "";
	public String functionComment = "";
	public String cleanCCode = "";
	public final List<LineComment> lineComments = new ArrayList<>();
	public final List<VarRename> varRenames = new ArrayList<>();
	public final List<HardPart> hardParts = new ArrayList<>();
	public final List<String> uncertainties = new ArrayList<>();

	/** Raw response text from the model (for debugging/inspection). */
	public String rawResponse = "";
	public String providerName = "";
	public String modelName = "";

	public String getSourceInfo() {
		return (providerName.isEmpty() ? "AI" : providerName) + " (" + (modelName.isEmpty() ? "default" : modelName) + ")";
	}
	/** Token usage information. */
	public String usageText = "";
	/** True if the response was successfully parsed from JSON. */
	public boolean parsedFromJson;

	private static final Pattern VALID_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{1,63}");

	private static final Set<String> C_KEYWORDS = new HashSet<>(Arrays.asList("auto", "break",
		"case", "char", "const", "continue", "default", "do", "double", "else", "enum",
		"extern", "float", "for", "goto", "if", "inline", "int", "long", "register",
		"restrict", "return", "short", "signed", "sizeof", "static", "struct", "switch",
		"typedef", "union", "unsigned", "void", "volatile", "while", "bool", "true",
		"false", "null", "nullptr", "class", "new", "delete", "this", "template",
		"namespace", "using"));

	/**
	 * Parses the model response. If the response is not valid JSON, the entire text
	 * is treated as a summary so the user can still read the explanation.
	 */
	public static AnalysisOutcome parse(String content, Program program) {
		AnalysisOutcome outcome = new AnalysisOutcome();
		outcome.rawResponse = content == null ? "" : content;

		JsonObject root;
		try {
			root = JsonParser.parseString(extractJsonObject(outcome.rawResponse)).getAsJsonObject();
		}
		catch (Exception e) {
			outcome.summary = outcome.rawResponse;
			return outcome;
		}
		outcome.parsedFromJson = true;

		outcome.summary = string(root, "summary");
		outcome.functionComment = string(root, "function_comment");
		outcome.functionName = sanitizeFunctionName(string(root, "function_name"));

		// Parse reconstructed clean C code
		String clean = string(root, "clean_c_code");
		if (clean.isEmpty()) {
			clean = string(root, "reconstructed_code");
		}
		if (clean.isEmpty()) {
			clean = string(root, "clean_code");
		}
		if (clean.isEmpty()) {
			clean = string(root, "code");
		}
		outcome.cleanCCode = cleanCodeString(clean);

		AddressSpace space = program == null ? null
				: program.getAddressFactory().getDefaultAddressSpace();

		JsonArray comments = array(root, "line_comments");
		Set<String> usedAddresses = new HashSet<>();
		for (JsonElement element : comments) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject o = element.getAsJsonObject();
			LineComment item = new LineComment();
			item.rawAddress = string(o, "address");
			item.comment = string(o, "comment");
			item.confidence = number(o, "confidence", 0.5);
			item.address = parseAddress(space, item.rawAddress);
			if (item.comment.isBlank()) {
				continue;
			}
			if (item.address == null) {
				item.apply = false;
				item.note = "address could not be resolved";
			}
			else if (!usedAddresses.add(item.address.toString())) {
				item.apply = false;
				item.note = "duplicate comment for same address";
			}
			outcome.lineComments.add(item);
		}

		JsonArray renames = array(root, "variable_renames");
		Set<String> usedNames = new HashSet<>();
		for (JsonElement element : renames) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject o = element.getAsJsonObject();
			VarRename item = new VarRename();
			item.oldName = string(o, "old_name");
			item.newName = string(o, "new_name").trim();
			item.type = string(o, "type");
			item.reason = string(o, "reason");
			item.confidence = number(o, "confidence", 0.5);
			if (item.oldName.isBlank() || item.newName.isBlank()) {
				continue;
			}
			if (item.oldName.equals(item.newName)) {
				item.apply = false;
				item.note = "name unchanged";
			}
			else if (!isValidIdentifier(item.newName)) {
				item.apply = false;
				item.note = "invalid variable name";
			}
			else if (!usedNames.add(item.newName)) {
				item.apply = false;
				item.note = "duplicate new variable name";
			}
			outcome.varRenames.add(item);
		}

		JsonArray hard = array(root, "hard_parts");
		for (JsonElement element : hard) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject o = element.getAsJsonObject();
			HardPart part = new HardPart();
			part.address = string(o, "address");
			part.explanation = string(o, "explanation");
			part.comment = string(o, "comment");
			if (part.explanation.isBlank() && part.comment.isBlank()) {
				continue;
			}
			outcome.hardParts.add(part);
		}

		JsonArray uncertain = array(root, "uncertainties");
		for (JsonElement element : uncertain) {
			if (element.isJsonPrimitive()) {
				String text = element.getAsString();
				if (!text.isBlank()) {
					outcome.uncertainties.add(text);
				}
			}
		}

		return outcome;
	}

	/** Cleans markdown code fences and stray address markers from C code string. */
	static String cleanCodeString(String code) {
		if (code == null) {
			return "";
		}
		String trimmed = code.trim();
		if (trimmed.startsWith("```")) {
			int firstNl = trimmed.indexOf('\n');
			int lastFence = trimmed.lastIndexOf("```");
			if (firstNl >= 0 && lastFence > firstNl) {
				trimmed = trimmed.substring(firstNl + 1, lastFence).trim();
			}
			else if (firstNl >= 0) {
				trimmed = trimmed.substring(firstNl + 1).trim();
			}
		}
		// Strip any address tags [0x...] that may have leaked
		trimmed = trimmed.replaceAll("(?m)^\\s*\\[0x[0-9a-fA-F]+\\]\\s*", "");
		return trimmed;
	}

	/**
	 * Returns reconstructed clean human-written C code.
	 * If the AI model provided cleanCCode, it is returned.
	 * Otherwise, a programmatic fallback clean-up is performed on the raw decompiled code,
	 * stripping address tags, replacing all variables, and renaming any leftover uVar artifacts.
	 */
	public String getOrGenerateCleanCode(DecompiledContext context) {
		if (cleanCCode != null && !cleanCCode.trim().isEmpty()) {
			return cleanCCode;
		}
		if (context == null) {
			return "";
		}
		String source = context.rawCode;
		if (source == null || source.trim().isEmpty()) {
			source = context.annotatedCode;
		}
		if (source == null || source.trim().isEmpty()) {
			return "";
		}

		// Programmatic fallback reconstruction
		String code = source.replaceAll("\\[0x[0-9a-fA-F]+\\]\\s*", "");

		// Rename function if suggested
		if (!functionName.isEmpty() && context.function != null) {
			code = code.replaceAll("\\b" + Pattern.quote(context.function.getName()) + "\\b", functionName);
		}

		// Replace all variables that were mapped in varRenames
		for (VarRename r : varRenames) {
			if (r.oldName != null && !r.oldName.isEmpty() && r.newName != null && !r.newName.isEmpty()) {
				code = code.replaceAll("\\b" + Pattern.quote(r.oldName) + "\\b", r.newName);
			}
		}

		// Eliminate any remaining machine decompiler artifacts
		code = code.replaceAll("\\bpuVar(\\d+)\\b", "p_val_$1");
		code = code.replaceAll("\\buVar(\\d+)\\b", "u_val_$1");
		code = code.replaceAll("\\biVar(\\d+)\\b", "idx_$1");
		code = code.replaceAll("\\bbVar(\\d+)\\b", "flag_$1");
		code = code.replaceAll("\\blVar(\\d+)\\b", "num_$1");
		code = code.replaceAll("\\bparam_(\\d+)\\b", "arg_$1");
		code = code.replaceAll("\\blocal_([0-9a-fA-F]+)\\b", "var_$1");
		code = code.replaceAll("\\bunaff_([A-Za-z0-9_]+)\\b", "reg_$1");
		code = code.replaceAll("\\bundefined(\\d+)\\b", "uint$1_t");
		code = code.replaceAll("\\bundefined\\b", "void*");

		return code;
	}

	/** Extracts JSON object text from model response (stripping markdown code fences). */
	static String extractJsonObject(String text) {
		String trimmed = text == null ? "" : text.trim();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException("empty response");
		}
		int fence = trimmed.indexOf("```");
		if (fence >= 0) {
			int contentStart = trimmed.indexOf('\n', fence);
			int fenceEnd = contentStart < 0 ? -1 : trimmed.indexOf("```", contentStart);
			if (contentStart > 0 && fenceEnd > contentStart) {
				trimmed = trimmed.substring(contentStart + 1, fenceEnd).trim();
			}
		}
		int first = trimmed.indexOf('{');
		int last = trimmed.lastIndexOf('}');
		if (first >= 0 && last > first) {
			trimmed = trimmed.substring(first, last + 1);
		}
		return trimmed;
	}

	private static Address parseAddress(AddressSpace space, String text) {
		if (space == null || text == null) {
			return null;
		}
		String value = text.trim();
		if (value.isEmpty()) {
			return null;
		}
		if (value.startsWith("[") && value.endsWith("]")) {
			value = value.substring(1, value.length() - 1).trim();
		}
		try {
			return space.getAddress(value);
		}
		catch (AddressFormatException | IllegalArgumentException e) {
			return null;
		}
	}

	/** Validates function name suggestion; returns empty string if invalid. */
	static String sanitizeFunctionName(String name) {
		if (name == null) {
			return "";
		}
		String value = name.trim();
		if (value.isEmpty() || "null".equalsIgnoreCase(value)) {
			return "";
		}
		if (!isValidIdentifier(value)) {
			return "";
		}
		return value;
	}

	public static boolean isValidIdentifier(String name) {
		if (name == null || name.isEmpty()) {
			return false;
		}
		if (!VALID_IDENTIFIER.matcher(name).matches()) {
			return false;
		}
		return !C_KEYWORDS.contains(name.toLowerCase());
	}

	public List<VarRename> selectedRenames() {
		List<VarRename> list = new ArrayList<>();
		for (VarRename r : varRenames) {
			if (r.apply) {
				list.add(r);
			}
		}
		return list;
	}

	public List<LineComment> selectedComments() {
		List<LineComment> list = new ArrayList<>();
		for (LineComment c : lineComments) {
			if (c.apply && c.address != null) {
				list.add(c);
			}
		}
		return list;
	}

	private static String string(JsonObject o, String key) {
		if (o.has(key) && !o.get(key).isJsonNull()) {
			return o.get(key).getAsString();
		}
		return "";
	}

	private static double number(JsonObject o, String key, double defaultValue) {
		if (o.has(key) && !o.get(key).isJsonNull()) {
			try {
				return o.get(key).getAsDouble();
			}
			catch (Exception e) {
				return defaultValue;
			}
		}
		return defaultValue;
	}

	private static JsonArray array(JsonObject o, String key) {
		if (o.has(key) && o.get(key).isJsonArray()) {
			return o.getAsJsonArray(key);
		}
		return new JsonArray();
	}
}
