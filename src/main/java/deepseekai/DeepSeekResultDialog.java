/* ###
 * DeepSeek AI - Ghidra Extension
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package deepseekai;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;

import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Program;

/**
 * Dialog displaying analysis suggestions returned by AI.
 * <p>
 * The user can review the clean handwritten C code (zero uVar artifacts),
 * side-by-side comparison with original decompilation, summary, variable renames,
 * and select which modifications to apply before committing them to the program.
 */
public class DeepSeekResultDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	private final DeepSeekAIPlugin plugin;
	private final Program program;
	private final Function function;
	private final DecompiledContext context;
	private final AnalysisOutcome outcome;
	private final DeepSeekConfig config;

	private final DefaultTableModel renameModel;
	private final DefaultTableModel commentModel;
	private final JCheckBox renameFunctionBox;
	private final JCheckBox functionCommentBox;
	private final JLabel selectionLabel;

	private boolean applying;

	public DeepSeekResultDialog(DeepSeekAIPlugin plugin, Program program, Function function,
			DecompiledContext context, AnalysisOutcome outcome) {
		super();
		this.plugin = plugin;
		this.program = program;
		this.function = function;
		this.context = context;
		this.outcome = outcome;
		this.config = plugin.getConfig();

		setTitle(function.getName() + " - AI Analysis [" + outcome.getSourceInfo() + "]");
		setModal(false);
		setLayout(new BorderLayout());

		renameModel = buildRenameModel();
		commentModel = buildCommentModel();

		renameFunctionBox = new JCheckBox(
			"Rename function: " + displayFunctionName(),
			!outcome.functionName.isEmpty());
		functionCommentBox = new JCheckBox(
			"Set function entry comment",
			!outcome.functionComment.isEmpty() || !outcome.summary.isEmpty());
		selectionLabel = new JLabel();

		add(buildHeader(), BorderLayout.NORTH);
		add(buildTabs(), BorderLayout.CENTER);
		add(buildFooter(), BorderLayout.SOUTH);

		updateSelectionLabel();
		setSize(1020, 700);
		setLocationRelativeTo(null);
	}

	private JTabbedPane buildTabs() {
		JTabbedPane tabs = new JTabbedPane();

		// 1. ✨ Clean C Code (Handwritten) - DEFAULT FIRST TAB
		String codeLang = (config != null && config.targetLanguage.contains("C++")) ? "C++" : "C";
		tabs.addTab("✨ Clean Code (" + codeLang + ")", buildCleanCodePanel());

		// 2. 🔄 Side-by-Side (Original vs Clean)
		tabs.addTab("🔄 Side-by-Side (Decompiled vs " + codeLang + ")", buildSideBySidePanel());

		// 3. Summary
		tabs.addTab("Summary", scrollable(textArea(outcome.summary, false, true)));

		// 4. Variable Renames
		JTable renameTable = buildTable(renameModel);
		tabs.addTab("Variable Renames (" + outcome.varRenames.size() + ")",
			scrollable(renameTable));

		// 5. Line Comments
		JTable commentTable = buildTable(commentModel);
		tabs.addTab("Line Comments (" + outcome.lineComments.size() + ")",
			scrollable(commentTable));

		// 6. Complex Blocks
		tabs.addTab("Complex Blocks (" + outcome.hardParts.size() + ")",
			scrollable(textArea(hardPartsText(), false, true)));

		// 7. Uncertainties
		tabs.addTab("Uncertainties (" + outcome.uncertainties.size() + ")",
			scrollable(textArea(uncertaintiesText(), false, true)));

		// 8. Raw Response
		tabs.addTab("Raw Response", scrollable(textArea(outcome.rawResponse, true, false)));

		return tabs;
	}

	private JPanel buildCleanCodePanel() {
		JPanel panel = new JPanel(new BorderLayout());

		// Top toolbar with actions and info badge
		JPanel toolbar = new JPanel(new BorderLayout());
		toolbar.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

		String codeLang = (config != null && config.targetLanguage.contains("C++")) ? "C++" : "C";
		String ext = (config != null && config.targetLanguage.contains("C++")) ? ".cpp" : ".c";
		JLabel label = new JLabel("✨ Reconstructed " + (config != null ? config.targetLanguage : "C/C++") + " Code (Zero uVar / machine artifacts)");
		label.setFont(label.getFont().deriveFont(Font.BOLD));
		toolbar.add(label, BorderLayout.WEST);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		JButton copyBtn = new JButton("📋 Copy Clean C");
		copyBtn.setToolTipText("Copy reconstructed clean C code to system clipboard");
		copyBtn.addActionListener(e -> copyCleanCodeToClipboard(copyBtn));

		JButton exportBtn = new JButton("💾 Export " + ext + " File");
		exportBtn.setToolTipText("Save reconstructed clean C code to a .c source file");
		exportBtn.addActionListener(e -> exportCleanCodeToFile());

		actions.add(copyBtn);
		actions.add(exportBtn);
		toolbar.add(actions, BorderLayout.EAST);

		panel.add(toolbar, BorderLayout.NORTH);

		String cleanCode = outcome.getOrGenerateCleanCode(context);
		JTextArea codeArea = textArea(cleanCode, true, false);
		codeArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		panel.add(new JScrollPane(codeArea), BorderLayout.CENTER);

		return panel;
	}

	private JPanel buildSideBySidePanel() {
		JPanel panel = new JPanel(new BorderLayout());

		// Left: Original Ghidra Decompilation
		JPanel left = new JPanel(new BorderLayout());
		JLabel leftTitle = new JLabel("  Original Decompiled (Ghidra)");
		leftTitle.setFont(leftTitle.getFont().deriveFont(Font.BOLD));
		leftTitle.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		left.add(leftTitle, BorderLayout.NORTH);

		String origCode = context.annotatedCode != null && !context.annotatedCode.isEmpty()
				? context.annotatedCode
				: context.rawCode;
		JTextArea leftArea = textArea(origCode, true, false);
		leftArea.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		left.add(new JScrollPane(leftArea), BorderLayout.CENTER);

		// Right: Clean Reconstructed Code
		JPanel right = new JPanel(new BorderLayout());
		JLabel rightTitle = new JLabel("  ✨ Clean Reconstructed (Handwritten)");
		rightTitle.setFont(rightTitle.getFont().deriveFont(Font.BOLD));
		rightTitle.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		right.add(rightTitle, BorderLayout.NORTH);

		String cleanCode = outcome.getOrGenerateCleanCode(context);
		JTextArea rightArea = textArea(cleanCode, true, false);
		rightArea.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		right.add(new JScrollPane(rightArea), BorderLayout.CENTER);

		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
		splitPane.setResizeWeight(0.5);
		splitPane.setContinuousLayout(true);
		panel.add(splitPane, BorderLayout.CENTER);

		return panel;
	}

	private void copyCleanCodeToClipboard(JButton sourceButton) {
		String code = outcome.getOrGenerateCleanCode(context);
		if (code.isEmpty()) {
			return;
		}
		try {
			StringSelection selection = new StringSelection(code);
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
			if (sourceButton != null) {
				String originalText = sourceButton.getText();
				sourceButton.setText("✓ Copied!");
				javax.swing.Timer timer = new javax.swing.Timer(2000, e -> sourceButton.setText(originalText));
				timer.setRepeats(false);
				timer.start();
			}
		}
		catch (Exception ex) {
			plugin.showError("Could not copy code to clipboard: " + ex.getMessage(), ex);
		}
	}

	private void exportCleanCodeToFile() {
		String code = outcome.getOrGenerateCleanCode(context);
		if (code.isEmpty()) {
			return;
		}
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Export Clean C Code");
		String ext = (config != null && config.targetLanguage.contains("C++")) ? ".cpp" : ".c";
		String defaultName = (!outcome.functionName.isEmpty() ? outcome.functionName : function.getName()) + ext;
		chooser.setSelectedFile(new File(defaultName));
		int result = chooser.showSaveDialog(this);
		if (result == JFileChooser.APPROVE_OPTION) {
			File file = chooser.getSelectedFile();
			try {
				Files.writeString(file.toPath(), code, StandardCharsets.UTF_8);
				plugin.showInfo("Exported clean C code to:\n" + file.getAbsolutePath());
			}
			catch (Exception ex) {
				plugin.showError("Failed to save file: " + ex.getMessage(), ex);
			}
		}
	}

	private JPanel buildHeader() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));

		JPanel topRow = new JPanel(new BorderLayout());
		JLabel title = new JLabel(function.getName() + "   @ 0x" +
			function.getEntryPoint().toString().replace(" ", ""));
		title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize() + 2f));
		topRow.add(title, BorderLayout.WEST);

		JPanel headerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		JButton quickCopyBtn = new JButton("📋 Copy Clean C");
		quickCopyBtn.setToolTipText("Quickly copy clean handwritten C code to clipboard");
		quickCopyBtn.addActionListener(e -> copyCleanCodeToClipboard(quickCopyBtn));
		headerActions.add(quickCopyBtn);
		topRow.add(headerActions, BorderLayout.EAST);
		topRow.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(topRow);

		JLabel detail = new JLabel("Signature: " + context.signature + "  |  Model: " + outcome.getSourceInfo());
		detail.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(detail);

		if (!outcome.usageText.isEmpty()) {
			JLabel usage = new JLabel(outcome.usageText);
			usage.setAlignmentX(LEFT_ALIGNMENT);
			panel.add(usage);
		}
		if (!outcome.parsedFromJson) {
			JLabel warn = new JLabel(
				"WARNING: Response could not be parsed as JSON, showing raw text. " +
					"Try changing the model or temperature setting.");
			warn.setAlignmentX(LEFT_ALIGNMENT);
			panel.add(warn);
		}
		panel.add(Box.createVerticalStrut(4));
		return panel;
	}

	private String displayFunctionName() {
		return outcome.functionName.isEmpty() ? "(no suggestion)" : outcome.functionName;
	}

	private JPanel buildFooter() {
		JPanel outer = new JPanel(new BorderLayout());
		outer.setBorder(BorderFactory.createEmptyBorder(6, 10, 8, 10));

		JPanel checks = new JPanel(new GridLayout(0, 2));
		checks.add(renameFunctionBox);
		renameFunctionBox.setEnabled(!outcome.functionName.isEmpty());
		checks.add(functionCommentBox);
		functionCommentBox.setEnabled(true);
		outer.add(checks, BorderLayout.NORTH);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
		JButton applySelected = new JButton("Apply Selected to Ghidra");
		applySelected.addActionListener(e -> apply(true));
		JButton selectAll = new JButton("Select All");
		selectAll.addActionListener(e -> setAll(true));
		JButton selectNone = new JButton("Deselect All");
		selectNone.addActionListener(e -> setAll(false));
		JButton copyBtn = new JButton("📋 Copy Clean C");
		copyBtn.addActionListener(e -> copyCleanCodeToClipboard(copyBtn));
		JButton close = new JButton("Close");
		close.addActionListener(e -> dispose());

		buttons.add(applySelected);
		buttons.add(selectAll);
		buttons.add(selectNone);
		buttons.add(copyBtn);
		buttons.add(close);
		buttons.add(selectionLabel);

		outer.add(buttons, BorderLayout.SOUTH);
		return outer;
	}

	private JTextArea textArea(String text, boolean monospaced, boolean wrap) {
		JTextArea area = new JTextArea(text == null ? "" : text);
		area.setEditable(false);
		area.setLineWrap(wrap);
		area.setWrapStyleWord(wrap);
		area.setCaretPosition(0);
		if (monospaced) {
			area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, area.getFont().getSize() + 1));
		}
		return area;
	}

	private JScrollPane scrollable(java.awt.Component component) {
		return new JScrollPane(component);
	}

	private DefaultTableModel buildRenameModel() {
		DefaultTableModel model = new DefaultTableModel(new Object[] {
			"Apply", "Old Name", "New Name", "Type", "Confidence", "Reason"
		}, 0) {
			private static final long serialVersionUID = 1L;

			@Override
			public Class<?> getColumnClass(int column) {
				return column == 0 ? Boolean.class : String.class;
			}

			@Override
			public boolean isCellEditable(int row, int column) {
				return column == 0;
			}
		};
		for (AnalysisOutcome.VarRename rename : outcome.varRenames) {
			String reason = rename.reason;
			if (!rename.note.isEmpty()) {
				reason = "[" + rename.note + "] " + reason;
			}
			model.addRow(new Object[] { rename.apply, rename.oldName, rename.newName,
				rename.type, formatConfidence(rename.confidence), reason });
		}
		model.addTableModelListener(this::onTableChanged);
		return model;
	}

	private DefaultTableModel buildCommentModel() {
		DefaultTableModel model = new DefaultTableModel(new Object[] {
			"Apply", "Address", "Comment", "Confidence"
		}, 0) {
			private static final long serialVersionUID = 1L;

			@Override
			public Class<?> getColumnClass(int column) {
				return column == 0 ? Boolean.class : String.class;
			}

			@Override
			public boolean isCellEditable(int row, int column) {
				return column == 0;
			}
		};
		for (AnalysisOutcome.LineComment comment : outcome.lineComments) {
			String text = comment.comment;
			if (!comment.note.isEmpty()) {
				text = "[" + comment.note + "] " + text;
			}
			model.addRow(new Object[] { comment.apply, comment.rawAddress, text,
				formatConfidence(comment.confidence) });
		}
		model.addTableModelListener(this::onTableChanged);
		return model;
	}

	private JTable buildTable(DefaultTableModel model) {
		JTable table = new JTable(model);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
		table.getTableHeader().setReorderingAllowed(false);
		table.setRowHeight(Math.max(20, table.getRowHeight()));
		if (model.getColumnCount() > 0) {
			table.getColumnModel().getColumn(0).setMaxWidth(70);
		}
		return table;
	}

	private void onTableChanged(TableModelEvent event) {
		if (event.getType() == TableModelEvent.UPDATE) {
			syncFromTables();
			updateSelectionLabel();
		}
	}

	/** Synchronizes table checkboxes back to outcome items. */
	private void syncFromTables() {
		for (int row = 0; row < renameModel.getRowCount() && row < outcome.varRenames.size();
				row++) {
			Object value = renameModel.getValueAt(row, 0);
			outcome.varRenames.get(row).apply = Boolean.TRUE.equals(value);
		}
		for (int row = 0; row < commentModel.getRowCount() &&
			row < outcome.lineComments.size(); row++) {
			Object value = commentModel.getValueAt(row, 0);
			outcome.lineComments.get(row).apply = Boolean.TRUE.equals(value);
		}
	}

	private void setAll(boolean selected) {
		for (int row = 0; row < renameModel.getRowCount(); row++) {
			renameModel.setValueAt(Boolean.valueOf(selected), row, 0);
		}
		for (int row = 0; row < commentModel.getRowCount(); row++) {
			commentModel.setValueAt(Boolean.valueOf(selected), row, 0);
		}
		syncFromTables();
		updateSelectionLabel();
	}

	private void updateSelectionLabel() {
		int renames = outcome.selectedRenames().size();
		int comments = outcome.selectedComments().size();
		selectionLabel.setText("   " + renames + " variables, " + comments + " comments selected");
	}

	private static String formatConfidence(double confidence) {
		return String.format("%.2f", confidence);
	}

	private String hardPartsText() {
		if (outcome.hardParts.isEmpty()) {
			return "(Model reported no complex sections.)";
		}
		StringBuilder sb = new StringBuilder();
		for (AnalysisOutcome.HardPart part : outcome.hardParts) {
			sb.append("* ").append(part.address.isEmpty() ? "-" : part.address).append('\n');
			if (!part.explanation.isEmpty()) {
				sb.append("  Reason: ").append(part.explanation).append('\n');
			}
			if (!part.comment.isEmpty()) {
				sb.append("  Suggested comment: ").append(part.comment).append('\n');
			}
			sb.append('\n');
		}
		return sb.toString();
	}

	private String uncertaintiesText() {
		if (outcome.uncertainties.isEmpty()) {
			return "(Model reported no uncertainties.)";
		}
		StringBuilder sb = new StringBuilder();
		for (String item : outcome.uncertainties) {
			sb.append("* ").append(item).append('\n');
		}
		return sb.toString();
	}

	private void apply(boolean selectedOnly) {
		if (applying) {
			return;
		}
		syncFromTables();
		List<AnalysisOutcome.VarRename> renames = new ArrayList<>();
		List<AnalysisOutcome.LineComment> comments = new ArrayList<>();

		for (AnalysisOutcome.VarRename rename : outcome.varRenames) {
			if (!selectedOnly || rename.apply) {
				renames.add(rename);
			}
		}
		for (AnalysisOutcome.LineComment comment : outcome.lineComments) {
			if ((!selectedOnly || comment.apply) && comment.address != null) {
				comments.add(comment);
			}
		}

		boolean renameFn = renameFunctionBox.isSelected();
		boolean setComment = functionCommentBox.isSelected();
		if (renames.isEmpty() && comments.isEmpty() && !renameFn && !setComment) {
			plugin.showInfo("Nothing selected to apply.");
			return;
		}
		applying = true;
		plugin.applyOutcome(program, function, outcome, renames, comments, renameFn, setComment);
		applying = false;
	}
}
