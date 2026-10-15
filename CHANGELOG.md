# Changelog

## 0.10.0-gui (in development)

### Minecraft-style interface

- Use a native-based search textbox in BetaMoon Controls, with a blinking caret, bounded paste, and scrolling long queries.
- Style diagnostic dialogs and update notices with compact charcoal pixel frames, native buttons, readable links, and restrained list selection and scrollbars.
- Keep long dialog messages scrollable, adapt narrow-screen footer layouts, and preserve Scripts selection and scrolling when returning from dialogs.
- Size dialogs to their wrapped content within viewport limits, and identify script issues with explicit severity labels and independently wrapped source links.

### Particles

- Use Minecraft display ticks for the decorative ring block, retaining ordinary Lua per-particle animation callbacks.
- Restore automatic repeating block ticks when saved chunks load, so existing particle ring blocks resume
  emission after rejoining a world. Retry schedules while surrounding chunks are unavailable.
- Add registered particle sprite types and reusable burst/emitter effects, with custom PNGs, Minecraft atlas cells,
  independent animation, color/alpha, rotation, size, motion, and collision policies.
- Add optional per-instance Lua `onTick` updates with scoped particle controls and transient memory.
- Default to admitting up to 1,024 new custom particles per client tick; configurable with `particles.maxPerTick`.
- Allow client-wide particle budgets up to 65,535 active or new sprites per tick, retaining defaults of
  4,096 active and 1,024 new sprites per tick.
- Extend world particle spawning with custom references/keys and add effect handles, actor attachments,
  client budgets, diagnostics, and owner/world cleanup. Custom presentation is local-client only.
