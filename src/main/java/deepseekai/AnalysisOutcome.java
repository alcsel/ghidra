/* ###
 * DeepSeek AI - Ghidra Extension
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package deepseekai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * Modelin dondurdugu JSON yanitinin islenmis hali.
 */
public class AnalysisOutcome {

	/** Kod satirina eklenecek yorum onerisi. */
	public static class LineComment {
		public String rawAddress = "";
		public Address address;
		public String comment = "";
		public double confidence = 0.5;
		public boolean apply = true;
		public String note = "";
	}

	/** Degisken isimlendirme onerisi. */
	public static class VarRename {
		public String oldName = "";
		public String newName = "";
		public String type = "";
		public String reason = "";
		public double confidence = 0.5;
		public boolean apply = true;
		public String note = "";
	}

	/** Anlasilmasi zor kisim. */
	public static class HardPart {
		public String address = "";
		public String explanation = "";
		public String comment = "";
	}

	public String summary = "";
	public String functionName = "";
	public String functionComment = "";
	public final List<LineComment> lineComments = new ArrayList<>();
	public final List<VarRename> varRenames = new ArrayList<>();
	public final List<HardPart> hardParts = new ArrayList<>();
	public final List<String> uncertainties = new ArrayList<>();

	/** Modelin ham yaniti (hata ayiklama icin). */
	public String rawResponse = "";
	/** Token kullanimi gibi ek bilgiler. */
	public String usageText = "";
	/** Yanit gecerli JSON olarak cozumlendiyse true. */
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
	 * Model yanitini cozumler. Yanit gecerli JSON degilse metnin tamami aciklama
	 * olarak kabul edilir; boylece kullanici en azindan aciklamayi gorebilir.
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
				item.note = "adres cozumlenemedi";
			}
			else if (!usedAddresses.add(item.address.toString())) {
				item.apply = false;
				item.note = "ayni adres icin yinelenen yorum";
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
				item.note = "isim degismemis";
			}
			else if (!isValidIdentifier(item.newName)) {
				item.apply = false;
				item.note = "gecersiz degisken adi";
			}
			else if (!usedNames.add(item.newName)) {
				item.apply = false;
				item.note = "ayni yeni isim birden fazla kez kullanilmis";
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

	/** Model yanitindan JSON nesnesini ayiklar (markdown citlari temizler). */
	static String extractJsonObject(String text) {
		String trimmed = text == null ? "" : text.trim();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException("bos yanit");
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

	/** Fonksiyon adi onerisini dogrular; gecersizse bos dondurur. */
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

	/** Gecerli ve guvenli bir C tanimlayicisi mi? */
	public static boolean isValidIdentifier(String name) {
		if (name == null || !VALID_IDENTIFIER.matcher(name).matches()) {
			return false;
		}
		if (name.startsWith("__")) {
			return false;
		}
		return !C_KEYWORDS.contains(name.toLowerCase());
	}

	static String string(JsonObject o, String name) {
		if (o == null || !o.has(name) || o.get(name).isJsonNull()) {
			return "";
		}
		JsonElement element = o.get(name);
		if (element.isJsonPrimitive()) {
			try {
				return element.getAsString();
			}
			catch (Exception e) {
				return element.toString();
			}
		}
		return element.toString();
	}

	static double number(JsonObject o, String name, double fallback) {
		if (o == null || !o.has(name) || o.get(name).isJsonNull()) {
			return fallback;
		}
		try {
			return o.get(name).getAsDouble();
		}
		catch (Exception e) {
			return fallback;
		}
	}

	static JsonArray array(JsonObject o, String name) {
		if (o == null || !o.has(name) || !o.get(name).isJsonArray()) {
			return new JsonArray();
		}
		return o.getAsJsonArray(name);
	}

	/** Uygulanacak degisken isimlendirmelerini dondurur. */
	public List<VarRename> selectedRenames() {
		List<VarRename> list = new ArrayList<>();
		for (VarRename rename : varRenames) {
			if (rename.apply) {
				list.add(rename);
			}
		}
		return list;
	}

	/** Uygulanacak yorumlari dondurur. */
	public List<LineComment> selectedComments() {
		List<LineComment> list = new ArrayList<>();
		for (LineComment comment : lineComments) {
			if (comment.apply && comment.address != null) {
				list.add(comment);
			}
		}
		return list;
	}

	/** Onaylanan degiskenleri isim -> oneri seklinde dondurur (yinelemeleri eler). */
	public Map<String, VarRename> selectedRenameMap() {
		Map<String, VarRename> map = new LinkedHashMap<>();
		for (VarRename rename : selectedRenames()) {
			map.putIfAbsent(rename.oldName, rename);
		}
		return map;
	}
}
