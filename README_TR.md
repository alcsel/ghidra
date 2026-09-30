# Ghidra AI Eklentisi - Çoklu Yapay Zeka Asistanı (v2.0)

[![Ghidra](https://img.shields.io/badge/Ghidra-12.1.2%2B-blue.svg)](https://ghidra-sre.org/)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)
[![Multi-AI](https://img.shields.io/badge/AI-OpenAI%20%7C%20Claude%20%7C%20Gemini%20%7C%20DeepSeek%20%7C%20Ollama-blueviolet.svg)](https://github.com/ghidra-ai)

Ghidra decompiler arayüzüne doğrudan entegre olan, tüm majör Büyük Dil Modellerini (LLM) destekleyen gelişmiş tersine mühendislik yapay zeka eklentisi. İster bulut servislerini (OpenAI, Claude, Gemini, DeepSeek, Groq, OpenRouter) kullanın, ister veri gizliliği için yerel **Ollama** modelleriyle %100 çevrimdışı (offline) çalışın.

---

### Desteklenen Yapay Zeka Sağlayıcıları ve Modeller

| Sağlayıcı | Desteklenen Modeller | Protokol | Anahtar Gerekli mi? | Çevre Değişkeni (Env Var) | En İyi Kullanım Alanı |
| :--- | :--- | :--- | :---: | :--- | :--- |
| **OpenAI** | `gpt-4o`, `gpt-4o-mini`, `o1`, `o3-mini`, `gpt-4.5` | OpenAI Chat | Evet | `OPENAI_API_KEY` | Genel tersine mühendislik, karmaşık algoritma analizi |
| **Anthropic Claude** | `claude-3-7-sonnet-20250219`, `claude-3-5-sonnet`, `claude-3-5-haiku` | Anthropic Messages | Evet | `ANTHROPIC_API_KEY` | Derin mantıksal akıl yürütme (hybrid thinking), büyük fonksiyonlar |
| **Google Gemini** | `gemini-2.5-pro`, `gemini-2.5-flash`, `gemini-2.0-flash` | OpenAI Uyumlu | Evet | `GEMINI_API_KEY`, `GOOGLE_API_KEY` | Yüksek hız, devasa bağlam penceresi, uygun maliyet |
| **DeepSeek** | `deepseek-chat` (V3), `deepseek-reasoner` (R1) | OpenAI Chat | Evet | `DEEPSEEK_API_KEY` | Düşük maliyetle yüksek başarımlı kod analizi ve R1 mantık yürütme |
| **Ollama** *(Yerel / Çevrimdışı)* | `qwen2.5-coder:32b`, `llama3.3:70b`, `deepseek-r1:14b`, `codellama` | OpenAI Chat | **Hayır (Ücretsiz/Offline)** | `OLLAMA_API_KEY` *(opsiyonel)* | **%100 gizlilik gerektiren, dışarı veri sızmaması gereken şirket/kurum analizleri** |
| **Groq** | `llama-3.3-70b-versatile`, `deepseek-r1-distill-llama-70b` | OpenAI Chat | Evet | `GROQ_API_KEY` | Ultra hızlı LPU çıkarımı (saniyede yüzlerce token) |
| **OpenRouter** | `anthropic/claude-3.7-sonnet`, `deepseek/deepseek-r1`, `openai/gpt-4o` | OpenAI Chat | Evet | `OPENROUTER_API_KEY` | Tek API anahtarı ile 200'den fazla farklı laboratuvar modeline erişim |
| **Mistral AI** | `codestral-latest`, `mistral-large-latest` | OpenAI Chat | Evet | `MISTRAL_API_KEY` | Kod için özel optimize edilmiş modeller |
| **xAI** | `grok-2`, `grok-2-mini`, `grok-beta` | OpenAI Chat | Evet | `XAI_API_KEY` | xAI Grok serisi modelleri |
| **Özel / Yerel Sunucu** | `local-model`, `custom-model` | OpenAI Chat | İsteğe Bağlı | `CUSTOM_API_KEY`, `AI_API_KEY` | Özel LM Studio, vLLM, TextGenWebUI veya Azure OpenAI sunucuları |

---

### Temel Yetenekler

| Yetenek | Açıklama |
| :--- | :--- |
| **Çoklu Sağlayıcı Merkezi** | OpenAI, Claude, Gemini, DeepSeek, Ollama, Groq ve diğerleri arasında tek tıkla geçiş yapın. |
| **Dinamik Model Keşfi** | Ayarlar penceresindeki **Modelleri Çek 🔄** butonu ile yerel Ollama veya sunucudaki yüklü modelleri anında listeleyin. |
| **Sağlayıcı Bazlı Anahtar Belleği** | Sağlayıcılar arasında geçiş yaptığınızda girdiğiniz API anahtarları, özel URL ve seçtiğiniz model kaybolmaz. |
| **Fonksiyon Açıklaması** | Decompile edilen mantığın ve algoritmanın doğal dilde detaylı açıklamasını sunar. |
| **Satır İçi Teknik Yorumlar** | Şifrelenmiş, karmaşık veya dikkat çeken matematiksel blokları tespit eder ve doğrudan adreslere yorum ekler. |
| **Değişken İsimlendirme** | Derleyici kalıntılarını (`uVar1`, `param_1`, `iVar3`), anlamsal isimlerle (`packet_len`, `buffer_ptr`) değiştirir. |
| **Fonksiyon İsimlendirme** | Fonksiyonun amacına uygun net bir isim ve giriş noktası için blok yorumu önerir. |
| **Belirsizlik Tespiti** | Halüsinasyon görmez; emin olunamayan kısımları "Belirsizlikler" sekmesinde listeler. |
| **Etkileşimli İnceleme Arayüzü** | Önerileri onaylamadan önce filtreleyebilir, adreslere çift tıklayarak decompiler'da zıplayabilirsiniz. |
| **Toplu Fonksiyon Analizi (Batch)** | Tüm binary'yi otomatik olarak sırayla analiz eder, isimlendirir ve zenginleştirilmiş C dosyası olarak dışa aktarır. |
| **Sıfır Dış Bağımlılık** | Ghidra'nın dahili kütüphaneleriyle (`java.net.http`, `Gson`) derlenir; harici JAR dosyası gerektirmez. |

---

## 1. Gereksinimler

- **Ghidra**: `12.1.2` (veya `11.x`+)
- **JDK**: `21+` (Örn. Eclipse Adoptium Temurin 21, Microsoft OpenJDK 21)
- **Yapay Zeka Erişimi**: Seçtiğiniz sağlayıcıdan API anahtarı veya yerel **Ollama** kurulumu.

---

## 2. Kurulum

### Yöntem A - PowerShell Scripti (Tavsiye Edilen, Çevrimdışı)

Gradle kurulumuna veya internet bağlantısına gerek duymaz. Ghidra'nın dahili JAR dosyalarıyla derler:

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

Ghidra farklı bir dizindeyse yolu belirtin:

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1 -GhidraDir "C:\Tools\ghidra_12.1.2_PUBLIC"
```

Scriptin yaptığı işlemler:
1. Kaynak kodları `javac` ile derler.
2. `DeepSeekAI.tool` şablonunu Ghidra'nın `CodeBrowser.tool` şablonundan türeterek eklentiyi entegre eder.
3. `dist/` klasörü altına sürüm zip paketini oluşturur.
4. Eklentiyi doğrudan `Ghidra/Extensions/DeepSeekAI` dizinine ve kullanıcı araç kutusuna kurar.

### Yöntem B - Manuel ZIP Kurulumu

1. Ghidra ana ekranında: `File > Install Extensions...`
2. Sağ üstteki `+` (yeşil artı) butonuna tıklayın.
3. `dist/ghidra_12.1.2_PUBLIC_YYYYMMDD_DeepSeekAI.zip` dosyasını seçin.
4. Ghidra'yı yeniden başlatın.

---

## 3. Yapılandırma ve API Anahtarları

### Seçenek 1 - Etkileşimli Ayarlar Penceresi (GUI)

1. Ghidra CodeBrowser'ı açın.
2. `Tools > AI Assistant > Settings (Providers & Keys)...` menüsüne tıklayın.
3. İstediğiniz Yapay Zeka sağlayıcısını seçin.
4. API anahtarınızı girin (veya `Get Key ↗` butonuna basarak doğrudan sağlayıcı konsoluna gidin).
5. Ollama veya OpenAI için `Fetch Models 🔄` butonuna basarak modelleri çekin.
6. `Test Connection` butonuna basarak bağlantıyı ve gecikmeyi test edin.
7. `Save & Apply` butonuna basarak kaydedin.

### Seçenek 2 - Ortam Değişkenleri (Environment Variables)

Eklenti, aşağıdaki ortam değişkenlerini otomatik olarak okur:

| Sağlayıcı | Ortam Değişkeni |
| :--- | :--- |
| **OpenAI** | `OPENAI_API_KEY` |
| **Anthropic Claude** | `ANTHROPIC_API_KEY` |
| **Google Gemini** | `GEMINI_API_KEY` veya `GOOGLE_API_KEY` |
| **DeepSeek** | `DEEPSEEK_API_KEY` |
| **Groq** | `GROQ_API_KEY` |
| **OpenRouter** | `OPENROUTER_API_KEY` |
| **Mistral** | `MISTRAL_API_KEY` |
| **xAI Grok** | `XAI_API_KEY` |
| **Özel / Custom** | `CUSTOM_API_KEY` veya `AI_API_KEY` |

PowerShell ile tanımlama:
```powershell
[System.Environment]::SetEnvironmentVariable("OPENAI_API_KEY", "sk-...", "User")
[System.Environment]::SetEnvironmentVariable("ANTHROPIC_API_KEY", "sk-ant-...", "User")
[System.Environment]::SetEnvironmentVariable("DEEPSEEK_API_KEY", "sk-...", "User")
```

### Seçenek 3 - Yerel Özellikler Dosyası (`.properties`)

Kullanıcı ev dizininizde `~/.ghidra_ai.properties` (veya `~/.deepseek_ghidra.properties`) dosyası oluşturun:

```properties
# Aktif sağlayıcı: OPENAI, ANTHROPIC, GEMINI, DEEPSEEK, OLLAMA, GROQ, OPENROUTER, MISTRAL, XAI
provider=OPENAI

# Sağlayıcı bazlı API anahtarları
openai.apiKey=sk-...
anthropic.apiKey=sk-ant-...
gemini.apiKey=AIzaSy...
deepseek.apiKey=sk-...
groq.apiKey=gsk_...
openrouter.apiKey=sk-or-...
mistral.apiKey=...
xai.apiKey=...
```

---

## 4. Kullanım

### 4.1 Tekil Fonksiyon Analizi

1. İmlecinizi Listing veya Decompiler penceresinde analiz etmek istediğiniz fonksiyonun içine getirin.
2. `Tools > AI Assistant > Analyze Function` seçeneğine tıklayın.
3. Yapay zeka kodu analiz eder ve sonuç inceleme penceresi açılır:
   - **Özet**: Fonksiyonun genel amacı ve çalışma mantığı.
   - **Değişken İsimleri**: Önerilen yeni isimler, tipler ve güven puanları.
   - **Satır Yorumları**: Önemli talimatlara eklenecek teknik açıklamalar.
   - **Karmaşık Bloklar**: Şifreleme, matematik veya özel algoritma tespitleri.
   - **Belirsizlikler**: İnsan doğrulaması tavsiye edilen şüpheli durumlar.
4. İstediğiniz değişiklikleri seçip **Apply Confirmed Changes** butonuna basarak Ghidra veritabanına tek işlemde (transaction) uygulayın.

### 4.2 Hızlı Sağlayıcı Değiştirme

GPT-4o, Claude 3.7 ve yerel Ollama modellerini birbirleriyle kıyaslamak mı istiyorsunuz?
- Menüden `Tools > AI Assistant > Quick Switch Provider / Model...` seçeneğine tıklayın.
- Listeden yeni sağlayıcıyı seçin; tüm ayarlar ve anahtarlar anında devreye girer!

### 4.3 %100 Çevrimdışı (Offline) Tersine Mühendislik (Ollama)

Gizli şirket dosyaları veya internet bağlantısı olmayan güvenli laboratuvar ortamları için:
1. Bilgisayarınıza [Ollama](https://ollama.com/) kurun ve modeli çalıştırın:
   ```bash
   ollama run qwen2.5-coder:32b
   ```
2. Ghidra'da `Tools > AI Assistant > Settings (Providers & Keys)...` penceresini açın.
3. Sağlayıcı olarak **Ollama (Local / Offline RE)** seçin.
4. Base URL varsayılan olarak `http://localhost:11434/v1` gelecektir. API anahtarı gerekmez!
5. `Fetch Models 🔄` butonuna basarak yüklü modelinizi seçin.
6. `Save & Apply` ile kaydedin. Kodlarınız cihazınızdan asla dışarı çıkmaz!

### 4.4 Toplu Analiz (Batch Analysis)

Tüm binary'yi veya seçili fonksiyon kümesini topluca analiz edin:
1. `Tools > AI Assistant > Batch Analyze Functions...` penceresini açın.
2. Filtreleri belirleyin (kütüphane fonksiyonlarını atla, boyut sınırları vb.).
3. Otomatik isimlendirme, yorum ekleme ve `.c` çıktı dosyası oluşturmayı aktifleştirin.
4. **Start Batch Analysis** butonuna basarak işlemi başlatın.

---

## 5. Teşhis ve Test Scriptleri

Ghidra `Script Manager` veya `analyzeHeadless` üzerinden çalıştırabileceğiniz scriptler:

| Script | Görevi |
| :--- | :--- |
| `CheckDeepSeekInstall.java` | Eklenti kurulumunu, bağımlılıkları ve 10 sağlayıcı entegrasyonunu doğrular. |
| `TestAiApi.java` | GUI veya komut satırından herhangi bir yapay zeka sağlayıcısına ping atarak bağlantıyı test eder. |
| `TestBatchAnalysis.java` | Toplu analiz motorunu test eder. |
| `CheckPluginRegistration.java` | Aktif araçtaki eklenti kayıt durumunu denetler. |

Komut satırından bağlantı testi örneği:
```powershell
analyzeHeadless.bat C:\Temp TempProj -postScript TestAiApi.java openai gpt-4o sk-...
analyzeHeadless.bat C:\Temp TempProj -postScript TestAiApi.java anthropic claude-3-7-sonnet-20250219 sk-ant-...
analyzeHeadless.bat C:\Temp TempProj -postScript TestAiApi.java ollama qwen2.5-coder:32b
```

---

## 6. Lisans

Apache License 2.0 ile lisanslanmıştır. Detaylar için [LICENSE](LICENSE) dosyasına bakınız.