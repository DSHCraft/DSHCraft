# DShCraft direct HMCL fork

Upstream: HMCL-dev/HMCL
Pinned commit: 77b42b6f993ba8f2be25555edded9374809281eb
License: GPL-3.0-or-later

DShCraft keeps HMCL's JavaFX visual implementation directly. CSS, theme packs, built-in images, animation primitives, decorator/window code and PersonalizationPage remain upstream files. Agent code only replaces business models and business destinations.


1.3 list UI de-duplication: HMCL's original GameList search/list skin and GameListCell visual shell are extracted into shared Java classes used by both Minecraft and Agent business adapters. No parallel HMCL-like stylesheet or screenshot recreation is maintained.
