/* ###
 * DeepSeek AI - Ghidra Extension
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package deepseekai;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import ghidra.program.model.address.AddressSetView;
import ghidra.program.model.listing.Function;

/**
 * Toplu (batch) AI analizinin ayarlari.
 */
public class BatchAiOptions {

	/** Hangi fonksiyonlar islenecek. */
	public enum Scope {
		/** Programdaki tum fonksiyonlar. */
		ALL,
		/** Kullanicinin secmis oldugu adres araligindaki fonksiyonlar. */
		SELECTION,
		/** Yalnizca imlecin bulundugu fonksiyon. */
		CURRENT
	}

	public Scope scope = Scope.ALL;

	/** Secili aralik (scope == SELECTION iken kullanilir). */
	public AddressSetView selection;

	/** Yalnizca adi cozulememis fonksiyonlar (FUN_xxxx, sub_xxxx) islensin. */
	public boolean onlyUndefinedNames = true;

	public boolean skipThunks = true;
	public boolean skipExternal = true;

	/** Bu boyutun altindaki fonksiyonlar atlanir (bayt). 0 = filtre yok. */
	public int minFunctionBytes = 16;

	/** Bu boyutu asan fonksiyonlar atlanir (bayt). 0 = filtre yok. */
	public int maxFunctionBytes = 24000;

	/** Bir calistirmada islenecek en fazla fonksiyon (0 = sinirsiz). */
	public int maxFunctions = 0;

	/** API istekleri arasinda beklenecek sure (ms). */
	public int delayMillis = 0;

	/** Uygulanacak degisiklikler. */
	public boolean applyFunctionNames = true;
	public boolean applyVariableRenames = true;
	public boolean applyComments = true;

	/** Onbellekten yararlan (ayni fonksiyon iki kez API'ye gonderilmez). */
	public boolean useCache = true;

	/** Daha once uygulanmis degisiklikleri tekrar uygulama. */
	public boolean skipAlreadyApplied = true;

	/** Sirasiyla cagrilan fonksiyonlari da baglam olarak gonder (daha isabetli, daha pahali). */
	public boolean includeCallersContext = false;

	/** Zenginlestirilmis C cikti dosyasi (null = dosya yazilmaz). */
	public File cOutputFile;

	/** Onbellek dosyasi (null ise otomatik belirlenir). */
	public File cacheFile;

	/** Ozet: listeye uyan fonksiyonlari secer. */
	public List<Function> selectFunctions(List<Function> all, Function current) {
		List<Function> chosen = new ArrayList<>();
		int limit = maxFunctions <= 0 ? Integer.MAX_VALUE : maxFunctions;
		for (Function function : all) {
			if (chosen.size() >= limit) {
				break;
			}
			if (!accepts(function, current)) {
				continue;
			}
			chosen.add(function);
		}
		return chosen;
	}

	private boolean accepts(Function function, Function current) {
		if (function == null) {
			return false;
		}
		switch (scope) {
			case CURRENT:
				if (current == null || !function.equals(current)) {
					return false;
				}
				break;
			case SELECTION:
				if (selection == null) {
					return false;
				}
				if (!selection.contains(function.getEntryPoint())) {
					return false;
				}
				break;
			case ALL:
			default:
				break;
		}
		if (skipExternal && function.isExternal()) {
			return false;
		}
		if (skipThunks && function.isThunk()) {
			return false;
		}
		if (onlyUndefinedNames && !isUndefinedName(function.getName())) {
			return false;
		}
		long size = function.getBody().getNumAddresses();
		if (minFunctionBytes > 0 && size < minFunctionBytes) {
			return false;
		}
		if (maxFunctionBytes > 0 && size > maxFunctionBytes) {
			return false;
		}
		return true;
	}

	/** Ghidra'nin otomatik urettigi isim mi (FUN_00401040, sub_401000, thunk_FUN_...)? */
	public static boolean isUndefinedName(String name) {
		if (name == null) {
			return false;
		}
		return name.startsWith("FUN_") || name.startsWith("sub_") ||
			name.startsWith("thunk_FUN_") || name.startsWith("LAB_");
	}
}
