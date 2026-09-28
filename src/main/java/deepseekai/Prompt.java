/* ###
 * DeepSeek AI - Ghidra Extension
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package deepseekai;

/**
 * DeepSeek API'sine gonderilecek istemleri (prompt) olusturur.
 */
public class Prompt {

	private Prompt() {
		// yardimci sinif
	}

	/** Modelin uymasi gereken JSON semasi ve kurallari. */
	public static String systemPrompt(DeepSeekConfig config) {
		String language = DeepSeekConfig.isBlank(config.language) ? "Turkce" : config.language;
		return """
			Sen kidemli bir tersine muhendislik (reverse engineering) ve yazilim analizi uzmanisin.
			Sana Ghidra ile decompile edilmis bir fonksiyonun C benzeri sahte kodu (pseudocode),
			sembol tablosu ve adres bilgileri verilecek.

			GOREVIN
			1. Fonksiyonun ne yaptigini ayrintili sekilde acikla.
			2. Anlasilmasi zor, karmasik veya dikkat cekici kisimlari tespit edip yorum yaz.
			3. Degiskenleri (parametreler ve yerel degiskenler) anlamli bicimde yeniden adlandir.
			4. Mumkunse fonksiyon icin anlamli bir isim ve ozet yorum oner.
			5. Emin olmadigin konulari acikca belirt.

			KURALLAR
			- SADECE gecerli bir JSON nesnesi dondur. JSON disinda tek bir karakter bile yazma.
			- Markdown kod blogu (```) kullanma. Aciklama metnini JSON'un icine koy.
			- Sana verilen kodda satirlar [0x00401020] seklinde adres etiketleri ile baslar.
			  Yorum onerirken "address" alanina bu etiketlerdeki adresi AYNEN yaz.
			  Emin olmadigin adresler icin yorum onerisi verme.
			- Uydurma yapma. Bilgi yetersizse "uncertainties" listesine ekle ve confidence dusur.
			- Degisken isimleri: gecerli C tanimlayicisi, snake_case, 2-48 karakter,
			  sadece harf/rakam/alt cizgi, rakamla baslamasin, C anahtar kelimesi olmasin.
			  Ayni yeni ismi birden fazla degiskene verme.
			- Isimlendirme yaparken degiskenin tipini, kullanim yerlerini ve cagrilan
			  fonksiyonlari dikkate al. Ornek: strlen sonucu -> length, recv tamponu -> buffer.
			- confidence: 0.0 (tahmin) ile 1.0 (kesin) arasinda bir sayi olsun.
			- Yazacagin tum aciklama ve yorumlar %s dilinde olsun.

			JSON SEMASI
			{
			  "summary": "fonksiyonun ne yaptigini anlatan ayrintili paragraf",
			  "function_name": "onerdigin_fonksiyon_adi veya null",
			  "function_comment": "fonksiyon giris noktasina eklenecek kisa blok yorumu",
			  "hard_parts": [
			    {
			      "address": "0x00401020 veya null",
			      "explanation": "bu kisim neden zor veya onemli",
			      "comment": "kod satirina eklenecek kisa yorum"
			    }
			  ],
			  "line_comments": [
			    { "address": "0x00401020", "comment": "kisa teknik yorum", "confidence": 0.9 }
			  ],
			  "variable_renames": [
			    {
			      "old_name": "uVar1",
			      "new_name": "packet_length",
			      "type": "int veya null",
			      "reason": "neden bu isim",
			      "confidence": 0.8
			    }
			  ],
			  "uncertainties": [ "emin olmadigin nokta" ]
			}
			""".formatted(language);
	}

	/** Analiz edilecek fonksiyonun detaylarini iceren kullanici istemi. */
	public static String userPrompt(DecompiledContext context, DeepSeekConfig config) {
		String language = DeepSeekConfig.isBlank(config.language) ? "Turkce" : config.language;
		StringBuilder sb = new StringBuilder();

		sb.append("Asagidaki Ghidra fonksiyonunu analiz et ve yanitini ").append(language)
				.append(" dilinde ver.\n\n");

		sb.append("## FONKSIYON\n");
		sb.append("Ad: ").append(context.function.getName()).append('\n');
		sb.append("Giris adresi: 0x")
				.append(context.function.getEntryPoint().toString().replace(" ", ""))
				.append('\n');
		sb.append("Imza: ").append(context.signature).append('\n');
		sb.append("Cagirdigi fonksiyonlar: ").append(context.calledFunctionsText()).append("\n\n");

		sb.append("## DEGISKENLER (isimlendirme icin kullan)\n");
		if (context.symbols.isEmpty()) {
			sb.append("(sembol bilgisi alinamadi)\n");
		}
		else {
			for (DecompiledContext.SymbolInfo symbol : context.symbols) {
				sb.append("- ").append(symbol.describe()).append('\n');
			}
		}
		sb.append('\n');

		sb.append("## DECOMPILE EDILMIS KOD\n");
		sb.append("(her satirin basindaki [0x...] etiketi o satirin adresidir)\n");
		sb.append("```c\n");
		sb.append(context.annotatedCode);
		sb.append("```\n\n");

		if (context.codeTruncated) {
			sb.append("NOT: Kod uzunlugu nedeniyle kisaltildi; sadece gosterilen kisim hakkinda ")
					.append("yorum yap.\n\n");
		}

		sb.append("Simdi yalnizca JSON dondur.");
		return sb.toString();
	}
}
