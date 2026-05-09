#book logic
execute as @e[distance=0..,tag=lectern_overhaul.book.root,type=text_display] at @s if entity @a[distance=..32] run function lectern_overhaul:book_logic/tick

#update books from older version of datapack
execute as @e[distance=0..,tag=lectern.book,type=marker] at @s run function lectern_overhaul:update_to_new/init

#tick player
execute as @a run function lectern_overhaul:tick_player