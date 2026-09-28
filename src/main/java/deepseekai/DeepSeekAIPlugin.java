/* ###
 * DeepSeek AI - Ghidra Extension
 *
 * Analyzes decompiled code using the DeepSeek API, comments on complex logic,
 * and renames variables and functions.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package deepseekai;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import docking.ActionContext;
import docking.action.DockingAction;
import docking.action.MenuData;
import ghidra.app.plugin.PluginCategoryNames;
import ghidra.app.plugin.ProgramPlugin;
import ghidra.framework.options.ToolOptions;
import ghidra.framework.plugintool.PluginInfo;
import ghidra.framework.plugintool.PluginTool;
import ghidra.framework.plugintool.util.PluginStatus;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Program;
import ghidra.util.Msg;
import ghidra.util.task.TaskLauncher;

/**
 * Main plugin class for DeepSeek AI extension.
 * <p>
 * Decompiles the selected function, queries the DeepSeek API, and presents
 * suggestions (explanation, comments, variable renames) for user approval.
 */
//@formatter:off
@PluginInfo(
	status = PluginStatus.STABLE,
	packageName = "Ghidra Core",
	category = PluginCategoryNames.ANALYSIS,
	shortDescription = "Analyzes decompiled code with DeepSeek AI",
	description = "Analyzes the selected function via the DeepSeek API to explain logic, " +
		"add comments to complex blocks, and suggest meaningful names for variables. " +
		"Suggestions are presented for review before applying."
)
//@formatter:on
public class DeepSeekAIPlugin extends ProgramPlugin {

	private static final String TITLE = "DeepSeek AI";
	private static final String MENU_ROOT = "DeepSeek AI";

	private ToolOptions options;
	private DeepSeekConfig config;

	private final List<DockingAction> actions = new ArrayList<>();

	private DeepSeekResultDialog resultDialog;
	private DeepSeekOptionsDialog optionsDialog;

	private Program lastProgram;
	private Function lastFunction;
	private DecompiledContext lastContext;
	private AnalysisOutcome lastOutcome;

	public DeepSeekAIPlugin(PluginTool tool) {
		super(tool);
	}

	@Override
	public void init() {
		super.init();
		options = tool.getOptions(DeepSeekConfig.OWNER);
		config = new DeepSeekConfig();
		config.register(options);
		config.load(options);
		createActions();
	}

	private void createActions() {
		DockingAction analyzeAction = new DockingAction("DeepSeek AI: Analyze Function",
			getName()) {
			@Override
			public void actionPerformed(ActionContext context) {
				analyzeCurrentFunction();
			}
		};
		analyzeAction.setMenuBarData(new MenuData(
			new String[] { "Tools", MENU_ROOT, "Analyze Function" }));
		analyzeAction.setDescription(
			"Analyzes the function containing the cursor using DeepSeek.");
		analyzeAction.setEnabled(true);
		analyzeAction.markHelpUnnecessary();
		tool.addAction(analyzeAction);
		actions.add(analyzeAction);

		DockingAction batchAction =
			new DockingAction("DeepSeek AI: Batch Analyze Functions", getName()) {
				@Override
				public void actionPerformed(ActionContext context) {
					showBatchDialog();
				}
			};
		batchAction.setMenuBarData(new MenuData(
			new String[] { "Tools", MENU_ROOT, "Batch Analyze Functions..." }));
		batchAction.setDescription("Analyzes functions sequentially using DeepSeek, " +
			"renames symbols, and optionally exports to an enriched C file.");
		batchAction.setEnabled(true);
		batchAction.markHelpUnnecessary();
		tool.addAction(batchAction);
		actions.add(batchAction);

		DockingAction settingsAction = new DockingAction("DeepSeek AI: Settings", getName()) {
			@Override
			public void actionPerformed(ActionContext context) {
				showSettings();
			}
		};
		settingsAction.setMenuBarData(
			new MenuData(new String[] { "Tools", MENU_ROOT, "Settings (API Key)..." }));
		settingsAction.setDescription("Configure API key, model, and other preferences.");
		settingsAction.setEnabled(true);
		settingsAction.markHelpUnnecessary();
		tool.addAction(settingsAction);
		actions.add(settingsAction);

		DockingAction showAction = new DockingAction("DeepSeek AI: Show Last Result",
			getName()) {
			@Override
			public void actionPerformed(ActionContext context) {
				showLastResult();
			}
		};
		showAction.setMenuBarData(
			new MenuData(new String[] { "Tools", MENU_ROOT, "Show Last Result" }));
		showAction.setDescription("Reopens the most recent analysis result dialog.");
		showAction.setEnabled(true);
		showAction.markHelpUnnecessary();
		tool.addAction(showAction);
		actions.add(showAction);

		DockingAction aboutAction = new DockingAction("DeepSeek AI: About", getName()) {
			@Override
			public void actionPerformed(ActionContext context) {
				showAbout();
			}
		};
		aboutAction.setMenuBarData(new MenuData(new String[] { "Tools", MENU_ROOT, "About" }));
		aboutAction.setDescription("Shows information about the DeepSeek AI extension.");
		aboutAction.setEnabled(true);
		aboutAction.markHelpUnnecessary();
		tool.addAction(aboutAction);
		actions.add(aboutAction);
	}

