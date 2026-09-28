# DeepSeek AI — Ghidra Eklentisi

Ghidra içinden **DeepSeek API** ile decompile edilmiş kodu analiz eden eklenti.

Kısaca yaptığı işler:

| Yetenek | Açıklama |
|---|---|
| **Açıklama** | Seçili fonksiyonun ne yaptığını ayrıntılı biçimde Türkçe açıklar. |
| **Yorum yazma** | Anlaşılması zor satırları bulur ve o satırlara teknik yorum önerir. |
| **Değişken isimlendirme** | `uVar1`, `param_1`, `iVar3` gibi isimleri anlamlı isimlerle (`packet_length`, `socket_fd`, `buffer`) değiştirir. |
| **Fonksiyon isimlendirme** | Anlamlıysa fonksiyona yeni isim ve blok yorumu önerir. |
| **Belirsizlik raporu** | Emin olmadığı noktaları ayrı bir sekmede listeler (uydurma yapmaz). |

Tüm öneriler önce bir sonuç penceresinde gösterilir, siz onaylamadan programa **hiçbir şey** uygulanmaz. Uygulama tek bir Ghidra transaction'ında yapılır, yani **Ctrl+Z** ile tamamen geri alınabilir.

---

## 1. Gereksinimler

- Ghidra **12.x** (test: 12.1.2 PUBLIC)
- **JDK 21+** (Ghidra 12 zaten Java 21 ister)
- İnternet bağlantısı ve bir **DeepSeek API anahtarı** (`sk-...`) — <https://platform.deepseek.com>

## 2. Kurulum

### Yol A — Betikle (önerilen, internet gerekmez)

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

Betik şunları yapar:

