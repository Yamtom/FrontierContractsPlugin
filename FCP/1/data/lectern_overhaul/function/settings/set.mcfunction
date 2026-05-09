execute if score @s lectern_overhaul.set_settings matches ..99999 run return run function lectern_overhaul:settings/chat_settings/init
execute if score @s lectern_overhaul.set_settings matches 200000.. run return run function lectern_overhaul:settings/chat_settings/init

scoreboard players operation @s lectern_overhaul.settings.enabled = @s lectern_overhaul.set_settings
scoreboard players operation @s lectern_overhaul.settings.enabled %= #10 lectern_overhaul.set_settings
scoreboard players operation @s lectern_overhaul.set_settings /= #10 lectern_overhaul.set_settings

scoreboard players operation @s lectern_overhaul.settings.mode = @s lectern_overhaul.set_settings
scoreboard players operation @s lectern_overhaul.settings.mode %= #10 lectern_overhaul.set_settings
scoreboard players operation @s lectern_overhaul.set_settings /= #10 lectern_overhaul.set_settings

scoreboard players operation @s lectern_overhaul.settings.view_range = @s lectern_overhaul.set_settings
scoreboard players operation @s lectern_overhaul.settings.view_range %= #10 lectern_overhaul.set_settings
scoreboard players operation @s lectern_overhaul.set_settings /= #10 lectern_overhaul.set_settings

scoreboard players operation @s lectern_overhaul.settings.sounds = @s lectern_overhaul.set_settings
scoreboard players operation @s lectern_overhaul.settings.sounds %= #10 lectern_overhaul.set_settings
scoreboard players operation @s lectern_overhaul.set_settings /= #10 lectern_overhaul.set_settings

scoreboard players operation @s lectern_overhaul.settings.animations_speed = @s lectern_overhaul.set_settings
scoreboard players operation @s lectern_overhaul.settings.animations_speed %= #10 lectern_overhaul.set_settings

scoreboard players reset @s lectern_overhaul.set_settings