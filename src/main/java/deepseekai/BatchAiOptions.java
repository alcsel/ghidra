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
 * Configuration options and filters for batch AI analysis.
 */
public class BatchAiOptions {

	/** Target function scope. */
	public enum Scope {
		/** All functions in the program. */
		ALL,
		/** Functions within the user's active address selection. */
		SELECTION,
		/** Only the function containing the cursor. */
		CURRENT
	}

	public Scope scope = Scope.ALL;

	/** Active selection (used when scope == SELECTION). */
	public AddressSetView selection;

	/** Only process functions with auto-generated names (FUN_xxxx, sub_xxxx). */
	public boolean onlyUndefinedNames = true;

	public boolean skipThunks = true;
	public boolean skipExternal = true;

	/** Skip functions smaller than this size in bytes (0 = no filter). */
	public int minFunctionBytes = 16;

	/** Skip functions larger than this size in bytes (0 = no filter). */
	public int maxFunctionBytes = 24000;

	/** Maximum number of functions to process in a single run (0 = unlimited). */
	public int maxFunctions = 0;

	/** Delay between API requests in milliseconds (useful for rate limiting). */
	public int delayMillis = 0;

	/** Modifications to automatically apply. */
	public boolean applyFunctionNames = true;
	public boolean applyVariableRenames = true;
	public boolean applyComments = true;

	/** Use persistent cache (avoids sending the same function to the API twice). */
	public boolean useCache = true;

	/** Skip re-applying modifications that have already been applied. */
	public boolean skipAlreadyApplied = true;

	/** Include called function names as context (higher accuracy, slightly more tokens). */
	public boolean includeCallersContext = false;

	/** Enriched C pseudocode output file (null = no file export). */
	public File cOutputFile;

	/** Cache file path (null = automatically determined). */
	public File cacheFile;

	/** Filters the input function list according to the configured options. */
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

	/** Returns true if the function has an auto-generated Ghidra name. */
	public static boolean isUndefinedName(String name) {
		if (name == null) {
			return false;
		}
		return name.startsWith("FUN_") || name.startsWith("sub_") ||
			name.startsWith("thunk_FUN_") || name.startsWith("LAB_");
	}
}