1. Kaynakları Ghidra'nın kendi jar'larına karşı `javac` ile derler.
2. `DeepSeekAI.tool` araç şablonunu Ghidra'nın gerçek `CodeBrowser.tool` dosyasından üretir ve `deepseekai.DeepSeekAIPlugin` sınıfını içine ekler.
3. `dist\ghidra_12.1.2_PUBLIC_<tarih>_DeepSeekAI.zip` paketini oluşturur.
4. Eklentiyi `Ghidra\Extensions\DeepSeekAI\` altına **kurar** ve araç şablonunu `%USERPROFILE%\.ghidra\.ghidra_12.1.2_PUBLIC\tools\DeepSeekAI.tool` altına kopyalar.

Betik parametreleri:

| Parametre | Açıklama |
|---|---|
| `-GhidraDir` | Ghidra kurulum dizini. Verilmezse `GHIDRA_INSTALL_DIR` ortam değişkeni, o da yoksa `Desktop`/`Documents`/`Downloads`/`C:\`/`C:\Tools`/`D:\` altında `ghidra_*_PUBLIC` otomatik aranır. |
| `-JavaHome` | JDK 21+ dizini. Verilmezse `JAVA_HOME`, o da yoksa bilinen Adoptium/Corretto yolları denenir. |
| `-Author` | `extension.properties` içine yazılacak yazar adı (varsayılan: `Selim Calici`). |
| `-NoInstall` | Sadece derle ve paketle; Ghidra kurulumuna kopyalama. |

```powershell
.\build.ps1 -GhidraDir "D:\ghidra_12.1.2_PUBLIC" -JavaHome "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
.\build.ps1 -NoInstall
```

### Yol B — Gradle ile (internet gerekir)

Depoda standart Gradle wrapper bulunur. Gradle 9.4.1 otomatik indirilir.

```powershell
$env:GHIDRA_INSTALL_DIR = "C:\yol\ghidra_12.1.2_PUBLIC"
.\gradlew.bat buildExtension
```

`-P` ile de verilebilir: `.\gradlew.bat -PGHIDRA_INSTALL_DIR=C:\yol\ghidra_12.1.2_PUBLIC buildExtension`

Çıktı: `dist\ghidra_<sürüm>_<tarih>_DeepSeekAI.zip`

> Not: `build.ps1` ve Gradle yolu aynı dosya adıyla aynı paketi üretir.
> `build.ps1` internet gerektirmez ve eklentiyi doğrudan Ghidra kurulumunuza da kopyalar.

### Yol C — Ghidra arayüzü ile

1. Ghidra'yı açın.
2. **File → Install Extensions… → +** ve `dist\..._DeepSeekAI.zip` dosyasını seçin.
3. Ghidra'yı yeniden başlatın.

### Kurulumdan sonra kontrol

- Menüde **Tools → DeepSeek AI** görünüyor mu?
- Görünmüyorsa: **File → Configure →** arama kutusuna `DeepSeek` yazın →
  **DeepSeek AI** satırını işaretleyin → OK.

## 3. Kullanım

1. Bir program açın ve analiz edilmiş (decompile edilebilen) bir fonksiyonun **içine** tıklayın.
2. **Tools → DeepSeek AI → Ayarlar (API anahtarı)…** menüsünden API anahtarınızı girin
   ve **Bağlantıyı Test Et** ile doğrulayın.
3. **Tools → DeepSeek AI → Fonksiyonu Analiz Et** (veya doğrudan menüdeki bu komut).
4. Birkaç saniye içinde sonuç penceresi açılır:

| Sekme | İçerik |
|---|---|
| **Açıklama** | Fonksiyonun ne yaptığı. |
| **Yorumlar** | `Adres → yorum` önerileri (işaretli olanlar uygulanır). |
| **Değişkenler** | `Eski ad → Yeni ad` önerileri, tip ve güven puanı. |
| **Zor Kısımlar** | Neden zor olduğu + önerilen yorum. |
| **Belirsizlikler** | Modelin emin olmadığı noktalar. |
| **Ham Yanıt** | API'den dönen ham metin (hata ayıklama için). |

5. **Seçilenleri Uygula** düğmesine basın. Tüm değişiklikler tek transaction'da uygulanır.

### Kısayol: otomatik uygulama

Ayarlar'da *sormadan uygula* seçeneklerini işaretlerseniz analiz biter bitmez sonuçlar
doğrudan programa yazılır (sonuç penceresi açılmaz, sadece özet gösterilir).

## 4. Ayarlar

Ayarlar hem eklentinin kendi penceresinden hem de **Edit → Tool Options → DeepSeek AI**
üzerinden değiştirilebilir:

| Ayar | Varsayılan | Açıklama |
|---|---|---|
| API Key | (boş) | DeepSeek API anahtarı. |
| API Base URL | `https://api.deepseek.com` | Uç nokta. |
| Model | `deepseek-chat` | `deepseek-chat` (hızlı) veya `deepseek-reasoner` (derin akıl yürütme). |
| Temperature | `0.2` | Düşük = daha kararlı sonuç. |
| Max Tokens | `8192` | Yanıt uzunluğu sınırı. |
| Request Timeout | `180` sn | Ağ zaman aşımı. |
| Response Language | `Turkce` | Açıklama ve yorumların dili. |
| Max decompiled code characters | `24000` | API'ye gönderilen kodun üst sınırı. |
| Auto apply … | kapalı | Analiz bittikten sonra onay sormadan uygula. |

### API anahtarı nerede saklanır?

Öncelik sırası:

1. **Ayarlar penceresi / Edit → Tool Options → DeepSeek AI** — girdiğiniz anahtar
   Ghidra'nın tool options dosyasında saklanır (`%USERPROFILE%\.ghidra\.ghidra_<sürüm>\...`).
2. **`DEEPSEEK_API_KEY` ortam değişkeni** — bu dosyaya hiç yazılmaz.
3. **`%USERPROFILE%\.deepseek_ghidra.properties`** — yerel gizli anahtar dosyası:

```properties
apiKey=sk-xxxxxxxxxxxxxxxx
```

Ortam değişkenini kalıcı olarak tanımlamak:

```powershell
setx DEEPSEEK_API_KEY "sk-xxxxxxxxxxxxxxxx"
```

> **Güvenlik:** Kaynak kodda hiçbir API anahtarı yoktur; depoyu güvenle paylaşabilirsiniz.
> `.gitignore` yerel sır dosyalarını (`secrets.properties`, `*.local.properties`,
> `.deepseek_ghidra.properties`) zaten dışlar. Anahtarınızı `extension.properties`,
> `build.gradle` gibi depoya giren dosyalara **yazmayın**.

## 5. Nasıl çalışır?

