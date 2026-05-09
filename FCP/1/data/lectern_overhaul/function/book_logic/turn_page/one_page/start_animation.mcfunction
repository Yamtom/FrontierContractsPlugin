tag @s remove lectern_overhaul.book.start_animation

scoreboard players set @s lectern_overhaul.animation_timer -11

execute if score @s[tag=lectern_overhaul.book.animation_add] lectern_overhaul.page matches 2.. if score @s lectern_overhaul.page <= @s lectern_overhaul.page_max as @e[tag=lectern_overhaul.book.page,distance=..0.1,limit=1,type=item_display] run scoreboard players set @s lectern_overhaul.animation_timer 0
execute if score @s[tag=lectern_overhaul.book.animation_remove] lectern_overhaul.page matches 1.. if score @s lectern_overhaul.page < @s lectern_overhaul.page_max as @e[tag=lectern_overhaul.book.page,distance=..0.1,limit=1,type=item_display] run scoreboard players set @s lectern_overhaul.animation_timer -23

execute if score @s[tag=lectern_overhaul.book.animation_remove] lectern_overhaul.page matches 0 as @e[tag=lectern_overhaul.book.left_cover,distance=..0.1,limit=1,type=item_display] run scoreboard players set @s lectern_overhaul.animation_timer -23
execute if score @s[tag=lectern_overhaul.book.animation_add] lectern_overhaul.page matches 1 as @e[tag=lectern_overhaul.book.left_cover,distance=..0.1,limit=1,type=item_display] run scoreboard players set @s lectern_overhaul.animation_timer 0

execute if score @s[tag=lectern_overhaul.book.animation_add] lectern_overhaul.page > @s lectern_overhaul.page_max as @e[tag=lectern_overhaul.book.right_cover,distance=..0.1,limit=1,type=item_display] run scoreboard players set @s lectern_overhaul.animation_timer -23
execute if score @s[tag=lectern_overhaul.book.animation_remove] lectern_overhaul.page = @s lectern_overhaul.page_max as @e[tag=lectern_overhaul.book.right_cover,distance=..0.1,limit=1,type=item_display] run scoreboard players set @s lectern_overhaul.animation_timer 0

tag @s remove lectern_overhaul.book.animation_add
tag @s remove lectern_overhaul.book.animation_remove