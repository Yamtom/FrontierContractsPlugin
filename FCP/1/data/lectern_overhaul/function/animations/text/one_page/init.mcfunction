execute if score @s lectern_overhaul.settings.animations_speed matches 2 run return run function lectern_overhaul:animations/text/one_page/instant

execute unless entity @s[tag=lectern_overhaul.book.closed] if score @s lectern_overhaul.animation_timer matches 0..10 run function lectern_overhaul:animations/text/one_page/appear/init
execute unless entity @s[tag=lectern_overhaul.book.closed] if score @s lectern_overhaul.animation_timer matches -11..-1 run function lectern_overhaul:animations/text/one_page/disappear/init

scoreboard players add @s lectern_overhaul.animation_timer 1
execute if score @s lectern_overhaul.settings.animations_speed matches 1 run scoreboard players add @s lectern_overhaul.animation_timer 1
execute if score @s lectern_overhaul.animation_timer matches 10.. run return run scoreboard players reset @s lectern_overhaul.animation_timer

execute if score @s lectern_overhaul.settings.animations_speed matches 0 if score @s lectern_overhaul.animation_timer matches 0 run function lectern_overhaul:book_logic/set_text/one_page/init
execute if score @s lectern_overhaul.settings.animations_speed matches 1 if score @s lectern_overhaul.animation_timer matches 1 run function lectern_overhaul:book_logic/set_text/one_page/init

tag @s add lectern_overhaul.book.no_two_page_mode_logic