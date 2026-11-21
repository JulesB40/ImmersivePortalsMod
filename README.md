# Immersive Portals: Minecraft 26.3 fork (alpha)

Unofficial fork of [Immersive Portals](https://github.com/iPortalTeam/ImmersivePortalsMod), ported to Minecraft 26.3.

- Portal views use the Blaze3D/renderpearl API, so they run on both the Vulkan and OpenGL backends.
- Works with Sodium 0.9 (`mc26.3-0.9.3-beta.5`) or without it.
- Entities keep moving smoothly when they cross a portal instead of snapping forward.
- Each dimension seen through a portal uses its own lightmap, fog, and time of day.
- Each view is culled to the part of the screen its portal covers.

Requires Java 25, Fabric Loader 0.20+, and Fabric API 0.166+.

**Not working or not verified yet:** Iris is declared incompatible. Alternate dimensions and GUI portals are parked in `misc/`. Rotating-portal camera animation is not applied to the main view. Dedicated servers, real multiplayer, and nested portals have not been tested. Several far portals into one dimension share one renderer.

Automated in-game tests:

```
./gradlew runAutoTest -Pautotest=basic      # or crossing, lighting, occluded
./gradlew runAutoTest -Penable_sodium=false # without Sodium
```

Results and screenshots are written to `run/imm_ptl_autotest/`. The graphics backend comes from `preferredGraphicsBackend` in `run/options.txt`.

---

Update: this repo is not being maintained now. Forks are welcomed.

# Immersive Portals Mod

It's a Minecraft mod that provides see-through portals and seamless teleportation. It also can create "Non-Euclidean" (Uneuclidean) space effect.

![immptl.png](https://i.loli.net/2021/09/30/chHMG45dsnZNqep.png)

[On CurseForge](https://www.curseforge.com/minecraft/mc-mods/immersive-portals-mod)     [On Modrinth](https://modrinth.com/mod/immersiveportals)     [Website](https://qouteall.fun/immptl/)

This mod changes a lot of underlying Minecraft mechanics. This mod allows the client to load multiple dimensions at the same time and synchronize remote world information(blocks/entities) to client. It can render portal-in-portals. The portal rendering is roughly compatible with some versions of Sodium and Iris. The portal can transform player scale and gravity direction.  [Implementation Details](https://qouteall.fun/immptl/wiki/Implementation-Details)

(This is the Fabric version of Immersive Portals. [The Forge version](https://github.com/iPortalTeam/ImmersivePortalsModForNeo))

## API

This mod also provides some API for:

* Manage see-through portals
* Dynamically add dimensions
* Synchronize remote chunks to client
* Render the world into GUI
* Other utilities

[API description](https://qouteall.fun/immptl/wiki/API-for-Other-Mods.html).

## How to run this code
https://fabricmc.net/wiki/tutorial:setup

## Other

[Wiki](https://qouteall.fun/immptl/wiki/)

[Discord Server](https://discord.gg/BZxgURK)

[Support qouteall on Patreon](https://www.patreon.com/qouteall)

