/* ###
 * DeepSeek AI - Ghidra Extension
 *
 * Teshis betigi: modele gonderilen baglamin (context) dogru olusturulup
 * olusturulmadigini gosterir; API cagrisi YAPMAZ.
 *
 * Ghidra'da : Window > Script Manager > DumpFunctionContext > Run
 * Headless  : analyzeHeadless <proj> <name> -process <prog> -postScript DumpFunctionContext.java
 *
 * Opsiyonel arguman:
 *   api  -> Gercek bir DeepSeek istegi gonderir ve yaniti cozumler
 *           (kucuk bir ucret/token harcar).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
import java.util.List;

import deepseekai.AnalysisOutcome;
import deepseekai.DeepSeekClient;
import deepseekai.DeepSeekConfig;
import deepseekai.DecompiledContext;
import deepseekai.DecompilerHelper;
import deepseekai.Prompt;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.FunctionIterator;
import ghidra.program.model.listing.Program;

public class DumpFunctionContext extends GhidraScript {

	@Override
	protected void run() throws Exception {
		Program program = getCurrentProgram();
		if (program == null) {
			println("Acik bir program yok.");
			return;
		}
		Function function = pickFunction(program);
		if (function == null) {
			println("Analiz edilecek fonksiyon bulunamadi. Once otomatik analizi calistirin.");
			return;
		}

		DeepSeekConfig config = new DeepSeekConfig();
		config.maxCodeChars = 24000;

		println("=== DeepSeek AI baglam dokumu ===");
		println("Program   : " + program.getName());
		println("Fonksiyon : " + function.getName() + " @ " +
			function.getEntryPoint().toString());
		println("");

		DecompiledContext context =
			DecompilerHelper.build(program, function, monitor, config.maxCodeChars);

		int withAddress = 0;
		for (DecompiledContext.CodeLine line : context.lines) {
			if (line.address != null) {
				withAddress++;
			}
		}
		println("Satir sayisi           : " + context.lines.size());
		println("Adresi cozulen satir   : " + withAddress);
		println("Sembol sayisi          : " + context.symbols.size());
		println("Cagrilan fonksiyonlar  : " + context.calledFunctionsText());
		println("Kod kisaltildi mi      : " + context.codeTruncated);

		if (withAddress == 0) {
			println("UYARI: Hicbir satira adres eslenmedi! Satir-yorum uygulamasi calismaz.");
		}

		println("");
		println("--- MODELE GONDERILEN KOD (ilk 40 satir) ---");
		String[] lines = context.annotatedCode.split("\n");
		for (int i = 0; i < Math.min(40, lines.length); i++) {
			println(lines[i]);
		}
		println("--- (toplam " + lines.length + " satir) ---");

		println("");
		println("--- SEMBOL TABLOSU (ilk 20) ---");
		int count = 0;
		for (DecompiledContext.SymbolInfo symbol : context.symbols) {
			if (count++ >= 20) {
				break;
			}
			println("  " + symbol.describe());
		}

		String systemPrompt = Prompt.systemPrompt(config);
		String userPrompt = Prompt.userPrompt(context, config);
		println("");
		println("Sistem istemi  : " + systemPrompt.length() + " karakter");
		println("Kullanici istemi: " + userPrompt.length() + " karakter");

		if (!wantsApiCall()) {
			println("");
			println("(API cagrisi atlandi. Denemek icin script argumani olarak 'api' verin.)");
			return;
		}

		println("");
		println("--- GERCEK API CAGRISI ---");
		config.maxTokens = 2048;
		try {
			DeepSeekClient.ChatResponse response =
				new DeepSeekClient().chat(systemPrompt, userPrompt, config, monitor);
			println("Kullanim: " + response.usageText());
			AnalysisOutcome outcome =
				AnalysisOutcome.parse(response.content, program);
			println("JSON cozumlendi        : " + outcome.parsedFromJson);
			println("Ozet uzunlugu          : " + outcome.summary.length());
			println("Onerilen fonksiyon adi : " +
				(outcome.functionName.isEmpty() ? "(yok)" : outcome.functionName));
			println("Yorum onerisi          : " + outcome.lineComments.size());
			println("Degisken onerisi       : " + outcome.varRenames.size());
			println("");
			println("--- OZET ---");
			println(outcome.summary);
			println("");
			println("--- DEGISKEN ONERILERI ---");
			for (AnalysisOutcome.VarRename rename : outcome.varRenames) {
				println("  " + rename.oldName + " -> " + rename.newName + "  (" + rename.type +
					", guven " + rename.confidence + ") " + rename.reason);
			}
			println("");
			println("--- YORUM ONERILERI ---");
			for (AnalysisOutcome.LineComment comment : outcome.lineComments) {
				println("  " + comment.rawAddress + " : " + comment.comment + "  (cozumlenen: " +
					(comment.address != null) + ")");
			}
		}
		catch (Throwable t) {
			println("API cagrisi basarisiz: " + t);
		}
	}

	private boolean wantsApiCall() {
		for (String arg : getScriptArgs()) {
			if ("api".equalsIgnoreCase(arg)) {
				return true;
			}
		}
		return false;
	}

	private Function pickFunction(Program program) {
		try {
			FunctionIterator iterator = program.getFunctionManager().getFunctions(true);
			Function fallback = null;
			while (iterator.hasNext()) {
				Function candidate = iterator.next();
				if (candidate == null || candidate.isExternal() || candidate.isThunk()) {
					continue;
				}
				if (fallback == null) {
					fallback = candidate;
				}
				// Biraz icerikli bir fonksiyon tercih edilir
				if (candidate.getBody().getNumAddresses() > 40) {
					return candidate;
				}
			}
			return fallback;
		}
		catch (Throwable t) {
			return null;
		}
	}
}
