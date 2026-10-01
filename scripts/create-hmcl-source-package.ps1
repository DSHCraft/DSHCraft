param(
    [string]$Output = 'artifacts/DSHCraft-1.3.0-source.zip'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$outputPath = [IO.Path]::GetFullPath((Join-Path $projectRoot $Output))
$artifacts = [IO.Path]::GetFullPath((Join-Path $projectRoot 'artifacts'))
if (-not $outputPath.StartsWith($artifacts + [IO.Path]::DirectorySeparatorChar,
        [StringComparison]::OrdinalIgnoreCase) -or
        [IO.Path]::GetExtension($outputPath) -ne '.zip') {
    throw 'Source archive must be a ZIP inside the project artifacts directory.'
}

$inputs = @(
    'hmcl-ui', 'README.md', 'LICENSE', 'NOTICE.md', 'OPEN_SOURCE.md',
    'CONTRIBUTING.md', 'SECURITY.md', 'DISCLAIMER.md', 'THIRD_PARTY_NOTICES.md',
    'BINARY_LICENSE_AUDIT.md', 'SIGNING.md', 'CHANGELOG.md', 'MANUAL_TESTS.md', 'licenses',
    'START_HERE.cmd', 'START_HERE.sh',
    'scripts/start-hmcl-preview.ps1', 'scripts/create-hmcl-source-package.ps1',
    'scripts/windows',
    '.github/workflows/ci.yml'
)
foreach ($item in $inputs) {
    if (-not (Test-Path -LiteralPath (Join-Path $projectRoot $item))) {
        throw "Missing source package input: $item"
    }
}

$excludes = @(
    '*/build', '*/.gradle', '*/.idea', '*/.hmcl', '*/.local', '*/out', '*/artifacts',
    'hmcl-ui/.github', 'hmcl-ui/.gitee', 'hmcl-ui/.cnb',
    '*.class', '*.log', '*.exe', '*.deb', '*.pfx', '*.p12',
    '*.key', '*.key.pem', '*.private', '*.sigstore',
    '*.jks', '*.env', '*.env.*', '*.tsbuildinfo',
    'hmcl-ui/DSHCRAFT_PATCH.diff', 'hmcl-ui/DSHCRAFT_UI_BASELINE.sha256'
)
$excludeArguments = @($excludes | ForEach-Object { '--exclude=' + $_ })
Push-Location $projectRoot
try {
    & tar -a -cf $outputPath @excludeArguments @inputs
    if ($LASTEXITCODE -ne 0) { throw "tar failed with code $LASTEXITCODE" }
    $entries = @(& tar -tf $outputPath)
    if ($LASTEXITCODE -ne 0) { throw "Cannot inspect archive: $LASTEXITCODE" }
    foreach ($required in @('hmcl-ui/LICENSE', 'hmcl-ui/gradlew.bat',
            'hmcl-ui/HMCL/src/main/resources/assets/img/dshcraft-mark.png', 'NOTICE.md',
            'BINARY_LICENSE_AUDIT.md', 'licenses/ANTLR-4.11.1-BSD-3-Clause.txt',
            'CHANGELOG.md', 'MANUAL_TESTS.md', '.github/workflows/ci.yml',
            'scripts/windows/set-pe-icon.ps1', 'scripts/windows/sign-launcher.ps1', 'SIGNING.md')) {
        if ($entries -notcontains $required) { throw "Archive lacks $required" }
    }
    if (@($entries | Where-Object {
            $_ -match '(^|/)(build|\.gradle|\.hmcl|\.local|out)/' -or
            $_ -match '^hmcl-ui/\.(github|gitee|cnb)/' -or
            $_ -match '\.(exe|deb|pfx|p12|jks|key|private|log|tsbuildinfo)$'
        }).Count -gt 0) {
        throw 'Archive contains a generated or private path.'
    }
    if (@($entries | Where-Object { $_ -match '(^|/)(node_modules|target|dist|artifacts|runtimes)/' }).Count -gt 0) {
        throw 'Archive contains dependencies, build output, archives, or local runtime data.'
    }
    Get-Item -LiteralPath $outputPath | Select-Object FullName, Length
    Get-FileHash -LiteralPath $outputPath -Algorithm SHA256 | Select-Object Hash
} finally {
    Pop-Location
}
