/* ###
 * DeepSeek AI - Ghidra Extension
 *
 * Teshis/test betigi: toplu (batch) AI analizini kucuk bir ornekle calistirir.
 *
 * Ghidra'da : Window > Script Manager > TestBatchAnalysis > Run
 * Headless  : analyzeHeadless ... -postProcess -postScript TestBatchAnalysis.java api
 *
 * 'api' argumani verilmezse API cagrisi YAPILMAZ (sadece fonksiyon listesini gosterir).
 * 'api' verilirse gercek DeepSeek cagrisi yapilir ve sonuclar programa uygulanir.
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
		Program program = getCurrentProgram();
		if (program == null) {
			println("Acik program yok.");
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
		FunctionIterator iterator = program.getFunctionManager().getFunctions(true);
		while (iterator.hasNext()) {
			all.add(iterator.next());
		}
		List<Function> chosen = options.selectFunctions(all, null);

		println("=== Toplu analiz testi ===");
		println("Program          : " + program.getName());
		println("Toplam fonksiyon : " + all.size());
		println("Secilen          : " + chosen.size());
		for (Function f : chosen) {
			println("   - " + f.getName() + "  (" + f.getBody().getNumAddresses() + " bayt)");
		}

		if (!useApi) {
			println("(API cagrisi atlandi; denemek icin 'api' argumani verin.)");
			return;
		}

		DeepSeekConfig config = new DeepSeekConfig();
		config.loadApiKeyOnly();
		config.maxTokens = 1500;
		if (!config.hasApiKey()) {
			println("API anahtari yok - '" + DeepSeekConfig.getKeyFile() + "'");
			return;
		}

		BatchAiEngine.Result result =
			new BatchAiEngine(program, config, options).run(chosen, monitor);
		println("");
		println(result.summary());

		println("");
		println("--- Fonksiyon adlari (guncel) ---");
		for (Function f : chosen) {
			println("   " + f.getName() + "   @ " + f.getEntryPoint());
		}

		File cFile = options.cOutputFile;
		if (cFile.isFile()) {
			println("");
			println("--- .c cikti dosyasi: " + cFile + " (" + cFile.length() + " bayt) ---");
			try (BufferedReader reader =
				Files.newBufferedReader(cFile.toPath(), StandardCharsets.UTF_8)) {
				String line;
				int count = 0;
				while ((line = reader.readLine()) != null && count < 45) {
					println(line);
					count++;
				}
				println("... (ilk " + count + " satir gosterildi)");
			}
		}
		else {
			println("UYARI: .c dosyasi olusmadi!");
		}

		// Onbellek gercekten yazildi mi?
		File cacheFile = options.cacheFile;
		println("");
		println("Onbellek: " + cacheFile + " (" +
			(cacheFile.isFile() ? cacheFile.length() + " bayt" : "YOK") + ")");
	}
}