	@Override
	protected void dispose() {
		if (resultDialog != null) {
			resultDialog.dispose();
			resultDialog = null;
		}
		if (optionsDialog != null) {
			optionsDialog.dispose();
			optionsDialog = null;
		}
		actions.clear();
		super.dispose();
	}

	// ------------------------------------------------------------------
	// Menu Actions
	// ------------------------------------------------------------------

	private void analyzeCurrentFunction() {
		Program program = getCurrentProgram();
		if (program == null) {
			showInfo("Please open a program first.");
			return;
		}
		Function function = resolveCurrentFunction(program);
		if (function == null) {
			showInfo("Please place the cursor inside the function you want to analyze.");
			return;
		}
		if (!config.hasApiKey()) {
			showInfo("DeepSeek API key is not configured. Please fill in the Settings dialog.");
			showSettings();
			return;
		}
		if (function.isExternal() || function.isThunk()) {
			showInfo("External and thunk functions cannot be analyzed.");
			return;
		}

		new TaskLauncher(new DeepSeekAnalyzeTask(this, program, function, config), null);
	}

	private Function resolveCurrentFunction(Program program) {
		if (currentLocation != null) {
			Address address = currentLocation.getAddress();
			Function function =
				program.getFunctionManager().getFunctionContaining(address);
			if (function != null) {
				return function;
			}
		}
		return null;
	}

	/** Returns the function containing the cursor, or null if none. */
	public Function getCurrentFunction() {
		Program program = getCurrentProgram();
		if (program == null) {
			return null;
		}
		return resolveCurrentFunction(program);
	}

	// ------------------------------------------------------------------
	// Batch Analysis
	// ------------------------------------------------------------------

	private void showBatchDialog() {
		Program program = getCurrentProgram();
		if (program == null) {
			showInfo("Please open a program first.");
			return;
		}
		if (!config.hasApiKey()) {
			showInfo("DeepSeek API key is not configured. Please fill in the Settings dialog.");
			showSettings();
			return;
		}
		new BatchAiDialog(this, program, config).setVisible(true);
	}

	/** Launches batch analysis in the background. */
	public void startBatchAnalysis(Program program, BatchAiOptions options,
			List<Function> functions) {
		new TaskLauncher(new BatchAiTask(this, program, config, options, functions), null);
	}

	/** Called when batch analysis finishes (on Swing EDT). */
	public void batchFinished(Program program, BatchAiEngine.Result result) {
		showText(TITLE + " - Batch Analysis Result", result.summary());
	}

	private void showSettings() {
		if (optionsDialog != null && optionsDialog.isDisplayable()) {
			optionsDialog.toFront();
			return;
		}
		optionsDialog = new DeepSeekOptionsDialog(this, config);
		optionsDialog.setVisible(true);
		optionsDialog = null;
	}

	private void showLastResult() {
		if (lastOutcome == null || lastContext == null) {
			showInfo("No analysis has been performed yet.");
			return;
		}
		openResultDialog(lastProgram, lastFunction, lastContext, lastOutcome);
	}

