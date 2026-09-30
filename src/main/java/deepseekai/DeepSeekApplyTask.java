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
 * Applies user-approved modifications to the target program.
 * <p>
 * Variable renaming requires an active {@link HighFunction}; the function
 * is decompiled once more if needed, and changes are committed via {@link OutcomeApplier}
 * in a single transaction.
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
		super("DeepSeek AI: Applying modifications", false, true, false);
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
				monitor.setMessage("Refreshing variable symbols...");
				highFunction = DecompilerHelper.decompileHighFunction(program, function, monitor);
			}
			monitor.setMessage("Applying modifications...");

			// Apply only user-selected suggestions
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
			counts.problems.add("Unexpected error: " + t);
		}
		SwingUtilities.invokeLater(() -> plugin.applyFinished(program, function, counts));
	}

	/** Produces a subset copy of the outcome containing only selected items. */
	private static AnalysisOutcome subSet(AnalysisOutcome source,
			List<AnalysisOutcome.VarRename> renames,
			List<AnalysisOutcome.LineComment> comments) {
		AnalysisOutcome copy = new AnalysisOutcome();
		copy.cleanCCode = source.cleanCCode;
		copy.summary = source.summary;
		copy.functionName = source.functionName;
		copy.functionComment = source.functionComment;
		copy.rawResponse = source.rawResponse;
		copy.providerName = source.providerName;
		copy.modelName = source.modelName;
		copy.usageText = source.usageText;
		copy.parsedFromJson = source.parsedFromJson;
		copy.varRenames.addAll(renames);
		copy.lineComments.addAll(comments);
		copy.hardParts.addAll(source.hardParts);
		copy.uncertainties.addAll(source.uncertainties);
		return copy;
	}
}
