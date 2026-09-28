/* ###
 * DeepSeek AI - Ghidra Extension
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
 * Arka planda fonksiyonu decompile eder, DeepSeek'e gonderir ve sonucu
 * eklentiye iletir.
 */
public class DeepSeekAnalyzeTask extends Task {

	private final DeepSeekAIPlugin plugin;
	private final Program program;
	private final Function function;
	private final DeepSeekConfig config;

	public DeepSeekAnalyzeTask(DeepSeekAIPlugin plugin, Program program, Function function,
			DeepSeekConfig config) {
		super("DeepSeek AI: " + function.getName() + " analiz ediliyor", false, true, false);
		this.plugin = plugin;
		this.program = program;
		this.function = function;
		this.config = config;
	}

	@Override
	public void run(TaskMonitor monitor) throws CancelledException {
		try {
			monitor.setMessage("Decompile ediliyor: " + function.getName());
			DecompiledContext context =
				DecompilerHelper.build(program, function, monitor, config.maxCodeChars);
			monitor.checkCanceled();

			monitor.setMessage("DeepSeek API'ye gonderiliyor (" + config.model + ")...");
			String system = Prompt.systemPrompt(config);
			String user = Prompt.userPrompt(context, config);
			DeepSeekClient.ChatResponse response =
				new DeepSeekClient().chat(system, user, config, monitor);
			monitor.checkCanceled();

			monitor.setMessage("Yanit isleniyor...");
			AnalysisOutcome outcome = AnalysisOutcome.parse(response.content, program);
			outcome.usageText = response.usageText();
			if (!response.finishReason.isEmpty()) {
				outcome.usageText = outcome.usageText.isEmpty() ? response.finishReason
						: (outcome.usageText + ", bitis: " + response.finishReason);
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
