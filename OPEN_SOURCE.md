# Open source release checklist

This repository is being prepared for a source release. The following boundaries are intentional:

The public GitHub repository is https://github.com/DSHCraft/DSHCraft. The local handoff directory has no Git metadata, so source must first be staged in a clean clone before it can be committed and pushed. Source publication is separate from a compiled Release: do not attach the unsigned local executable or Debian package as a release artifact.

The source tree is ready for a first public Git push after reviewing the staged file list and diff. Keep the repository focused on the Java/HMCL product, build scripts, tests, legal notices, and maintainer documentation. Exclude local caches, generated binaries, source ZIP archives, old React/Tauri recovery material, updater credentials, and user/runtime data.

- The top-level `LICENSE` and this source tree use GPL-3.0-or-later for DSHCraft launcher code.
- `hmcl-ui/` is the GPL HMCL derivative and retains its original source notices and license. The Java JAR embeds the project GPL and source notices under `META-INF/dshcraft/`.
- Third-party Gradle dependencies remain governed by their own licenses; release archives must include their notices when they redistribute binaries. The bundled JFoenix MIT text is stored in `licenses/JFoenix-MIT.txt` and embedded into the JAR.
- A release must exclude old React/Tauri build caches, Gradle caches, local `hmcl.home` directories, runtime caches, credential data and signing private keys.
- The supplied DSHCraft artwork is included in the Java product as `dshcraft-logo.png` and `dshcraft-mark.png`; source builds may replace these files with compatible artwork.
- Product terms and third-party service/security disclaimers are in `DISCLAIMER.md`; they do not replace or weaken the HMCL GPLv3 notices and corresponding-source obligations.

Before publishing a release, complete these checks:

- run the Java/HMCL build and its Agent tests on the target platform;
- run the real Core, instance, Provider, Mod, Pack and updater checks permitted by the machine;
- generate a third-party license inventory for any redistributed binary bundle;
- resolve the remaining unverified items in `BINARY_LICENSE_AUDIT.md` and verify the newly embedded ANTLR runtime notice in final binaries;
- check `THIRD_PARTY_NOTICES.md` and include every required dependency notice text with any binary release;
- use a production updater signing key stored outside the repository;
- finish the fork-specific verified installation/rollback path and UI flow; signed feed fetch and artifact verification alone do not install updates;
- inspect the archive for keys, credential values, temporary paths and generated user data.

The public source bundle is produced from the Java/HMCL tree plus root legal/build helper files. Historical React/Tauri caches, recovery archives, local credentials and generated binaries are excluded. A binary release remains gated on a complete redistributed-dependency notice inventory and production signing configuration.
