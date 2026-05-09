#settings
execute if entity @s[tag=!lectern_overhaul.player.set_default_settings] run function lectern_overhaul:settings/default
execute if score @s lectern_overhaul.set_settings matches -1099..-1001 run function lectern_overhaul:settings/chat_settings/update
execute if score @s lectern_overhaul.set_settings matches 1.. run function lectern_overhaul:settings/set

#warning about resource pack
execute unless predicate lectern_overhaul:warning_disabled run function lectern_overhaul:warning/check

scoreboard players enable @s lectern_overhaul.set_settings
scoreboard players enable @s lectern_overhaul.disable_warning