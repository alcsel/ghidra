/* ###
 * DeepSeek AI - Ghidra Extension
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package deepseekai;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ghidra.app.decompiler.DecompInterface;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Program;
import ghidra.util.exception.CancelledException;
import ghidra.util.task.TaskMonitor;

/**
 * Toplu (batch) AI analizinin cekirdegi. Arayuzden bagimsizdir; boylece
 * hem GUI gorevinden ({@link BatchAiTask}) hem de headless betiklerden
 * cagrilabilir ve test edilebilir.
 * <p>
 * Her fonksiyon icin:
 * <ol>
 * <li>decompile edilir,</li>
 * <li>onbellekte sonuc yoksa DeepSeek'e gonderilir,</li>
 * <li>oneriler programa uygulanir (isimlendirme + yorumlar),</li>
 * <li>istenmisse zenginlestirilmis C ciktisina eklenir.</li>
 * </ol>
 */
public class BatchAiEngine {

	/** Toplu analiz sonucunun ozeti. */
	public static class Result {
		public int total;
		public int processed; // API'ye gonderilen
		public int fromCache;
		public int failed;
		public int functionsRenamed;
		public int variablesRenamed;
		public int commentsAdded;
		public int promptTokens;
		public int completionTokens;
		public long elapsedMillis;
		public File cFile;
		public final List<String> problems = new ArrayList<>();

		public int totalTokens() {
			return promptTokens + completionTokens;
		}

		public String summary() {
			StringBuilder sb = new StringBuilder();
			sb.append("Toplu analiz tamamlandi.\n\n");
			sb.append("  Islenecek fonksiyon : ").append(total).append('\n');
			sb.append("  API'ye gonderilen   : ").append(processed).append('\n');
			sb.append("  Onbellekten gelen   : ").append(fromCache).append('\n');
			sb.append("  Hata                : ").append(failed).append('\n');
			sb.append("  Yeni fonksiyon adi  : ").append(functionsRenamed).append('\n');
			sb.append("  Yeni degisken adi   : ").append(variablesRenamed).append('\n');
			sb.append("  Eklenen yorum       : ").append(commentsAdded).append('\n');
			sb.append("  Token               : ").append(promptTokens).append(" giris + ")
					.append(completionTokens).append(" cikis = ").append(totalTokens())
					.append('\n');
			sb.append("  Sure                : ").append(elapsedMillis / 1000).append(" saniye\n");
			if (cFile != null) {
				sb.append("\n  C ciktisi           : ").append(cFile.getAbsolutePath()).append('\n');
			}
			OutcomeApplier.appendProblems(sb, problems);
			return sb.toString();
		}
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Program program;
	private final DeepSeekConfig config;
	private final BatchAiOptions options;

	private JsonObject cache;
	private File cacheFile;
	private int cacheDirty;

	private BufferedWriter cWriter;
	private int exportedFunctions;
	private boolean exportEnabled;

	public BatchAiEngine(Program program, DeepSeekConfig config, BatchAiOptions options) {
		this.program = program;
		this.config = config;
		this.options = options;
	}

	/**
	 * Verilen fonksiyonlari sirayla isler.
	 *
	 * @param functions islenecek fonksiyon listesi (onceden filtrelenmis)
	 * @param monitor ilerleme/iptal (null olabilir)
	 */
	public Result run(List<Function> functions, TaskMonitor monitor) throws CancelledException {
		Result result = new Result();
		result.total = functions.size();
		long started = System.currentTimeMillis();

		loadCache();
		DecompInterface decompiler = new DecompInterface();
		try {
			decompiler.openProgram(program);
			if (monitor != null) {
				monitor.initialize(functions.size());
			}
			exportEnabled = options.cOutputFile != null;
			try {
				openExportFile(result);
				writeExportHeader(result, functions);
			}
			catch (IOException e) {
				exportEnabled = false;
				result.cFile = null;
				result.problems.add("C cikti dosyasi olusturulamadi: " + e.getMessage());
			}

			int index = 0;
			for (Function function : functions) {
				checkCanceled(monitor);
				index++;
				if (monitor != null) {
					monitor.setMessage("(" + index + "/" + functions.size() + ") " +
						function.getName());
				}
				try {
					processFunction(decompiler, function, result, monitor);
				}
				catch (CancelledException e) {
					throw e;
				}
				catch (Throwable t) {
					result.failed++;
					result.problems.add(function.getName() + ": " + t);
				}
				if (monitor != null) {
					monitor.incrementProgress(1);
				}
				saveCache(false);
				sleepQuietly(options.delayMillis);
			}
		}
		finally {
			closeExportFile(functions);
			decompiler.dispose();
			result.elapsedMillis = System.currentTimeMillis() - started;
			saveCache(true);
		}
		return result;
	}

