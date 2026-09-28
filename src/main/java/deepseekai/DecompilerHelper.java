/* ###
 * DeepSeek AI - Ghidra Extension
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package deepseekai;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ghidra.app.decompiler.ClangBreak;
import ghidra.app.decompiler.ClangNode;
import ghidra.app.decompiler.ClangToken;
import ghidra.app.decompiler.ClangTokenGroup;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.decompiler.DecompiledFunction;
import ghidra.program.model.address.Address;
import ghidra.program.model.data.DataType;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Program;
import ghidra.program.model.pcode.HighFunction;
import ghidra.program.model.pcode.HighSymbol;
import ghidra.program.model.pcode.LocalSymbolMap;
import ghidra.util.Msg;
import ghidra.util.task.TaskMonitor;

/**
 * Utility helper for decompilation operations and token stream extraction.
 */
public class DecompilerHelper {

	private DecompilerHelper() {
		// utility class
	}

	/**
	 * Decompiles the specified function and collects full context for the model.
	 */
	public static DecompiledContext build(Program program, Function function, TaskMonitor monitor,
			int maxCodeChars) throws Exception {

		DecompInterface decompiler = new DecompInterface();
		try {
			decompiler.openProgram(program);
			decompiler.setSimplificationStyle("decompile");
			return buildWith(decompiler, program, function, monitor, maxCodeChars);
		}
		finally {
			decompiler.dispose();
		}
	}

	/**
	 * Used for batch analysis: decompiles using a caller-provided {@link DecompInterface},
	 * avoiding the overhead of creating new decompiler processes repeatedly.
	 * <p>
	 * NOTE: This method does NOT dispose the decompiler; ownership belongs to caller.
	 */
	public static DecompiledContext buildWith(DecompInterface decompiler, Program program,
			Function function, TaskMonitor monitor, int maxCodeChars) throws Exception {

		DecompileResults results = decompiler.decompileFunction(function, 180, monitor);
		if (results == null || !results.decompileCompleted()) {
			String message = results == null ? "Decompiler returned no results"
					: results.getErrorMessage();
			throw new IOException("Could not decompile function: " + message);
		}

		DecompiledContext context = new DecompiledContext();
		context.function = function;

		DecompiledFunction decompiled = results.getDecompiledFunction();
		if (decompiled != null) {
			context.rawCode = nullToEmpty(decompiled.getC());
			context.signature = nullToEmpty(decompiled.getSignature()).trim();
		}
		if (context.signature.isEmpty()) {
			context.signature = function.getName();
		}

		collectLines(context, results.getCCodeMarkup());
		if (context.lines.isEmpty()) {
			for (String line : context.rawCode.split("\n", -1)) {
				context.lines.add(new DecompiledContext.CodeLine(line, null));
			}
		}

		context.highFunction = results.getHighFunction();
		collectSymbols(context, context.highFunction);
		context.calledFunctions.addAll(collectCalledFunctions(function, monitor));
		context.annotatedCode = annotate(context, maxCodeChars);
		return context;
	}

	/**
	 * Combines code lines with address prefix tags.
	 */
	private static String annotate(DecompiledContext context, int maxCodeChars) {
		StringBuilder annotated = new StringBuilder();
		int used = 0;
		for (DecompiledContext.CodeLine line : context.lines) {
			if (used > maxCodeChars) {
				context.codeTruncated = true;
				annotated.append("... /* code truncated */\n");
				break;
			}
			String label = line.address == null ? "[          ]"
					: ("[" + line.addressText() + "]");
			annotated.append(label).append(' ').append(line.text).append('\n');
			used += line.text.length() + 14;
		}
		return annotated.toString();
	}

	private static void collectLines(DecompiledContext context, ClangTokenGroup markup) {
		if (markup == null) {
			return;
		}
		try {
			WalkState state = new WalkState();
			walk(markup, state, context.lines);
			if (state.buffer.length() > 0) {
				context.lines.add(new DecompiledContext.CodeLine(state.buffer.toString(),
					state.address));
			}
		}
		catch (Throwable t) {
			Msg.trace(DecompilerHelper.class, "Could not convert markup to lines, falling back to raw code",
				t);
			context.lines.clear();
		}
	}

	private static class WalkState {
		final StringBuilder buffer = new StringBuilder();
		Address address;

		void reset() {
			buffer.setLength(0);
			address = null;
		}
	}