	private void showAbout() {
		showText(TITLE + " - About", """
			DeepSeek AI Ghidra Extension

			Features:
			  - Decompiles the selected function.
			  - Sends pseudocode and symbols to the DeepSeek API.
			  - Explains function behavior and algorithmic intent.
			  - Identifies complex blocks and suggests inline technical comments.
			  - Suggests meaningful names for variables and functions.
			  - Applies approved changes in a single atomic undoable transaction.

			Menu Shortcuts:
			  Tools > DeepSeek AI > Analyze Function
			  Tools > DeepSeek AI > Batch Analyze Functions...
			  Tools > DeepSeek AI > Settings (API Key)...
			  Tools > DeepSeek AI > Show Last Result

			Settings are also accessible under Edit > Tool Options > DeepSeek AI.
			""");
	}

	// ------------------------------------------------------------------
	// Results from Background Tasks
	// ------------------------------------------------------------------

	/** Called on successful single-function analysis completion (on Swing EDT). */
	public void analysisFinished(Program program, Function function, DecompiledContext context,
			AnalysisOutcome outcome) {
		lastProgram = program;
		lastFunction = function;
		lastContext = context;
		lastOutcome = outcome;

		if (outcome.parsedFromJson && (config.autoApplyComments || config.autoApplyRenames ||
			config.autoApplyFunctionName)) {
			applyFromConfig(program, function, outcome);
			return;
		}
		openResultDialog(program, function, context, outcome);
	}

	private void applyFromConfig(Program program, Function function, AnalysisOutcome outcome) {
		List<AnalysisOutcome.VarRename> renames = config.autoApplyRenames
				? outcome.selectedRenames()
				: List.of();
		List<AnalysisOutcome.LineComment> comments = config.autoApplyComments
				? outcome.selectedComments()
				: List.of();
		boolean renameFunction = config.autoApplyFunctionName && !outcome.functionName.isEmpty();
		boolean setComment = config.autoApplyComments;
		if (renames.isEmpty() && comments.isEmpty() && !renameFunction && !setComment) {
			openResultDialog(program, function, lastContext, outcome);
			return;
		}
		applyOutcome(program, function, outcome, renames, comments, renameFunction, setComment);
	}

	/** Called when an analysis error occurs. */
	public void analysisFailed(Program program, Function function, Throwable error) {
		String message = error == null ? "Unknown error" : error.getMessage();
		Msg.showError(this, null, TITLE,
			function.getName() + " could not be analyzed:\n" + message, error);
	}

	/** Called after modifications have been applied to the program. */
	public void applyFinished(Program program, Function function,
			OutcomeApplier.ApplyCounts counts) {
		showText(TITLE + " - Apply Results", function.getName() + "\n\n" + counts.summary());
	}

	/** Applies confirmed modifications to the program. */
	public void applyOutcome(Program program, Function function, AnalysisOutcome outcome,
			List<AnalysisOutcome.VarRename> renames,
			List<AnalysisOutcome.LineComment> comments, boolean renameFunction,
			boolean setFunctionComment) {
		new TaskLauncher(
			new DeepSeekApplyTask(this, program, function, outcome, renames, comments,
				renameFunction, setFunctionComment),
			resultDialog);
	}

	public void saveConfig(DeepSeekConfig newConfig) {
		config = newConfig;
		if (options != null) {
			newConfig.save(options);
		}
		showInfo("Settings saved.");
	}

	public void showInfo(String message) {
		Msg.showInfo(this, resultDialog, TITLE, message);
	}

	/** Shows an error message dialog. */
	public void showError(String message, Throwable error) {
		Msg.showError(this, resultDialog, TITLE, message + "\n" +
			(error == null ? "" : error.getMessage()), error);
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	private void openResultDialog(Program program, Function function, DecompiledContext context,
			AnalysisOutcome outcome) {
		if (resultDialog != null) {
			resultDialog.dispose();
		}
		resultDialog = new DeepSeekResultDialog(this, program, function, context, outcome);
		resultDialog.setVisible(true);
	}

	/** Displays long text in a scrollable dialog. */
	private void showText(String title, String text) {
		JTextArea area = new JTextArea(text);
		area.setEditable(false);
		area.setCaretPosition(0);
		JScrollPane scrollPane = new JScrollPane(area);
		JPanel panel = new JPanel(new BorderLayout());
		panel.add(scrollPane, BorderLayout.CENTER);
		panel.setPreferredSize(new Dimension(760, 440));

		javax.swing.JOptionPane.showMessageDialog(null, panel, title,
			javax.swing.JOptionPane.INFORMATION_MESSAGE);
	}
}
