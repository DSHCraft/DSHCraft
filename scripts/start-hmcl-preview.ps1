param(
    [string]$UserDirectory,
    [string]$RuntimeDirectory,
    [string]$PreviewDirectory,
    [string]$JavaExecutable = 'javaw.exe'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$jarSource = Join-Path $projectRoot 'hmcl-ui\HMCL\build\libs\DSHCraft-1.3.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jarSource -PathType Leaf)) {
    throw 'Build hmcl-ui with .\gradlew.bat :HMCL:build before starting the preview.'
}

# Every preview owns an immutable executable copy. Gradle may replace build/libs
# while this process is alive, but must never replace the JAR it is loading.
if (-not $PreviewDirectory) {
    # Keep executable and test state outside Windows Temp so HMCL's data-loss
    # warning is not triggered by our own preview workflow.
    $previewDirectory = Join-Path $projectRoot ('.hmcl\previews\' + [guid]::NewGuid().ToString('N'))
} else {
    $previewDirectory = [IO.Path]::GetFullPath($PreviewDirectory)
}
New-Item -ItemType Directory -Path $previewDirectory -Force | Out-Null
$snapshot = Join-Path $previewDirectory 'DSHCraft-preview.jar'
Copy-Item -LiteralPath $jarSource -Destination $snapshot
$digest = (Get-FileHash -LiteralPath $snapshot -Algorithm SHA256).Hash
if ($digest -ne (Get-FileHash -LiteralPath $jarSource -Algorithm SHA256).Hash) {
    throw 'The build changed while creating the preview. Wait for the build to finish and retry.'
}
(Get-Item -LiteralPath $snapshot).IsReadOnly = $true
if (-not $UserDirectory) { $UserDirectory = Join-Path $previewDirectory 'user-data' }
$localDirectory = Join-Path $previewDirectory 'local-data'
New-Item -ItemType Directory -Path $localDirectory | Out-Null
$options = @('-Xmx1g', ('-Dhmcl.home="' + [IO.Path]::GetFullPath($UserDirectory) + '"'),
    ('-Dhmcl.dir="' + $localDirectory + '"'))
$dependencyCache = Join-Path $projectRoot '.hmcl\dependencies'
if (Test-Path -LiteralPath $dependencyCache -PathType Container) {
    $options += ('-Dhmcl.dependencies.dir="' + $dependencyCache + '"')
}
if ($RuntimeDirectory) {
    $options += ('-Ddshcraft.runtime.root="' + [IO.Path]::GetFullPath($RuntimeDirectory) + '"')
}
$options += @('-jar', ('"' + $snapshot + '"'))
$program = (Get-Command $JavaExecutable -ErrorAction Stop).Source
# This preview is an interactive application being shown to its developer.
$process = Start-Process -FilePath $program -ArgumentList $options -WorkingDirectory $previewDirectory -WindowStyle Normal -PassThru
[pscustomobject]@{
    LauncherPid = $process.Id
    Snapshot = $snapshot
    SHA256 = $digest
    UserDirectory = [IO.Path]::GetFullPath($UserDirectory)
    Logs = Join-Path $localDirectory 'logs'
}
