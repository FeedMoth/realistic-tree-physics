# Realistic Tree Physics

Transform tree harvesting in Minecraft with dynamic, physics-based timber animations. Chop down trees and watch them fall naturally with realistic weight and motion—all managed seamlessly by the server, in singleplayer and on dedicated servers.

## Features

- Dynamic Falling Physics: Trees tip over and fall in the direction you're facing when chopped, collapsing onto the ground as interactable blocks.
- Partial Felling: Chop the base log to bring down the whole tree, or chop halfway up to leave the stump standing and bring down only the top.
- Manual Harvesting: Fallen logs remain in the world as block structures rather than instant item drops, preserving standard block-breaking gameplay.
- Vanilla Chopping (Sneak Mode): Crouch while chopping to disable the falling physics and harvest a single log—perfect for quick crafting or building tweaks.
- Balanced Durability: Tool durability is only consumed for the exact block you break.
- Works on dedicated servers and in singleplayer: players joining a server that has it do not need to install it themselves; install it in your own game for singleplayer.

## Installation & Requirements

- Ensure you are running Minecraft 26.1-26.3, 1.21.11, or 1.20.1 with Fabric Loader and Fabric API installed. Grab the jar that matches your version.
- Drop that jar into your mods folder: your server's mods folder for a dedicated server, or your game's mods folder for singleplayer. Jar names include the Minecraft version, so there is no guessing: realistic-tree-physics-1.1.1+26.3.jar is for 26.3.
- Restart the server, or the game.

## Configuration

Upon the first launch, the mod generates a configuration file at: `config/realistic-tree-physics.json`

Open this file to:

- Enable or disable the mod.
- Adjust tree tipping speed and fall physics.
- Customize block detection definitions for what counts as a tree.

## License

Copyright (c) 2026 FeedMoth. All Rights Reserved.