1. **Decompile** — `DecompInterface` ile fonksiyon C pseudocode'una çevrilir.
2. **Adres etiketleme** — decompiler'ın `ClangTokenGroup` işaretlemesi (markup) gezilerek
   her satırın başına `[0x00401020]` biçiminde adres etiketi eklenir. Böylece modelin
   verdiği yorum adresleri birebir eşleşir.
3. **Sembol tablosu** — `HighFunction.getLocalSymbolMap()` üzerinden parametreler ve
   yerel değişkenler (tip, konum, adres) modele verilir.
4. **İstem (prompt)** — modele katı bir JSON şeması dayatılır: `summary`,
   `function_name`, `function_comment`, `hard_parts`, `line_comments`,
   `variable_renames`, `uncertainties`.
5. **Doğrulama** — dönen adresler `AddressSpace` ile çözümlenir; değişken adları
   geçerli C tanımlayıcısı / C anahtar kelimesi değil / yinelenen isim yok /
   adres yinelenmiyor kontrollerinden geçer. Uymayan öneriler otomatik işaretsiz gelir.
6. **Uygulama** — `HighFunctionDBUtil.updateDBVariable(...)`,
   `Function.setName(...)`, `Listing.setComment(...)` çağrılarıyla tek transaction.

## 6. Sorun giderme

| Belirti | Çözüm |
|---|---|
| Menüde "DeepSeek AI" yok | **File → Configure** → arama → **DeepSeek AI** işaretleyin. Eklenti kurulu mu: `Ghidra\Extensions\DeepSeekAI\lib\DeepSeekAI.jar` |
| `HTTP 401` | API anahtarı hatalı/boş. **Ayarlar → Bağlantıyı Test Et**. |
| `HTTP 402` | DeepSeek hesabınızda bakiye yok. |
| `Decompile edilemedi` | Fonksiyon çok büyük ya da decompiler desteklemiyor; başka bir fonksiyon deneyin. |
| `Yanıt JSON olarak çözümlenemedi` | Yanıt penceresindeki **Ham Yanıt** sekmesine bakın. `Temperature` değerini düşürün veya `deepseek-chat` modeline geçin. |
| Yorumlar/değişkenler uygulanmıyor | Modelin verdiği adres koda çözümlenmemiş olabilir; tabloda `[adres çözümlenemedi]` notunu arayın. |
| Eklenti yüklenmiyor (Log) | `%USERPROFILE%\.ghidra\.ghidra_<sürüm>\application.log` dosyasına bakın. |

## 7. Kaynak düzeni

```
DeepSeekGhidra\
├── build.ps1                         Derle + paketle + kur (offline, javac)
├── build.gradle / settings.gradle     Ghidra Gradle derlemesi (online)
├── gradlew / gradlew.bat / gradle\    Standart Gradle wrapper (9.4.1)
├── extension.properties               Eklenti meta verisi
├── Module.manifest                    Ghidra modul isareti
├── LICENSE                            Apache License 2.0
├── README.md
├── .gitignore / .gitattributes
├── .github\workflows\build.yml        CI: derle + Release
├── src\main\java\deepseekai\
│   ├── DeepSeekAIPlugin.java          Ana eklenti (menuler, akis)
│   ├── DeepSeekConfig.java            Ayarlar
│   ├── DeepSeekClient.java            HTTP + JSON istemcisi
│   ├── DecompilerHelper.java          Decompile + markup -> adresli satirlar
│   ├── DecompiledContext.java         Fonksiyon baglami (DTO)
│   ├── Prompt.java                    Sistem/kullanici istemleri
│   ├── AnalysisOutcome.java           JSON yanitini ayristirma + dogrulama
│   ├── DeepSeekAnalyzeTask.java       Arka plan analiz gorevi
│   ├── DeepSeekApplyTask.java         Degisiklikleri uygulayan gorev
│   ├── DeepSeekResultDialog.java      Sonuc penceresi
│   └── DeepSeekOptionsDialog.java     Ayar penceresi
├── src\main\resources\defaultTools\
│   └── DeepSeekAI.tool                Ghidra arac sablonu (jar'a girer)
└── ghidra_scripts\                    Teshis betikleri (Script Manager'da gorunur)
    ├── CheckDeepSeekInstall.java      Kurulum dogrulama
    ├── TestDeepSeekApi.java           API anahtari testi
    └── DumpFunctionContext.java       Modele giden baglamin dokumu
```

