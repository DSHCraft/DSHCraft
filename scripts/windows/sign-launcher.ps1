param(
    [Parameter(Mandatory = $true)][string]$Executable,
    [Parameter(Mandatory = $true)][ValidatePattern('^[0-9A-Fa-f]{40}$')][string]$CertificateThumbprint
)

$ErrorActionPreference = 'Stop'
$target = (Resolve-Path -LiteralPath $Executable).Path
$certificate = Get-Item -LiteralPath ('Cert:\CurrentUser\My\' + $CertificateThumbprint)
if (-not $certificate.HasPrivateKey) { throw 'The signing certificate has no private key.' }
if ((Get-AuthenticodeSignature -FilePath $target).Status -ne 'NotSigned') {
    throw 'Build a fresh unsigned EXE before signing. This helper does not rewrite an existing signature.'
}

# Java's native -jar reader requires the ZIP end record and its comment to reach
# EOF. Authenticode appends an aligned certificate table after the embedded JAR.
# Reserve that tail as the ZIP comment before the final signing operation so both
# Java and WinVerifyTrust accept the same immutable file.
$original = [IO.File]::ReadAllBytes($target)
$endRecord = -1
for ($offset = $original.Length - 22; $offset -ge [Math]::Max(0, $original.Length - 65557); $offset--) {
    if ([BitConverter]::ToUInt32($original, $offset) -eq 0x06054b50) {
        $commentLength = [BitConverter]::ToUInt16($original, $offset + 20)
        if ($offset + 22 + $commentLength -eq $original.Length) {
            $endRecord = $offset
            break
        }
    }
}
if ($endRecord -lt 0) { throw 'The executable has no complete embedded ZIP end record.' }
$initialCommentLength = [BitConverter]::ToUInt16($original, $endRecord + 20)
$staging = Join-Path (Split-Path -Parent $target) ('.dshcraft-signing-' + [guid]::NewGuid().ToString('N') + '.exe')
$backup = $staging + '.unsigned-backup'

try {
    [IO.File]::WriteAllBytes($staging, $original)
    $measurement = Set-AuthenticodeSignature -FilePath $staging -Certificate $certificate -HashAlgorithm SHA256
    if ($measurement.Status -ne 'Valid') { throw ('Signing failed: ' + $measurement.StatusMessage) }
    $tailBytes = (Get-Item -LiteralPath $staging).Length - $original.Length

    $complete = $false
    for ($attempt = 0; $attempt -lt 3; $attempt++) {
        $commentLength = $initialCommentLength + $tailBytes
        if ($tailBytes -le 0 -or $commentLength -gt 65535) { throw 'The signing certificate tail exceeds the ZIP comment limit.' }
        $candidate = [byte[]]$original.Clone()
        $lengthBytes = [BitConverter]::GetBytes([uint16]$commentLength)
        [Array]::Copy($lengthBytes, 0, $candidate, $endRecord + 20, 2)
        [IO.File]::WriteAllBytes($staging, $candidate)
        $signature = Set-AuthenticodeSignature -FilePath $staging -Certificate $certificate -HashAlgorithm SHA256
        if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Thumbprint -ne $CertificateThumbprint) {
            throw ('Final signing verification failed: ' + $signature.StatusMessage)
        }
        $actualTail = (Get-Item -LiteralPath $staging).Length - $original.Length
        if ($actualTail -eq $tailBytes) {
            $complete = $true
            break
        }
        $tailBytes = $actualTail
    }
    if (-not $complete) { throw 'The certificate size changed repeatedly; the original executable was preserved.' }
    [IO.File]::Replace($staging, $target, $backup)
    foreach ($algorithm in @('SHA256', 'SHA512')) {
        $digest = (Get-FileHash -LiteralPath $target -Algorithm $algorithm).Hash.ToLowerInvariant()
        [IO.File]::WriteAllText($target + '.' + $algorithm.ToLowerInvariant(), $digest + [char]10, [Text.Encoding]::ASCII)
    }
    Get-AuthenticodeSignature -FilePath $target | Select-Object Path, Status
} finally {
    if (Test-Path -LiteralPath $staging) { Remove-Item -LiteralPath $staging }
    if (Test-Path -LiteralPath $backup) { Remove-Item -LiteralPath $backup }
}
