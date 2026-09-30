/* ###
 * Ghidra AI Extension
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package deepseekai;

import javax.swing.SwingUtilities;

import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Program;
import ghidra.util.exception.CancelledException;
import ghidra.util.task.Task;
import ghidra.util.task.TaskMonitor;

/**
 * Background task that decompiles the selected function, queries the AI provider,
 * and passes the parsed result to the plugin.
 */
public class DeepSeekAnalyzeTask extends Task {

	private final DeepSeekAIPlugin plugin;
	private final Program program;
	private final Function function;
	private final DeepSeekConfig config;

	public DeepSeekAnalyzeTask(DeepSeekAIPlugin plugin, Program program, Function function,
			DeepSeekConfig config) {
		super("AI Analysis: " + function.getName(), false, true, false);
		this.plugin = plugin;
		this.program = program;
		this.function = function;
		this.config = config;
	}

	@Override
	public void run(TaskMonitor monitor) throws CancelledException {
		try {
			monitor.setMessage("Decompiling: " + function.getName());
			DecompiledContext context =
				DecompilerHelper.build(program, function, monitor, config.maxCodeChars);
			monitor.checkCanceled();

			monitor.setMessage("Sending to " + config.provider.getDisplayName() + " (" + config.model + ")...");
			String system = Prompt.systemPrompt(config);
			String user = Prompt.userPrompt(context, config);
			DeepSeekClient.ChatResponse response =
				new DeepSeekClient().chat(system, user, config, monitor);
			monitor.checkCanceled();

			monitor.setMessage("Processing response...");
			AnalysisOutcome outcome = AnalysisOutcome.parse(response.content, program);
			outcome.providerName = response.providerName;
			outcome.modelName = response.modelName;
			outcome.usageText = response.usageText();
			if (!response.finishReason.isEmpty()) {
				outcome.usageText = outcome.usageText.isEmpty() ? response.finishReason
						: (outcome.usageText + ", finish: " + response.finishReason);
			}

			SwingUtilities.invokeLater(
				() -> plugin.analysisFinished(program, function, context, outcome));
		}
		catch (CancelledException e) {
			throw e;
		}
		catch (Throwable t) {
			SwingUtilities.invokeLater(() -> plugin.analysisFailed(program, function, t));
		}
	}
}