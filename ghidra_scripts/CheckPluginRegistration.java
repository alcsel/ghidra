/* ###
 * DeepSeek AI - Ghidra Extension
 *
 * Teshis: eklenti sinifinin Ghidra'nin plugin/paket kayit mekanizmasina
 * girip girmedigini kontrol eder. "File > Configure" listesinde gorunmeme
 * sorununu teshis etmek icin yazildi.
 *
 * Ghidra'da : Window > Script Manager > CheckPluginRegistration > Run
 * Headless  : analyzeHeadless ... -postScript CheckPluginRegistration.java
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
import java.lang.reflect.Constructor;
import java.util.List;

import ghidra.app.script.GhidraScript;
import ghidra.framework.plugintool.Plugin;
import ghidra.framework.plugintool.util.DefaultPluginsConfiguration;
import ghidra.framework.plugintool.util.PluginDescription;
import ghidra.framework.plugintool.util.PluginPackage;
import ghidra.util.classfinder.ClassSearcher;

public class CheckPluginRegistration extends GhidraScript {

	@Override
	protected void run() throws Exception {
		println("=== Plugin kayit kontrolu ===");

		// 1) ClassSearcher eklenti sinifini buluyor mu?
		List<Class<? extends Plugin>> pluginClasses = ClassSearcher.getClasses(Plugin.class);
		println("ClassSearcher toplam plugin sinifi : " + pluginClasses.size());
		Class<? extends Plugin> mine = null;
		for (Class<? extends Plugin> c : pluginClasses) {
			if (c.getName().startsWith("deepseekai")) {
				mine = c;
				println("  -> bulundu: " + c.getName());
			}
		}
		if (mine == null) {
			println("SONUC: Eklenti sinifi ClassSearcher'da YOK (jar classpath'te degil).");
			return;
		}

		// 2) PluginDescription olusturulabiliyor mu?
		PluginDescription description = null;
		try {
			description = PluginDescription.getPluginDescription(mine);
			println("PluginDescription olustu.");
			println("  ad       : " + description.getName());
			println("  kategori : " + description.getCategory());
			println("  durum    : " + description.getStatus());
			println("  modul    : " + description.getModuleName());
		}
		catch (Throwable t) {
			println("HATA: PluginDescription olusturulamadi: " + t);
		}

		// 3) Paket cozumlenebiliyor mu? (Configure listesi paketlere gore gruplanir)
		if (description != null) {
			try {
				PluginPackage pkg = description.getPluginPackage();
				if (pkg == null) {
					println("HATA: getPluginPackage() NULL dondu -> Configure listesinde gorunmez!");
				}
				else {
					println("Paket    : " + pkg.getName() + "  (aktivasyon: " +
						pkg.getActivationLevel() + ")");
				}
			}
			catch (Throwable t) {
				println("HATA: getPluginPackage() istisna atti: " + t);
			}
		}

		// 4) "Ghidra Core" paketi gercekten var mi?
		println("");
		println("PluginPackage.exists(\"Ghidra Core\") = " + PluginPackage.exists("Ghidra Core"));
		try {
			PluginPackage core = PluginPackage.getPluginPackage("Ghidra Core");
			println("getPluginPackage(\"Ghidra Core\") = " +
				(core == null ? "NULL" : core.getName()));
		}
		catch (Throwable t) {
			println("getPluginPackage(\"Ghidra Core\") istisna: " + t);
		}

		// 5) Configure diyalogunun kullandigi yapilandirma uzerinden ara
		println("");
		try {
			DefaultPluginsConfiguration config = new DefaultPluginsConfiguration();
			List<PluginPackage> packages = config.getPluginPackages();
			println("Yapilandirmadaki paket sayisi: " + packages.size());
			boolean found = false;
			for (PluginPackage p : packages) {
				List<PluginDescription> list = config.getPluginDescriptions(p);
				for (PluginDescription d : list) {
					if (d.getPluginClass().getName().startsWith("deepseekai")) {
						println("  -> LISTEDE: paket=" + p.getName() + " ad=" + d.getName());
						found = true;
					}
				}
				if ("Ghidra Core".equals(p.getName())) {
					println("  Ghidra Core paketindeki plugin sayisi: " + list.size());
				}
			}
			println(found ? "SONUC: Eklenti yapilandirma listesinde VAR."
				: "SONUC: Eklenti yapilandirma listesinde YOK!");
		}
		catch (Throwable t) {
			println("yapilandirma kontrolu basarisiz: " + t);
		}

		// 6) Arac yapilandirmasi (GhidraPluginsConfiguration - package-private)
		println("");
		try {
			Class<?> cls =
				Class.forName("ghidra.framework.project.tool.GhidraPluginsConfiguration");
			Constructor<?> ctor = cls.getDeclaredConstructor();
			ctor.setAccessible(true);
			Object toolConfig = ctor.newInstance();
			java.lang.reflect.Method getPackages = cls.getMethod("getPluginPackages");
			@SuppressWarnings("unchecked")
			List<PluginPackage> toolPackages =
				(List<PluginPackage>) getPackages.invoke(toolConfig);
			println("Arac yapilandirmasindaki paket sayisi: " + toolPackages.size());
			java.lang.reflect.Method getDescs =
				cls.getMethod("getPluginDescriptions", PluginPackage.class);
			boolean found2 = false;
			for (PluginPackage p : toolPackages) {
				@SuppressWarnings("unchecked")
				List<PluginDescription> list =
					(List<PluginDescription>) getDescs.invoke(toolConfig, p);
				for (PluginDescription d : list) {
					if (d.getPluginClass().getName().startsWith("deepseekai")) {
						println("  -> ARAC LISTESINDE: paket=" + p.getName());
						found2 = true;
					}
				}
			}
			println(found2 ? "SONUC: Arac yapilandirmasinda VAR."
				: "SONUC: Arac yapilandirmasinda YOK!");
		}
		catch (Throwable t) {
			println("arac yapilandirmasi kontrolu basarisiz: " + t);
		}
	}
}
