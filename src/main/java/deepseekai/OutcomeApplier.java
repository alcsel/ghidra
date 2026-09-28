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
 * Applies model suggestions to the target Ghidra program.
 * <p>
 * Centralized logic shared by both single function analysis ({@link DeepSeekApplyTask})
 * and batch analysis ({@link BatchAiEngine}). All modifications are executed
 * inside a single atomic undoable transaction.
 */
public class OutcomeApplier {

	/** Counts and statistics of applied modifications. */
	public static class ApplyCounts {
		public int variablesRenamed;
		public int variablesSkipped;
		public int commentsAdded;
		public int commentsSkipped;
		public boolean functionRenamed;
		public boolean functionCommentSet;
		public final List<String> problems = new ArrayList<>();

		/** Human-readable summary of applied changes. */
		public String summary() {
			StringBuilder sb = new StringBuilder();
			sb.append("Applied modifications:\n");
			sb.append("  - Variables renamed : ").append(variablesRenamed);
			if (variablesSkipped > 0) {
				sb.append(" (").append(variablesSkipped).append(" skipped)");
			}
			sb.append('\n');
			sb.append("  - Comments added    : ").append(commentsAdded);
			if (commentsSkipped > 0) {
				sb.append(" (").append(commentsSkipped).append(" skipped)");
			}
			sb.append('\n');
			if (functionRenamed) {
				sb.append("  - Function name updated\n");
			}
			if (functionCommentSet) {
				sb.append("  - Function comment updated\n");
			}
			appendProblems(sb, problems);
			return sb.toString();
		}
	}

	/** Appends problems and warnings to the summary. */
	public static void appendProblems(StringBuilder sb, List<String> problems) {
		if (problems.isEmpty()) {
			return;
		}
		sb.append("\nWarnings (").append(problems.size()).append("):\n");
		int limit = Math.min(problems.size(), 12);
		for (int i = 0; i < limit; i++) {
			sb.append("  ! ").append(problems.get(i)).append('\n');
		}
		if (problems.size() > limit) {
			sb.append("  ! ... ").append(problems.size() - limit).append(" more warnings\n");
		}
	}

	private OutcomeApplier() {
		// utility class
	}

	/**
	 * Applies suggestions to the program.
	 *
	 * @param highFunction fresh HighFunction from decompilation (required for variable renaming)
	 * @param renameVariables whether to rename variables / parameters
	 * @param renameFunction whether to rename the function
	 * @param addComments whether to add line comments
	 * @param setFunctionComment whether to set the function entry comment
	 * @return counts of applied modifications
	 */
	public static ApplyCounts apply(Program program, Function function, HighFunction highFunction,
			AnalysisOutcome outcome, boolean renameVariables, boolean renameFunction,
			boolean addComments, boolean setFunctionComment) {

		ApplyCounts counts = new ApplyCounts();
		int transaction =
			program.startTransaction("DeepSeek AI: Apply analysis to " + function.getName());
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
				// ignore
			}
		}
		if (hasParameterRename) {
			try {
				function.setSignatureSource(SourceType.USER_DEFINED);
			}
			catch (Throwable t) {
				counts.problems.add("Could not update signature source: " + t.getMessage());
			}
		}

		for (AnalysisOutcome.VarRename rename : outcome.varRenames) {
			HighSymbol symbol = symbols.get(rename.oldName);
			if (symbol == null) {
				counts.variablesSkipped++;
				counts.problems.add("Variable not found: " + rename.oldName);
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
				counts.problems.add("Could not rename variable: " + rename.oldName + " -> " +
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
				counts.problems.add("Could not add comment (" + comment.rawAddress + "): " +
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
			counts.problems.add("Could not set function comment: " + t.getMessage());
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
			counts.problems.add("Could not rename function: " + t.getMessage());
		}
	}

	/** Resolves model suggested type to a Ghidra DataType, preserving current type as fallback. */
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
				// ignore, preserve current type
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
