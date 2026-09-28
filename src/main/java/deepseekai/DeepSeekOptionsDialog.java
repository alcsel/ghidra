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

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import ghidra.util.task.Task;
import ghidra.util.task.TaskLauncher;
import ghidra.util.task.TaskMonitor;

/**
 * API anahtari ve model ayarlarini duzenleme penceresi.
 */
public class DeepSeekOptionsDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	private final DeepSeekAIPlugin plugin;
	private final DeepSeekConfig config;

	private final JPasswordField apiKeyField;
	private final JComboBox<String> baseUrlField;
	private final JComboBox<String> modelField;
	private final JSpinner temperatureSpinner;
	private final JSpinner maxTokensSpinner;
	private final JSpinner timeoutSpinner;
	private final JSpinner maxCharsSpinner;
	private final JComboBox<String> languageField;
	private final JCheckBox autoComments;
	private final JCheckBox autoRenames;
	private final JCheckBox autoFunctionName;
	private final JLabel statusLabel;

	public DeepSeekOptionsDialog(DeepSeekAIPlugin plugin, DeepSeekConfig config) {
		super();
		this.plugin = plugin;
		this.config = config;

		setTitle("DeepSeek AI - Ayarlar");
		setModal(true);
		setLayout(new BorderLayout());

		apiKeyField = new JPasswordField(config.apiKey == null ? "" : config.apiKey, 44);
		apiKeyField.setEchoChar('*');

		baseUrlField = new JComboBox<>(new String[] { DeepSeekConfig.DEFAULT_BASE_URL,
			"https://api.deepseek.com/v1", "http://localhost:11434/v1" });
		baseUrlField.setEditable(true);
		baseUrlField.setSelectedItem(config.baseUrl);

		modelField = new JComboBox<>(new String[] { "deepseek-chat", "deepseek-reasoner" });
		modelField.setEditable(true);
		modelField.setSelectedItem(config.model);

		temperatureSpinner = new JSpinner(
			new SpinnerNumberModel(config.temperature, 0.0, 2.0, 0.1));
		maxTokensSpinner = new JSpinner(
			new SpinnerNumberModel(config.maxTokens, 256, 131072, 512));
		timeoutSpinner = new JSpinner(
			new SpinnerNumberModel(config.timeoutSeconds, 20, 1800, 10));
		maxCharsSpinner = new JSpinner(
			new SpinnerNumberModel(config.maxCodeChars, 2000, 400000, 2000));

		languageField = new JComboBox<>(new String[] { "Turkce", "English", "Deutsch", "Francais" });
		languageField.setEditable(true);
		languageField.setSelectedItem(config.language);

		autoComments = new JCheckBox("Yorumlari sormadan uygula", config.autoApplyComments);
		autoRenames = new JCheckBox("Degisken adlarini sormadan uygula", config.autoApplyRenames);
		autoFunctionName =
			new JCheckBox("Fonksiyon adini sormadan uygula", config.autoApplyFunctionName);

		statusLabel = new JLabel(" ");
		statusLabel.setForeground(Color.GRAY);

		add(buildForm(), BorderLayout.CENTER);
		add(buildButtons(), BorderLayout.SOUTH);

		pack();
		setLocationRelativeTo(null);
	}

	private JPanel buildForm() {
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(4, 4, 4, 4);
		c.anchor = GridBagConstraints.WEST;

		int row = 0;
		addRow(panel, c, row++, "API anahtari (sk-...)", apiKeyField,
			"https://platform.deepseek.com adresinden alinir. Ortam degiskeni DEEPSEEK_API_KEY de kullanilabilir.");
		addRow(panel, c, row++, "API adresi", baseUrlField,
			"Varsayilan: https://api.deepseek.com");
		addRow(panel, c, row++, "Model", modelField, "deepseek-chat (hizli) / deepseek-reasoner (derin akil yurutme)");
		addRow(panel, c, row++, "Temperature", temperatureSpinner,
			"Dusuk deger daha kararli ve tekrarlanabilir sonuc verir.");
		addRow(panel, c, row++, "Maksimum token", maxTokensSpinner,
			"Uzun fonksiyonlar icin artirilabilir.");
		addRow(panel, c, row++, "Zaman asimi (sn)", timeoutSpinner, "");
		addRow(panel, c, row++, "Maksimum kod karakteri", maxCharsSpinner,
			"API'ye gonderilecek decompile ciktisi icin ust sinir.");
		addRow(panel, c, row++, "Yanit dili", languageField, "");

		c.gridx = 1;
		c.gridy = row++;
		c.gridwidth = 2;
		panel.add(autoComments, c);
		c.gridy = row++;
		panel.add(autoRenames, c);
		c.gridy = row++;
		panel.add(autoFunctionName, c);

		c.gridx = 1;
		c.gridy = row;
		c.gridwidth = 2;
		panel.add(statusLabel, c);

		return panel;
	}

	private void addRow(JPanel panel, GridBagConstraints c, int row, String label,
			javax.swing.JComponent field, String tooltip) {
		c.gridx = 0;
		c.gridy = row;
		c.gridwidth = 1;
		c.fill = GridBagConstraints.NONE;
		JLabel jLabel = new JLabel(label);
		panel.add(jLabel, c);

		c.gridx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0;
		panel.add(field, c);
		if (!tooltip.isEmpty()) {
			field.setToolTipText(tooltip);
		}
		c.weightx = 0.0;
	}

	private JPanel buildButtons() {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));

		JButton test = new JButton("Baglantiyi Test Et");
		test.addActionListener(e -> testConnection());
		JButton save = new JButton("Kaydet");
		save.addActionListener(e -> {
			save();
			dispose();
		});
		JButton cancel = new JButton("Iptal");
		cancel.addActionListener(e -> dispose());

		panel.add(test);
		panel.add(save);
		panel.add(cancel);
		return panel;
	}

	private void save() {
		config.apiKey = new String(apiKeyField.getPassword()).trim();
		Object base = baseUrlField.getSelectedItem();
		config.baseUrl = base == null ? DeepSeekConfig.DEFAULT_BASE_URL : base.toString().trim();
		Object model = modelField.getSelectedItem();
		config.model = model == null ? DeepSeekConfig.DEFAULT_MODEL : model.toString().trim();
		config.temperature = ((Number) temperatureSpinner.getValue()).doubleValue();
		config.maxTokens = ((Number) maxTokensSpinner.getValue()).intValue();
		config.timeoutSeconds = ((Number) timeoutSpinner.getValue()).intValue();
		config.maxCodeChars = ((Number) maxCharsSpinner.getValue()).intValue();
		Object language = languageField.getSelectedItem();
		config.language = language == null ? DeepSeekConfig.DEFAULT_LANGUAGE
				: language.toString().trim();
		config.autoApplyComments = autoComments.isSelected();
		config.autoApplyRenames = autoRenames.isSelected();
		config.autoApplyFunctionName = autoFunctionName.isSelected();
		config.apiKeyFromEnvironment = false;
		plugin.saveConfig(config);
	}

	private void testConnection() {
		DeepSeekConfig probe = new DeepSeekConfig();
		probe.apiKey = new String(apiKeyField.getPassword()).trim();
		Object base = baseUrlField.getSelectedItem();
		probe.baseUrl = base == null ? DeepSeekConfig.DEFAULT_BASE_URL : base.toString().trim();
		Object model = modelField.getSelectedItem();
		probe.model = model == null ? DeepSeekConfig.DEFAULT_MODEL : model.toString().trim();
		probe.temperature = 0.0;
		probe.maxTokens = 16;
		probe.timeoutSeconds = Math.max(30,
			((Number) timeoutSpinner.getValue()).intValue());

		if (DeepSeekConfig.isBlank(probe.apiKey)) {
			statusLabel.setForeground(Color.RED);
			statusLabel.setText("API anahtari bos.");
			return;
		}

		statusLabel.setForeground(Color.GRAY);
		statusLabel.setText("Baglanti test ediliyor...");
		new TaskLauncher(new ConnectionTestTask(probe, statusLabel), null);
	}

	/** Basit bir istek atarak API anahtarini ve adresi dogrular. */
	private static class ConnectionTestTask extends Task {

		private final DeepSeekConfig config;
		private final JLabel statusLabel;

		ConnectionTestTask(DeepSeekConfig config, JLabel statusLabel) {
			super("DeepSeek AI: baglanti testi", false, false, true);
			this.config = config;
			this.statusLabel = statusLabel;
		}

		@Override
		public void run(TaskMonitor monitor) {
			String message;
			Color color;
			try {
				DeepSeekClient.ChatResponse response = new DeepSeekClient().chat(
					"Kisa cevap ver.", "Merhaba, sadece 'ok' yaz.", config, monitor);
				String content = response.content.trim();
				message = "Baglanti basarili. Yanit: " +
					DeepSeekClient.abbreviate(content, 60);
				color = new Color(0, 128, 0);
			}
			catch (Throwable t) {
				message = "Baglanti basarisiz: " + t.getMessage();
				color = Color.RED;
			}
			String finalMessage = message;
			Color finalColor = color;
			SwingUtilities.invokeLater(() -> {
				statusLabel.setForeground(finalColor);
				statusLabel.setText(DeepSeekClient.abbreviate(finalMessage, 120));
			});
		}
	}
}
