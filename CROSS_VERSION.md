# Cross-version compatibility assessment (2026-10-29)

Status: assessment only. No multi-version build exists yet. The live
and test builds target exactly one version: Minecraft 26.3, Fabric
Loader 0.20.1, Fabric API 0.165.0+26.3, Java 25.

## Why one jar cannot cover "as many versions as possible"

Fabric mods are compiled against Yarn mappings for one Minecraft
version. Class, method, and field names change between versions, and
the game code itself changes. A jar built for 26.3 will not load on
1.21.x, and vice versa. "Cross compatible" for a Fabric mod means a
build matrix (one jar per supported version), not one universal jar.

## Version-specific coupling points in this codebase

1. DisplayHelper (the biggest one): uses reflection to reach
   private static fields DATA_BLOCK_STATE_ID, DATA_TRANSLATION_ID,
   DATA_SCALE_ID, DATA_LEFT_ROTATION_ID, DATA_RIGHT_ROTATION_ID,
   and then public SynchedEntityData.set(). Those field names and
   their privacy are version-specific. In older versions the
   Display.setTransformation / BlockDisplay.setBlockState methods
   were public, so no reflection was needed at all. In 26.3 they
   are private. Any version where Mojang renames those fields
   breaks the animation (falls still settle, by design).
2. Command permissions (26.3 changed this): the OP gate uses
   CommandSourceStack.permissions().hasPermission(Permissions.
   COMMANDS_GAMEMASTER). Older versions used
   CommandSourceStack.hasPermission(int). A build for an older
   version must use the old call.
3. Stable, low-risk APIs used elsewhere: BlockPos, BlockState,
   BlockTags.LOGS / LEAVES, ItemTags.AXES, PlayerBlockBreakEvents,
   ServerTickEvents, BlockDisplay entities, ItemStack / ItemEntity.
   These have been stable across many versions, with only
   occasional renames. They are the reason a per-version build is
   mostly a recompile plus the two coupling points above, not a
   rewrite.
4. Java version: 26.3 needs Java 25. Older Minecraft versions need
   Java 21 (1.20.5+) or Java 17 (1.18-1.20.4). The build matrix
   must pin a JDK per Minecraft version.
5. Fabric API version: one Fabric API release per Minecraft
   version. The mod only uses Fabric API for events, commands,
   and the config dir, so the API surface is small, but the
   dependency string in fabric.mod.json must still match each
   target version.

## Realistic strategy for a public release

- Keep the core (TreeDetector, FallingTree geometry and settle,
  config) version-agnostic and free of version-specific calls.
- Isolate the version-specific code into one small module per
  Minecraft version: DisplayHelper plus the command-permission
  call. Everything else is shared source.
- Use a proper Fabric Loom Gradle build for the public release,
  not the manual javac build used on this VM. Loom applies access
  wideners at compile time, which is the clean replacement for
  the reflection in DisplayHelper: declare the private Display
  methods/fields in a .accesswidener file instead of reaching
  them with Field.setAccessible at runtime. That also removes
  the one pattern a jar inspector is likely to flag.
- Suggested first matrix, in order of value: 26.3 (current, done),
  then the latest 1.21.x Fabric release, then 1.21.1 (the
  long-lived LTS many servers still run). Do not promise 1.20 or
  older until the 1.21 build is proven, because the Display API
  and the permission API both differ there.
- Each matrix entry needs its own test-server run (the same
  bottom-chop, mid-chop stump, and ground-drop checks used for
  1.1.0) before it is listed as supported. A version is not
  "supported" because it compiled.
- NeoForge / Quilt / vanilla-only distribution are separate
  ports, not free byproducts of a Fabric build. Do not claim
  them until one is actually built.

## What not to claim

- No single-jar multi-version support.
- No support for a version that has not been built and
  test-server verified.
- No removal of the reflection flag until the access-widener
  build exists and is verified on a test server.
