# LemmeCutTrees

A lightweight, server-authoritative Fabric tree-felling mod for Minecraft 26.1.2.

LemmeCutTrees preserves vanilla block breaking for loot, enchantments, durability,
exhaustion, sounds, and compatible Fabric callbacks. It is designed to work on a
dedicated Fabric server without requiring the mod on connecting clients, and in an
integrated single-player server when installed client-side.

Players may fell several trees concurrently. Before accepting another tree, the mod
reserves enough remaining axe durability for that tree and all queued logs; Unbreaking
is treated conservatively, while creative and unbreakable axes are unrestricted.

This project derives its tree-detection defaults from
[TreeFeller 1.30.2](https://github.com/ThizThizzyDizzy/tree-feller) by
ThizThizzyDizzy and is distributed under GPL-3.0-only.

The build uses Gradle 9.5.0 because Loom 1.17.19 declares Gradle API 9.5 as its
minimum compatible plugin variant.
