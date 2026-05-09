data modify entity @s data.book set from block ~ ~-0.5 ~ Book
execute if data entity @s data.book.components."minecraft:writable_book_content" run tag @s add lectern_overhaul.book.writable_book
execute unless entity @s[tag=lectern_overhaul.book.writable_book] store result score @s lectern_overhaul.page_max run data get entity @s data.book.components."minecraft:written_book_content".pages
execute if entity @s[tag=lectern_overhaul.book.writable_book] store result score @s lectern_overhaul.page_max run data get entity @s data.book.components."minecraft:writable_book_content".pages
scoreboard players operation @s lectern_overhaul.settings.animations_speed = #SPEED lectern_overhaul.settings.animations_speed
scoreboard players set @s lectern_overhaul.page 0
scoreboard players set @s lectern_overhaul.animation_timer -1
scoreboard players operation @s lectern_overhaul.settings.mode = #MODE lectern_overhaul.settings.mode
execute if score @s lectern_overhaul.settings.mode matches 2 run scoreboard players set @s lectern_overhaul.left_page -1
tag @s remove set.data