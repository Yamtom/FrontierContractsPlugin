scoreboard players add @s lectern_overhaul.player.warning_timer 1

execute if score @s lectern_overhaul.player.warning_timer matches 20 run tellraw @a {translate:"warning.lectern_overhaul.resource_pack",fallback:"⚠ [Lectern Overhaul] Resource Pack isn't installed/loaded.\n Please download it and/or enable it in \"Resource Packs...\" menu.",color:gold}