	private static void checkCanceled(TaskMonitor monitor) throws CancelledException {
		if (monitor != null) {
			monitor.checkCanceled();
		}
	}

	// ------------------------------------------------------------------
	// Tek fonksiyon isleme
	// ------------------------------------------------------------------

	private void processFunction(DecompInterface decompiler, Function function, Result result,
			TaskMonitor monitor) throws Exception {

		String key = function.getEntryPoint().toString();
		JsonObject entry = getCacheEntry(key);
		boolean alreadyApplied =
			entry != null && entry.has("applied") && entry.get("applied").getAsBoolean();
		boolean wantExport = exportEnabled;

		if (alreadyApplied && !wantExport) {
			result.fromCache++;
			return;
		}

		boolean wantApply = !alreadyApplied &&
			(options.applyFunctionNames || options.applyVariableRenames || options.applyComments);
		boolean wantApi = entry == null;

		DecompiledContext context = null;
		if (wantApi || wantApply || wantExport) {
			context = DecompilerHelper.buildWith(decompiler, program, function, monitor,
				config.maxCodeChars);
		}

		AnalysisOutcome outcome;
		if (wantApi) {
			if (monitor != null) {
				monitor.setMessage("DeepSeek: " + function.getName());
			}
			String system = Prompt.systemPrompt(config);
			String user = Prompt.userPrompt(context, config);
			DeepSeekClient.ChatResponse response =
				new DeepSeekClient().chat(system, user, config, monitor);

			outcome = AnalysisOutcome.parse(response.content, program);
			outcome.usageText = response.usageText();
			result.processed++;
			result.promptTokens += response.promptTokens;
			result.completionTokens += response.completionTokens;
		}
		else {
			outcome = AnalysisOutcome.parse(DeepSeekClient.jsonString(entry, "rawResponse"),
				program);
			result.fromCache++;
		}

		if (wantApply) {
			checkCanceled(monitor);
			OutcomeApplier.ApplyCounts counts = OutcomeApplier.apply(program, function,
				context == null ? null : context.highFunction, outcome,
				options.applyVariableRenames, options.applyFunctionNames, options.applyComments,
				options.applyComments);
			result.functionsRenamed += counts.functionRenamed ? 1 : 0;
			result.variablesRenamed += counts.variablesRenamed;
			result.commentsAdded += counts.commentsAdded;
			for (String problem : counts.problems) {
				if (result.problems.size() < 200) {
					result.problems.add(function.getName() + ": " + problem);
				}
			}
			storeCacheEntry(key, outcome, true);
		}
		else if (entry == null) {
			storeCacheEntry(key, outcome, alreadyApplied);
		}

		if (wantExport) {
			// Isimlendirme uygulandiysa guncel adlari gormek icin tekrar decompile et
			DecompiledContext exportContext = wantApply
					? DecompilerHelper.buildWith(decompiler, program, function, monitor,
						config.maxCodeChars)
					: context;
			writeExportSection(function, exportContext, outcome);
			result.cFile = options.cOutputFile;
		}
	}

	// ------------------------------------------------------------------
	// Onbellek
	// ------------------------------------------------------------------

	private File resolveCacheFile() {
		if (options.cacheFile != null) {
			return options.cacheFile;
		}
		if (options.cOutputFile != null) {
			return new File(options.cOutputFile.getAbsolutePath() + ".cache.json");
		}
		String name = program.getName() + ".deepseek-cache.json";
		return new File(System.getProperty("user.home", "."), name);
	}

	private void loadCache() {
		cache = new JsonObject();
		cache.add("entries", new JsonObject());
		if (!options.useCache) {
			return;
		}
		cacheFile = resolveCacheFile();
		if (cacheFile == null || !cacheFile.isFile()) {
			return;
		}
		try (Reader reader =
			Files.newBufferedReader(cacheFile.toPath(), StandardCharsets.UTF_8)) {
			JsonObject loaded = JsonParser.parseReader(reader).getAsJsonObject();
			if (loaded.has("entries") && loaded.get("entries").isJsonObject()) {
				cache = loaded;
			}
		}
		catch (Exception e) {
			cache = new JsonObject();
			cache.add("entries", new JsonObject());
		}
	}

