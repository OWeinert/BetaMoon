# Multi-file base mod

Copy the complete `01_beg_02_multifile_mod` directory into
`.minecraft/lua_scripts/`. Keep the files together:

```text
01_beg_02_multifile_mod/
  betamoon.mod.json
  main.lua
  lifecycle.lua
```

`betamoon.mod.json` identifies the entrypoint and supplies the mod metadata.
`main.lua` is the only entrypoint and delegates BetaMoon's lifecycle hooks to
the private `lifecycle.lua` module through `require("lifecycle")`.

The example intentionally adds no gameplay content. Use it as a starting layout
for a mod that becomes easier to understand when its declarations, behavior, or
configuration are split across several files. Every private module remains part
of this one mod and shares its lifecycle and resource ownership.
