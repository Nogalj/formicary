# Formicary -- implementation decisions log

Running log of implementation-level choices the spec left open. Design forks that
meaningfully change gameplay go to Logan instead of here.

## M0 Bootstrap

- **`reference/` copied from ModTest** rather than re-extracted: identical version pins
  (MC 1.21 / NeoForge 21.0.167 / Parchment 2024.11.10), so the decompiled sources are
  the same artifact. Gitignored, regenerable.
- **No Config class in the bootstrap** -- ModTest's main class carries an example config;
  Formicary starts without one and adds config only when a real tunable needs it.
