execute if score @s lectern_overhaul.animation_timer matches -23 run playsound item.book.page_turn block @a[scores={lectern_overhaul.settings.sounds=1}] ~ ~ ~ 0.3
execute if score @s lectern_overhaul.animation_timer matches 0 run playsound item.book.page_turn block @a[scores={lectern_overhaul.settings.sounds=1}] ~ ~ ~ 0.3

execute if score #SPEED lectern_overhaul.settings.animations_speed matches 2 run return run scoreboard players reset @s lectern_overhaul.animation_timer

execute if score @s lectern_overhaul.animation_timer matches -23..-3 run function lectern_overhaul:animations/page/previous/init
execute if score @s lectern_overhaul.animation_timer matches 0..20 run function lectern_overhaul:animations/page/next/init

scoreboard players add @s lectern_overhaul.animation_timer 1
execute if score #SPEED lectern_overhaul.settings.animations_speed matches 1 run scoreboard players add @s lectern_overhaul.animation_timer 1
execute if score @s lectern_overhaul.animation_timer matches -2..-1 run scoreboard players reset @s lectern_overhaul.animation_timer
execute if score @s lectern_overhaul.animation_timer matches 21.. run scoreboard players reset @s lectern_overhaul.animation_timer