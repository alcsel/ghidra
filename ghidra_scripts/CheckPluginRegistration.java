/* ###
 * DeepSeek AI - Ghidra Extension
 *
 * Diagnostic script: investigates why DeepSeekAIPlugin is or is not visible in the GUI.
 *
 * Ghidra: Window > Script Manager > CheckPluginRegistration > Run
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
import java.lang.reflect.Constructor;
import java.util.List;

import ghidra.app.plugin.core.colorizer.ColorizingServicePlugin;
import ghidra.app.script.GhidraScript;
import ghidra.framework.model.Tool;
import ghidra.framework.plugintool.Plugin;
import ghidra.framework.plugintool.PluginConfigurationModel;
import ghidra.framework.plugintool.PluginDescription;
import ghidra.framework.plugintool.PluginTool;
import ghidra.framework.plugintool.dialog.DefaultPluginsConfiguration;
import ghidra.framework.plugintool.util.PluginPackage;
import ghidra.util.classfinder.ClassSearcher;

public class CheckPluginRegistration extends GhidraScript {

	@Override
	protected void run() throws Exception {
		println("=== DeepSeek AI Plugin Registration Diagnostic ===");

		// 1) Active tool inspection
		PluginTool activeTool = null;
		Tool[] tools = state.getTool().getToolFrame().getToolkit() != null ? null : null;
		activeTool = (PluginTool) state.getTool();
		println("Active tool: " + (activeTool == null ? "NULL" : activeTool.getName()));

		// 2) Active plugins in current tool
		if (activeTool != null) {
			println("");
			println("--- Plugins in active tool (" + activeTool.getManagedPlugins().size() + ") ---");
			boolean loaded = false;
			for (Plugin p : activeTool.getManagedPlugins()) {
				if (p.getClass().getName().startsWith("deepseekai")) {
					println("  [LOADED] " + p.getClass().getName());
					loaded = true;
				}
			}
			if (!loaded) {
				println("  DeepSeekAIPlugin is NOT loaded in this tool.");
			}
		}

		// 3) ClassSearcher discovery
		println("");
		println("--- ClassSearcher Plugin.class Search ---");
		List<Class<? extends Plugin>> classes = ClassSearcher.getClasses(Plugin.class);
		println("Total Plugin classes found: " + classes.size());
		boolean inClassSearcher = false;
		for (Class<? extends Plugin> c : classes) {
			if (c.getName().startsWith("deepseekai")) {
				println("  -> FOUND: " + c.getName());
				inClassSearcher = true;
			}
		}
		if (!inClassSearcher) {
			println("  NOT found in ClassSearcher! Jar might not be in an active extension.");
		}

		// 4) PluginPackage check
		println("");
		try {
			PluginPackage core = PluginPackage.getPluginPackage("Ghidra Core");
			println("Ghidra Core package: " +
				(core == null ? "NULL" : core.getName()));
		}
		catch (Throwable t) {
			println("getPluginPackage("Ghidra Core") exception: " + t);
		}

		// 5) Search through configuration used by Configure dialog
		println("");
		try {
			DefaultPluginsConfiguration config = new DefaultPluginsConfiguration();
			List<PluginPackage> packages = config.getPluginPackages();
			println("Package count in configuration: " + packages.size());
			boolean found = false;
			for (PluginPackage p : packages) {
				List<PluginDescription> list = config.getPluginDescriptions(p);
				for (PluginDescription d : list) {
					if (d.getPluginClass().getName().startsWith("deepseekai")) {
						println("  -> IN LIST: package=" + p.getName() + " name=" + d.getName());
						found = true;
					}
				}
				if ("Ghidra Core".equals(p.getName())) {
					println("  Plugins in Ghidra Core package: " + list.size());
				}
			}
			println(found ? "RESULT: Plugin IS in configuration list."
				: "RESULT: Plugin is NOT in configuration list!");
		}
		catch (Throwable t) {
			println("Configuration check failed: " + t);
		}

		// 6) Tool configuration (GhidraPluginsConfiguration - package-private)
		println("");
		try {
			Class<?> cls =
				Class.forName("ghidra.framework.project.tool.GhidraPluginsConfiguration");
			Constructor<?> ctor = cls.getDeclaredConstructor();
			ctor.setAccessible(true);
			Object toolConfig = ctor.newInstance();
			java.lang.reflect.Method getPackages = cls.getMethod("getPluginPackages");
			@SuppressWarnings("unchecked")
			List<PluginPackage> toolPackages =
				(List<PluginPackage>) getPackages.invoke(toolConfig);
			println("Package count in tool configuration: " + toolPackages.size());
			java.lang.reflect.Method getDescs =
				cls.getMethod("getPluginDescriptions", PluginPackage.class);
			boolean found2 = false;
			for (PluginPackage p : toolPackages) {
				@SuppressWarnings("unchecked")
				List<PluginDescription> list =
					(List<PluginDescription>) getDescs.invoke(toolConfig, p);
				for (PluginDescription d : list) {
					if (d.getPluginClass().getName().startsWith("deepseekai")) {
						println("  -> IN TOOL LIST: package=" + p.getName());
						found2 = true;
					}
				}
			}
			println(found2 ? "RESULT: Present in tool configuration."
				: "RESULT: NOT present in tool configuration!");
		}
		catch (Throwable t) {
			println("Tool configuration check failed: " + t);
		}
	}
}
