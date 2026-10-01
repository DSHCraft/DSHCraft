# DSHCraft Signing

DSHCraft uses two independent signing roots:

- `HMCL_SIGNATURE_KEY`: RSA PKCS#8 private key used to sign the launcher JAR with `SHA512withRSA`.
- DSHCraft updater key: Ed25519 private key used to sign the exact update manifest bytes.

Only the corresponding public keys are committed under `hmcl-ui/HMCL/src/main/resources/assets/`. Private keys must stay outside the repository and outside Release assets. The current local development keys are stored under `%USERPROFILE%\\.dshcraft\\signing`.

Build a signed Windows artifact from PowerShell:

```powershell
$env:HMCL_SIGNATURE_KEY = Join-Path $env:USERPROFILE '.dshcraft/signing/hmcl-signing-private.pkcs8.der'
cd hmcl-ui
.\gradlew.bat :HMCL:makeExecutables --offline --no-daemon
```

The updater manifest and detached Ed25519 signature must be generated from the exact final Release artifact. Never commit either private key or a test key.

## Windows executable signing

After building the RSA-signed launcher payload, use the existing Windows code-signing certificate from `CurrentUser/My`:

```powershell
.\scripts\windows\sign-launcher.ps1 -Executable .\hmcl-ui\HMCL\build\libs\DSHCraft-1.3.0-SNAPSHOT.exe -CertificateThumbprint '<your certificate thumbprint>'
```

Start from a freshly built EXE. The helper stages signing, accounts for the appended Authenticode certificate table in the embedded ZIP comment before final signing, verifies the final native signature, and refreshes checksums. Signing the concatenated EXE directly without this step causes Java's native `-jar` reader to reject it as a corrupt JAR. The JAR payload signature and updater verification remain required independently.
