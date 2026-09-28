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
import ghidra.util.exception.CancelledException;
import ghidra.util.task.Task;
import ghidra.util.task.TaskMonitor;

/**
 * Background task running batch AI analysis via {@link BatchAiEngine}.
 */
public class BatchAiTask extends Task {

	private final DeepSeekAIPlugin plugin;
	private final Program program;
	private final DeepSeekConfig config;
	private final BatchAiOptions options;
	private final List<Function> functions;

	public BatchAiTask(DeepSeekAIPlugin plugin, Program program, DeepSeekConfig config,
			BatchAiOptions options, List<Function> functions) {
		super("DeepSeek AI: Batch Analysis", true, true, false);
		this.plugin = plugin;
		this.program = program;
		this.config = config;
		this.options = options;
		this.functions = functions;
	}

	@Override
	public void run(TaskMonitor monitor) {
		try {
			BatchAiEngine.Result result =
				new BatchAiEngine(program, config, options).run(functions, monitor);
			SwingUtilities.invokeLater(() -> plugin.batchFinished(program, result));
		}
		catch (CancelledException e) {
			plugin.showInfo("Batch analysis was cancelled.");
		}
		catch (Throwable t) {
			plugin.showError("Batch analysis failed: " + t.getMessage(), t);
		}
	}
}
