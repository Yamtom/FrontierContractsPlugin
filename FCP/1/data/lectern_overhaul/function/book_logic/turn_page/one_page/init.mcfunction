execute unless score @s lectern_overhaul.animation_timer matches -2147483648..2147483647 if entity @e[tag=lectern_overhaul.book.interact,tag=left,dx=0,dy=0,dz=0,nbt={interaction:{}},type=interaction] if score @s lectern_overhaul.page matches 1.. run function lectern_overhaul:book_logic/turn_page/one_page/change_page {operation:"remove"}
execute unless score @s lectern_overhaul.animation_timer matches -2147483648..2147483647 if entity @e[tag=lectern_overhaul.book.interact,tag=right,dx=0,dy=0,dz=0,nbt={interaction:{}},type=interaction] if score @s lectern_overhaul.page <= @s lectern_overhaul.page_max run function lectern_overhaul:book_logic/turn_page/one_page/change_page {operation:"add"}

execute if entity @s[tag=lectern_overhaul.book.start_animation] at @s run function lectern_overhaul:book_logic/turn_page/one_page/start_animation

execute as @e[tag=lectern_overhaul.book.interact,dx=0,dy=0,dz=0,type=interaction] run data remove entity @s interaction