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
 * Bir fonksiyonun decompile edilmis hali ve modele gonderilecek ek bilgiler.
 */
public class DecompiledContext {

	/** Decompiler ciktisinin tek bir satiri. */
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

	/** Modelin isimlendirebilecegi bir sembol (parametre veya yerel degisken). */
	public static class SymbolInfo {
		public String name = "";
		public String type = "?";
		public String storage = "?";
		public boolean parameter;
		public String pcAddress = "";
		public long id;

		public String describe() {
			StringBuilder sb = new StringBuilder();
			sb.append(parameter ? "parametre" : "yerel");
			sb.append("  ").append(type).append(' ').append(name);
			if (!"?".equals(storage) && !storage.isEmpty()) {
				sb.append("   [konum: ").append(storage).append(']');
			}
			if (!pcAddress.isEmpty()) {
				sb.append("   [adres: ").append(pcAddress).append(']');
			}
			return sb.toString();
		}
	}

	public Function function;
	public String signature = "";
	public String rawCode = "";
	/**
	 * Decompiler'in urettigi HighFunction. Degisken isimlendirme
	 * ({@code HighFunctionDBUtil.updateDBVariable}) icin gereklidir.
	 * Kisa omurludur; saklanmamalidir.
	 */
	public HighFunction highFunction;
	/** Modelin gorecegi, satir baslarinda adres etiketi olan kod. */
	public String annotatedCode = "";
	public boolean codeTruncated;

	public final List<CodeLine> lines = new ArrayList<>();
	public final List<SymbolInfo> symbols = new ArrayList<>();
	public final List<String> calledFunctions = new ArrayList<>();

	/** Cagrilan fonksiyonlari tek satirda ozetler. */
	public String calledFunctionsText() {
		if (calledFunctions.isEmpty()) {
			return "(cagri yok)";
		}
		return String.join(", ", calledFunctions);
	}
}