	private JsonObject entries() {
		return cache.getAsJsonObject("entries");
	}

	private JsonObject getCacheEntry(String key) {
		if (!options.useCache || cache == null) {
			return null;
		}
		JsonObject all = entries();
		if (all == null || !all.has(key) || !all.get(key).isJsonObject()) {
			return null;
		}
		JsonObject entry = all.getAsJsonObject(key);
		// Farkli model ile uretilmis sonuclari karistirmayalim
		String model = DeepSeekClient.jsonString(entry, "model");
		if (!model.isEmpty() && !model.equals(config.model)) {
			return null;
		}
		return entry;
	}

	private void storeCacheEntry(String key, AnalysisOutcome outcome, boolean applied) {
		if (!options.useCache || cache == null) {
			return;
		}
		JsonObject entry = new JsonObject();
		entry.addProperty("applied", applied);
		entry.addProperty("model", config.model);
		entry.addProperty("suggestedName", outcome.functionName);
		entry.addProperty("summary", outcome.summary);
		entry.addProperty("rawResponse", outcome.rawResponse);
		entries().add(key, entry);
		cacheDirty++;
	}

	private void saveCache(boolean force) {
		if (!options.useCache || cache == null || cacheFile == null) {
			return;
		}
		if (!force && cacheDirty < 5) {
			return;
		}
		cacheDirty = 0;
		cache.addProperty("program", program.getName());
		cache.addProperty("model", config.model);
		cache.addProperty("updated", new Date().toString());
		try (Writer writer =
			Files.newBufferedWriter(cacheFile.toPath(), StandardCharsets.UTF_8)) {
			GSON.toJson(cache, writer);
		}
		catch (Exception e) {
			// onbellek yazilamazsa analiz devam etsin
		}
	}

	// ------------------------------------------------------------------
	// C cikti dosyasi
	// ------------------------------------------------------------------

	private void openExportFile(Result result) throws IOException {
		if (options.cOutputFile == null) {
			return;
		}
		File parent = options.cOutputFile.getParentFile();
		if (parent != null && !parent.isDirectory()) {
			parent.mkdirs();
		}
		cWriter = Files.newBufferedWriter(options.cOutputFile.toPath(), StandardCharsets.UTF_8);
		result.cFile = options.cOutputFile;
	}

	private void writeExportHeader(Result result, List<Function> functions) throws IOException {
		if (cWriter == null) {
			return;
		}
		cWriter.write("/******************************************************************************\n");
		cWriter.write(" * Ghidra + DeepSeek AI - decompile edilmis kaynak kodu\n");
		cWriter.write(" *\n");
		cWriter.write(" * Program  : " + program.getName() + "\n");
		cWriter.write(" * Dil      : " + program.getLanguageID() + "\n");
		cWriter.write(" * Tarih    : " + new Date() + "\n");
		cWriter.write(" * Model    : " + config.model + "\n");
		cWriter.write(" * Araclar  : Ghidra " + ghidra.framework.Application.getApplicationVersion() +
			" + DeepSeek AI eklentisi\n");
		cWriter.write(" *\n");
		cWriter.write(" * NOT: Bu dosya otomatik uretilmistir. Orijinal kaynak kod degildir;\n");
		cWriter.write(" *      decompiler ciktisi olup AI tarafindan isimlendirilmis ve\n");
		cWriter.write(" *      yorumlanmistir. Derlenebilir oldugu garanti edilmez.\n");
		cWriter.write(" *\n");
		cWriter.write(" * Fonksiyon indeksi dosyanin sonundadir.\n");
		cWriter.write(" ******************************************************************************/\n\n");
	}

