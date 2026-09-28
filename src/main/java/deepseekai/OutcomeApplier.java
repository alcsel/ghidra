/* ###
 * DeepSeek AI - Ghidra Extension
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package deepseekai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ghidra.program.model.data.DataType;
import ghidra.program.model.listing.CommentType;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Listing;
import ghidra.program.model.listing.Program;
import ghidra.program.model.pcode.HighFunction;
import ghidra.program.model.pcode.HighFunctionDBUtil;
import ghidra.program.model.pcode.HighSymbol;
import ghidra.program.model.symbol.SourceType;

/**
 * Model onerilerini programa uygular. Hem tek fonksiyon analizinde
 * ({@link DeepSeekApplyTask}) hem toplu analizde ({@link BatchAiEngine})
 * ayni mantik kullanilsin diye tek yerde toplanmistir.
 * <p>
 * Tum degisiklikler tek bir transaction icinde yapilir.
 */
public class OutcomeApplier {

	/** Uygulanan degisikliklerin sayilari. */
	public static class ApplyCounts {
		public int variablesRenamed;
		public int variablesSkipped;
		public int commentsAdded;
		public int commentsSkipped;
		public boolean functionRenamed;
		public boolean functionCommentSet;
		public final List<String> problems = new ArrayList<>();

		/** Insan tarafindan okunabilir ozet. */
		public String summary() {
			StringBuilder sb = new StringBuilder();
			sb.append("Uygulanan degisiklikler:\n");
			sb.append("  - Yeniden adlandirilan degisken: ").append(variablesRenamed);
			if (variablesSkipped > 0) {
				sb.append(" (").append(variablesSkipped).append(" atlandi)");
			}
			sb.append('\n');
			sb.append("  - Eklenen yorum: ").append(commentsAdded);
			if (commentsSkipped > 0) {
				sb.append(" (").append(commentsSkipped).append(" atlandi)");
			}
			sb.append('\n');
			if (functionRenamed) {
				sb.append("  - Fonksiyon adi guncellendi\n");
			}
			if (functionCommentSet) {
				sb.append("  - Fonksiyon yorumu guncellendi\n");
			}
			appendProblems(sb, problems);
			return sb.toString();
		}
	}

	/** Sorun listesini ozete ekler. */
	public static void appendProblems(StringBuilder sb, List<String> problems) {
		if (problems.isEmpty()) {
			return;
		}
		sb.append("\nUyarilar (").append(problems.size()).append("):\n");
		int limit = Math.min(problems.size(), 12);
		for (int i = 0; i < limit; i++) {
			sb.append("  ! ").append(problems.get(i)).append('\n');
		}
		if (problems.size() > limit) {
			sb.append("  ! ... ").append(problems.size() - limit).append(" uyari daha\n");
		}
	}

	private OutcomeApplier() {
		// yardimci sinif
	}

	/**
	 * Onerileri programa uygular.
	 *
	 * @param highFunction ayni decompile sonucundan alinan HighFunction (zorunlu,
	 *            degisken isimlendirme icin)
	 * @param renameVariables yerel degisken/parametre isimleri degistirilsin mi
	 * @param renameFunction fonksiyon adi degistirilsin mi
	 * @param addComments satir yorumlari eklensin mi
	 * @param setFunctionComment fonksiyon giris yorumu yazilsin mi
	 * @return uygulama sayilari
	 */
	public static ApplyCounts apply(Program program, Function function, HighFunction highFunction,
			AnalysisOutcome outcome, boolean renameVariables, boolean renameFunction,
			boolean addComments, boolean setFunctionComment) {

		ApplyCounts counts = new ApplyCounts();
		int transaction =
			program.startTransaction("DeepSeek AI: " + function.getName() + " analizini uygula");
		boolean success = false;
		try {
			if (renameVariables && highFunction != null && !outcome.varRenames.isEmpty()) {
				applyRenames(program, function, highFunction, outcome, counts);
			}
			if (addComments && !outcome.lineComments.isEmpty()) {
				applyComments(program, outcome, counts);
			}
			if (setFunctionComment) {
				applyFunctionComment(program, function, outcome, counts);
			}
			if (renameFunction) {
				applyFunctionName(function, outcome, counts);
			}
			success = true;
		}
		finally {
			program.endTransaction(transaction, success);
		}
		return counts;
	}

