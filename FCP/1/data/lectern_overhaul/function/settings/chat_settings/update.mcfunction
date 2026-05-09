scoreboard players operation @s lectern_overhaul.set_settings *= #-1 lectern_overhaul.set_settings

scoreboard players operation #SETTING lectern_overhaul.set_settings = @s lectern_overhaul.set_settings
scoreboard players operation #SETTING lectern_overhaul.set_settings %= #10 lectern_overhaul.set_settings
scoreboard players operation @s lectern_overhaul.set_settings /= #10 lectern_overhaul.set_settings

execute if score #SETTING lectern_overhaul.set_settings matches 1 run data modify storage lectern_overhaul:temp settings.score set value "lectern_overhaul.settings.enabled"
execute if score #SETTING lectern_overhaul.set_settings matches 2 run data modify storage lectern_overhaul:temp settings.score set value "lectern_overhaul.settings.mode"
execute if score #SETTING lectern_overhaul.set_settings matches 3 run data modify storage lectern_overhaul:temp settings.score set value "lectern_overhaul.settings.view_range"
execute if score #SETTING lectern_overhaul.set_settings matches 4 run data modify storage lectern_overhaul:temp settings.score set value "lectern_overhaul.settings.sounds"
execute if score #SETTING lectern_overhaul.set_settings matches 5 run data modify storage lectern_overhaul:temp settings.score set value "lectern_overhaul.settings.animations_speed"

scoreboard players operation #SCORE lectern_overhaul.set_settings = @s lectern_overhaul.set_settings
execute store result storage lectern_overhaul:temp settings.value int 1 run scoreboard players operation #SCORE lectern_overhaul.set_settings %= #10 lectern_overhaul.set_settings
function lectern_overhaul:settings/chat_settings/set_score with storage lectern_overhaul:temp settings

scoreboard players reset @s lectern_overhaul.set_settings
function lectern_overhaul:settings/chat_settings/init