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
 * Toplu (batch) AI analizi icin ayar penceresi.
 * <p>
 * Maliyet bilinciyle tasarlandi: kac fonksiyonun gonderilecegini ve tahmini
 * token sayisini gosterir; belirli bir sayinin uzerinde onay ister.
 */
public class BatchAiDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	/** Onay istemeden islenecek en fazla fonksiyon. */
	private static final int CONFIRM_THRESHOLD = 15;

	private final DeepSeekAIPlugin plugin;
	private final Program program;
	private final DeepSeekConfig config;

	private final JRadioButton scopeAll = new JRadioButton("Tum program", true);
	private final JRadioButton scopeSelection = new JRadioButton("Sadece secili aralik");
	private final JRadioButton scopeCurrent = new JRadioButton("Sadece imlecteki fonksiyon");

	private final JCheckBox onlyUndefined = new JCheckBox(
		"Sadece ismi cozulememis fonksiyonlar (FUN_*/sub_*)", true);
	private final JCheckBox skipThunks = new JCheckBox("Thunk ve external fonksiyonlari atla", true);
	private final JSpinner minBytes = new JSpinner(new SpinnerNumberModel(16, 0, 1000000, 8));
	private final JSpinner maxBytes =
		new JSpinner(new SpinnerNumberModel(24000, 0, 10000000, 1000));
	private final JSpinner maxFunctions =
		new JSpinner(new SpinnerNumberModel(0, 0, 1000000, 10));
	private final JSpinner delayMillis = new JSpinner(new SpinnerNumberModel(0, 0, 10000, 100));

	private final JCheckBox applyNames = new JCheckBox("Fonksiyon adlarini uygula", true);
	private final JCheckBox applyVars = new JCheckBox("Degisken adlarini uygula", true);
	private final JCheckBox applyComments = new JCheckBox("Yorumlari uygula", true);
	private final JCheckBox useCache = new JCheckBox("Onbellek kullan (ayni fonksiyonu tekrar gonderme)",
		true);

	private final JCheckBox writeC = new JCheckBox("Zenginlestirilmis .c dosyasi yaz", true);
	private final JTextField cFileField;

	private final JLabel estimateLabel = new JLabel(" ");

	public BatchAiDialog(DeepSeekAIPlugin plugin, Program program, DeepSeekConfig config) {
		super();
		this.plugin = plugin;
		this.program = program;
		this.config = config;

		setTitle("DeepSeek AI - Tum Fonksiyonlari Analiz Et");
		setModal(true);
		setLayout(new BorderLayout());

		cFileField = new JTextField(defaultOutputPath(), 40);
		cFileField.setEnabled(writeC.isSelected());
		writeC.addActionListener(e -> cFileField.setEnabled(writeC.isSelected()));

		if (plugin.getProgramSelection() == null || plugin.getProgramSelection().isEmpty()) {
			scopeSelection.setEnabled(false);
			scopeSelection.setToolTipText("Once Listing'de bir adres araligi secin.");
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
		panel.add(section("1) Hangi fonksiyonlar?"), at(c, 0, row++));
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
		panel.add(new JLabel("En az / en fazla boyut (bayt):"), c);
		c.gridx = 1;
		JPanel sizePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		sizePanel.add(minBytes);
		sizePanel.add(new JLabel("-"));
		sizePanel.add(maxBytes);
		panel.add(sizePanel, c);
		c.gridx = 0;
		c.gridy = ++row;
		panel.add(new JLabel("En fazla fonksiyon (0 = sinirsiz):"), c);
		c.gridx = 1;
		panel.add(maxFunctions, c);
		c.gridx = 0;
		c.gridy = ++row;
		panel.add(new JLabel("Istekler arasi bekleme (ms):"), c);
		c.gridx = 1;
		panel.add(delayMillis, c);

		c.gridx = 0;
		c.gridy = ++row;
		c.gridwidth = 3;
		panel.add(section("2) Ne uygulanacak?"), c);
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
		panel.add(section("3) Cikti"), c);
		c.gridwidth = 1;
		c.gridx = 0;
		c.gridy = ++row;
		c.gridwidth = 3;
		panel.add(writeC, c);
		c.gridwidth = 1;
		c.gridy = ++row;
		c.gridx = 0;
		panel.add(new JLabel("Dosya:"), c);
		c.gridx = 1;
		c.weightx = 1.0;
		panel.add(cFileField, c);
		c.gridx = 2;
		c.weightx = 0.0;
		JButton browse = new JButton("Gozat...");
		browse.addActionListener(e -> chooseFile());
		panel.add(browse, c);

		c.gridx = 0;
		c.gridy = ++row;
		c.gridwidth = 3;
		estimateLabel.setForeground(new Color(0, 90, 160));
		panel.add(estimateLabel, c);

		// Filtre degisince tahmini guncelle
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
		JButton start = new JButton("Analizi Baslat");
		start.addActionListener(e -> start());
		JButton cancel = new JButton("Iptal");
		cancel.addActionListener(e -> dispose());
		panel.add(start);
		panel.add(cancel);
		return panel;
	}

	private void chooseFile() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("C ciktisini kaydet");
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

	/** Programa ait fonksiyonlarin tamami (adres sirasina gore). */
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
				extra = "   |   UYARI: API anahtari tanimli degil!";
			}
			estimateLabel.setText("Secilen fonksiyon: " + count + "   |   tahmini ~" +
				String.format("%,d", approxTokens) + " token" + extra);
		}
		catch (Throwable t) {
			estimateLabel.setText("Secilen fonksiyon hesaplanamadi: " + t.getMessage());
		}
	}

	private void start() {
		List<Function> functions = selectedFunctions();
		if (functions.isEmpty()) {
			JOptionPane.showMessageDialog(this,
				"Filtrelere uyan fonksiyon bulunamadi.\n\n" +
					"Ipucu: 'Sadece ismi cozulememis fonksiyonlar' kutusunu kaldirip tekrar deneyin.",
				"DeepSeek AI", JOptionPane.WARNING_MESSAGE);
			return;
		}
		if (!config.hasApiKey()) {
			JOptionPane.showMessageDialog(this,
				"API anahtari tanimli degil. Once Ayarlar penceresinden girin.",
				"DeepSeek AI", JOptionPane.ERROR_MESSAGE);
			return;
		}
		if (functions.size() > CONFIRM_THRESHOLD) {
			long approxTokens = (long) functions.size() * 2500L;
			int answer = JOptionPane.showConfirmDialog(this,
				functions.size() + " fonksiyon DeepSeek'e gonderilecek.\n" +
					"Tahmini tuketim: ~" + String.format("%,d", approxTokens) + " token.\n" +
					"Bu islem ucretli olabilir ve uzun surebilir.\n\n" +
					"Devam edilsin mi?",
				"DeepSeek AI - Maliyet onayi", JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE);
			if (answer != JOptionPane.YES_OPTION) {
				return;
			}
		}
		dispose();
		plugin.startBatchAnalysis(program, buildOptions(), functions);
	}
}
