/* ###
 * Ghidra AI Extension
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package deepseekai;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import ghidra.util.task.Task;
import ghidra.util.task.TaskLauncher;
import ghidra.util.task.TaskMonitor;

/**
 * Multi-Provider Configuration Dialog for Ghidra AI Extension.
 * <p>
 * Provides interactive switching between major AI providers (OpenAI, Anthropic Claude,
 * Google Gemini, DeepSeek, Ollama, Groq, OpenRouter, Mistral, xAI, Custom),
 * manages per-provider credentials, supports dynamic model listing, and connection testing.
 */
public class DeepSeekOptionsDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	private final DeepSeekAIPlugin plugin;
	private final DeepSeekConfig config;

	// Provider selection
	private final JComboBox<AiProvider> providerCombo;
	private final JLabel providerDescLabel;

	// Credentials & Endpoints
	private final JPasswordField apiKeyField;
	private final JToggleButton showKeyBtn;
	private final JButton getKeyBtn;
	private final JLabel keySourceLabel;
	private final JComboBox<String> baseUrlField;
	private final JComboBox<String> modelField;
	private final JButton fetchModelsBtn;

	// Parameters
	private final JSpinner temperatureSpinner;
	private final JSpinner maxTokensSpinner;
	private final JSpinner timeoutSpinner;
	private final JSpinner maxCharsSpinner;
	private final JComboBox<String> languageField;
	private final JComboBox<String> targetLanguageField;

	// Auto-apply options
	private final JCheckBox autoComments;
	private final JCheckBox autoRenames;
	private final JCheckBox autoFunctionName;

	// Status feedback
	private final JLabel statusLabel;

	// Temporary in-memory cache for dialog session to avoid losing edits when switching providers
	private AiProvider currentSelectedProvider;

	public DeepSeekOptionsDialog(DeepSeekAIPlugin plugin, DeepSeekConfig config) {
		super();
		this.plugin = plugin;
		this.config = config;
		this.currentSelectedProvider = config.provider;

		setTitle("AI Code Assistant - Settings (Multi-Provider)");
		setModal(true);
		setLayout(new BorderLayout(8, 8));

		// 1) Provider combo
		providerCombo = new JComboBox<>(AiProvider.values());
		providerCombo.setSelectedItem(config.provider);
		providerDescLabel = new JLabel(" ");
		providerDescLabel.setForeground(new Color(90, 90, 90));
		providerDescLabel.setFont(providerDescLabel.getFont().deriveFont(Font.ITALIC, 11.5f));

		// 2) API key & helpers
		apiKeyField = new JPasswordField(config.apiKey == null ? "" : config.apiKey, 36);
		apiKeyField.setEchoChar('*');
		showKeyBtn = new JToggleButton("Show");
		showKeyBtn.setToolTipText("Toggle API key visibility");
		showKeyBtn.addActionListener(e -> {
			if (showKeyBtn.isSelected()) {
				apiKeyField.setEchoChar((char) 0);
				showKeyBtn.setText("Hide");
			}
			else {
				apiKeyField.setEchoChar('*');
				showKeyBtn.setText("Show");
			}
		});

		getKeyBtn = new JButton("Get Key ↗");
		getKeyBtn.setToolTipText("Open provider website to obtain an API key");
		getKeyBtn.addActionListener(e -> openProviderKeyUrl());

		keySourceLabel = new JLabel(" ");
		keySourceLabel.setForeground(new Color(110, 110, 110));
		keySourceLabel.setFont(keySourceLabel.getFont().deriveFont(11f));

		// 3) Base URL & Model
		baseUrlField = new JComboBox<>();
		baseUrlField.setEditable(true);

		modelField = new JComboBox<>();
		modelField.setEditable(true);

		fetchModelsBtn = new JButton("Fetch Models 🔄");
		fetchModelsBtn.setToolTipText("Query provider endpoint to fetch installed or available models");
		fetchModelsBtn.addActionListener(e -> fetchOnlineModels());

		// 4) Parameters
		temperatureSpinner = new JSpinner(new SpinnerNumberModel(config.temperature, 0.0, 2.0, 0.1));
		maxTokensSpinner = new JSpinner(new SpinnerNumberModel(config.maxTokens, 256, 131072, 512));
		timeoutSpinner = new JSpinner(new SpinnerNumberModel(config.timeoutSeconds, 10, 1800, 10));
		maxCharsSpinner = new JSpinner(new SpinnerNumberModel(config.maxCodeChars, 2000, 400000, 2000));

		languageField = new JComboBox<>(new String[] {
			"English", "Turkish", "German", "French", "Chinese", "Spanish", "Japanese", "Korean", "Russian", "Italian", "Portuguese"
		});
		languageField.setEditable(true);
		languageField.setSelectedItem(config.language);

		targetLanguageField = new JComboBox<>(new String[] {
			"C / C++ (Idiomatic Auto)",
			"C (Modern C99 / C11)",
			"C++ (Modern C++17 / C++20 with Classes & RAII)"
		});
		targetLanguageField.setEditable(true);
		targetLanguageField.setSelectedItem(config.targetLanguage);

		autoComments = new JCheckBox("Auto-apply comments without prompting", config.autoApplyComments);
		autoRenames = new JCheckBox("Auto-apply variable renames without prompting", config.autoApplyRenames);
		autoFunctionName = new JCheckBox("Auto-apply function name without prompting", config.autoApplyFunctionName);

		statusLabel = new JLabel(" ");
		statusLabel.setForeground(Color.GRAY);

		// Synchronize UI for current provider
		syncUiToProvider(config.provider, false);

		// Hook provider selection listener
		providerCombo.addActionListener(e -> {
			AiProvider selected = (AiProvider) providerCombo.getSelectedItem();
			if (selected != null && selected != currentSelectedProvider) {
				// Store current input in map before switching
				saveCurrentFieldsToProvider(currentSelectedProvider);
				currentSelectedProvider = selected;
				syncUiToProvider(selected, true);
			}
		});

		// Build Layout
		JPanel mainPanel = new JPanel();
		mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
		mainPanel.setBorder(BorderFactory.createEmptyBorder(12, 14, 8, 14));

		mainPanel.add(buildProviderPanel());
		mainPanel.add(Box.createVerticalStrut(8));
		mainPanel.add(buildConfigTabs());
		mainPanel.add(Box.createVerticalStrut(6));

		add(mainPanel, BorderLayout.CENTER);
		add(buildButtons(), BorderLayout.SOUTH);

		pack();
		setMinimumSize(new Dimension(640, 520));
		setLocationRelativeTo(null);
	}

	private JPanel buildProviderPanel() {
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBorder(BorderFactory.createTitledBorder("AI Provider Selection"));
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(4, 6, 4, 6);
		c.anchor = GridBagConstraints.WEST;

		c.gridx = 0;
		c.gridy = 0;
		c.fill = GridBagConstraints.NONE;
		JLabel pLabel = new JLabel("Active Provider:");
		pLabel.setFont(pLabel.getFont().deriveFont(Font.BOLD));
		panel.add(pLabel, c);

		c.gridx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0;
		panel.add(providerCombo, c);

		c.gridx = 1;
		c.gridy = 1;
		panel.add(providerDescLabel, c);

		return panel;
	}

	private JTabbedPane buildConfigTabs() {
		JTabbedPane tabs = new JTabbedPane();

		// Tab 1: Connection & Credentials
		JPanel connPanel = new JPanel(new GridBagLayout());
		connPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(5, 5, 5, 5);
		c.anchor = GridBagConstraints.WEST;

		int row = 0;

		// API Key Row
		c.gridx = 0;
		c.gridy = row;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0.0;
		JLabel keyLabel = new JLabel("API Key:");
		connPanel.add(keyLabel, c);

		JPanel keyContainer = new JPanel(new BorderLayout(4, 0));
		keyContainer.add(apiKeyField, BorderLayout.CENTER);

		JPanel keyBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		keyBtns.add(showKeyBtn);
		keyBtns.add(getKeyBtn);
		keyContainer.add(keyBtns, BorderLayout.EAST);

		c.gridx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0;
		connPanel.add(keyContainer, c);

		// Key Source Hint Row
		row++;
		c.gridx = 1;
		c.gridy = row;
		connPanel.add(keySourceLabel, c);

		// Base URL Row
		row++;
		c.gridx = 0;
		c.gridy = row;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0.0;
		connPanel.add(new JLabel("Base URL:"), c);

		c.gridx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0;
		connPanel.add(baseUrlField, c);

		// Model Row
		row++;
		c.gridx = 0;
		c.gridy = row;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0.0;
		connPanel.add(new JLabel("Model Name:"), c);

		JPanel modelContainer = new JPanel(new BorderLayout(4, 0));
		modelContainer.add(modelField, BorderLayout.CENTER);
		modelContainer.add(fetchModelsBtn, BorderLayout.EAST);

		c.gridx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0;
		connPanel.add(modelContainer, c);

		// Response Language Row
		row++;
		c.gridx = 0;
		c.gridy = row;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0.0;
		connPanel.add(new JLabel("Language:"), c);

		c.gridx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0;
		connPanel.add(languageField, c);

		c.gridx = 0;
		c.gridy = row++;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
		connPanel.add(new JLabel("Code Language:"), c);

		c.gridx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0;
		connPanel.add(targetLanguageField, c);

		tabs.addTab("Connection & Model", connPanel);

		// Tab 2: Tuning & Limits
		JPanel tuningPanel = new JPanel(new GridBagLayout());
		tuningPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		c = new GridBagConstraints();
		c.insets = new Insets(5, 5, 5, 5);
		c.anchor = GridBagConstraints.WEST;

		row = 0;
		addParamRow(tuningPanel, c, row++, "Temperature:", temperatureSpinner,
			"0.0 = deterministic and factual, 1.0+ = creative. Note: o1/o3 reasoning models use fixed temperature.");
		addParamRow(tuningPanel, c, row++, "Max Output Tokens:", maxTokensSpinner,
			"Maximum response tokens generated by the model. Increase for large functions.");
		addParamRow(tuningPanel, c, row++, "Request Timeout (s):", timeoutSpinner,
			"Timeout in seconds for API completion requests.");
		addParamRow(tuningPanel, c, row++, "Max Code Chars:", maxCharsSpinner,
			"Maximum characters of decompiled pseudocode sent in a prompt.");

		tabs.addTab("Generation Limits", tuningPanel);

		// Tab 3: Automation Options
		JPanel autoPanel = new JPanel();
		autoPanel.setLayout(new BoxLayout(autoPanel, BoxLayout.Y_AXIS));
		autoPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
		autoPanel.add(autoComments);
		autoPanel.add(Box.createVerticalStrut(6));
		autoPanel.add(autoRenames);
		autoPanel.add(Box.createVerticalStrut(6));
		autoPanel.add(autoFunctionName);
		autoPanel.add(Box.createVerticalGlue());

		tabs.addTab("Automation", autoPanel);

		return tabs;
	}

	private void addParamRow(JPanel panel, GridBagConstraints c, int row, String label,
			javax.swing.JComponent comp, String tip) {
		c.gridx = 0;
		c.gridy = row;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0.0;
		JLabel lbl = new JLabel(label);
		panel.add(lbl, c);

		c.gridx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0;
		panel.add(comp, c);
		if (!tip.isEmpty()) {
			comp.setToolTipText(tip);
			lbl.setToolTipText(tip);
		}
	}

	private JPanel buildButtons() {
		JPanel bottomPanel = new JPanel(new BorderLayout());
		bottomPanel.setBorder(BorderFactory.createEmptyBorder(4, 14, 10, 14));

		JPanel statusSub = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
		statusSub.add(statusLabel);
		bottomPanel.add(statusSub, BorderLayout.NORTH);

		JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));

		JButton testBtn = new JButton("Test Connection");
		testBtn.addActionListener(e -> testConnection());

		JButton saveBtn = new JButton("Save & Apply");
		saveBtn.setFont(saveBtn.getFont().deriveFont(Font.BOLD));
		saveBtn.addActionListener(e -> {
			save();
			dispose();
		});

		JButton cancelBtn = new JButton("Cancel");
		cancelBtn.addActionListener(e -> dispose());

		btns.add(testBtn);
		btns.add(saveBtn);
		btns.add(cancelBtn);

		bottomPanel.add(btns, BorderLayout.SOUTH);
		return bottomPanel;
	}

	private void syncUiToProvider(AiProvider provider, boolean isSwitch) {
		providerDescLabel.setText(provider.getDescription());

		// Base URL presets
		List<String> urlPresets = new ArrayList<>();
		urlPresets.add(provider.getDefaultBaseUrl());
		if (provider == AiProvider.DEEPSEEK) {
			urlPresets.add("https://api.deepseek.com/v1");
		}
		else if (provider == AiProvider.GEMINI) {
			urlPresets.add("https://generativelanguage.googleapis.com/v1beta/openai");
			urlPresets.add("https://generativelanguage.googleapis.com/v1beta");
		}
		else if (provider == AiProvider.OLLAMA) {
			urlPresets.add("http://localhost:11434/v1");
			urlPresets.add("http://127.0.0.1:11434/v1");
		}
		else if (provider == AiProvider.OPENAI) {
			urlPresets.add("https://api.openai.com/v1");
		}

		String savedUrl = config.providerUrls.getOrDefault(provider, provider.getDefaultBaseUrl());
		if (!urlPresets.contains(savedUrl)) {
			urlPresets.add(0, savedUrl);
		}
		baseUrlField.setModel(new DefaultComboBoxModel<>(urlPresets.toArray(new String[0])));
		baseUrlField.setSelectedItem(savedUrl);

		// Models presets
		List<String> models = new ArrayList<>(provider.getRecommendedModels());
		String savedModel = config.providerModels.getOrDefault(provider, provider.getDefaultModel());
		if (!models.contains(savedModel)) {
			models.add(0, savedModel);
		}
		modelField.setModel(new DefaultComboBoxModel<>(models.toArray(new String[0])));
		modelField.setSelectedItem(savedModel);

		// API Key
		String key = config.providerKeys.getOrDefault(provider, "");
		if (isSwitch && DeepSeekConfig.isBlank(key)) {
			// Try checking env var for this provider
			for (String env : provider.getEnvVarNames()) {
				String ev = System.getenv(env);
				if (!DeepSeekConfig.isBlank(ev)) {
					key = ev.trim();
					break;
				}
			}
			if (DeepSeekConfig.isBlank(key)) {
				String fromFile = DeepSeekConfig.readKeyFromFile(provider);
				if (!DeepSeekConfig.isBlank(fromFile)) {
					key = fromFile;
				}
			}
		}
		apiKeyField.setText(key == null ? "" : key);

		// Hint / help text
		updateKeyStatusHint(provider);

		// Enable or disable dynamic model fetch
		fetchModelsBtn.setEnabled(provider.supportsDynamicModelList());
	}

	private void updateKeyStatusHint(AiProvider provider) {
		if (!provider.requiresApiKey()) {
			keySourceLabel.setText("No API key required for local offline inference.");
			getKeyBtn.setEnabled(false);
			return;
		}
		getKeyBtn.setEnabled(true);
		String envList = String.join(", ", provider.getEnvVarNames());
		keySourceLabel.setText("Env: " + envList + " | File: ~/.ghidra_ai.properties");
	}

	private void saveCurrentFieldsToProvider(AiProvider provider) {
		if (provider == null) {
			return;
		}
		String key = new String(apiKeyField.getPassword()).trim();
		config.providerKeys.put(provider, key);

		Object base = baseUrlField.getSelectedItem();
		String url = base == null ? provider.getDefaultBaseUrl() : base.toString().trim();
		config.providerUrls.put(provider, url);

		Object model = modelField.getSelectedItem();
		String m = model == null ? provider.getDefaultModel() : model.toString().trim();
		config.providerModels.put(provider, m);
	}

	private void openProviderKeyUrl() {
		AiProvider p = (AiProvider) providerCombo.getSelectedItem();
		if (p == null || !p.requiresApiKey()) {
			return;
		}
		try {
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
				Desktop.getDesktop().browse(new URI(p.getApiKeyUrl()));
			}
			else {
				JOptionPane.showMessageDialog(this, "Please open: " + p.getApiKeyUrl(),
					"API Key Console", JOptionPane.INFORMATION_MESSAGE);
			}
		}
		catch (Throwable t) {
			JOptionPane.showMessageDialog(this, "Could not open browser: " + t.getMessage(),
				"Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void fetchOnlineModels() {
		AiProvider p = (AiProvider) providerCombo.getSelectedItem();
		if (p == null) {
			return;
		}
		DeepSeekConfig probe = createProbeConfig();
		statusLabel.setForeground(Color.BLUE);
		statusLabel.setText("Fetching models from " + probe.baseUrl + "...");
		fetchModelsBtn.setEnabled(false);

		new TaskLauncher(new Task("Fetching AI Models", false, false, true) {
			@Override
			public void run(TaskMonitor monitor) {
				try {
					List<String> fetched = new DeepSeekClient().fetchModels(probe, monitor);
					SwingUtilities.invokeLater(() -> {
						fetchModelsBtn.setEnabled(true);
						if (fetched.isEmpty()) {
							statusLabel.setForeground(Color.RED);
							statusLabel.setText("No models returned by provider endpoint.");
							return;
						}
						// Merge with recommended and sort
						List<String> updated = new ArrayList<>(fetched);
						for (String rec : p.getRecommendedModels()) {
							if (!updated.contains(rec)) {
								updated.add(rec);
							}
						}
						Collections.sort(updated);
						String currentSelected = String.valueOf(modelField.getSelectedItem());
						modelField.setModel(new DefaultComboBoxModel<>(updated.toArray(new String[0])));
						modelField.setSelectedItem(currentSelected);
						statusLabel.setForeground(new Color(0, 128, 0));
						statusLabel.setText("Fetched " + fetched.size() + " models successfully!");
					});
				}
				catch (Throwable t) {
					SwingUtilities.invokeLater(() -> {
						fetchModelsBtn.setEnabled(true);
						statusLabel.setForeground(Color.RED);
						statusLabel.setText("Fetch models failed: " + t.getMessage());
					});
				}
			}
		}, null);
	}

	private DeepSeekConfig createProbeConfig() {
		DeepSeekConfig probe = new DeepSeekConfig();
		AiProvider p = (AiProvider) providerCombo.getSelectedItem();
		probe.provider = p == null ? AiProvider.DEEPSEEK : p;
		probe.apiKey = new String(apiKeyField.getPassword()).trim();
		Object base = baseUrlField.getSelectedItem();
		probe.baseUrl = base == null ? probe.provider.getDefaultBaseUrl() : base.toString().trim();
		Object model = modelField.getSelectedItem();
		probe.model = model == null ? probe.provider.getDefaultModel() : model.toString().trim();
		probe.temperature = 0.0;
		probe.maxTokens = 32;
		probe.timeoutSeconds = Math.max(30, ((Number) timeoutSpinner.getValue()).intValue());
		return probe;
	}

	private void save() {
		AiProvider activeProvider = (AiProvider) providerCombo.getSelectedItem();
		if (activeProvider == null) {
			activeProvider = AiProvider.DEEPSEEK;
		}

		saveCurrentFieldsToProvider(activeProvider);

		config.provider = activeProvider;
		config.apiKey = new String(apiKeyField.getPassword()).trim();
		Object base = baseUrlField.getSelectedItem();
		config.baseUrl = base == null ? activeProvider.getDefaultBaseUrl() : base.toString().trim();
		Object model = modelField.getSelectedItem();
		config.model = model == null ? activeProvider.getDefaultModel() : model.toString().trim();

		config.temperature = ((Number) temperatureSpinner.getValue()).doubleValue();
		config.maxTokens = ((Number) maxTokensSpinner.getValue()).intValue();
		config.timeoutSeconds = ((Number) timeoutSpinner.getValue()).intValue();
		config.maxCodeChars = ((Number) maxCharsSpinner.getValue()).intValue();
		Object language = languageField.getSelectedItem();
		config.language = language == null ? DeepSeekConfig.DEFAULT_LANGUAGE : language.toString().trim();
		Object targetLang = targetLanguageField.getSelectedItem();
		config.targetLanguage = targetLang == null ? DeepSeekConfig.DEFAULT_TARGET_LANG : targetLang.toString().trim();
		config.autoApplyComments = autoComments.isSelected();
		config.autoApplyRenames = autoRenames.isSelected();
		config.autoApplyFunctionName = autoFunctionName.isSelected();
		config.apiKeyFromEnvironment = false;

		plugin.saveConfig(config);
	}

	private void testConnection() {
		DeepSeekConfig probe = createProbeConfig();

		if (probe.provider.requiresApiKey() && DeepSeekConfig.isBlank(probe.apiKey)) {
			statusLabel.setForeground(Color.RED);
			statusLabel.setText("API key is required for " + probe.provider.getDisplayName());
			return;
		}

		statusLabel.setForeground(Color.BLUE);
		statusLabel.setText("Connecting to " + probe.provider.getDisplayName() + " (" + probe.model + ")...");

		new TaskLauncher(new Task("AI Provider Connection Test", false, false, true) {
			@Override
			public void run(TaskMonitor monitor) {
				String message;
				Color color;
				long start = System.currentTimeMillis();
				try {
					DeepSeekClient.ChatResponse response = new DeepSeekClient().chat(
						"You are a test ping bot. Respond with only 'OK'.",
						"Ping", probe, monitor);
					long elapsed = System.currentTimeMillis() - start;
					String content = response.content.trim();
					message = "Connected to " + probe.provider.getDisplayName() + " in " + elapsed +
						"ms! Response: " + DeepSeekClient.abbreviate(content, 60);
					color = new Color(0, 128, 0);
				}
				catch (Throwable t) {
					message = "Connection failed: " + t.getMessage();
					color = Color.RED;
				}
				String finalMsg = message;
				Color finalClr = color;
				SwingUtilities.invokeLater(() -> {
					statusLabel.setForeground(finalClr);
					statusLabel.setText(DeepSeekClient.abbreviate(finalMsg, 140));
				});
			}
		}, null);
	}
}