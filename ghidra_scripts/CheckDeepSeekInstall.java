/* ###
 * DeepSeek AI - Ghidra Extension
 *
 * Kurulum dogrulama betigi.
 *
 * Ghidra'da: Window > Script Manager > CheckDeepSeekInstall > Run
 * Headless : analyzeHeadless ... -postScript CheckDeepSeekInstall.java
 *
 * Su kontrolleri yapar:
 *   1. deepseekai.DeepSeekAIPlugin sinifi ClassSearcher tarafindan bulunuyor mu?
 *   2. Gson ve java.net.http bagimliliklari cozulebiliyor mu?
 *   3. JSON ayristirma gercekten calisiyor mu?
 *   4. DeepSeekAI araç (.tool) sablonu Ghidra tarafindan goruluyor mu?
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
		println(" DeepSeek AI eklentisi - kurulum kontrolu");
		println("==========================================================");

		checkPluginClass();
		checkDependencies();
		checkJsonParsing();
		checkToolTemplate();

		println("");
		if (problems == 0) {
			println("SONUC: Tum kontroller basarili. Eklenti kullanima hazir.");
		}
		else {
			println("SONUC: " + problems + " sorun bulundu (yukaridaki HATA satirlarina bakin).");
		}
	}

	/** Ghidra eklenti sinifini ClassSearcher ile arar. */
	private void checkPluginClass() {
		boolean found = false;
		try {
			List<Class<? extends Plugin>> classes = ClassSearcher.getClasses(Plugin.class);
			for (Class<? extends Plugin> c : classes) {
				if (c.getName().startsWith("deepseekai.")) {
					println("[OK]   Eklenti sinifi bulundu : " + c.getName());
					found = true;
				}
			}
		}
		catch (Throwable t) {
			fail("Eklenti siniflari listelenemedi: " + t);
			return;
		}
		if (!found) {
			fail("deepseekai.DeepSeekAIPlugin bulunamadi. " +
				"Jar dosyasi Ghidra\\Extensions\\DeepSeekAI\\lib altinda mi?");
		}
	}

	/** Gson ve java.net.http erisilebilir mi? */
	private void checkDependencies() {
		try {
			Class.forName("com.google.gson.JsonObject");
			println("[OK]   Gson kutuphanesi erisilebilir");
		}
		catch (Throwable t) {
			fail("Gson bulunamadi: " + t);
		}
		try {
			Class.forName("java.net.http.HttpClient");
			println("[OK]   java.net.http.HttpClient erisilebilir");
		}
		catch (Throwable t) {
			fail("java.net.http bulunamadi (Java 11+ gerekir): " + t);
		}
		try {
			Class<?> configClass = Class.forName("deepseekai.DeepSeekConfig");
			configClass.getDeclaredConstructor().newInstance();
			println("[OK]   deepseekai.DeepSeekConfig orneklenebiliyor");
		}
		catch (Throwable t) {
			fail("DeepSeekConfig yuklenemedi: " + t);
		}
	}

	/** AnalysisOutcome.parse gercekten calisiyor mu? */
	private void checkJsonParsing() {
		String sample = "{\"summary\":\"ornek aciklama\",\"function_name\":\"do_something\"," +
			"\"line_comments\":[{\"address\":\"0x401000\",\"comment\":\"ornek yorum\"," +
			"\"confidence\":0.9}]," +
			"\"variable_renames\":[{\"old_name\":\"uVar1\",\"new_name\":\"counter\"," +
			"\"reason\":\"sayac\",\"confidence\":0.75}]," +
			"\"uncertainties\":[\"ornek belirsizlik\"]}";
		try {
			Class<?> outcomeClass = Class.forName("deepseekai.AnalysisOutcome");
			Method parse = outcomeClass.getMethod("parse", String.class, Program.class);
			Object outcome = parse.invoke(null, sample, (Program) null);

			Field summaryField = outcomeClass.getField("summary");
			Field nameField = outcomeClass.getField("functionName");
			Field renamesField = outcomeClass.getField("varRenames");
			Field commentsField = outcomeClass.getField("lineComments");
			Field parsedField = outcomeClass.getField("parsedFromJson");

			String summary = String.valueOf(summaryField.get(outcome));
			String name = String.valueOf(nameField.get(outcome));
			int renames = ((List<?>) renamesField.get(outcome)).size();
			int comments = ((List<?>) commentsField.get(outcome)).size();
			boolean parsed = Boolean.TRUE.equals(parsedField.get(outcome));

			if (!parsed) {
				fail("JSON ayristirma basarisiz (parsedFromJson=false)");
				return;
			}
			if (!"ornek aciklama".equals(summary)) {
				fail("Ozet alani yanlis okundu: " + summary);
				return;
			}
			if (!"do_something".equals(name)) {
				fail("Fonksiyon adi alani yanlis okundu: " + name);
				return;
			}
			if (renames != 1 || comments != 1) {
				fail("Liste uzunluklari beklenmedik: renames=" + renames + " comments=" +
					comments);
				return;
			}
			println("[OK]   JSON ayristirma calisiyor (1 degisken, 1 yorum, onerilen ad: " +
				name + ")");
		}
		catch (Throwable t) {
			fail("JSON ayristirma testi hata verdi: " + t);
		}
	}

	/** DeepSeekAI araç sablonu Ghidra tarafindan goruluyor mu? */
	private void checkToolTemplate() {
		try {
			Set<ToolTemplate> tools = ToolUtils.getDefaultApplicationTools();
			println("[..]   Ghidra " + tools.size() + " varsayilan araç sablonu goruyor:");
			boolean found = false;
			for (ToolTemplate tool : tools) {
				println("       - " + tool.getName());
				if (tool.getName().toLowerCase().contains("deepseek")) {
					found = true;
				}
			}
			if (found) {
				println("[OK]   DeepSeekAI araç sablonu bulundu (jar icindeki defaultTools/)");
			}
			else {
				println("[..]   DeepSeekAI araç sablonu varsayilanlar arasinda yok.");
				println("       Araclar (Tool Chest) penceresinde yine de gorunur: " +
					"ayrica ~/.ghidra/.ghidra_<surum>/tools/DeepSeekAI.tool dosyasi da kurulur.");
			}
		}
		catch (Throwable t) {
			println("[..]   Araç sablonlari okunamadi: " + t.getMessage());
		}
	}

	private void fail(String message) {
		problems++;
		println("[HATA] " + message);
	}
}
