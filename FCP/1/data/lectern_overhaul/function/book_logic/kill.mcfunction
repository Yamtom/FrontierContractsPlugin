data modify storage lectern_overhaul:temp book.item set from entity @s data.book
summon item ~ ~ ~ {Item:{id:"stone",count:1},PickupDelay:10s,Tags:["lectern_overhaul.set_item"]}
execute as @e[tag=lectern_overhaul.set_item,distance=..0.1,limit=1,type=item] run function lectern_overhaul:book_logic/set_item_to_drop
execute on passengers run kill @s
execute positioned ~-0.5 ~ ~-0.5 as @e[tag=lectern_overhaul.book.interact,dx=0,dy=0,dz=0,limit=2,type=interaction] run kill @s
kill @s
$execute if block ~ ~-0.5 ~ lectern[powered=true] run setblock ~ ~-0.5 ~ lectern[powered=false,facing=$(facing)]