	/**
	 * Walks the decompiler Clang AST token tree. {@link ClangBreak} tokens represent
	 * line boundaries; each line address is derived from the first address-bearing token.
	 */
	private static void walk(ClangNode node, WalkState state,
			List<DecompiledContext.CodeLine> out) {
		if (node instanceof ClangBreak) {
			out.add(new DecompiledContext.CodeLine(state.buffer.toString(), state.address));
			state.reset();
			return;
		}
		if (node instanceof ClangToken) {
			ClangToken token = (ClangToken) node;
			String text = token.getText();
			if (text == null) {
				text = "";
			}
			if (state.address == null && !text.trim().isEmpty()) {
				Address min = token.getMinAddress();
				if (min != null) {
					state.address = min;
				}
			}
			state.buffer.append(text);
			return;
		}
		int count = node.numChildren();
		for (int i = 0; i < count; i++) {
			walk(node.Child(i), state, out);
		}
	}

	private static void collectSymbols(DecompiledContext context, HighFunction highFunction) {
		if (highFunction == null) {
			return;
		}
		try {
			for (HighSymbol symbol : symbolIndex(highFunction).values()) {
				context.symbols.add(toInfo(symbol));
			}
		}
		catch (Throwable t) {
			Msg.trace(DecompilerHelper.class, "Could not read symbols", t);
		}
	}

	private static DecompiledContext.SymbolInfo toInfo(HighSymbol symbol) {
		DecompiledContext.SymbolInfo info = new DecompiledContext.SymbolInfo();
		info.name = nullToEmpty(symbol.getName());
		info.id = symbol.getId();
		try {
			info.parameter = symbol.isParameter();
		}
		catch (Throwable t) {
			info.parameter = false;
		}
		try {
			DataType type = symbol.getDataType();
			info.type = type == null ? "?" : type.getName();
		}
		catch (Throwable t) {
			info.type = "?";
		}
		try {
			Object storage = symbol.getStorage();
			info.storage = storage == null ? "?" : storage.toString();
		}
		catch (Throwable t) {
			info.storage = "?";
		}
		try {
			Address pc = symbol.getPCAddress();
			info.pcAddress = pc == null ? "" : ("0x" + pc.toString().replace(" ", ""));
		}
		catch (Throwable t) {
			info.pcAddress = "";
		}
		return info;
	}

	private static List<String> collectCalledFunctions(Function function, TaskMonitor monitor) {
		List<String> names = new ArrayList<>();
		try {
			Set<Function> called = function.getCalledFunctions(monitor);
			for (Function callee : called) {
				if (callee == null || callee.getName().equals(function.getName())) {
					continue;
				}
				names.add(callee.getName());
			}
		}
		catch (Throwable t) {
			Msg.trace(DecompilerHelper.class, "Could not read called functions", t);
		}
		return names;
	}

	/**
	 * Returns HighSymbol objects mapped by name. Used during variable renaming.
	 */
	public static Map<String, HighSymbol> symbolIndex(HighFunction highFunction) {
		Map<String, HighSymbol> index = new LinkedHashMap<>();
		if (highFunction == null) {
			return index;
		}
		LocalSymbolMap symbolMap = highFunction.getLocalSymbolMap();
		int params = symbolMap.getNumParams();
		for (int i = 0; i < params; i++) {
			HighSymbol param = symbolMap.getParamSymbol(i);
			if (param != null) {
				index.put(param.getName(), param);
			}
		}
		Iterator<HighSymbol> iterator = symbolMap.getSymbols();
		while (iterator.hasNext()) {
			HighSymbol symbol = iterator.next();
			index.putIfAbsent(symbol.getName(), symbol);
		}
		return index;
	}

	/**
	 * Re-decompiles the function and returns an up-to-date HighFunction.
	 */
	public static HighFunction decompileHighFunction(Program program, Function function,
			TaskMonitor monitor) throws Exception {
		DecompInterface decompiler = new DecompInterface();
		try {
			decompiler.openProgram(program);
			DecompileResults results = decompiler.decompileFunction(function, 180, monitor);
			if (results == null || !results.decompileCompleted()) {
				throw new IOException("Could not refresh decompilation for variables: " +
					(results == null ? "?" : results.getErrorMessage()));
			}
			return results.getHighFunction();
		}
		finally {
			decompiler.dispose();
		}
	}

	private static String nullToEmpty(String s) {
		return s == null ? "" : s;
	}
}
