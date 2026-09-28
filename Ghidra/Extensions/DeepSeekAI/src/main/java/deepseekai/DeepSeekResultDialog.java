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
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;

import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Program;

/**
 * Dialog displaying analysis suggestions returned by DeepSeek.
 * <p>
 * The user can review the summary, variable renames, line comments, complex blocks,
 * and select which modifications to apply before committing them to the program.
 */
public class DeepSeekResultDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	private final DeepSeekAIPlugin plugin;
	private final Program program;
	private final Function function;
	private final DecompiledContext context;
	private final AnalysisOutcome outcome;

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

		setTitle(function.getName() + " - DeepSeek AI Analysis");
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
		setSize(880, 600);
		setLocationRelativeTo(null);
	}

	private JTabbedPane buildTabs() {
		JTabbedPane tabs = new JTabbedPane();

		// 1. Summary
		tabs.addTab("Summary", scrollable(textArea(outcome.summary, false)));

		// 2. Variable Renames
		JTable renameTable = buildTable(renameModel);
		tabs.addTab("Variable Renames (" + outcome.varRenames.size() + ")",
			scrollable(renameTable));

		// 3. Line Comments
		JTable commentTable = buildTable(commentModel);
		tabs.addTab("Line Comments (" + outcome.lineComments.size() + ")",
			scrollable(commentTable));

		// 4. Complex Blocks
		tabs.addTab("Complex Blocks (" + outcome.hardParts.size() + ")",
			scrollable(textArea(hardPartsText(), false)));

		// 5. Uncertainties
		tabs.addTab("Uncertainties (" + outcome.uncertainties.size() + ")",
			scrollable(textArea(uncertaintiesText(), false)));

		// 6. Raw Response
		tabs.addTab("Raw Response", scrollable(textArea(outcome.rawResponse, true)));

		return tabs;
	}

	private JPanel buildHeader() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

		JLabel title = new JLabel(function.getName() + "   @ 0x" +
			function.getEntryPoint().toString().replace(" ", ""));
		title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize() + 2f));
		title.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(title);

		JLabel detail = new JLabel("Signature: " + context.signature);
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
		JButton applySelected = new JButton("Apply Selected");
		applySelected.addActionListener(e -> apply(true));
		JButton selectAll = new JButton("Select All");
		selectAll.addActionListener(e -> setAll(true));
		JButton selectNone = new JButton("Deselect All");
		selectNone.addActionListener(e -> setAll(false));
		JButton close = new JButton("Close");
		close.addActionListener(e -> dispose());

		buttons.add(applySelected);
		buttons.add(selectAll);
		buttons.add(selectNone);
		buttons.add(close);
		buttons.add(selectionLabel);

		outer.add(buttons, BorderLayout.SOUTH);
		return outer;
	}

	private JTextArea textArea(String text, boolean monospaced) {
		JTextArea area = new JTextArea(text == null ? "" : text);
		area.setEditable(false);
		area.setLineWrap(!monospaced);
		area.setWrapStyleWord(!monospaced);
		area.setCaretPosition(0);
		if (monospaced) {
			area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, area.getFont().getSize()));
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
