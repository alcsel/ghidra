/* ###
 * DeepSeek AI - Ghidra Extension
 *
 * DeepSeek API ile decompile edilmis kodu aciklar, anlasilmasi zor kisimlara
 * yorum yazar ve degiskenleri isimlendirir.
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
 * DeepSeek AI eklentisinin ana sinifi.
 * <p>
 * Menuden secilen fonksiyonu decompile eder, DeepSeek API'sine gonderir ve
 * donen onerileri (aciklama, yorum, degisken isimlendirme) kullanicinin
 * onayina sunar.
 */
//@formatter:off
@PluginInfo(
	status = PluginStatus.STABLE,
	packageName = "Ghidra Core",
	category = PluginCategoryNames.ANALYSIS,
	shortDescription = "DeepSeek AI ile decompile edilmis kodu analiz eder",
	description = "Secili fonksiyonu DeepSeek API'sine gondererek kodu aciklar, " +
		"anlasilmasi zor kisimlara yorum yazar ve degiskenleri isimlendirir. " +
		"Oneriler uygulanmadan once kullaniciya gosterilir."
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
		DockingAction analyzeAction = new DockingAction("DeepSeek AI: Fonksiyonu Analiz Et",
			getName()) {
			@Override
			public void actionPerformed(ActionContext context) {
				analyzeCurrentFunction();
			}
		};
		analyzeAction.setMenuBarData(new MenuData(
			new String[] { "Tools", MENU_ROOT, "Fonksiyonu Analiz Et" }));
		analyzeAction.setDescription(
			"Imlecin bulundugu fonksiyonu DeepSeek ile analiz eder.");
		analyzeAction.setEnabled(true);
		analyzeAction.markHelpUnnecessary();
		tool.addAction(analyzeAction);
		actions.add(analyzeAction);

		DockingAction batchAction =
			new DockingAction("DeepSeek AI: Tum Fonksiyonlari Analiz Et", getName()) {
				@Override
				public void actionPerformed(ActionContext context) {
					showBatchDialog();
				}
			};
		batchAction.setMenuBarData(new MenuData(
			new String[] { "Tools", MENU_ROOT, "Tum Fonksiyonlari Analiz Et (Toplu)..." }));
		batchAction.setDescription("Programdaki fonksiyonlari sirayla DeepSeek ile analiz eder, " +
			"isimlendirir ve istenirse .c dosyasi olarak disa aktarir.");
		batchAction.setEnabled(true);
		batchAction.markHelpUnnecessary();
		tool.addAction(batchAction);
		actions.add(batchAction);

		DockingAction settingsAction = new DockingAction("DeepSeek AI: Ayarlar", getName()) {
			@Override
			public void actionPerformed(ActionContext context) {
				showSettings();
			}
		};
		settingsAction.setMenuBarData(
			new MenuData(new String[] { "Tools", MENU_ROOT, "Ayarlar (API anahtari)..." }));
		settingsAction.setDescription("API anahtari, model ve diger ayarlari duzenler.");
		settingsAction.setEnabled(true);
		settingsAction.markHelpUnnecessary();
		tool.addAction(settingsAction);
		actions.add(settingsAction);

		DockingAction showAction = new DockingAction("DeepSeek AI: Son Sonucu Goster",
			getName()) {
			@Override
			public void actionPerformed(ActionContext context) {
				showLastResult();
			}
		};
		showAction.setMenuBarData(
			new MenuData(new String[] { "Tools", MENU_ROOT, "Son Sonucu Goster" }));
		showAction.setDescription("En son analiz sonucunu tekrar acar.");
		showAction.setEnabled(true);
		showAction.markHelpUnnecessary();
		tool.addAction(showAction);
		actions.add(showAction);

		DockingAction aboutAction = new DockingAction("DeepSeek AI: Hakkinda", getName()) {
			@Override
			public void actionPerformed(ActionContext context) {
				showAbout();
			}
		};
		aboutAction.setMenuBarData(new MenuData(new String[] { "Tools", MENU_ROOT, "Hakkinda" }));
		aboutAction.setDescription("Eklenti hakkinda bilgi gosterir.");
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
	// Menü eylemleri
	// ------------------------------------------------------------------

	private void analyzeCurrentFunction() {
		Program program = getCurrentProgram();
		if (program == null) {
			showInfo("Once bir program acmalisiniz.");
			return;
		}
		Function function = resolveCurrentFunction(program);
		if (function == null) {
			showInfo("Imleci analiz etmek istediginiz fonksiyonun icinde konumlandirin.");
			return;
		}
		if (!config.hasApiKey()) {
			showInfo("DeepSeek API anahtari tanimli degil. Ayarlar penceresini doldurun.");
			showSettings();
			return;
		}
		if (function.isExternal() || function.isThunk()) {
			showInfo("Harici (external) veya thunk fonksiyonlar analiz edilemez.");
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

	/** Imlecin bulundugu fonksiyon (yoksa null). */
	public Function getCurrentFunction() {
		Program program = getCurrentProgram();
		if (program == null) {
			return null;
		}
		return resolveCurrentFunction(program);
	}

	// ------------------------------------------------------------------
	// Toplu (batch) analiz
	// ------------------------------------------------------------------

	private void showBatchDialog() {
		Program program = getCurrentProgram();
		if (program == null) {
			showInfo("Once bir program acmalisiniz.");
			return;
		}
		if (!config.hasApiKey()) {
			showInfo("DeepSeek API anahtari tanimli degil. Ayarlar penceresini doldurun.");
			showSettings();
			return;
		}
		new BatchAiDialog(this, program, config).setVisible(true);
	}

	/** Toplu analizi arka planda baslatir. */
	public void startBatchAnalysis(Program program, BatchAiOptions options,
			List<Function> functions) {
		new TaskLauncher(new BatchAiTask(this, program, config, options, functions), null);
	}

	/** Toplu analiz bittiginde cagrilir (Swing thread'inde). */
	public void batchFinished(Program program, BatchAiEngine.Result result) {
		showText(TITLE + " - Toplu Analiz Sonucu", result.summary());
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
			showInfo("Henuz bir analiz yapilmadi.");
			return;
		}
		openResultDialog(lastProgram, lastFunction, lastContext, lastOutcome);
	}

	private void showAbout() {
		showText(TITLE + " - Hakkinda", """
			DeepSeek AI Ghidra eklentisi

			Ne yapar?
			  - Imlecin bulundugu fonksiyonu decompile eder.
			  - Kodu ve sembol bilgilerini DeepSeek API'sine gonderir.
			  - Fonksiyonun ne yaptigini aciklar.
			  - Anlasilmasi zor satirlar icin yorum onerir.
			  - Degiskenleri ve fonksiyonu anlamli bicimde isimlendirir.
			  - Onerileri siz onayladiktan sonra programa uygular (tek transaction).

			Menuler
			  Tools > DeepSeek AI > Fonksiyonu Analiz Et
			  Tools > DeepSeek AI > Ayarlar (API anahtari)...
			  Tools > DeepSeek AI > Son Sonucu Goster

			Ayarlar ayrica Edit > Tool Options > DeepSeek AI altinda da bulunur.
			""");
	}

	// ------------------------------------------------------------------
	// Arka plan gorevlerinden gelen sonuclar
	// ------------------------------------------------------------------

	/** Analiz basariyla tamamlandiginda cagrilir (Swing thread'inde). */
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

	/** Analiz sirasinda bir hata olustugunda cagrilir. */
	public void analysisFailed(Program program, Function function, Throwable error) {
		String message = error == null ? "bilinmeyen hata" : error.getMessage();
		Msg.showError(this, null, TITLE,
			function.getName() + " analiz edilemedi:\n" + message, error);
	}

	/** Degisiklikler uygulandiktan sonra cagrilir. */
	public void applyFinished(Program program, Function function,
			OutcomeApplier.ApplyCounts counts) {
		showText(TITLE + " - Uygulama Sonucu", function.getName() + "\n\n" + counts.summary());
	}

	/** Kullanicinin onayladigi degisiklikleri programa uygular. */
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
		showInfo("Ayarlar kaydedildi.");
	}

	public void showInfo(String message) {
		Msg.showInfo(this, resultDialog, TITLE, message);
	}

	/** Hata mesaji gosterir. */
	public void showError(String message, Throwable error) {
		Msg.showError(this, resultDialog, TITLE, message + "\n" +
			(error == null ? "" : error.getMessage()), error);
	}

	// ------------------------------------------------------------------
	// Yardimcilar
	// ------------------------------------------------------------------

	private void openResultDialog(Program program, Function function, DecompiledContext context,
			AnalysisOutcome outcome) {
		if (resultDialog != null) {
			resultDialog.dispose();
		}
		resultDialog = new DeepSeekResultDialog(this, program, function, context, outcome);
		resultDialog.setVisible(true);
	}

	/** Uzun metinleri kaydirilabilir bir pencerede gosterir. */
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
