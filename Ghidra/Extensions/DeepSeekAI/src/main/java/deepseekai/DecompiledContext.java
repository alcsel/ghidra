/* ###
 * DeepSeek AI - Ghidra Extension
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package deepseekai;

import java.util.ArrayList;
import java.util.List;

import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.pcode.HighFunction;

/**
 * Context container holding decompiled code, annotated lines, and symbol metadata.
 */
public class DecompiledContext {

	/** Single line of decompiled C output with optional address mapping. */
	public static class CodeLine {
		public final String text;
		public final Address address;

		public CodeLine(String text, Address address) {
			this.text = text == null ? "" : text;
			this.address = address;
		}

		public String addressText() {
			return address == null ? "" : ("0x" + address.toString().replace(" ", ""));
		}
	}

	/** Function symbol (parameter or local variable) available for AI renaming. */
	public static class SymbolInfo {
		public String name = "";
		public String type = "?";
		public String storage = "?";
		public boolean parameter;
		public String pcAddress = "";
		public long id;

		public String describe() {
			StringBuilder sb = new StringBuilder();
			sb.append(parameter ? "param" : "local");
			sb.append("  ").append(type).append(' ').append(name);
			if (!"?".equals(storage) && !storage.isEmpty()) {
				sb.append("   [storage: ").append(storage).append(']');
			}
			if (!pcAddress.isEmpty()) {
				sb.append("   [address: ").append(pcAddress).append(']');
			}
			return sb.toString();
		}
	}

	public Function function;
	public String signature = "";
	public String rawCode = "";
	/**
	 * HighFunction produced by the decompiler. Required for variable renaming
	 * ({@code HighFunctionDBUtil.updateDBVariable}). Transient; must not be cached long-term.
	 */
	public HighFunction highFunction;
	/** Annotated C code shown to the model with [0x...] address tags at each line. */
	public String annotatedCode = "";
	public boolean codeTruncated;

	public final List<CodeLine> lines = new ArrayList<>();
	public final List<SymbolInfo> symbols = new ArrayList<>();
	public final List<String> calledFunctions = new ArrayList<>();

	/** Summarizes called functions on a single line. */
	public String calledFunctionsText() {
		if (calledFunctions.isEmpty()) {
			return "(no calls)";
		}
		return String.join(", ", calledFunctions);
	}
}
