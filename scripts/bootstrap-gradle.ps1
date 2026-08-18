$ErrorActionPreference = "Stop"

$version = "9.5.0"
$minimumJavaMajor = 17
$cacheRoot = Join-Path $env:USERPROFILE ".gradle\health-guard-bootstrap"
$zipPath = Join-Path $cacheRoot "gradle-$version-bin.zip"
$gradleHome = Join-Path $cacheRoot "gradle-$version"
$gradleBat = Join-Path $gradleHome "bin\gradle.bat"

function Get-JavaVersionText([string]$javaExe) {
    $stdoutPath = [System.IO.Path]::GetTempFileName()
    $stderrPath = [System.IO.Path]::GetTempFileName()

    try {
        $process = Start-Process `
            -FilePath $javaExe `
            -ArgumentList "-version" `
            -NoNewWindow `
            -Wait `
            -PassThru `
            -RedirectStandardOutput $stdoutPath `
            -RedirectStandardError $stderrPath

        $stdout = Get-Content $stdoutPath -Raw -ErrorAction SilentlyContinue
        $stderr = Get-Content $stderrPath -Raw -ErrorAction SilentlyContinue
        $output = (($stdout + "`n" + $stderr).Trim())

        if ($process.ExitCode -ne 0) {
            throw "Java version check failed with exit code $($process.ExitCode): $output"
        }

        return $output
    }
    finally {
        Remove-Item $stdoutPath, $stderrPath -Force -ErrorAction SilentlyContinue
    }
}

function Initialize-Java {
    $javaExe = $null

    if ($env:JAVA_HOME) {
        $javaFromHome = Join-Path $env:JAVA_HOME "bin\java.exe"
        if (Test-Path $javaFromHome) {
            $javaExe = $javaFromHome
        } else {
            Write-Warning "JAVA_HOME points to an invalid directory: $env:JAVA_HOME"
            Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
        }
    }

    if (-not $javaExe) {
        $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
        if ($javaCommand) {
            $javaExe = $javaCommand.Source
        }
    }

    if (-not $javaExe) {
        $androidStudioJbr = "C:\Program Files\Android\Android Studio\jbr"
        $androidStudioJava = Join-Path $androidStudioJbr "bin\java.exe"
        if (Test-Path $androidStudioJava) {
            $env:JAVA_HOME = $androidStudioJbr
            $env:Path = "$androidStudioJbr\bin;$env:Path"
            $javaExe = $androidStudioJava
            Write-Host "Using Android Studio JDK: $androidStudioJbr"
        }
    }

    if (-not $javaExe) {
        throw "Java JDK $minimumJavaMajor+ was not found. Install JDK 17 or newer, or install Android Studio and rerun this script."
    }

    $versionOutput = Get-JavaVersionText $javaExe
    if ($versionOutput -notmatch 'version\s+"(?<major>\d+)') {
        throw "Could not determine Java version from: $versionOutput"
    }

    $major = [int]$Matches["major"]
    if ($major -lt $minimumJavaMajor) {
        throw "Java $major was found, but this project requires JDK $minimumJavaMajor or newer."
    }

    if (-not $env:JAVA_HOME) {
        $env:JAVA_HOME = Split-Path (Split-Path $javaExe -Parent) -Parent
    }

    Write-Host "Java OK: JDK $major ($javaExe)"
}

function Initialize-AndroidSdk {
    $sdkCandidates = @()

    if ($env:ANDROID_HOME) {
        $sdkCandidates += $env:ANDROID_HOME
    }

    if ($env:LOCALAPPDATA) {
        $sdkCandidates += (Join-Path $env:LOCALAPPDATA "Android\Sdk")
    }

    $sdkHome = $sdkCandidates |
        Where-Object { $_ -and (Test-Path $_) } |
        Select-Object -First 1

    if (-not $sdkHome) {
        throw "Android SDK was not found. Open Android Studio > Tools > SDK Manager, note 'Android SDK Location', then set ANDROID_HOME to that folder or create local.properties with sdk.dir=<path>."
    }

    $env:ANDROID_HOME = $sdkHome
    $sdkForProperties = $sdkHome.Replace('\', '/')
    Set-Content -Path ".\local.properties" -Value "sdk.dir=$sdkForProperties" -Encoding ASCII

    Write-Host "Android SDK OK: $sdkHome"
    Write-Host "Created local.properties with sdk.dir (this file is gitignored)."
}

Initialize-Java
Initialize-AndroidSdk

New-Item -ItemType Directory -Force -Path $cacheRoot | Out-Null

if (-not (Test-Path $gradleBat)) {
    Write-Host "Downloading Gradle $version..."
    Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-$version-bin.zip" -OutFile $zipPath
    Write-Host "Extracting Gradle..."
    Expand-Archive -Path $zipPath -DestinationPath $cacheRoot -Force
}

Write-Host "Generating the standard Gradle Wrapper..."
& $gradleBat wrapper --gradle-version $version --distribution-type bin
if ($LASTEXITCODE -ne 0) {
    throw "Gradle wrapper generation failed with exit code $LASTEXITCODE."
}

if (-not (Test-Path ".\gradlew.bat")) {
    throw "Gradle reported success, but .\gradlew.bat was not created."
}

Write-Host "Done. You can now run: .\gradlew.bat :app:assembleDebug"