	/** Fonksiyon indeksini (guncel adlarla) dosyanin sonuna yazar. */
	private void writeExportIndex(List<Function> functions) throws IOException {
		if (cWriter == null) {
			return;
		}
		cWriter.write("\n/******************************************************************************\n");
		cWriter.write(" * FONKSIYON INDEKSI (" + functions.size() + ")\n");
		cWriter.write(" ******************************************************************************/\n");
		for (Function function : functions) {
			String address = "0x" + function.getEntryPoint().toString().replace(" ", "");
			cWriter.write(" *   " + pad(address, 12) + " " + function.getName() + "\n");
		}
		cWriter.write(" ******************************************************************************/\n");
	}

	/** C blok yorumu uretir; uzun metni 78 sutunda sarar ve yorum kapatma dizisini kacislar. */
	private static String commentBlock(String label, String text) {
		final String prefix = " *   ";
		final int width = 78;
		String head = "/* " + (label == null || label.isEmpty() ? "" : label + ": ");
		StringBuilder sb = new StringBuilder();
		boolean firstWord = true;
		int col = 0;
		for (String word : sanitize(text).split("\\s+")) {
			if (word.isEmpty()) {
				continue;
			}
			if (firstWord) {
				sb.append(head);
				col = head.length();
				firstWord = false;
			}
			else if (col + 1 + word.length() > width) {
				sb.append('\n').append(prefix);
				col = prefix.length();
			}
			else {
				sb.append(' ');
				col++;
			}
			sb.append(word);
			col += word.length();
		}
		if (firstWord) {
			sb.append(head);
		}
		sb.append("\n */\n");
		return sb.toString();
	}

	private void writeExportSection(Function function, DecompiledContext context,
			AnalysisOutcome outcome) throws IOException {
		if (cWriter == null || context == null) {
			return;
		}
		exportedFunctions++;
		String address = "0x" + function.getEntryPoint().toString().replace(" ", "");
		cWriter.write("/* " + repeat('-', 76) + " */\n");
		cWriter.write("/* " + pad(address, 12) + " " + pad(function.getName(), 46) +
			" boyut: " + function.getBody().getNumAddresses() + "\n");
		cWriter.write("/* " + repeat('-', 76) + " */\n");
		if (outcome != null && !outcome.summary.isBlank()) {
			cWriter.write(commentBlock("OZET", outcome.summary));
		}
		if (!context.signature.isBlank() && !context.signature.equals(function.getName())) {
			cWriter.write(commentBlock("IMZA", context.signature));
		}
		if (outcome != null && !outcome.varRenames.isEmpty()) {
			cWriter.write("/*\n * Degisken eslesmesi:\n");
			for (AnalysisOutcome.VarRename rename : outcome.varRenames) {
				String type = rename.type.isBlank() ? "" : ("  (" + rename.type + ")");
				cWriter.write(" *   " + pad(rename.oldName, 18) + " -> " + rename.newName + type +
					"\n");
			}
			cWriter.write(" */\n");
		}
		if (!context.calledFunctions.isEmpty()) {
			cWriter.write(commentBlock("Cagrilanlar", String.join(", ", context.calledFunctions)));
		}
		if (outcome != null && !outcome.uncertainties.isEmpty()) {
			cWriter.write(commentBlock("AI BELIRSIZLIKLERI",
				String.join(" | ", outcome.uncertainties)));
		}
		cWriter.write("\n");
		cWriter.write(context.rawCode);
		if (!context.rawCode.endsWith("\n")) {
			cWriter.write("\n");
		}
		cWriter.write("\n");
		cWriter.flush();
	}

	private void closeExportFile(List<Function> functions) {
		if (cWriter == null) {
			return;
		}
		try {
			if (exportedFunctions == 0) {
				cWriter.write("/* Hicbir fonksiyon disa aktarilmadi (filtreler veya iptal). */\n");
			}
			else {
				writeExportIndex(functions);
			}
			cWriter.write("\n/* Dosya sonu - " + exportedFunctions + " fonksiyon */\n");
			cWriter.close();
		}
		catch (IOException e) {
			// yoksay
		}
		cWriter = null;
	}

	private static String pad(String text, int width) {
		StringBuilder sb = new StringBuilder(text == null ? "" : text);
		while (sb.length() < width) {
			sb.append(' ');
		}
		return sb.toString();
	}

	private static String repeat(char c, int count) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < count; i++) {
			sb.append(c);
		}
		return sb.toString();
	}

	private static String sanitize(String text) {
		return text.replace("*/", "* /");
	}

	private static void sleepQuietly(int millis) {
		if (millis <= 0) {
			return;
		}
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
