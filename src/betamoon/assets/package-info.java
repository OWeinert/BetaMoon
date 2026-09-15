/**
 * Headless asset metadata and registration lifecycle. This package depends only
 * on the JDK: Lua declaration parsing, script context, resource providers, and
 * client decoders/renderers belong in separate layers.
 *
 * <p>
 * AssetId is the durable identity. Registrations are immutable generation
 * snapshots; consumers resolve an ID again after publication or unload. Script
 * source filenames identify local ownership, not network identities. Generation
 * numbers are local and never substitute for future protocol negotiation.
 *
 * <p>
 * Paths describe resources, not permission to read files. A future filesystem
 * provider must enforce containment after resolving symbolic links; a pack
 * provider must distinguish real ZIP entries from Minecraft's classpath
 * fallback. Providers and client backends own streams, decoded data, and
 * graphics/audio lifetime. Registry removal does not dispose backend resources
 * while presentation still uses them.
 */
package betamoon.assets;
