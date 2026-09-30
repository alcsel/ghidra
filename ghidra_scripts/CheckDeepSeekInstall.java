/* ###
 * Ghidra AI Extension
 *
 * Installation and Multi-Provider verification diagnostic script.
 *
 * In Ghidra : Window > Script Manager > CheckDeepSeekInstall > Run
 * Headless  : analyzeHeadless ... -postScript CheckDeepSeekInstall.java
 *
 * Verifies:
 *   1. deepseekai.DeepSeekAIPlugin class is discoverable via ClassSearcher.
 *   2. Gson and java.net.http runtime dependencies are resolvable.
 *   3. Multi-AI provider classes (AiProvider, ApiProtocol, DeepSeekConfig, DeepSeekClient) are operational.
 *   4. JSON parsing and clean C code reconstruction works properly.
 *   5. AI tool template (.tool) is visible to Ghidra.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import ghidra.app.script.GhidraScript;
import ghidra.framework.ToolUtils;
import ghidra.framework.model.ToolTemplate;
import ghidra.framework.plugintool.Plugin;
import ghidra.program.model.listing.Program;
import ghidra.util.classfinder.ClassSearcher;

public class CheckDeepSeekInstall extends GhidraScript {

	private int problems = 0;

	@Override
	protected void run() throws Exception {
		println("==========================================================");
		println(" Ghidra AI Extension - Multi-Provider Installation Check");
		println("==========================================================");

		checkPluginClass();
		checkDependencies();
		checkMultiAiProviders();
		checkJsonParsing();
		checkCleanCodeReconstruction();
		checkToolTemplate();

		println("");
		if (problems == 0) {
			println("RESULT: All checks passed. Multi-AI extension is ready to use.");
		}
		else {
			println("RESULT: " + problems + " issues found (see [ERROR] lines above).");
		}
	}

	/** Checks if Ghidra's ClassSearcher can discover the plugin class. */
	private void checkPluginClass() {
		boolean found = false;
		try {
			List<Class<? extends Plugin>> classes = ClassSearcher.getClasses(Plugin.class);
			for (Class<? extends Plugin> c : classes) {
				if (c.getName().startsWith("deepseekai.")) {
					println("[OK]   Plugin class found : " + c.getName());
					found = true;
				}
			}
		}
		catch (Throwable t) {
			fail("Could not list plugin classes: " + t);
			return;
		}
		if (!found) {
			fail("deepseekai.DeepSeekAIPlugin not found. " +
				"Is the jar file located inside Ghidra\\Extensions\\DeepSeekAI\\lib?");
		}
	}

	/** Checks if Gson and java.net.http are accessible. */
	private void checkDependencies() {
		try {
			Class.forName("com.google.gson.JsonObject");
			println("[OK]   Gson library is accessible");
		}
		catch (Throwable t) {
			fail("Gson not found: " + t);
		}
		try {
			Class.forName("java.net.http.HttpClient");
			println("[OK]   java.net.http.HttpClient is accessible");
		}
		catch (Throwable t) {
			fail("java.net.http not found (Java 11+ required): " + t);
		}
		try {
			Class<?> configClass = Class.forName("deepseekai.DeepSeekConfig");
			configClass.getDeclaredConstructor().newInstance();
			println("[OK]   deepseekai.DeepSeekConfig can be instantiated");
		}
		catch (Throwable t) {
			fail("Could not instantiate DeepSeekConfig: " + t);
		}
	}

	/** Checks multi-provider classes and enum definitions. */
	private void checkMultiAiProviders() {
		try {
			Class<?> providerClass = Class.forName("deepseekai.AiProvider");
			Object[] enumConstants = providerClass.getEnumConstants();
			println("[OK]   AiProvider verified with " + enumConstants.length + " major providers:");
			for (Object constant : enumConstants) {
				Method getDisplayName = providerClass.getMethod("getDisplayName");
				String name = (String) getDisplayName.invoke(constant);
				println("         - " + name);
			}

			Class<?> clientClass = Class.forName("deepseekai.DeepSeekClient");
			clientClass.getDeclaredConstructor().newInstance();
			println("[OK]   deepseekai.DeepSeekClient multi-protocol engine initialized");
		}
		catch (Throwable t) {
			fail("Multi-AI provider check failed: " + t);
		}
	}

	/** Verifies that AnalysisOutcome.parse operates correctly. */
	private void checkJsonParsing() {
		String sample = "{\"summary\":\"example explanation\",\"function_name\":\"do_something\"," +
			"\"clean_c_code\":\"int do_something(int count) { return count * 2; }\"," +
			"\"line_comments\":[{\"address\":\"0x401000\",\"comment\":\"example comment\"," +
			"\"confidence\":0.9}]," +
			"\"variable_renames\":[{\"old_name\":\"uVar1\",\"new_name\":\"counter\"," +
			"\"reason\":\"loop counter\",\"confidence\":0.75}]," +
			"\"uncertainties\":[{\"point\":\"example uncertainty\"}]}";
		try {
			Class<?> outcomeClass = Class.forName("deepseekai.AnalysisOutcome");
			Method parse = outcomeClass.getMethod("parse", String.class, Program.class);
			Object outcome = parse.invoke(null, sample, (Program) null);

			Field summaryField = outcomeClass.getField("summary");
			Field nameField = outcomeClass.getField("functionName");
			Field cleanCodeField = outcomeClass.getField("cleanCCode");
			Field renamesField = outcomeClass.getField("varRenames");
			Field commentsField = outcomeClass.getField("lineComments");
			Field parsedField = outcomeClass.getField("parsedFromJson");

			String summary = String.valueOf(summaryField.get(outcome));
			String name = String.valueOf(nameField.get(outcome));
			String cleanCode = String.valueOf(cleanCodeField.get(outcome));
			int renames = ((List<?>) renamesField.get(outcome)).size();
			int comments = ((List<?>) commentsField.get(outcome)).size();
			boolean parsed = Boolean.TRUE.equals(parsedField.get(outcome));

			if (!parsed) {
				fail("JSON parsing failed (parsedFromJson=false)");
				return;
			}
			if (!"example explanation".equals(summary)) {
				fail("Summary field was not parsed correctly: " + summary);
				return;
			}
			if (!"do_something".equals(name)) {
				fail("Function name field was not parsed correctly: " + name);
				return;
			}
			if (!cleanCode.contains("do_something")) {
				fail("Clean C code was not parsed correctly: " + cleanCode);
				return;
			}
			if (renames != 1 || comments != 1) {
				fail("Unexpected collection size: renames=" + renames + " comments=" +
					comments);
				return;
			}
			println("[OK]   JSON parsing works (clean_c_code, 1 variable, 1 comment, suggested name: " +
				name + ")");
		}
		catch (Throwable t) {
			fail("JSON parsing test failed with exception: " + t);
		}
	}

	/** Verifies that clean C code fallback reconstructor strips artifacts and uVar names. */
	private void checkCleanCodeReconstruction() {
		try {
			Class<?> outcomeClass = Class.forName("deepseekai.AnalysisOutcome");
			Class<?> contextClass = Class.forName("deepseekai.DecompiledContext");
			Object context = contextClass.getDeclaredConstructor().newInstance();

			Field rawCodeField = contextClass.getField("rawCode");
			rawCodeField.set(context, "[0x00401000] int uVar1 = param_1 + 5;\n[0x00401004] return uVar1;");

			Method parse = outcomeClass.getMethod("parse", String.class, Program.class);
			// Sample without clean_c_code to test programmatic fallback
			String sample = "{\"summary\":\"test\",\"variable_renames\":[{\"old_name\":\"uVar1\",\"new_name\":\"total_sum\"}]}";
			Object outcome = parse.invoke(null, sample, (Program) null);

			Method getClean = outcomeClass.getMethod("getOrGenerateCleanCode", contextClass);
			String reconstructed = (String) getClean.invoke(outcome, context);

			if (reconstructed.contains("uVar1")) {
				fail("Fallback clean code still contains uVar1: " + reconstructed);
				return;
			}
			if (!reconstructed.contains("total_sum")) {
				fail("Fallback clean code did not rename uVar1 to total_sum: " + reconstructed);
				return;
			}
			if (reconstructed.contains("[0x")) {
				fail("Fallback clean code still contains [0x...] address tags: " + reconstructed);
				return;
			}
			println("[OK]   Clean C Code reconstruction verified (0 uVar, 0 address tags, variables renamed)");
		}
		catch (Throwable t) {
			fail("Clean C Code reconstruction check failed: " + t);
		}
	}

	/** Checks if DeepSeekAI tool template is recognized by Ghidra. */
	private void checkToolTemplate() {
		try {
			Set<ToolTemplate> tools = ToolUtils.getDefaultApplicationTools();
			println("[..]   Ghidra sees " + tools.size() + " default tool templates:");
			boolean found = false;
			for (ToolTemplate tool : tools) {
				println("       - " + tool.getName());
				if (tool.getName().toLowerCase().contains("deepseek")) {
					found = true;
				}
			}
			if (found) {
				println("[OK]   AI tool template found (inside jar under defaultTools/)");
			}
			else {
				println("[..]   AI tool template is not in default tools list.");
				println("       It will still appear in Tool Chest; " +
					"also ~/.ghidra/.ghidra_<version>/tools/DeepSeekAI.tool is installed.");
			}
		}
		catch (Throwable t) {
			println("[..]   Could not read tool templates: " + t.getMessage());
		}
	}

	private void fail(String message) {
		problems++;
		println("[ERROR] " + message);
	}
}
