advancement revoke @s only lectern_overhaul:used_book

execute unless score @s lectern_overhaul.settings.enabled matches 1 run return fail

execute store result score #DISTANCE lectern_overhaul.raycast run attribute @s block_interaction_range get 10

execute anchored eyes positioned ^ ^ ^.1 run function lectern_overhaul:place_book/raycast_loop