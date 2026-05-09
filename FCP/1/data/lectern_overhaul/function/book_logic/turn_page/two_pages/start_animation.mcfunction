tag @s remove lectern_overhaul.book.start_animation

scoreboard players set @s lectern_overhaul.animation_timer -11
execute as @e[tag=lectern_overhaul.book.second_text,distance=..0.1,limit=1,type=text_display] run scoreboard players set @s lectern_overhaul.animation_timer -11

execute if score @s[tag=lectern_overhaul.book.animation_add] lectern_overhaul.page matches 4.. if score @s lectern_overhaul.left_page <= @s lectern_overhaul.page_max as @e[tag=lectern_overhaul.book.page,distance=..0.1,limit=1,type=item_display] run scoreboard players set @s lectern_overhaul.animation_timer 0
execute if score @s[tag=lectern_overhaul.book.animation_remove] lectern_overhaul.page matches 2.. unless score @s lectern_overhaul.page = @s lectern_overhaul.page_max unless score @s lectern_overhaul.left_page = @s lectern_overhaul.page_max as @e[type=item_display,tag=lectern_overhaul.book.page,distance=..0.1,limit=1] run scoreboard players set @s lectern_overhaul.animation_timer -23

execute if score @s[tag=lectern_overhaul.book.animation_remove] lectern_overhaul.page matches 0 as @e[tag=lectern_overhaul.book.left_cover,distance=..0.1,limit=1,type=item_display] run scoreboard players set @s lectern_overhaul.animation_timer -23
execute if score @s[tag=lectern_overhaul.book.animation_add] lectern_overhaul.page matches 2 as @e[tag=lectern_overhaul.book.left_cover,distance=..0.1,limit=1,type=item_display] run scoreboard players set @s lectern_overhaul.animation_timer 0

execute if entity @s[tag=lectern_overhaul.book.animation_add] if score @s lectern_overhaul.left_page > @s lectern_overhaul.page_max as @e[tag=lectern_overhaul.book.right_cover,distance=..0.1,limit=1,type=item_display] run scoreboard players set @s lectern_overhaul.animation_timer -23
execute if entity @s[tag=lectern_overhaul.book.animation_remove] if score @s lectern_overhaul.page = @s lectern_overhaul.page_max as @e[tag=lectern_overhaul.book.right_cover,distance=..0.1,limit=1,type=item_display] run scoreboard players set @s lectern_overhaul.animation_timer 0
execute if entity @s[tag=lectern_overhaul.book.animation_remove] if score @s lectern_overhaul.left_page = @s lectern_overhaul.page_max as @e[tag=lectern_overhaul.book.right_cover,distance=..0.1,limit=1,type=item_display] run scoreboard players set @s lectern_overhaul.animation_timer 0


tag @s remove lectern_overhaul.book.animation_add
tag @s remove lectern_overhaul.book.animation_remove