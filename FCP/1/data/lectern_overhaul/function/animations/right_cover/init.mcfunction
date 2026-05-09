execute if score @s lectern_overhaul.animation_timer matches -23 run playsound block.wool.step block @a[scores={lectern_overhaul.settings.sounds=1}] ~ ~ ~ 0.2
execute if score @s lectern_overhaul.animation_timer matches 0 run playsound block.wool.step block @a[scores={lectern_overhaul.settings.sounds=1}] ~ ~ ~ 0.2

execute if score #SPEED lectern_overhaul.settings.animations_speed matches 2 if score @s lectern_overhaul.animation_timer matches ..-4 run scoreboard players set @s lectern_overhaul.animation_timer -3
execute if score #SPEED lectern_overhaul.settings.animations_speed matches 2 if score @s lectern_overhaul.animation_timer matches 0..19 run scoreboard players set @s lectern_overhaul.animation_timer 20

execute if score @s lectern_overhaul.animation_timer matches -23..-3 run function lectern_overhaul:animations/right_cover/close/init
execute if score @s lectern_overhaul.animation_timer matches 0..20 run function lectern_overhaul:animations/right_cover/open/init

scoreboard players add @s lectern_overhaul.animation_timer 1
execute if score #SPEED lectern_overhaul.settings.animations_speed matches 1 run scoreboard players add @s lectern_overhaul.animation_timer 1
execute if score @s lectern_overhaul.animation_timer matches -2..-1 run scoreboard players reset @s lectern_overhaul.animation_timer
execute if score @s lectern_overhaul.animation_timer matches 21.. run scoreboard players reset @s lectern_overhaul.animation_timer