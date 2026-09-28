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
