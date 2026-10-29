# Realistic Tree Physics

Transform tree harvesting in Minecraft with dynamic, physics-based timber animations. Chop down trees and watch them fall naturally with realistic weight and motion—all managed seamlessly on the server side.

## Features

- Dynamic Falling Physics: Trees tip over and fall in the direction you're facing when chopped, collapsing onto the ground as interactable blocks.
- Partial Felling: Chop the base log to bring down the whole tree, or chop halfway up to leave the stump standing and bring down only the top.
- Manual Harvesting: Fallen logs remain in the world as block structures rather than instant item drops, preserving standard block-breaking gameplay.
- Vanilla Chopping (Sneak Mode): Crouch while chopping to disable the falling physics and harvest a single log—perfect for quick crafting or building tweaks.
- Balanced Durability: Tool durability is only consumed for the exact block you break.
- Purely Server-Side: No client installation required. Java players can join without downloading extra files.

## Installation & Requirements

- Ensure your server is running Minecraft 26.2-26.3 with Fabric Loader and Fabric API installed. Grab the jar that matches your version.
- Drop that jar into your server's mods folder. Jar names include the Minecraft version, so there is no guessing: realistic-tree-physics-1.1.0+26.2.jar is for 26.2, realistic-tree-physics-1.1.0+26.3.jar is for 26.3.
- Restart the server.

## Configuration

Upon the first launch, the mod generates a configuration file at: `config/realistic-tree-physics.json`

Open this file to:

- Enable or disable the mod.
- Adjust tree tipping speed and fall physics.
- Customize block detection definitions for what counts as a tree.

## License

Copyright (c) 2026 FeedMoth. All Rights Reserved.
