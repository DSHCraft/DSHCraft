# Third-party source and binary notices

- Hello Minecraft! Launcher (HMCL), upstream source: https://github.com/HMCL-dev/HMCL. DSHCraft is a renamed GPL-3.0-or-later derivative. Preserve the original notices and the GPLv3 section 7 terms described in `hmcl-ui/docs/README.md`: distinguish modified versions by name/version and retain the displayed copyright declaration.
- JFoenix, bundled as `hmcl-ui/lib/JFoenix.jar`, upstream source and MIT license: https://github.com/sshahine/JFoenix/blob/master/LICENSE. Its copyright and permission notice are in `licenses/JFoenix-MIT.txt` and embedded into the Java launcher.
- ANTLR runtime 4.11.1, bundled through TOML parsing, upstream license: https://github.com/antlr/antlr4/blob/4.11.1/LICENSE.txt. Its BSD 3-clause copyright, conditions and disclaimer are in `licenses/ANTLR-4.11.1-BSD-3-Clause.txt` and embedded into the Java launcher.
- DeepSeek Harness (`@deepseek-ai/dsh`) is downloaded into isolated user runtimes rather than shipped in this source bundle. Its packages and installed plugins keep their own licenses.

The Java launcher embeds the DSHCraft/HMCL GPL text, this notice, `NOTICE.md`, and the JFoenix MIT text under `META-INF/dshcraft/`. It also inherits several license files from Gradle dependencies. This is not yet a complete binary dependency inventory: verify the licenses and required notices of every redistributed dependency before distributing the Java executable or Debian package. The source preparation ZIP is not a signed production binary release.

The current runtime-coordinate and shaded-JAR audit, including remaining unverified components, is recorded in `BINARY_LICENSE_AUDIT.md`. It is a worklist, not a compliance certificate.
