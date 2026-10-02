# Dot-source from PowerShell: . ./tools-env.ps1
$betterRoot = $PSScriptRoot
$betterJdk = Get-ChildItem (Join-Path $betterRoot '.tools/jdk') -Directory | Select-Object -First 1
if (-not $betterJdk) { throw 'Workspace JDK missing.' }
$env:JAVA_HOME = $betterJdk.FullName
$env:ANDROID_HOME = Join-Path $betterRoot '.tools/android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:Path = "$env:JAVA_HOME/bin;$betterRoot/.tools/gradle-8.13/bin;$betterRoot/.tools/platform-tools;$env:Path"
