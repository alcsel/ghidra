/* ###
 * Ghidra AI Extension
 *
 * Diagnostic script: investigates why DeepSeekAIPlugin is or is not visible in the GUI.
 *
 * In Ghidra : Window > Script Manager > CheckPluginRegistration > Run
 * Headless  : analyzeHeadless ... -postScript CheckPluginRegistration.java
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
import java.util.List;

import ghidra.app.script.GhidraScript;
import ghidra.framework.plugintool.Plugin;
import ghidra.framework.plugintool.PluginTool;
import ghidra.framework.plugintool.util.PluginPackage;
import ghidra.util.classfinder.ClassSearcher;

public class CheckPluginRegistration extends GhidraScript {

	@Override
	protected void run() throws Exception {
		println("=== AI Assistant Plugin Registration Diagnostic ===");

		// 1) Active tool inspection
		PluginTool activeTool = (PluginTool) state.getTool();
		println("Active tool: " + (activeTool == null ? "(headless / no active tool)" : activeTool.getName()));

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
				println("  AI Assistant Plugin is NOT loaded in this tool yet.");
				println("  To enable: File > Configure > Configure New Plugins > Check DeepSeek AI.");
			}
		}

		// 3) ClassSearcher discovery
		println("");
		println("--- ClassSearcher Plugin Discovery ---");
		List<Class<? extends Plugin>> classes = ClassSearcher.getClasses(Plugin.class);
		boolean foundInSearcher = false;
		for (Class<? extends Plugin> c : classes) {
			if (c.getName().startsWith("deepseekai")) {
				println("  [FOUND] " + c.getName());
				foundInSearcher = true;
			}
		}
		if (!foundInSearcher) {
			println("  [WARNING] Plugin class not found by ClassSearcher!");
		}

		// 4) PluginPackage check
		println("");
		println("--- PluginPackage Check ---");
		try {
			PluginPackage core = PluginPackage.getPluginPackage("Ghidra Core");
			println("  Ghidra Core package: " + (core == null ? "NULL" : core.getName()));
		}
		catch (Throwable t) {
			println("  PluginPackage check note: " + t.getMessage());
		}

		println("");
		println("Diagnostic completed.");
	}
}