Oluşan paketin içeriği:

```
DeepSeekAI/
├── extension.properties
├── Module.manifest
├── LICENSE
├── README.md
├── DeepSeekAI.tool                    (ayrıca jar içinde defaultTools/ altında)
├── ghidra_scripts/*.java
└── lib/
    ├── DeepSeekAI.jar                 deepseekai/*.class + defaultTools/DeepSeekAI.tool
    └── DeepSeekAI-src.zip             (yalnızca Gradle derlemesinde)
```

## 7b. Doğrulama (teşhis betikleri)

Eklenti kurulduktan sonra **Window → Script Manager** üzerinden (veya
`analyzeHeadless ... -postScript <betik>` ile) şu betikler çalıştırılabilir:

| Betik | Ne yapar |
|---|---|
| `CheckDeepSeekInstall.java` | Eklenti sınıfını `ClassSearcher` ile arar, Gson/`java.net.http` bağımlılıklarını, JSON ayrıştırmayı ve `DeepSeekAI` araç şablonunun Ghidra tarafından görülüp görülmediğini test eder. **API çağrısı yapmaz.** |
| `TestDeepSeekApi.java` | API anahtarını ve uç noktayı gerçek çok küçük bir istek ile doğrular (~30 token). Anahtarı `DEEPSEEK_API_KEY` ortam değişkeninden veya betik argümanından alır. |
| `DumpFunctionContext.java` | Seçili fonksiyon için modele giden bağlamı (adresli kod, sembol tablosu, istem uzunlukları) döker. `api` argümanı verilirse gerçek analiz çağrısını da yapar. |

`CheckDeepSeekInstall.java` beklenen çıktısı:

```
[OK]   Eklenti sinifi bulundu : deepseekai.DeepSeekAIPlugin
[OK]   Gson kutuphanesi erisilebilir
[OK]   java.net.http.HttpClient erisilebilir
[OK]   deepseekai.DeepSeekConfig orneklenebiliyor
[OK]   JSON ayristirma calisiyor (1 degisken, 1 yorum, onerilen ad: do_something)
[OK]   DeepSeekAI arac sablonu bulundu (jar icindeki defaultTools/)
SONUC: Tum kontroller basarili. Eklenti kullanima hazir.
```

## 8. Sınırlamalar

- Tek seferde **bir** fonksiyon analiz edilir (imlecin bulunduğu fonksiyon).
- Tip önerileri yalnızca Ghidra'nın yerleşik tiplerine eşlenebiliyorsa uygulanır
  (`int`, `uint`, `char`, `undefined4`, …); `struct`/pointer önerileri yok sayılır.
- Model yanıltıcı olabilir. Kritik analizlerde önerileri doğrulayın.

## 9. GitHub

Depo doğrudan GitHub'a gönderilmeye hazırdır:

- `.gitignore` — `build/`, `dist/`, `.gradle/`, IDE dosyaları ve yerel sır dosyaları hariç.
- `.gitattributes` — `gradlew` LF, `.bat`/`.ps1` CRLF, ikili dosyalar `binary`.
- `LICENSE` — Apache License 2.0.
- `.github/workflows/build.yml` — her push/PR'da eklentiyi Gradle ile derler ve
  `dist/*.zip` artefaktını yükler; `v*` biçiminde bir etiket atıldığında zip'i
  Release'e ekler.

```powershell
git init
git add .
git commit -m "DeepSeek AI Ghidra eklentisi: ilk surum"
git branch -M main
git remote add origin https://github.com/<kullanici>/<depo>.git
git push -u origin main
```

Yeni sürüm yayınlamak:

```powershell
git tag v1.0.0
git push origin v1.0.0
```

> CI, Ghidra'yı GitHub Releases üzerinden indirir. Farklı bir Ghidra sürümü
> kullanacaksanız `.github/workflows/build.yml` içindeki `GHIDRA_VERSION`
> değerini güncelleyin.

## 10. Lisans

[Apache License 2.0](LICENSE) — Ghidra eklenti şablonu ile aynı lisans.
