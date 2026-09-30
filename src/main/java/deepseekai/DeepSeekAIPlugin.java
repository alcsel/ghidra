/* ###
 * Ghidra AI Extension
 *
 * Multi-Provider AI Assistant for Ghidra.
 * Analyzes decompiled code using DeepSeek, OpenAI, Claude, Gemini, Ollama,
 * Groq, OpenRouter, Mistral, xAI & Custom models.
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

import javax.swing.JOptionPane;
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
 * Main plugin class for the Ghidra AI Assistant extension.
 * <p>
 * Decompiles the selected function, queries the configured AI provider
 * (OpenAI, Claude, Gemini, DeepSeek, Ollama, Groq, OpenRouter, Mistral, xAI, Custom),
 * and presents structured suggestions (explanation, comments, variable renames)
 * for user approval before applying changes to the database.
 */
//@formatter:off
@PluginInfo(
	status = PluginStatus.STABLE,
	packageName = "Ghidra Core",
	category = PluginCategoryNames.ANALYSIS,
	shortDescription = "Multi-Provider AI Decompiler Assistant (OpenAI, Claude, Gemini, DeepSeek, Ollama...)",
	description = "Analyzes decompiled functions using major AI models (DeepSeek, OpenAI GPT-4o/o1/o3, " +
		"Anthropic Claude 3.7/3.5, Google Gemini 2.5, Ollama offline local models, Groq, OpenRouter, " +
		"Mistral Codestral, xAI Grok). Explains complex algorithms, generates inline comments, " +
		"and suggests meaningful variable and function renames."
)
//@formatter:on
public class DeepSeekAIPlugin extends ProgramPlugin {

	private static final String TITLE = "AI Code Assistant";
	private static final String MENU_ROOT = "AI Assistant";
	private static final String MENU_LEGACY = "DeepSeek AI";

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
		// 1) Analyze Function
		DockingAction analyzeAction = new DockingAction("AI Assistant: Analyze Function", getName()) {
			@Override
			public void actionPerformed(ActionContext context) {
				analyzeCurrentFunction();
			}
		};
		analyzeAction.setMenuBarData(new MenuData(new String[] { "Tools", MENU_ROOT, "Analyze Function" }));
		analyzeAction.setDescription("Analyzes the function containing the cursor using active AI provider.");
		analyzeAction.setEnabled(true);
		analyzeAction.markHelpUnnecessary();
		tool.addAction(analyzeAction);
		actions.add(analyzeAction);

		// 2) Batch Analyze Functions
		DockingAction batchAction = new DockingAction("AI Assistant: Batch Analyze Functions", getName()) {
			@Override
			public void actionPerformed(ActionContext context) {
				showBatchDialog();
			}
		};
		batchAction.setMenuBarData(new MenuData(new String[] { "Tools", MENU_ROOT, "Batch Analyze Functions..." }));
		batchAction.setDescription("Sequentially analyzes functions with AI, renames symbols, and exports enriched C pseudocode.");
		batchAction.setEnabled(true);
		batchAction.markHelpUnnecessary();
		tool.addAction(batchAction);
		actions.add(batchAction);

		// 3) Quick Switch Provider
		DockingAction switchAction = new DockingAction("AI Assistant: Quick Switch Provider", getName()) {
			@Override
			public void actionPerformed(ActionContext context) {
				quickSwitchProvider();
			}
		};
		switchAction.setMenuBarData(new MenuData(new String[] { "Tools", MENU_ROOT, "Quick Switch Provider / Model..." }));
		switchAction.setDescription("Instantly switch active AI provider (DeepSeek, OpenAI, Claude, Gemini, Ollama, Groq, OpenRouter).");
		switchAction.setEnabled(true);
		switchAction.markHelpUnnecessary();
		tool.addAction(switchAction);
		actions.add(switchAction);

		// 4) Settings (Providers & Keys)
		DockingAction settingsAction = new DockingAction("AI Assistant: Settings", getName()) {
			@Override
			public void actionPerformed(ActionContext context) {
				showSettings();
			}
		};
		settingsAction.setMenuBarData(new MenuData(new String[] { "Tools", MENU_ROOT, "Settings (Providers & Keys)..." }));
		settingsAction.setDescription("Configure AI providers, API keys, models, and generation options.");
		settingsAction.setEnabled(true);
		settingsAction.markHelpUnnecessary();
		tool.addAction(settingsAction);
		actions.add(settingsAction);

