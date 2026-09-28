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
 * Dialog for editing API key, model selection, and extension preferences.
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

		setTitle("DeepSeek AI - Settings");
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

		languageField = new JComboBox<>(new String[] { "English", "Turkish", "German", "French", "Chinese", "Spanish" });
		languageField.setEditable(true);
		languageField.setSelectedItem(config.language);

		autoComments = new JCheckBox("Auto-apply comments without prompting", config.autoApplyComments);
		autoRenames = new JCheckBox("Auto-apply variable renames without prompting", config.autoApplyRenames);
		autoFunctionName =
			new JCheckBox("Auto-apply function name without prompting", config.autoApplyFunctionName);

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
		addRow(panel, c, row++, "API Key (sk-...)", apiKeyField,
			"Obtain from https://platform.deepseek.com. Environment variable DEEPSEEK_API_KEY can also be used.");
		addRow(panel, c, row++, "Base URL", baseUrlField,
			"Default: https://api.deepseek.com");
		addRow(panel, c, row++, "Model", modelField, "deepseek-chat (fast) / deepseek-reasoner (deep reasoning)");
		addRow(panel, c, row++, "Temperature", temperatureSpinner,
			"Lower values produce more deterministic and reproducible results.");
		addRow(panel, c, row++, "Max Tokens", maxTokensSpinner,
			"Can be increased for long functions.");
		addRow(panel, c, row++, "Timeout (sec)", timeoutSpinner, "");
		addRow(panel, c, row++, "Max Code Characters", maxCharsSpinner,
			"Upper limit for decompiled code characters sent to the API.");
		addRow(panel, c, row++, "Response Language", languageField, "");

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

		JButton test = new JButton("Test Connection");
		test.addActionListener(e -> testConnection());
		JButton save = new JButton("Save");
		save.addActionListener(e -> {
			save();
			dispose();
		});
		JButton cancel = new JButton("Cancel");
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
			statusLabel.setText("API key is empty.");
			return;
		}

		statusLabel.setForeground(Color.GRAY);
		statusLabel.setText("Testing connection...");
		new TaskLauncher(new ConnectionTestTask(probe, statusLabel), null);
	}

	/** Validates API key and connectivity with a minimal prompt. */
	private static class ConnectionTestTask extends Task {

		private final DeepSeekConfig config;
		private final JLabel statusLabel;

		ConnectionTestTask(DeepSeekConfig config, JLabel statusLabel) {
			super("DeepSeek AI: Connection Test", false, false, true);
			this.config = config;
			this.statusLabel = statusLabel;
		}

		@Override
		public void run(TaskMonitor monitor) {
			String message;
			Color color;
			try {
				DeepSeekClient.ChatResponse response = new DeepSeekClient().chat(
					"Give a short answer.", "Hello, reply with only 'ok'.", config, monitor);
				String content = response.content.trim();
				message = "Connection successful. Response: " +
					DeepSeekClient.abbreviate(content, 60);
				color = new Color(0, 128, 0);
			}
			catch (Throwable t) {
				message = "Connection failed: " + t.getMessage();
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
