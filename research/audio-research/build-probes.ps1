$ErrorActionPreference = 'Stop'
$taskJdk = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { Split-Path (Get-Command javac -ErrorAction Stop).Source }
$taskSdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$taskRoot = $PSScriptRoot
$taskClasses = Join-Path $taskRoot 'call-probe-classes'
$taskDex = Join-Path $taskRoot 'call-probe-dex'
New-Item -ItemType Directory -Force -Path $taskClasses,$taskDex | Out-Null
& (Join-Path $taskJdk 'javac.exe') -encoding UTF-8 -source 17 -target 17 -classpath (Join-Path $taskSdk 'platforms\android-36\android.jar') -d $taskClasses (Join-Path $taskRoot 'CallAudioProbe.java')
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
$taskJarArgs=@('--create','--file',(Join-Path $taskRoot 'call-probe-classes.jar'))
foreach ($taskClass in (Get-ChildItem -LiteralPath (Join-Path $taskClasses 'com\alberto\velo\research') -Filter 'CallAudioProbe*.class')) {
    $taskJarArgs += @('-C',$taskClasses,('com/alberto/velo/research/'+$taskClass.Name))
}
& (Join-Path $taskJdk 'jar.exe') @taskJarArgs
if ($LASTEXITCODE -ne 0) { throw 'jar failed' }
& (Join-Path $taskSdk 'build-tools\36.0.0\d8.bat') --min-api 31 --lib (Join-Path $taskSdk 'platforms\android-36\android.jar') --output $taskDex (Join-Path $taskRoot 'call-probe-classes.jar')
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }
& (Join-Path $taskJdk 'jar.exe') --create --file (Join-Path $taskRoot 'velo-call-audio-probe.jar') -C $taskDex classes.dex
if ($LASTEXITCODE -ne 0) { throw 'dex jar failed' }
Write-Output 'Built velo-call-audio-probe.jar'
