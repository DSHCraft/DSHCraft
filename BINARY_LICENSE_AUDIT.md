# Java binary dependency audit (in progress)

Checked on 2026-09-27 against the local `:HMCL:dependencies --configuration runtimeClasspath --offline` result and `DSHCraft-1.3.0-SNAPSHOT.jar`. This is an inventory, **not** a release clearance: Shadow minimization changes what is embedded, POM license labels can be inherited or incomplete, and each redistributed notice must still be checked in the final artifacts.

## Verified gaps and evidence

| Component | Evidence in current JAR | License evidence | Release action |
| --- | --- | --- | --- |
| ANTLR runtime 4.11.1 | `org/antlr/v4/runtime/` classes present (197 entries); rebuilt JAR contains `META-INF/dshcraft/ANTLR-4.11.1-BSD-3-Clause.txt` with byte-for-byte-equivalent text to the source license file after newline normalization | [Upstream 4.11.1 license](https://github.com/antlr/antlr4/blob/4.11.1/LICENSE.txt), BSD 3-clause | Retain the notice in final release materials. |
| NanoHTTPD 2.3.1 | `fi/iki/elonen/` classes present (28 entries); top-level `LICENSE.txt` begins with NanoHTTPD copyright | [Upstream 2.3.1 license](https://github.com/NanoHttpd/nanohttpd/blob/nanohttpd-project-2.3.1/LICENSE.md), modified BSD/3-clause | Confirm the entire embedded license survives packaging and applies to this exact artifact. |
| CommonMark 0.30.0 core and extensions | `org/commonmark/` classes present (238 entries); `META-INF/LICENSE.txt` begins with Atlassian copyright | [Upstream 0.30.0 license](https://github.com/commonmark/commonmark-java/blob/commonmark-parent-0.30.0/LICENSE.txt), BSD 2-clause | Confirm the embedded license covers every included extension artifact. |
| JNA 5.18.1 | `com/sun/jna/` classes present (177 entries); `META-INF/LICENSE` identifies Apache-2.0 or LGPL-2.1-or-later | POM declares dual license | Check the selected redistribution path and any bundled native component notices. |
| JFoenix local JAR | Bundled by `libs:JFoenix` | MIT text is in `licenses/JFoenix-MIT.txt` and `META-INF/dshcraft/` | Retain its copyright/permission text. |

## Remaining runtime coordinates by POM metadata

The following labels come from cached Gradle POMs, not from a legal review of the actual shaded output:

- Apache-2.0 labels: `org.glavo.kala:kala-compress-{archivers-zip,archivers-tar,base}:1.27.1-5`, `org.jetbrains:annotations:26.1.0`, `org.glavo:simple-png{-javafx}:0.3.0`, `com.google.code.gson:gson:2.14.0`, `com.google.errorprone:error_prone_annotations:2.48.0`, `org.tomlj:tomlj:1.1.1`, `org.glavo:lz4-java:1.10.4.1`, `org.glavo:{pci-ids,HelloNBT,weburl,webp,java-info,MonetFX}` at the resolved versions.
- MIT labels: `org.checkerframework:checker-qual:3.21.2`, `org.hildan.fxgson:fx-gson:5.0.0`, `org.jsoup:jsoup:1.23.2`, `org.nibor.autolink:autolink:0.12.0`, `io.nayuki:qrcodegen:1.8.0`.
- MPL-2.0 labels: `org.glavo:kala-encoding-detector:0.1.0`, `org.glavo:uuid-tools:0.2.0`.
- Other labels: `org.tukaani:xz:1.12` (0BSD), `org.jenkins-ci:constant-pool-scanner:1.2` (NetBeans CDDL/GPL), `com.github.hervegirod:fxsvgimage:1.9.1` (POM lists BSD-3-Clause and Apache-2.0), `org.glavo.hmcl:HMCLauncher:3.7.0.1` (GPL-3.0).
- POMs without direct license elements: `org.antlr:antlr4-runtime:4.11.1`, `org.nanohttpd:nanohttpd:2.3.1`, and the `org.commonmark` extension modules. Their upstream/project licenses are identified above, but inherited POM information and final notice placement require inspection.

The JavaFX runtime and native launcher stubs also need a final-platform inventory. Do not publish the current unsigned JAR/EXE/DEB on the strength of this document alone.