	private static void applyRenames(Program program, Function function, HighFunction highFunction,
			AnalysisOutcome outcome, ApplyCounts counts) {

		Map<String, HighSymbol> symbols = DecompilerHelper.symbolIndex(highFunction);
		boolean hasParameterRename = false;
		for (AnalysisOutcome.VarRename rename : outcome.varRenames) {
			HighSymbol symbol = symbols.get(rename.oldName);
			if (symbol == null) {
				continue;
			}
			try {
				if (symbol.isParameter()) {
					hasParameterRename = true;
					break;
				}
			}
			catch (Throwable t) {
				// yoksay
			}
		}
		if (hasParameterRename) {
			try {
				function.setSignatureSource(SourceType.USER_DEFINED);
			}
			catch (Throwable t) {
				counts.problems.add("Imza kaynagi degistirilemedi: " + t.getMessage());
			}
		}

		for (AnalysisOutcome.VarRename rename : outcome.varRenames) {
			HighSymbol symbol = symbols.get(rename.oldName);
			if (symbol == null) {
				counts.variablesSkipped++;
				counts.problems.add("Degisken bulunamadi: " + rename.oldName);
				continue;
			}
			try {
				DataType type = resolveType(program, rename.type, symbol);
				HighFunctionDBUtil.updateDBVariable(symbol, rename.newName, type,
					SourceType.USER_DEFINED);
				counts.variablesRenamed++;
			}
			catch (Throwable t) {
				counts.variablesSkipped++;
				counts.problems.add("Yeniden adlandirilamadi: " + rename.oldName + " -> " +
					rename.newName + " (" + t.getMessage() + ")");
			}
		}
	}

	private static void applyComments(Program program, AnalysisOutcome outcome,
			ApplyCounts counts) {
		Listing listing = program.getListing();
		for (AnalysisOutcome.LineComment comment : outcome.lineComments) {
			if (comment.address == null || DeepSeekConfig.isBlank(comment.comment)) {
				counts.commentsSkipped++;
				continue;
			}
			try {
				String existing = listing.getComment(CommentType.PRE, comment.address);
				if (existing != null && existing.contains(comment.comment.trim())) {
					counts.commentsSkipped++;
					continue;
				}
				listing.setComment(comment.address, CommentType.PRE,
					trim(comment.comment, 600));
				counts.commentsAdded++;
			}
			catch (Throwable t) {
				counts.commentsSkipped++;
				counts.problems.add("Yorum yazilamadi (" + comment.rawAddress + "): " +
					t.getMessage());
			}
		}
	}

	private static void applyFunctionComment(Program program, Function function,
			AnalysisOutcome outcome, ApplyCounts counts) {
		String text = outcome.functionComment;
		if (DeepSeekConfig.isBlank(text)) {
			text = outcome.summary;
		}
		if (DeepSeekConfig.isBlank(text)) {
			return;
		}
		try {
			program.getListing()
					.setComment(function.getEntryPoint(), CommentType.PLATE, trim(text, 1200));
			counts.functionCommentSet = true;
		}
		catch (Throwable t) {
			counts.problems.add("Fonksiyon yorumu yazilamadi: " + t.getMessage());
		}
	}

	private static void applyFunctionName(Function function, AnalysisOutcome outcome,
			ApplyCounts counts) {
		String name = outcome.functionName;
		if (DeepSeekConfig.isBlank(name) || name.equals(function.getName())) {
			return;
		}
		try {
			function.setName(name, SourceType.USER_DEFINED);
			counts.functionRenamed = true;
		}
		catch (Throwable t) {
			counts.problems.add("Fonksiyon adi degistirilemedi: " + t.getMessage());
		}
	}

	/** Modelin onerdigi tipi Ghidra veri tipine cevirir; olmazsa mevcut tipi korur. */
	public static DataType resolveType(Program program, String typeName, HighSymbol symbol) {
		DataType current = symbol.getDataType();
		if (DeepSeekConfig.isBlank(typeName)) {
			return current;
		}
		String name = typeName.trim();
		if (name.contains("*") || name.contains("[") || name.contains(" ") ||
			name.startsWith("struct") || name.startsWith("union") || name.startsWith("enum")) {
			return current;
		}
		String[] candidates = { "/" + name, "/" + name.toLowerCase(), aliasType(name) };
		for (String path : candidates) {
			if (path == null) {
				continue;
			}
			try {
				DataType dataType = program.getDataTypeManager().getDataType(path);
				if (dataType != null) {
					return dataType;
				}
			}
			catch (Throwable t) {
				// yoksay, mevcut tipi kullan
			}
		}
		return current;
	}

	private static String aliasType(String name) {
		String lower = name.toLowerCase();
		return switch (lower) {
			case "uint8_t", "byte" -> "/byte";
			case "uint16_t", "word" -> "/ushort";
			case "uint32_t", "dword" -> "/uint";
			case "uint64_t" -> "/ulonglong";
			case "int8" -> "/char";
			case "int16" -> "/short";
			case "int32" -> "/int";
			case "int64" -> "/longlong";
			case "unsigned" -> "/uint";
			case "size_t" -> "/uint";
			case "bool" -> "/bool";
			case "float" -> "/float";
			case "double" -> "/double";
			default -> null;
		};
	}

	static String trim(String text, int max) {
		String value = text.trim();
		if (value.length() <= max) {
			return value;
		}
		return value.substring(0, max) + " ...";
	}
}
