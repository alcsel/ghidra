/* ###
 * DeepSeek AI - Ghidra Extension
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package deepseekai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.SwingUtilities;

import ghidra.program.model.data.DataType;
import ghidra.program.model.listing.CommentType;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Listing;
import ghidra.program.model.listing.Program;
import ghidra.program.model.pcode.HighFunction;
import ghidra.program.model.pcode.HighFunctionDBUtil;
import ghidra.program.model.pcode.HighSymbol;
import ghidra.program.model.symbol.SourceType;
import ghidra.util.exception.CancelledException;
import ghidra.util.task.Task;
import ghidra.util.task.TaskMonitor;

/**
 * Modelin onerdigi degisiklikleri programa uygular. Tum degisiklikler tek bir
 * transaction icinde yapilir; boylece kullanici tek adimda geri alabilir.
 */
public class DeepSeekApplyTask extends Task {

	/** Uygulama sonucunun ozeti. */
	public static class ApplyReport {
		public int variablesRenamed;
		public int variablesSkipped;
		public boolean functionRenamed;
		public boolean functionCommentSet;
		public int commentsAdded;
		public int commentsSkipped;
		public final List<String> problems = new ArrayList<>();

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
			if (!problems.isEmpty()) {
				sb.append("\nUyarilar:\n");
				int limit = Math.min(problems.size(), 15);
				for (int i = 0; i < limit; i++) {
					sb.append("  ! ").append(problems.get(i)).append('\n');
				}
				if (problems.size() > limit) {
					sb.append("  ! ... ").append(problems.size() - limit).append(" uyari daha\n");
				}
			}
			return sb.toString();
		}
	}

	private final DeepSeekAIPlugin plugin;
	private final Program program;
	private final Function function;
	private final AnalysisOutcome outcome;
	private final List<AnalysisOutcome.VarRename> renames;
	private final List<AnalysisOutcome.LineComment> comments;
	private final boolean renameFunction;
	private final boolean setFunctionComment;

	public DeepSeekApplyTask(DeepSeekAIPlugin plugin, Program program, Function function,
			AnalysisOutcome outcome, List<AnalysisOutcome.VarRename> renames,
			List<AnalysisOutcome.LineComment> comments, boolean renameFunction,
			boolean setFunctionComment) {
		super("DeepSeek AI: degisiklikler uygulaniyor", false, true, false);
		this.plugin = plugin;
		this.program = program;
		this.function = function;
		this.outcome = outcome;
		this.renames = renames == null ? List.of() : renames;
		this.comments = comments == null ? List.of() : comments;
		this.renameFunction = renameFunction;
		this.setFunctionComment = setFunctionComment;
	}

	@Override
	public void run(TaskMonitor monitor) throws CancelledException {
		ApplyReport report = new ApplyReport();
		try {
			applyAll(monitor, report);
		}
		catch (CancelledException e) {
			throw e;
		}
		catch (Throwable t) {
			report.problems.add("Beklenmeyen hata: " + t);
		}
		SwingUtilities.invokeLater(() -> plugin.applyFinished(program, function, report));
	}

	private void applyAll(TaskMonitor monitor, ApplyReport report) throws Exception {
		Map<String, HighSymbol> symbols = null;
		if (!renames.isEmpty()) {
			monitor.setMessage("Degisken bilgileri yenileniyor...");
			HighFunction highFunction =
				DecompilerHelper.decompileHighFunction(program, function, monitor);
			symbols = DecompilerHelper.symbolIndex(highFunction);
		}

		monitor.setMessage("Degisiklikler uygulaniyor...");
		int transaction = program.startTransaction("DeepSeek AI: analiz sonuclarini uygula");
		boolean success = false;
		try {
			if (symbols != null) {
				applyRenames(symbols, report);
			}
			if (setFunctionComment) {
				applyFunctionComment(report);
			}
			if (renameFunction) {
				applyFunctionName(report);
			}
			applyComments(report);
			success = true;
		}
		finally {
			program.endTransaction(transaction, success);
		}
	}

	private void applyRenames(Map<String, HighSymbol> symbols, ApplyReport report) {
		boolean hasParameterRename = false;
		for (AnalysisOutcome.VarRename rename : renames) {
			HighSymbol symbol = symbols.get(rename.oldName);
			if (symbol != null && symbol.isParameter()) {
				hasParameterRename = true;
				break;
			}
		}
		if (hasParameterRename) {
			try {
				function.setSignatureSource(SourceType.USER_DEFINED);
			}
			catch (Throwable t) {
				report.problems.add("Fonksiyon imza kaynagi degistirilemedi: " + t.getMessage());
			}
		}

		for (AnalysisOutcome.VarRename rename : renames) {
			HighSymbol symbol = symbols.get(rename.oldName);
			if (symbol == null) {
				report.variablesSkipped++;
				report.problems
						.add("Degisken bulunamadi: " + rename.oldName + " -> " + rename.newName);
				continue;
			}
			DataType type = resolveType(rename.type, symbol);
			if (rename.newName.equals(symbol.getName()) && type == symbol.getDataType()) {
				report.variablesSkipped++;
				continue;
			}
			try {
				HighFunctionDBUtil.updateDBVariable(symbol, rename.newName, type,
					SourceType.USER_DEFINED);
				report.variablesRenamed++;
			}
			catch (Throwable t) {
				report.variablesSkipped++;
				report.problems.add("Yeniden adlandirilamadi: " + rename.oldName + " -> " +
					rename.newName + " (" + t.getMessage() + ")");
			}
		}
	}

	private void applyFunctionComment(ApplyReport report) {
		String text = outcome.functionComment;
		if (DeepSeekConfig.isBlank(text)) {
			text = outcome.summary;
		}
		if (DeepSeekConfig.isBlank(text)) {
			return;
		}
		try {
			Listing listing = program.getListing();
			listing.setComment(function.getEntryPoint(), CommentType.PLATE,
				trimComment(text, 1200));
			report.functionCommentSet = true;
		}
		catch (Throwable t) {
			report.problems.add("Fonksiyon yorumu yazilamadi: " + t.getMessage());
		}
	}

	private void applyFunctionName(ApplyReport report) {
		String name = outcome.functionName;
		if (DeepSeekConfig.isBlank(name) || name.equals(function.getName())) {
			return;
		}
		try {
			function.setName(name, SourceType.USER_DEFINED);
			report.functionRenamed = true;
		}
		catch (Throwable t) {
			report.problems.add("Fonksiyon adi degistirilemedi: " + t.getMessage());
		}
	}

	private void applyComments(ApplyReport report) {
		Listing listing = program.getListing();
		for (AnalysisOutcome.LineComment comment : comments) {
			if (comment.address == null || DeepSeekConfig.isBlank(comment.comment)) {
				report.commentsSkipped++;
				continue;
			}
			try {
				String existing = listing.getComment(CommentType.PRE, comment.address);
				if (existing != null && existing.contains(comment.comment.trim())) {
					report.commentsSkipped++;
					continue;
				}
				listing.setComment(comment.address, CommentType.PRE, trimComment(comment.comment,
					600));
				report.commentsAdded++;
			}
			catch (Throwable t) {
				report.commentsSkipped++;
				report.problems.add("Yorum yazilamadi (" + comment.rawAddress + "): " +
					t.getMessage());
			}
		}
	}

	/** Modelin onerdigi tipi Ghidra veri tipine cevirir; olmazsa mevcut tipi korur. */
	private DataType resolveType(String typeName, HighSymbol symbol) {
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
			case "char" -> "/char";
			case "float" -> "/float";
			case "double" -> "/double";
			default -> null;
		};
	}

	private static String trimComment(String text, int max) {
		String value = text.trim();
		if (value.length() <= max) {
			return value;
		}
		return value.substring(0, max) + " ...";
	}
}
