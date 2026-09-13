# Java implementation guidance

- Extend the existing block and item APIs in their respective packages. Do not
  introduce a shared block/item framework with unrelated responsibilities.
- Keep declaration parsing, runtime registries, and Lua context access separate.
- Use descriptive domain-specific names and small methods with a clear purpose.
- Use four spaces, braces for control-flow bodies, and approximately 120 columns.
- Put separate statements and field declarations on separate lines. Add blank
  lines between logical steps. Do not compress code to reduce file length.
- Prefer explicit Java imports and avoid fully qualified class names in method
  bodies when an import makes the code easier to read.
- Preserve existing functionality and local user changes when refactoring.
- Explain changes to core interaction routing, instrumentation, persistence, or
  other deeply rooted functionality before implementing them.
- For the current block/item API expansion, complete implementation first, then
  present all proposed example additions and the complete example ordering to
  the user BEFORE writing or renaming example scripts.

Formatting settings are in `config/java-format.properties` and `.editorconfig`.
