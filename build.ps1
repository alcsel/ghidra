<#
.SYNOPSIS
    Compiles, packages, and installs the DeepSeek AI Ghidra extension.

.DESCRIPTION
    This script compiles the extension using Ghidra's internal jars (offline,
    no internet connection required) via javac, creates an extension zip archive,
    and optionally installs it directly into the local Ghidra installation.

.PARAMETER GhidraDir
    Ghidra installation directory. If omitted, GHIDRA_INSTALL_DIR is used,
    or common installation locations are scanned automatically.

.PARAMETER JavaHome
    JDK 21+ installation directory. If omitted, JAVA_HOME is used,
    or standard JDK 21 installation locations are checked.

.PARAMETER Author
    Author name placed into extension.properties. Default is "Selim Calici".

.PARAMETER NoInstall
    Compile and package only; do not copy into the Ghidra installation directory.

.PARAMETER RefreshTool
    Forces re-extraction and generation of the DeepSeekAI.tool template
    from Ghidra's default CodeBrowser.tool.

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
    Write-Step "Searching for Ghidra installation..."
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
            Write-Ok "Found: $GhidraDir"
            break
        }
    }
}
if (-not $GhidraDir -or -not (Test-Path (Join-Path $GhidraDir "Ghidra\application.properties"))) {
    throw ("Ghidra installation not found. Please specify -GhidraDir or set " +
        "the GHIDRA_INSTALL_DIR environment variable.")
}
$GhidraDir = (Resolve-Path $GhidraDir).Path
$GhidraVersion = (Select-String -Path (Join-Path $GhidraDir "Ghidra\application.properties") `
        -Pattern '^application\.version=(.*)$').Matches[0].Groups[1].Value.Trim()
$GhidraRelease = (Select-String -Path (Join-Path $GhidraDir "Ghidra\application.properties") `
        -Pattern '^application\.release\.name=(.*)$').Matches[0].Groups[1].Value.Trim()
Write-Step "Ghidra: $GhidraDir (version $GhidraVersion $GhidraRelease)"

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
    throw "JDK 21 not found. Specify the JDK 21 directory using the -JavaHome parameter."
}
$Javac = Join-Path $JavaHome "bin\javac.exe"
$JarExe = Join-Path $JavaHome "bin\jar.exe"
$javaVersion = (& $Javac -version 2>&1) -join " "
Write-Step "Compiler: $javaVersion"
if ($javaVersion -notmatch "2[1-9]|3[0-9]") {
    Write-Warn2 "WARNING: Java 21+ is recommended for Ghidra 12."
}

# ---------------------------------------------------------------- Classpath
Write-Step "Preparing classpath..."
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
if ($jars.Count -eq 0) { throw "Ghidra jar files not found." }
Write-Ok "$($jars.Count) jar files found"
$classPath = ($jars -join ";")

# ---------------------------------------------------------------- Compile
Write-Step "Compiling sources..."
if (Test-Path $BuildDir) { Remove-Item $BuildDir -Recurse -Force }
$classesDir = Join-Path $BuildDir "classes"
New-Item -ItemType Directory -Path $classesDir -Force | Out-Null

$sources = @(Get-ChildItem -Path $SrcDir -Filter "*.java" -Recurse |
    ForEach-Object { $_.FullName })
if ($sources.Count -eq 0) { throw "No source files found to compile in: $SrcDir" }
Write-Ok "$($sources.Count) source files"

$oldPref = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$javacOutput = & $Javac -encoding UTF-8 -nowarn -classpath $classPath -d $classesDir $sources 2>&1 |
    Out-String
$javacExit = $LASTEXITCODE
$ErrorActionPreference = $oldPref
if ($javacExit -ne 0) {
    Write-Host $javacOutput
    throw "Compilation failed (javac error code: $javacExit)."
}
Write-Ok "Compilation completed"

# ---------------------------------------------------------------- Tool template
Write-Step "Tool template: $ExtName.tool"
$toolDefaults = Join-Path $ResDir "defaultTools"
New-Item -ItemType Directory -Path $toolDefaults -Force | Out-Null
$toolFile = Join-Path $toolDefaults "$ExtName.tool"

if ((Test-Path $toolFile) -and -not $RefreshTool) {
    Write-Ok "Using existing template in repository (use -RefreshTool to regenerate)"
}
else {
    $publicReleaseJar = Join-Path $GhidraDir "Ghidra\Configurations\Public_Release\lib\Public_Release.jar"
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($publicReleaseJar)
    try {
        $entry = $zip.Entries | Where-Object { $_.FullName -eq "defaultTools/CodeBrowser.tool" } |
            Select-Object -First 1
        if (-not $entry) { throw "CodeBrowser.tool template not found inside Public_Release.jar." }
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
    Write-Ok "Added deepseekai.DeepSeekAIPlugin to Ghidra Core package"
}
if (-not (Test-Path $toolFile)) { throw "Could not produce tool template: $toolFile" }

# ---------------------------------------------------------------- Jar packaging
Write-Step "Packaging jar..."
$jarFile = Join-Path $BuildDir "$ExtName.jar"
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
    throw "Failed to create jar archive."
}
Write-Ok (Split-Path $jarFile -Leaf)

