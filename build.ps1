<#
.SYNOPSIS
    DeepSeek AI Ghidra eklentisini derler, paketler ve kurar.

.DESCRIPTION
    Bu betik Ghidra'nin kendi jar dosyalarini kullanarak eklentiyi (internet
    gerektirmeden) javac ile derler, bir eklenti arsivi (zip) olusturur ve
    istege bagli olarak dogrudan Ghidra kurulumunun icine kurar.

.PARAMETER GhidraDir
    Ghidra kurulum dizini. Verilmezse GHIDRA_INSTALL_DIR ortam degiskeni,
    yoksa asagidaki varsayilan yol kullanilir.

.PARAMETER JavaHome
    JDK 21+ kurulum dizini. Verilmezse JAVA_HOME, yoksa bilinen
    Eclipse Adoptium yolu kullanilir.

.PARAMETER NoInstall
    Sadece derle ve paketle; Ghidra kurulumuna kopyalama.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\build.ps1
#>
[CmdletBinding()]
param(
    [string]$GhidraDir = "",
    [string]$JavaHome = "",
    [string]$Author = "Selim Calici",
    [switch]$NoInstall,
    [switch]$RefreshTool
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$SrcDir      = Join-Path $ProjectRoot "src\main\java"
$ResDir      = Join-Path $ProjectRoot "src\main\resources"
$BuildDir    = Join-Path $ProjectRoot "build"
$DistDir     = Join-Path $ProjectRoot "dist"
$ExtName     = "DeepSeekAI"

function Write-Step($text) { Write-Host "==> $text" -ForegroundColor Cyan }
function Write-Ok($text)   { Write-Host "    $text" -ForegroundColor Green }
function Write-Warn2($text){ Write-Host "    $text" -ForegroundColor Yellow }

# ---------------------------------------------------------------- Ghidra
if (-not $GhidraDir) { $GhidraDir = $env:GHIDRA_INSTALL_DIR }
if (-not $GhidraDir) {
    Write-Step "Ghidra kurulumu araniyor..."
    $roots = @(
        (Join-Path $env:USERPROFILE "Desktop"),
        (Join-Path $env:USERPROFILE "Documents"),
        (Join-Path $env:USERPROFILE "Downloads"),
        "C:\",
        "C:\Tools",
        "C:\Program Files",
        "D:\"
    )
    foreach ($root in $roots) {
        if (-not (Test-Path $root)) { continue }
        $candidate = Get-ChildItem -Path $root -Directory -Filter "ghidra_*_PUBLIC" `
                -ErrorAction SilentlyContinue |
            Where-Object { Test-Path (Join-Path $_.FullName "Ghidra\application.properties") } |
            Sort-Object Name -Descending | Select-Object -First 1
        if ($candidate) {
            $GhidraDir = $candidate.FullName
            Write-Ok "Bulundu: $GhidraDir"
            break
        }
    }
}
if (-not $GhidraDir -or -not (Test-Path (Join-Path $GhidraDir "Ghidra\application.properties"))) {
    throw ("Ghidra kurulumu bulunamadi. -GhidraDir parametresini ya da " +
        "GHIDRA_INSTALL_DIR ortam degiskenini kullanin.")
}
$GhidraDir = (Resolve-Path $GhidraDir).Path
$GhidraVersion = (Select-String -Path (Join-Path $GhidraDir "Ghidra\application.properties") `
        -Pattern '^application\.version=(.*)$').Matches[0].Groups[1].Value.Trim()
$GhidraRelease = (Select-String -Path (Join-Path $GhidraDir "Ghidra\application.properties") `
        -Pattern '^application\.release\.name=(.*)$').Matches[0].Groups[1].Value.Trim()
Write-Step "Ghidra: $GhidraDir (surum $GhidraVersion $GhidraRelease)"

# ---------------------------------------------------------------- JDK
if (-not $JavaHome) { $JavaHome = $env:JAVA_HOME }
if (-not $JavaHome -or -not (Test-Path (Join-Path $JavaHome "bin\javac.exe"))) {
    $candidates = @(
        "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot",
        "C:\Program Files\Java\jdk-21",
        "C:\Program Files\Microsoft\jdk-21.0.0.504-hotspot",
        "C:\Program Files\Amazon Corretto\jdk21"
    )
    foreach ($c in $candidates) {
        if (Test-Path (Join-Path $c "bin\javac.exe")) { $JavaHome = $c; break }
    }
}
if (-not $JavaHome -or -not (Test-Path (Join-Path $JavaHome "bin\javac.exe"))) {
    throw "JDK 21 bulunamadi. -JavaHome parametresi ile JDK 21 dizinini belirtin."
}
$Javac = Join-Path $JavaHome "bin\javac.exe"
$JarExe = Join-Path $JavaHome "bin\jar.exe"
$javaVersion = (& $Javac -version 2>&1) -join " "
Write-Step "Derleyici: $javaVersion"
if ($javaVersion -notmatch "2[1-9]|3[0-9]") {
    Write-Warn2 "UYARI: Ghidra 12 icin Java 21+ onerilir."
}

# ---------------------------------------------------------------- classpath
Write-Step "Sinif yolu (classpath) hazirlaniyor..."
$jarPatterns = @(
    "Ghidra\Framework\*\lib\*.jar",
    "Ghidra\Features\*\lib\*.jar",
    "Ghidra\Configurations\*\lib\*.jar",
    "Ghidra\Processors\*\lib\*.jar",
    "Ghidra\Debug\*\lib\*.jar"
)
$jars = New-Object System.Collections.Generic.List[string]
foreach ($pattern in $jarPatterns) {
    $full = Join-Path $GhidraDir $pattern
    Get-ChildItem -Path $full -ErrorAction SilentlyContinue | ForEach-Object {
        $jars.Add($_.FullName)
    }
}
$jars = $jars | Sort-Object -Unique
if ($jars.Count -eq 0) { throw "Ghidra jar dosyalari bulunamadi." }
Write-Ok "$($jars.Count) jar dosyasi bulundu"
$classPath = ($jars -join ";")

# ---------------------------------------------------------------- derleme
Write-Step "Kaynaklar derleniyor..."
if (Test-Path $BuildDir) { Remove-Item $BuildDir -Recurse -Force }
$classesDir = Join-Path $BuildDir "classes"
New-Item -ItemType Directory -Path $classesDir -Force | Out-Null

$sources = @(Get-ChildItem -Path $SrcDir -Filter "*.java" -Recurse |
    ForEach-Object { $_.FullName })
if ($sources.Count -eq 0) { throw "Derlenecek kaynak bulunamadi: $SrcDir" }
Write-Ok "$($sources.Count) kaynak dosya"

# Not: kaynak listesi javac'a dogrudan arguman olarak verilir. '@argfile' yontemi
# Turkce karakter iceren yollarda (orn. "Varsayilan Proje") bozulabiliyor.
$oldPref = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$javacOutput = & $Javac -encoding UTF-8 -nowarn -classpath $classPath -d $classesDir $sources 2>&1 |
    Out-String
$javacExit = $LASTEXITCODE
$ErrorActionPreference = $oldPref
if ($javacExit -ne 0) {
    Write-Host $javacOutput
    throw "Derleme basarisiz (javac cikis kodu $javacExit)."
}
Write-Ok "Derleme tamamlandi"

# ---------------------------------------------------------------- .tool dosyasi
Write-Step "Araç (tool) sablonu: $ExtName.tool"
$toolDefaults = Join-Path $ResDir "defaultTools"
New-Item -ItemType Directory -Path $toolDefaults -Force | Out-Null
$toolFile = Join-Path $toolDefaults "$ExtName.tool"

if ((Test-Path $toolFile) -and -not $RefreshTool) {
    # Sablon depoda saklanir; boylece Gradle/CI derlemeleri de onu kullanir.
    Write-Ok "Depodaki mevcut sablon kullaniliyor (yenilemek icin -RefreshTool)"
}
else {
    $publicReleaseJar = Join-Path $GhidraDir "Ghidra\Configurations\Public_Release\lib\Public_Release.jar"
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($publicReleaseJar)
    try {
        $entry = $zip.Entries | Where-Object { $_.FullName -eq "defaultTools/CodeBrowser.tool" } |
            Select-Object -First 1
        if (-not $entry) { throw "CodeBrowser.tool sablonu bulunamadi." }
        $reader = New-Object System.IO.StreamReader($entry.Open())
        $toolXml = $reader.ReadToEnd()
        $reader.Close()
    }
    finally { $zip.Dispose() }

    $toolXml = $toolXml -replace 'TOOL_NAME="CodeBrowser"', "TOOL_NAME=`"$ExtName`""
    $toolXml = $toolXml -replace '(<PACKAGE NAME="Ghidra Core">)',
        "`$1`r`n            <INCLUDE CLASS=`"deepseekai.DeepSeekAIPlugin`" />"
    [System.IO.File]::WriteAllText($toolFile, $toolXml,
        (New-Object System.Text.UTF8Encoding($false)))
    Write-Ok "Ghidra Core paketine deepseekai.DeepSeekAIPlugin eklendi"
}
if (-not (Test-Path $toolFile)) { throw "Araç sablonu olusturulamadi: $toolFile" }

# ---------------------------------------------------------------- jar
Write-Step "Jar paketleniyor..."
$jarFile = Join-Path $BuildDir "$ExtName.jar"
# src/main/resources icerigi (defaultTools/DeepSeekAI.tool dahil) jar kokune kopyalanir.
if (Test-Path $ResDir) {
    Get-ChildItem $ResDir -Force | ForEach-Object {
        Copy-Item $_.FullName -Destination $classesDir -Recurse -Force
    }
}
if (Test-Path $jarFile) { Remove-Item $jarFile -Force }
$oldPref = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$jarOutput = & $JarExe --create --file $jarFile -C $classesDir . 2>&1 | Out-String
$jarExit = $LASTEXITCODE
$ErrorActionPreference = $oldPref
if ($jarExit -ne 0) {
    Write-Host $jarOutput
    throw "jar olusturulamadi."
}
Write-Ok (Split-Path $jarFile -Leaf)

# ---------------------------------------------------------------- eklenti dizini
Write-Step "Eklenti dizini hazirlaniyor..."
$stageDir = Join-Path $BuildDir "stage\$ExtName"
$libDir = Join-Path $stageDir "lib"
New-Item -ItemType Directory -Path $libDir -Force | Out-Null

Copy-Item $jarFile (Join-Path $libDir "$ExtName.jar") -Force
Copy-Item (Join-Path $ProjectRoot "README.md") (Join-Path $stageDir "README.md") -Force
Copy-Item $toolFile (Join-Path $stageDir "$ExtName.tool") -Force
if (Test-Path (Join-Path $ProjectRoot "LICENSE")) {
    Copy-Item (Join-Path $ProjectRoot "LICENSE") (Join-Path $stageDir "LICENSE") -Force
}

# extension.properties ve Module.manifest depo kokunden alinir; boylece
# build.ps1 ile Gradle derlemesi ayni meta veriyi uretir.
$rootModuleManifest = Join-Path $ProjectRoot "Module.manifest"
if (-not (Test-Path $rootModuleManifest)) {
    throw "Module.manifest bulunamadi: $rootModuleManifest"
}
Copy-Item $rootModuleManifest (Join-Path $stageDir "Module.manifest") -Force

$rootExtProps = Join-Path $ProjectRoot "extension.properties"
if (-not (Test-Path $rootExtProps)) {
    throw "extension.properties bulunamadi: $rootExtProps"
}
# extension.properties Ghidra tarafindan Latin-1 olarak okunur; ASCII disi
# karakterleri temizliyoruz ki Turkce harfler bozulmasin.
$authorAscii = ($Author -replace '[^\x20-\x7E]', '').Trim()
if (-not $authorAscii) { $authorAscii = "unknown" }
$extPropsText = [System.IO.File]::ReadAllText($rootExtProps, [System.Text.Encoding]::UTF8)
$extPropsText = $extPropsText -replace '(?m)^author=.*$', "author=$authorAscii"
[System.IO.File]::WriteAllText((Join-Path $stageDir "extension.properties"),
    $extPropsText, (New-Object System.Text.UTF8Encoding($false)))

$scriptsDir = Join-Path $ProjectRoot "ghidra_scripts"
if (Test-Path $scriptsDir) {
    Copy-Item $scriptsDir (Join-Path $stageDir "ghidra_scripts") -Recurse -Force
    Write-Ok "ghidra_scripts (teshis betikleri) eklendi"
}
Write-Ok "$stageDir"

# ---------------------------------------------------------------- zip
Write-Step "Dagitim arsivi (zip) olusturuluyor..."
New-Item -ItemType Directory -Path $DistDir -Force | Out-Null
$stamp = (Get-Date).ToString("yyyyMMdd")
$zipName = "ghidra_${GhidraVersion}_${GhidraRelease}_${stamp}_$ExtName.zip"
$zipPath = Join-Path $DistDir $zipName
if (Test-Path $zipPath) { Remove-Item $zipPath -Force }

# Not: Compress-Archive Windows'ta ters bolu ('\') yazar; Ghidra'nin zip okuyucusu
# duz bolu ('/') bekler. Bu yuzden arsivi elle olusturuyoruz.
function New-ZipFromDirectory($sourceDir, $targetZip) {
    Add-Type -AssemblyName System.IO.Compression
    $fileStream = [System.IO.File]::Open($targetZip, [System.IO.FileMode]::Create)
    try {
        $archive = New-Object System.IO.Compression.ZipArchive(
            $fileStream, [System.IO.Compression.ZipArchiveMode]::Create)
        try {
            $base = (Resolve-Path $sourceDir).Path.TrimEnd('\')
            $hasEntries = $false
            Get-ChildItem $base -Recurse -Force | ForEach-Object {
                $relative = $_.FullName.Substring($base.Length).TrimStart('\') -replace '\\', '/'
                if ($_.PSIsContainer) {
                    $relative = $relative + "/"
                }
                $entry = $archive.CreateEntry($relative,
                    [System.IO.Compression.CompressionLevel]::Optimal)
                if (-not $_.PSIsContainer) {
                    $entryStream = $entry.Open()
                    try {
                        $input = [System.IO.File]::OpenRead($_.FullName)
                        try { $input.CopyTo($entryStream) } finally { $input.Dispose() }
                    }
                    finally { $entryStream.Dispose() }
                }
                $hasEntries = $true
            }
            if (-not $hasEntries) { throw "Arsivlenecek dosya bulunamadi: $base" }
        }
        finally { $archive.Dispose() }
    }
    finally { $fileStream.Dispose() }
}

# Zip kokunde eklenti klasoru olmali: DeepSeekAI/extension.properties ...
New-ZipFromDirectory (Split-Path $stageDir -Parent) $zipPath
Write-Ok $zipPath

# ---------------------------------------------------------------- kurulum
if (-not $NoInstall) {
    Write-Step "Ghidra kurulumuna kopyalaniyor..."
    $targetDir = Join-Path $GhidraDir "Ghidra\Extensions\$ExtName"
    if (Test-Path $targetDir) { Remove-Item $targetDir -Recurse -Force }
    Copy-Item $stageDir $targetDir -Recurse -Force
    Write-Ok $targetDir

    Write-Step "Arac sablonu kullanici araclar klasorune kopyalaniyor..."
    # Ghidra'nin kullanici ayar klasoru platforma gore degisir:
    #   Windows : %APPDATA%\ghidra\ghidra_<surum>_<release>\tools
    #   Linux/Mac: ~/.ghidra/.ghidra_<surum>_<release>/tools
    # Ikisine de kopyalayalim; Ghidra hangisini kullaniyorsa bulur.
    $toolDirs = @()
    if ($env:APPDATA) {
        $toolDirs += (Join-Path $env:APPDATA "ghidra\ghidra_${GhidraVersion}_${GhidraRelease}\tools")
    }
    $toolDirs += (Join-Path $env:USERPROFILE ".ghidra\.ghidra_${GhidraVersion}_${GhidraRelease}\tools")
    foreach ($userTools in $toolDirs) {
        try {
            New-Item -ItemType Directory -Path $userTools -Force | Out-Null
            Copy-Item $toolFile (Join-Path $userTools "$ExtName.tool") -Force
            Write-Ok (Join-Path $userTools "$ExtName.tool")
        }
        catch {
            Write-Warn2 "Kopyalanamadi: $userTools ($($_.Exception.Message))"
        }
    }

    Write-Host ""
    Write-Host "Kurulum tamamlandi. Ghidra'yi yeniden baslatin ve su adimlari izleyin:" -ForegroundColor Green
    Write-Host "  1) Tools > DeepSeek AI > Ayarlar (API anahtari)...  -> anahtarinizi girin"
    Write-Host "  2) Bir ASCII/ARM/x86 fonksiyonunun icine tiklayin"
    Write-Host "  3) Tools > DeepSeek AI > Fonksiyonu Analiz Et"
    Write-Host ""
    Write-Host "  Eklenti gorunmezse: File > Configure > (arama: DeepSeek) > DeepSeek AI isaretleyin."
}
else {
    Write-Host ""
    Write-Host "Paketleme tamamlandi (kurulum atlandi):" -ForegroundColor Green
    Write-Host "  Eklenti arsivi : $zipPath"
    Write-Host "  Derlenmis jar  : $jarFile"
    Write-Host "  Kurmak icin    : File > Install Extensions... > '+' > zip dosyasini secin"
}
