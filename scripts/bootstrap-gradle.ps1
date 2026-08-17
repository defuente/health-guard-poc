$ErrorActionPreference = "Stop"

$version = "9.5.0"
$cacheRoot = Join-Path $env:USERPROFILE ".gradle\health-guard-bootstrap"
$zipPath = Join-Path $cacheRoot "gradle-$version-bin.zip"
$gradleHome = Join-Path $cacheRoot "gradle-$version"
$gradleBat = Join-Path $gradleHome "bin\gradle.bat"

New-Item -ItemType Directory -Force -Path $cacheRoot | Out-Null

if (-not (Test-Path $gradleBat)) {
    Write-Host "Downloading Gradle $version..."
    Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-$version-bin.zip" -OutFile $zipPath
    Write-Host "Extracting Gradle..."
    Expand-Archive -Path $zipPath -DestinationPath $cacheRoot -Force
}

Write-Host "Generating the standard Gradle Wrapper..."
& $gradleBat wrapper --gradle-version $version --distribution-type bin

Write-Host "Done. You can now run: .\gradlew.bat :app:assembleDebug"