# ---------------------------------------------------------------- Staging
Write-Step "Preparing extension directory..."
$stageDir = Join-Path $BuildDir "stage\$ExtName"
$libDir = Join-Path $stageDir "lib"
New-Item -ItemType Directory -Path $libDir -Force | Out-Null

Copy-Item $jarFile (Join-Path $libDir "$ExtName.jar") -Force
Copy-Item (Join-Path $ProjectRoot "README.md") (Join-Path $stageDir "README.md") -Force
Copy-Item $toolFile (Join-Path $stageDir "$ExtName.tool") -Force
if (Test-Path (Join-Path $ProjectRoot "LICENSE")) {
    Copy-Item (Join-Path $ProjectRoot "LICENSE") (Join-Path $stageDir "LICENSE") -Force
}

$rootModuleManifest = Join-Path $ProjectRoot "Module.manifest"
if (-not (Test-Path $rootModuleManifest)) {
    throw "Module.manifest not found: $rootModuleManifest"
}
Copy-Item $rootModuleManifest (Join-Path $stageDir "Module.manifest") -Force

$rootExtProps = Join-Path $ProjectRoot "extension.properties"
if (-not (Test-Path $rootExtProps)) {
    throw "extension.properties not found: $rootExtProps"
}

$authorAscii = ($Author -replace '[^\x20-\x7E]', '').Trim()
if (-not $authorAscii) { $authorAscii = "unknown" }
$extPropsText = [System.IO.File]::ReadAllText($rootExtProps, (New-Object System.Text.UTF8Encoding($false)))
$extPropsText = $extPropsText -replace '(?m)^author=.*$', "author=$authorAscii"
[System.IO.File]::WriteAllText((Join-Path $stageDir "extension.properties"),
    $extPropsText, (New-Object System.Text.UTF8Encoding($false)))

$scriptsDir = Join-Path $ProjectRoot "ghidra_scripts"
if (Test-Path $scriptsDir) {
    Copy-Item $scriptsDir (Join-Path $stageDir "ghidra_scripts") -Recurse -Force
    Write-Ok "Included ghidra_scripts (diagnostic and utility scripts)"
}
Write-Ok "$stageDir"

# ---------------------------------------------------------------- Zip packaging
Write-Step "Creating distribution archive (zip)..."
New-Item -ItemType Directory -Path $DistDir -Force | Out-Null
$stamp = (Get-Date).ToString("yyyyMMdd")
$zipName = "ghidra_${GhidraVersion}_${GhidraRelease}_${stamp}_$ExtName.zip"
$zipPath = Join-Path $DistDir $zipName
if (Test-Path $zipPath) { Remove-Item $zipPath -Force }

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
            if (-not $hasEntries) { throw "No files found to archive in: $base" }
        }
        finally { $archive.Dispose() }
    }
    finally { $fileStream.Dispose() }
}

New-ZipFromDirectory (Split-Path $stageDir -Parent) $zipPath
Write-Ok $zipPath

# ---------------------------------------------------------------- Installation
if (-not $NoInstall) {
    Write-Step "Installing into Ghidra extensions directory..."
    $targetDir = Join-Path $GhidraDir "Ghidra\Extensions\$ExtName"
    if (Test-Path $targetDir) { Remove-Item $targetDir -Recurse -Force }
    Copy-Item $stageDir $targetDir -Recurse -Force
    Write-Ok $targetDir

    Write-Step "Copying tool template into user tools directory..."
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
            Write-Warn2 "Could not copy to: $userTools ($($_.Exception.Message))"
        }
    }

    Write-Host ""
    Write-Host "Installation completed. Restart Ghidra and follow these steps:" -ForegroundColor Green
    Write-Host "  1) Tools > DeepSeek AI > Settings (API Key)...  -> Enter your API key"
    Write-Host "  2) Click inside any decompiled function"
    Write-Host "  3) Tools > DeepSeek AI > Analyze Function"
    Write-Host ""
    Write-Host "  If the plugin does not appear: File > Configure > search 'DeepSeek' > check DeepSeek AI."
}
else {
    Write-Host ""
    Write-Host "Packaging completed (installation skipped):" -ForegroundColor Green
    Write-Host "  Extension archive : $zipPath"
    Write-Host "  Compiled jar      : $jarFile"
    Write-Host "  Manual install    : File > Install Extensions... > '+' > select zip file"
}
