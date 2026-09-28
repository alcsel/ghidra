/* ###
 * DeepSeek AI - Ghidra Extension
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package deepseekai;

import java.util.List;

import javax.swing.SwingUtilities;

import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Program;
import ghidra.program.model.pcode.HighFunction;
import ghidra.util.exception.CancelledException;
import ghidra.util.task.Task;
import ghidra.util.task.TaskMonitor;

/**
 * Kullanicinin onayladigi degisiklikleri programa uygular.
 * <p>
 * Degisken isimlendirme icin guncel bir {@link HighFunction} gerekir; bu yuzden
 * fonksiyon bir kez daha decompile edilir, ardindan
 * {@link OutcomeApplier} ile tek transaction icinde yazilir.
 */
public class DeepSeekApplyTask extends Task {

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
		OutcomeApplier.ApplyCounts counts = new OutcomeApplier.ApplyCounts();
		try {
			HighFunction highFunction = null;
			if (!renames.isEmpty()) {
				monitor.setMessage("Degisken bilgileri yenileniyor...");
				highFunction = DecompilerHelper.decompileHighFunction(program, function, monitor);
			}
			monitor.setMessage("Degisiklikler uygulaniyor...");

			// Yalnizca secilen onerileri uygula
			AnalysisOutcome selected = subSet(outcome, renames, comments);

			OutcomeApplier.ApplyCounts applied = OutcomeApplier.apply(program, function,
				highFunction, selected, !renames.isEmpty(), renameFunction, !comments.isEmpty(),
				setFunctionComment);
			counts.variablesRenamed = applied.variablesRenamed;
			counts.variablesSkipped = applied.variablesSkipped;
			counts.commentsAdded = applied.commentsAdded;
			counts.commentsSkipped = applied.commentsSkipped;
			counts.functionRenamed = applied.functionRenamed;
			counts.functionCommentSet = applied.functionCommentSet;
			counts.problems.addAll(applied.problems);
		}
		catch (CancelledException e) {
			throw e;
		}
		catch (Throwable t) {
			counts.problems.add("Beklenmeyen hata: " + t);
		}
		SwingUtilities.invokeLater(() -> plugin.applyFinished(program, function, counts));
	}

	/** Kullanicinin sectigi onerileri iceren kopya bir sonuc uretir. */
	private static AnalysisOutcome subSet(AnalysisOutcome source,
			List<AnalysisOutcome.VarRename> renames,
			List<AnalysisOutcome.LineComment> comments) {
		AnalysisOutcome copy = new AnalysisOutcome();
		copy.summary = source.summary;
		copy.functionName = source.functionName;
		copy.functionComment = source.functionComment;
		copy.rawResponse = source.rawResponse;
		copy.parsedFromJson = source.parsedFromJson;
		copy.varRenames.addAll(renames);
		copy.lineComments.addAll(comments);
		return copy;
	}
}
