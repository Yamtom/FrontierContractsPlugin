scoreboard players remove #DISTANCE lectern_overhaul.raycast 1

execute if block ~ ~ ~ lectern[has_book=true] align xyz positioned ~.5 ~1 ~.5 unless entity @e[tag=lectern_overhaul.book.root,limit=1,distance=..0.1,type=text_display] run return run function lectern_overhaul:place_book/start_summon

execute if score #DISTANCE lectern_overhaul.raycast matches 1.. positioned ^ ^ ^.1 run function lectern_overhaul:place_book/raycast_loop