/* ###
 * DeepSeek AI - Ghidra Extension
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package deepseekai;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.FunctionIterator;
import ghidra.program.model.listing.Program;

/**
 * Setup dialog for batch AI analysis.
 * <p>
 * Allows configuring function selection filters, modifications to apply,
 * export options (.c output), caching, and request pacing.
 */
public class BatchAiDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	/** Number of functions above which a cost warning is shown. */
	private static final int CONFIRM_THRESHOLD = 50;

	private final DeepSeekAIPlugin plugin;
	private final Program program;
	private final DeepSeekConfig config;

	private final JRadioButton scopeAll;
	private final JRadioButton scopeSelection;
	private final JRadioButton scopeCurrent;
	private final JCheckBox onlyUndefined;
	private final JCheckBox skipThunks;
	private final JSpinner minBytes;
	private final JSpinner maxBytes;
	private final JSpinner maxFunctions;
	private final JSpinner delayMillis;

	private final JCheckBox applyNames;
	private final JCheckBox applyVars;
	private final JCheckBox applyComments;
	private final JCheckBox useCache;

	private final JCheckBox writeC;
	private final JTextField cFileField;

	private final JLabel estimateLabel;

	public BatchAiDialog(DeepSeekAIPlugin plugin, Program program, DeepSeekConfig config) {
		super();
		this.plugin = plugin;
		this.program = program;
		this.config = config;

		setTitle("AI Assistant - Batch Analysis [" + config.provider.getDisplayName() + " / " + config.model + "]");
		setModal(true);
		setLayout(new BorderLayout());

		int totalFunctions = allFunctions().size();
		scopeAll = new JRadioButton("All functions in program (" + totalFunctions + ")", true);
		scopeSelection = new JRadioButton("Functions in current selection");
		scopeCurrent = new JRadioButton("Only current function");

		onlyUndefined = new JCheckBox(
			"Only undefined function names (FUN_xxxx, sub_xxxx)", true);
		skipThunks = new JCheckBox("Skip external and thunk functions", true);

		minBytes = new JSpinner(new SpinnerNumberModel(16, 0, 1000000, 16));
		maxBytes = new JSpinner(new SpinnerNumberModel(24000, 0, 1000000, 500));
		maxFunctions = new JSpinner(new SpinnerNumberModel(0, 0, 50000, 10));
		delayMillis = new JSpinner(new SpinnerNumberModel(0, 0, 10000, 100));

		applyNames = new JCheckBox("Auto-rename functions with AI suggested names", true);
		applyVars = new JCheckBox("Auto-rename variables (parameters & locals)", true);
		applyComments = new JCheckBox("Auto-add technical comments to complex code lines", true);
		useCache = new JCheckBox("Use cache (skip functions already analyzed)", true);

		writeC = new JCheckBox("Export enriched C pseudocode to file", true);
		cFileField = new JTextField(defaultOutputPath(), 28);

		estimateLabel = new JLabel(" ");

		if (plugin.getProgramSelection() == null || plugin.getProgramSelection().isEmpty()) {
			scopeSelection.setEnabled(false);
		}
		if (plugin.getProgramLocation() == null) {
			scopeCurrent.setEnabled(false);
		}
		ButtonGroup group = new ButtonGroup();
		group.add(scopeAll);
		group.add(scopeSelection);
		group.add(scopeCurrent);

		add(buildForm(), BorderLayout.CENTER);
		add(buildButtons(), BorderLayout.SOUTH);
		updateEstimate();

		pack();
		setLocationRelativeTo(null);
	}

	private String defaultOutputPath() {
		String base = program.getName();
		if (base.toLowerCase().endsWith(".exe") || base.lastIndexOf('.') > 0) {
			base = base.substring(0, base.lastIndexOf('.'));
		}
		String home = System.getProperty("user.home", ".");
		return new File(home, base + "_ai_decompiled.c").getAbsolutePath();
	}

	private JPanel buildForm() {
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(3, 4, 3, 4);
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0;
		c.gridwidth = 3;

		int row = 0;
		panel.add(section("1) Scope & Filters"), at(c, 0, row++));
		c.gridwidth = 1;
		c.gridx = 0;
		c.gridy = row++;
		panel.add(scopeAll, c);
		c.gridy = row++;
		panel.add(scopeSelection, c);
		c.gridy = row++;
		panel.add(scopeCurrent, c);

		c.gridx = 0;
		c.gridy = row++;
		c.gridwidth = 3;
		panel.add(onlyUndefined, c);
		c.gridy = row++;
		panel.add(skipThunks, c);

		c.gridwidth = 1;
		c.gridx = 0;
		c.gridy = row;
		panel.add(new JLabel("Min / max function size (bytes):"), c);
		c.gridx = 1;
		JPanel sizePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		sizePanel.add(minBytes);
		sizePanel.add(new JLabel("-"));
		sizePanel.add(maxBytes);
		panel.add(sizePanel, c);
		c.gridx = 0;
		c.gridy = ++row;
		panel.add(new JLabel("Max functions to process (0 = unlimited):"), c);
		c.gridx = 1;
		panel.add(maxFunctions, c);
		c.gridx = 0;
		c.gridy = ++row;
		panel.add(new JLabel("Delay between requests (ms):"), c);
		c.gridx = 1;
		panel.add(delayMillis, c);

		c.gridx = 0;
		c.gridy = ++row;
		c.gridwidth = 3;
		panel.add(section("2) Actions to Apply"), c);
		c.gridwidth = 1;
		c.gridx = 0;
		c.gridy = ++row;
		c.gridwidth = 3;
		panel.add(applyNames, c);
		c.gridy = ++row;
		panel.add(applyVars, c);
		c.gridy = ++row;
		panel.add(applyComments, c);
		c.gridy = ++row;
		panel.add(useCache, c);

		c.gridx = 0;
		c.gridy = ++row;
		panel.add(section("3) Output Options"), c);
		c.gridwidth = 1;
		c.gridx = 0;
		c.gridy = ++row;
		c.gridwidth = 3;
		panel.add(writeC, c);
		c.gridwidth = 1;
		c.gridy = ++row;
		c.gridx = 0;
		panel.add(new JLabel("File:"), c);
		c.gridx = 1;
		c.weightx = 1.0;
		panel.add(cFileField, c);
		c.gridx = 2;
		c.weightx = 0.0;
		JButton browse = new JButton("Browse...");
		browse.addActionListener(e -> chooseFile());
		panel.add(browse, c);

		c.gridx = 0;
		c.gridy = ++row;
		c.gridwidth = 3;
		estimateLabel.setForeground(new Color(0, 90, 160));
		panel.add(estimateLabel, c);

		// Update estimate when filters change
		onlyUndefined.addActionListener(e -> updateEstimate());
		skipThunks.addActionListener(e -> updateEstimate());
		scopeAll.addActionListener(e -> updateEstimate());
		scopeSelection.addActionListener(e -> updateEstimate());
		scopeCurrent.addActionListener(e -> updateEstimate());
		minBytes.addChangeListener(e -> updateEstimate());
		maxBytes.addChangeListener(e -> updateEstimate());
		maxFunctions.addChangeListener(e -> updateEstimate());

		return panel;
	}

	private static GridBagConstraints at(GridBagConstraints c, int x, int y) {
		c.gridx = x;
		c.gridy = y;
		return c;
	}

	private static JLabel section(String text) {
		JLabel label = new JLabel(text);
		label.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
		label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD));
		return label;
	}

	private JPanel buildButtons() {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
		JButton start = new JButton("Start Analysis");
		start.addActionListener(e -> start());
		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(e -> dispose());
		panel.add(start);
		panel.add(cancel);
		return panel;
	}

	private void chooseFile() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Save C Pseudocode Output");
		chooser.setSelectedFile(new File(cFileField.getText()));
		if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
			cFileField.setText(chooser.getSelectedFile().getAbsolutePath());
		}
	}

	private BatchAiOptions buildOptions() {
		BatchAiOptions options = new BatchAiOptions();
		if (scopeSelection.isSelected()) {
			options.scope = BatchAiOptions.Scope.SELECTION;
			options.selection = plugin.getProgramSelection();
		}
		else if (scopeCurrent.isSelected()) {
			options.scope = BatchAiOptions.Scope.CURRENT;
		}
		else {
			options.scope = BatchAiOptions.Scope.ALL;
		}
		options.onlyUndefinedNames = onlyUndefined.isSelected();
		options.skipThunks = skipThunks.isSelected();
		options.skipExternal = skipThunks.isSelected();
		options.minFunctionBytes = ((Number) minBytes.getValue()).intValue();
		options.maxFunctionBytes = ((Number) maxBytes.getValue()).intValue();
		options.maxFunctions = ((Number) maxFunctions.getValue()).intValue();
		options.delayMillis = ((Number) delayMillis.getValue()).intValue();
		options.applyFunctionNames = applyNames.isSelected();
		options.applyVariableRenames = applyVars.isSelected();
		options.applyComments = applyComments.isSelected();
		options.useCache = useCache.isSelected();
		if (writeC.isSelected() && !cFileField.getText().isBlank()) {
			options.cOutputFile = new File(cFileField.getText().trim());
		}
		return options;
	}

	/** Returns all functions in the program sorted by address. */
	private List<Function> allFunctions() {
		List<Function> list = new ArrayList<>();
		FunctionIterator iterator = program.getFunctionManager().getFunctions(true);
		while (iterator.hasNext()) {
			list.add(iterator.next());
		}
		return list;
	}

	private List<Function> selectedFunctions() {
		BatchAiOptions options = buildOptions();
		Function current = plugin.getCurrentFunction();
		return options.selectFunctions(allFunctions(), current);
	}

	private void updateEstimate() {
		try {
			int count = selectedFunctions().size();
			long approxTokens = (long) count * 2500L;
			String extra = "";
			if (!config.hasApiKey()) {
				extra = "   |   WARNING: API key not configured!";
			}
			estimateLabel.setText("Selected functions: " + count + "   |   Estimated ~" +
				String.format("%,d", approxTokens) + " tokens" + extra);
		}
		catch (Throwable t) {
			estimateLabel.setText("Could not calculate selected functions: " + t.getMessage());
		}
	}

	private void start() {
		List<Function> functions = selectedFunctions();
		if (functions.isEmpty()) {
			JOptionPane.showMessageDialog(this,
				"No functions match the selected filters.\n\n" +
					"Tip: Try unchecking 'Only undefined function names'.",
				"DeepSeek AI", JOptionPane.WARNING_MESSAGE);
			return;
		}
		if (!config.hasApiKey()) {
			JOptionPane.showMessageDialog(this,
				"API key is not configured. Please set it in Settings first.",
				"DeepSeek AI", JOptionPane.ERROR_MESSAGE);
			return;
		}
		if (functions.size() > CONFIRM_THRESHOLD) {
			long approxTokens = (long) functions.size() * 2500L;
			int answer = JOptionPane.showConfirmDialog(this,
				functions.size() + " functions will be sent to DeepSeek.\n" +
					"Estimated consumption: ~" + String.format("%,d", approxTokens) + " tokens.\n" +
					"This may incur API costs and take several minutes.\n\n" +
					"Continue?",
				"DeepSeek AI - Cost Confirmation", JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE);
			if (answer != JOptionPane.YES_OPTION) {
				return;
			}
		}
		dispose();
		plugin.startBatchAnalysis(program, buildOptions(), functions);
	}
}
