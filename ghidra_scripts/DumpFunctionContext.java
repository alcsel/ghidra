/* ###
 * DeepSeek AI - Ghidra Extension
 *
 * Diagnostic script: inspects whether context for the model is generated
 * correctly; does NOT make an API call by default.
 *
 * In Ghidra : Window > Script Manager > DumpFunctionContext > Run
 * Headless  : analyzeHeadless <proj> <name> -process <prog> -postScript DumpFunctionContext.java
 *
 * Optional arguments:
 *   api  -> Sends an actual DeepSeek request and parses the response.
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
			println("Please open a program first.");
			return;
		}
		Function function = pickFunction(program);
		if (function == null) {
			println("No function found to analyze. Please run auto-analysis first.");
			return;
		}

		DeepSeekConfig config = new DeepSeekConfig();
		config.maxCodeChars = 24000;

		println("=== DeepSeek AI Function Context Dump ===");
		println("Program   : " + program.getName());
		println("Function  : " + function.getName() + " @ " +
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
		println("Line count             : " + context.lines.size());
		println("Lines with address     : " + withAddress);
		println("Symbol count           : " + context.symbols.size());
		println("Called functions       : " + context.calledFunctionsText());
		println("Code truncated         : " + context.codeTruncated);

		if (withAddress == 0) {
			println("WARNING: No address mapped to lines! Line comments will not be applicable.");
		}

		println("");
		println("--- CODE SENT TO MODEL (first 40 lines) ---");
		String[] lines = context.annotatedCode.split("\\n");
		for (int i = 0; i < Math.min(40, lines.length); i++) {
			println(lines[i]);
		}
		println("--- (total " + lines.length + " lines) ---");

		println("");
		println("--- SYMBOL TABLE (first 20) ---");
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
		println("System prompt length : " + systemPrompt.length() + " characters");
		println("User prompt length   : " + userPrompt.length() + " characters");

		if (!wantsApiCall()) {
			println("");
			println("(API call skipped. Pass 'api' as script argument to test real request.)");
			return;
		}

		println("");
		println("--- REAL API CALL ---");
		config.maxTokens = 2048;
		try {
			DeepSeekClient.ChatResponse response =
				new DeepSeekClient().chat(systemPrompt, userPrompt, config, monitor);
			println("Usage: " + response.usageText());
			AnalysisOutcome outcome =
				AnalysisOutcome.parse(response.content, program);
			println("JSON parsed             : " + outcome.parsedFromJson);
			println("Summary length          : " + outcome.summary.length());
			println("Suggested function name : " +
				(outcome.functionName.isEmpty() ? "(none)" : outcome.functionName));
			println("Comment suggestions     : " + outcome.lineComments.size());
			println("Variable suggestions    : " + outcome.varRenames.size());
			println("");
			println("--- SUMMARY ---");
			println(outcome.summary);
			println("");
			println("--- VARIABLE SUGGESTIONS ---");
			for (AnalysisOutcome.VarRename rename : outcome.varRenames) {
				println("  " + rename.oldName + " -> " + rename.newName + "  (" + rename.type +
					", confidence " + rename.confidence + ") " + rename.reason);
			}
			println("");
			println("--- COMMENT SUGGESTIONS ---");
			for (AnalysisOutcome.LineComment comment : outcome.lineComments) {
				println("  " + comment.rawAddress + " : " + comment.comment + "  (resolved: " +
					(comment.address != null) + ")");
			}
		}
		catch (Throwable t) {
			println("API call failed: " + t);
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
