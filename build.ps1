param([switch]$Offline, [switch]$Lint)
$ErrorActionPreference = 'Stop'
$projectDir = Join-Path $PSScriptRoot 'android'
$javaExe = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr\bin\java.exe'
if ($env:JAVA_HOME) { $javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe' }
if (-not (Test-Path -LiteralPath $javaExe)) { throw 'Install Android Studio or set JAVA_HOME.' }
$gradleHome = Join-Path $env:USERPROFILE '.gradle'
$env:GRADLE_USER_HOME = $gradleHome
$env:ANDROID_USER_HOME = Join-Path $env:USERPROFILE '.android'
$gradleArgs = @('-classpath', 'gradle/wrapper/gradle-wrapper.jar', 'org.gradle.wrapper.GradleWrapperMain', ':app:assembleDebug', '--console=plain', '--max-workers=4')
if ($Offline) { $gradleArgs += '--offline' }
Push-Location -LiteralPath $projectDir
try {
    & $javaExe @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw 'Android build failed. Review the Gradle output.' }
    Copy-Item -LiteralPath 'app/build/outputs/apk/debug/app-debug.apk' -Destination (Join-Path $PSScriptRoot 'PocketAI-v2-preview.apk')
    if ($Lint) {
        $lintArgs = @('-classpath', 'gradle/wrapper/gradle-wrapper.jar', 'org.gradle.wrapper.GradleWrapperMain', ':app:lintDebug', '--console=plain')
        if ($Offline) { $lintArgs += '--offline' }
        & $javaExe @lintArgs
        if ($LASTEXITCODE -ne 0) { throw 'APK was built, but lint did not pass. Review the output.' }
    }
} finally { Pop-Location }
