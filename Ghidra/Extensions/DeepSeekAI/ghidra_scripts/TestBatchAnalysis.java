/* ###
 * DeepSeek AI - Ghidra Extension
 *
 * Diagnostic/test script: runs batch AI analysis on a small sample of functions.
 *
 * In Ghidra : Window > Script Manager > TestBatchAnalysis > Run
 * Headless  : analyzeHeadless ... -postProcess -postScript TestBatchAnalysis.java api
 *
 * If 'api' argument is not supplied, no API call is made (function selection only).
 * If 'api' is supplied, actual DeepSeek calls are made and changes applied.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
import java.io.BufferedReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import deepseekai.AnalysisOutcome;
import deepseekai.BatchAiEngine;
import deepseekai.BatchAiOptions;
import deepseekai.DeepSeekClient;
import deepseekai.DeepSeekConfig;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.FunctionIterator;
import ghidra.program.model.listing.Program;

public class TestBatchAnalysis extends GhidraScript {

	@Override
	protected void run() throws Exception {
		if (currentProgram == null) {
			println("Please open a program first.");
			return;
		}

		String outDir = System.getProperty("java.io.tmpdir", ".");
		boolean useApi = false;
		boolean includeNamed = false;
		for (String arg : getScriptArgs()) {
			if ("api".equalsIgnoreCase(arg)) {
				useApi = true;
			}
			if ("all".equalsIgnoreCase(arg)) {
				includeNamed = true;
			}
		}

		BatchAiOptions options = new BatchAiOptions();
		options.scope = BatchAiOptions.Scope.ALL;
		options.onlyUndefinedNames = !includeNamed;
		options.minFunctionBytes = 16;
		options.maxFunctionBytes = 24000;
		options.maxFunctions = 2;
		options.applyFunctionNames = true;
		options.applyVariableRenames = true;
		options.applyComments = true;
		options.useCache = true;
		options.cOutputFile = new File(outDir, "ds_batch_test.c");
		options.cacheFile = new File(outDir, "ds_batch_test.cache.json");

		List<Function> all = new ArrayList<>();
		FunctionIterator iterator = currentProgram.getFunctionManager().getFunctions(true);
		while (iterator.hasNext()) {
			all.add(iterator.next());
		}

		List<Function> chosen = options.selectFunctions(all, null);

		println("=== Batch Analysis Test ===");
		println("Program          : " + currentProgram.getName());
		println("Total functions  : " + all.size());
		println("Selected         : " + chosen.size());
		for (Function f : chosen) {
			println("   - " + f.getName() + "  (" + f.getBody().getNumAddresses() + " bytes)");
		}

		if (!useApi) {
			println("");
			println("(API call skipped; pass 'api' argument to test real request.)");
			return;
		}

		DeepSeekConfig config = new DeepSeekConfig();
		config.loadApiKeyOnly();
		config.maxTokens = 1500;
		if (!config.hasApiKey()) {
			println("API key not found - '" + DeepSeekConfig.getKeyFile() + "'");
			return;
		}

		BatchAiEngine.Result result =
			new BatchAiEngine(currentProgram, config, options).run(chosen, monitor);
		println("");
		println(result.summary());

		println("");
		println("--- Updated Function Names ---");
		for (Function f : chosen) {
			println("   " + f.getName() + "   @ " + f.getEntryPoint());
		}

		File cFile = options.cOutputFile;
		if (cFile.isFile()) {
			println("");
			println("--- C Output File: " + cFile + " (" + cFile.length() + " bytes) ---");
			try (BufferedReader reader =
				Files.newBufferedReader(cFile.toPath(), StandardCharsets.UTF_8)) {
				String line;
				int count = 0;
				while ((line = reader.readLine()) != null && count < 45) {
					println(line);
					count++;
				}
				println("... (first " + count + " lines displayed)");
			}
		}
		else {
			println("WARNING: .c output file was not created!");
		}

		File cacheFile = options.cacheFile;
		println("");
		println("Cache: " + cacheFile + " (" +
			(cacheFile.isFile() ? cacheFile.length() + " bytes" : "NOT FOUND") + ")");
	}
}
