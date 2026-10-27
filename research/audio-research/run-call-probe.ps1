param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [ValidateSet('idle','dry-wait','wait')][string]$Mode='idle',
    [ValidateRange(1,120)][int]$WaitSeconds=120
)
$ErrorActionPreference='Stop'
$taskAdb=Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$taskModel=(& $taskAdb -P 5051 -s $Serial shell getprop ro.product.model).Trim()
if ($LASTEXITCODE -ne 0 -or $taskModel -ne 'CPH2913') { throw 'This diagnostic is restricted to the verified OPPO CPH2913.' }
$taskJar=Join-Path $PSScriptRoot 'velo-call-audio-probe.jar'
$taskBuilder=Join-Path $PSScriptRoot 'build-probes.ps1'
$taskJava=Join-Path $PSScriptRoot 'CallAudioProbe.java'
if (-not (Test-Path -LiteralPath $taskJar) -or
    (Get-Item -LiteralPath $taskJar).LastWriteTimeUtc -lt (Get-Item -LiteralPath $taskBuilder).LastWriteTimeUtc -or
    (Get-Item -LiteralPath $taskJar).LastWriteTimeUtc -lt (Get-Item -LiteralPath $taskJava).LastWriteTimeUtc) {
    & $taskBuilder
}
$taskPcm=Join-Path $PSScriptRoot 'velo-frase-prueba.pcm'
$taskLog=Join-Path $PSScriptRoot ('oppo-'+$Mode+'-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'.txt')
try {
    & $taskAdb -P 5051 -s $Serial push $taskJar /data/local/tmp/velo-call-audio-probe.jar
    if ($LASTEXITCODE -ne 0) { throw 'Diagnostic upload failed' }
    if ($Mode -eq 'wait') {
        if (-not (Test-Path -LiteralPath $taskPcm)) { throw 'Missing predefined PCM test phrase' }
        & $taskAdb -P 5051 -s $Serial push $taskPcm /data/local/tmp/velo-frase-prueba.pcm
        if ($LASTEXITCODE -ne 0) { throw 'Test phrase upload failed' }
        $taskRemote='CLASSPATH=/data/local/tmp/velo-call-audio-probe.jar app_process /system/bin com.alberto.velo.research.CallAudioProbe wait '+$WaitSeconds+' /data/local/tmp/velo-frase-prueba.pcm'
    } elseif ($Mode -eq 'dry-wait') {
        $taskRemote='CLASSPATH=/data/local/tmp/velo-call-audio-probe.jar app_process /system/bin com.alberto.velo.research.CallAudioProbe dry-wait '+$WaitSeconds
    } else {
        $taskRemote='CLASSPATH=/data/local/tmp/velo-call-audio-probe.jar app_process /system/bin com.alberto.velo.research.CallAudioProbe idle'
    }
    & $taskAdb -P 5051 -s $Serial shell $taskRemote | Tee-Object -FilePath $taskLog
    if ($LASTEXITCODE -ne 0) { throw ('Diagnostic exited with code '+$LASTEXITCODE) }
} finally {
    & $taskAdb -P 5051 -s $Serial shell rm -f /data/local/tmp/velo-call-audio-probe.jar /data/local/tmp/velo-frase-prueba.pcm
}
