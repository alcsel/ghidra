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
 * Toplu AI analizini arka planda calistiran Ghidra gorevi.
 */
public class BatchAiTask extends Task {

	private final DeepSeekAIPlugin plugin;
	private final Program program;
	private final DeepSeekConfig config;
	private final BatchAiOptions options;
	private final List<Function> functions;

	public BatchAiTask(DeepSeekAIPlugin plugin, Program program, DeepSeekConfig config,
			BatchAiOptions options, List<Function> functions) {
		super("DeepSeek AI: toplu analiz (" + functions.size() + " fonksiyon)", true, true, false);
		this.plugin = plugin;
		this.program = program;
		this.config = config;
		this.options = options;
		this.functions = functions;
	}

	@Override
	public void run(TaskMonitor monitor) throws CancelledException {
		BatchAiEngine.Result result;
		try {
			result = new BatchAiEngine(program, config, options).run(functions, monitor);
		}
		catch (CancelledException e) {
			SwingUtilities.invokeLater(() -> plugin.showInfo(
				"Toplu analiz iptal edildi.\n\nO ana kadar yapilan degisiklikler ve onbellek " +
					"korundu. Kaldiginiz yerden devam etmek icin tekrar baslatabilirsiniz."));
			return;
		}
		catch (Throwable t) {
			SwingUtilities.invokeLater(() -> plugin.showError("Toplu analiz basarisiz oldu.", t));
			return;
		}
		SwingUtilities.invokeLater(() -> plugin.batchFinished(program, result));
	}
}
