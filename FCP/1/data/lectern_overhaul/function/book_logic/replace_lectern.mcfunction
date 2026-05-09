data modify storage lectern_overhaul:temp book.item set from block ~ ~-0.5 ~ Book
summon item ~ ~ ~ {Item:{id:"stone",count:1},PickupDelay:10s,Tags:["lectern_overhaul.set_item"]}
execute as @e[tag=lectern_overhaul.set_item,distance=..0.1,limit=1,type=item] run function lectern_overhaul:book_logic/set_item_to_drop
$fill ~ ~-0.5 ~ ~ ~-0.5 ~ lectern[has_book=false,powered=true,facing=$(facing)]