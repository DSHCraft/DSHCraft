# DSHCraft source notices

DSHCraft is distributed as an HMCL-derived Java desktop source workspace:

- `hmcl-ui/` is the final Java/HMCL desktop product. It is a GPL-3.0-or-later derivative of the user-supplied HMCL source tree. The upstream source notices, `hmcl-ui/LICENSE`, and the source/build obligations remain part of that tree.
- HMCL's GPLv3 section 7 terms require modified versions to be distinguishable by name/version and the displayed upstream copyright declaration to remain. DSHCraft uses a distinct name and retains the HMCL attribution in the About page.
- The former React/Tauri/browser UI source and launch entrypoints were removed at the user's request. Any historical build caches or a local recovery archive are not part of the release source package.

DSHCraft branding files are under `hmcl-ui/HMCL/src/main/resources/assets/img/`:

- `dshcraft-logo.png` is the supplied DSHCraft brand artwork.
- `dshcraft-mark.png` is the complete square launcher mark derived from that artwork for list rows and the title bar; the earlier cropped variant is retained only in a local recovery artifact, not in the release source package.
- `hmcl-ui/HMCL/image/dshcraft.png` is the same square mark used for Debian/Windows package branding.

DeepSeek Harness remains an external upstream project. DSHCraft invokes the published `@deepseek-ai/dsh` packages and keeps their own package licenses and notices in the isolated runtime. DSHCraft does not redistribute those npm dependencies in this source tree.

Do not commit API keys, signing private keys, Windows credential exports, temporary runtime directories, or generated installers.

See `THIRD_PARTY_NOTICES.md` for the currently identified bundled dependency notices and the remaining binary-license inventory gate.
