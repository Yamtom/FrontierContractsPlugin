execute if score @s lectern_overhaul.settings.animations_speed matches 2 run return run function lectern_overhaul:animations/text/two_pages/instant

scoreboard players operation #TIMER lectern_overhaul.animation_timer = @s lectern_overhaul.animation_timer

execute unless entity @s[tag=lectern_overhaul.book.closed] if score @s lectern_overhaul.animation_timer matches 0..10 run function lectern_overhaul:animations/text/two_pages/appear_left/init
execute unless entity @s[tag=lectern_overhaul.book.closed] if score @s lectern_overhaul.animation_timer matches -11..-1 run function lectern_overhaul:animations/text/two_pages/disappear_left/init
execute unless entity @s[tag=lectern_overhaul.book.closed] unless entity @s[tag=lectern_overhaul.book.no_second_text] as @e[type=text_display,tag=lectern_overhaul.book.second_text,distance=..0.1,limit=1] if score #TIMER lectern_overhaul.animation_timer matches 0..10 run function lectern_overhaul:animations/text/two_pages/appear_right/init
execute unless entity @s[tag=lectern_overhaul.book.closed] unless entity @s[tag=lectern_overhaul.book.no_second_text] as @e[type=text_display,tag=lectern_overhaul.book.second_text,distance=..0.1,limit=1] if score #TIMER lectern_overhaul.animation_timer matches -11..-1 run function lectern_overhaul:animations/text/two_pages/disappear_right/init

scoreboard players add @s lectern_overhaul.animation_timer 1
execute if score @s lectern_overhaul.settings.animations_speed matches 1 run scoreboard players add @s lectern_overhaul.animation_timer 1
execute if score @s lectern_overhaul.animation_timer matches 10.. run return run scoreboard players reset @s lectern_overhaul.animation_timer

execute if score @s lectern_overhaul.settings.animations_speed matches 0 if score @s lectern_overhaul.animation_timer matches 0 run function lectern_overhaul:book_logic/set_text/two_pages/init
execute if score @s lectern_overhaul.settings.animations_speed matches 1 if score @s lectern_overhaul.animation_timer matches 1 run function lectern_overhaul:book_logic/set_text/two_pages/init