		// 5) Show Last Result
		DockingAction showAction = new DockingAction("AI Assistant: Show Last Result", getName()) {
			@Override
			public void actionPerformed(ActionContext context) {
				showLastResult();
			}
		};
		showAction.setMenuBarData(new MenuData(new String[] { "Tools", MENU_ROOT, "Show Last Result" }));
		showAction.setDescription("Reopens the most recent AI analysis result dialog.");
		showAction.setEnabled(true);
		showAction.markHelpUnnecessary();
		tool.addAction(showAction);
		actions.add(showAction);

		// 6) About
		DockingAction aboutAction = new DockingAction("AI Assistant: About", getName()) {
			@Override
			public void actionPerformed(ActionContext context) {
				showAbout();
			}
		};
		aboutAction.setMenuBarData(new MenuData(new String[] { "Tools", MENU_ROOT, "About" }));
		aboutAction.setDescription("Shows information about supported AI providers and models.");
		aboutAction.setEnabled(true);
		aboutAction.markHelpUnnecessary();
		tool.addAction(aboutAction);
		actions.add(aboutAction);

		// Legacy menu compatibility (Tools > DeepSeek AI > ...)
		DockingAction legacySettings = new DockingAction("DeepSeek AI: Settings", getName()) {
			@Override
			public void actionPerformed(ActionContext context) {
				showSettings();
			}
		};
		legacySettings.setMenuBarData(new MenuData(new String[] { "Tools", MENU_LEGACY, "Settings (API Key)..." }));
		legacySettings.setEnabled(true);
		legacySettings.markHelpUnnecessary();
		tool.addAction(legacySettings);
		actions.add(legacySettings);
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
			showInfo("API key is not configured for " + config.provider.getDisplayName() +
				".\nPlease enter your key in the Settings dialog.");
			showSettings();
			return;
		}
		if (function.isExternal() || function.isThunk()) {
			showInfo("External and thunk functions cannot be analyzed.");
			return;
		}

		new TaskLauncher(new DeepSeekAnalyzeTask(this, program, function, config), null);
	}

	private void quickSwitchProvider() {
		AiProvider[] providers = AiProvider.values();
		AiProvider current = config.provider;
		AiProvider selected = (AiProvider) JOptionPane.showInputDialog(
			null,
			"Active Provider : " + current.getDisplayName() + "\n" +
			"Active Model    : " + config.model + "\n" +
			"Key Source      : " + config.apiKeySource() + "\n\n" +
			"Select new AI Provider:",
			"Quick Switch AI Provider",
			JOptionPane.QUESTION_MESSAGE,
			null,
			providers,
			current
		);
		if (selected != null && selected != current) {
			config.switchProvider(selected);
			saveConfig(config);
			showInfo("Switched to " + selected.getDisplayName() + "\nModel: " + config.model +
				"\nKey source: " + config.apiKeySource());
		}
	}

	private Function resolveCurrentFunction(Program program) {
		if (currentLocation != null) {
			Address address = currentLocation.getAddress();
			Function function = program.getFunctionManager().getFunctionContaining(address);
			if (function != null) {
				return function;
			}
		}
		return null;
	}

	public Function getCurrentFunction() {
		Program program = getCurrentProgram();
		if (program == null) {
			return null;
		}
		return resolveCurrentFunction(program);
	}

	private void showBatchDialog() {
		Program program = getCurrentProgram();
		if (program == null) {
			showInfo("Please open a program first.");
			return;
		}
		if (!config.hasApiKey()) {
			showInfo("API key is not configured for " + config.provider.getDisplayName() +
				".\nPlease enter your key in the Settings dialog.");
			showSettings();
			return;
		}
		new BatchAiDialog(this, program, config).setVisible(true);
	}

	public void startBatchAnalysis(Program program, BatchAiOptions options, List<Function> functions) {
		new TaskLauncher(new BatchAiTask(this, program, config, options, functions), null);
	}

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
		showText(TITLE + " - Multi-Provider AI Assistant", """
			Multi-Provider AI Code Assistant for Ghidra
			Version 2.0.0

			Supported Major AI Providers:
			  - OpenAI           : GPT-4o, GPT-4o-mini, o1, o3-mini
			  - Anthropic Claude : Claude 3.7 Sonnet (Hybrid Reasoning), 3.5 Sonnet, Haiku
			  - Google Gemini    : Gemini 2.5 Pro, 2.5 Flash, 2.0 Flash
			  - DeepSeek         : DeepSeek V3 (Chat) and R1 (Reasoner)
			  - Ollama           : 100% Offline Local Inference (Qwen2.5-Coder, Llama 3.3, R1)
			  - Groq             : Ultra-fast LPU execution (hundreds of tokens/sec)
			  - OpenRouter       : Universal AI Gateway with 200+ models
			  - Mistral AI       : Codestral & Mistral Large
			  - xAI              : Grok 2 & Grok Beta
			  - Custom / Local   : Any OpenAI-compatible server (vLLM, LM Studio, Azure)

			Key Reverse Engineering Features:
			  - Decompiles target function and sends pseudocode to the chosen AI model.
			  - Generates algorithm explanations and intent summaries.
			  - Spots complex obfuscations or math and suggests line comments.
			  - Proposes semantic variable renames and entry point function names.
			  - Interactive multi-tab review dialog with address jump links.
			  - One-click atomic commit with Ghidra transaction support.
			  - Batch processing engine with caching and enriched C export.
			  - Dynamic model discovery for Ollama and cloud providers.

			Menus:
			  Tools > AI Assistant > Analyze Function
			  Tools > AI Assistant > Batch Analyze Functions...
			  Tools > AI Assistant > Quick Switch Provider / Model...
			  Tools > AI Assistant > Settings (Providers & Keys)...
			  Tools > AI Assistant > Show Last Result
			""");
	}

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

	public void analysisFailed(Program program, Function function, Throwable error) {
		String message = error == null ? "Unknown error" : error.getMessage();
		Msg.showError(this, null, TITLE,
			function.getName() + " could not be analyzed:\n" + message, error);
	}

	public void applyFinished(Program program, Function function, OutcomeApplier.ApplyCounts counts) {
		showText(TITLE + " - Apply Results", function.getName() + "\n\n" + counts.summary());
	}

	public void applyOutcome(Program program, Function function, AnalysisOutcome outcome,
			List<AnalysisOutcome.VarRename> renames, List<AnalysisOutcome.LineComment> comments,
			boolean renameFunction, boolean setFunctionComment) {
		new TaskLauncher(
			new DeepSeekApplyTask(this, program, function, outcome, renames, comments,
				renameFunction, setFunctionComment),
			resultDialog);
	}

	public DeepSeekConfig getConfig() {
		return config;
	}

	public void saveConfig(DeepSeekConfig newConfig) {
		config = newConfig;
		if (options != null) {
			newConfig.save(options);
		}
		showInfo("Settings saved for " + newConfig.provider.getDisplayName() + " (" + newConfig.model + ").");
	}

	public void showInfo(String message) {
		Msg.showInfo(this, resultDialog, TITLE, message);
	}

	public void showError(String message, Throwable error) {
		Msg.showError(this, resultDialog, TITLE, message + "\n" +
			(error == null ? "" : error.getMessage()), error);
	}

	private void openResultDialog(Program program, Function function, DecompiledContext context,
			AnalysisOutcome outcome) {
		if (resultDialog != null) {
			resultDialog.dispose();
		}
		resultDialog = new DeepSeekResultDialog(this, program, function, context, outcome);
		resultDialog.setVisible(true);
	}

	private void showText(String title, String text) {
		JTextArea area = new JTextArea(text);
		area.setEditable(false);
		area.setCaretPosition(0);
		JScrollPane scrollPane = new JScrollPane(area);
		JPanel panel = new JPanel(new BorderLayout());
		panel.add(scrollPane, BorderLayout.CENTER);
		panel.setPreferredSize(new Dimension(760, 440));

		JOptionPane.showMessageDialog(null, panel, title, JOptionPane.INFORMATION_MESSAGE);
	}
}