execute unless entity @s[tag=lectern_overhaul.book.writable_book] if score @s lectern_overhaul.page matches 0 run return run function lectern_overhaul:book_logic/set_text/one_page/cover_text
data modify entity @s alignment set value "left"
data modify entity @s line_width set value 114

execute if score @s lectern_overhaul.page > @s lectern_overhaul.page_max run return run tag @s add lectern_overhaul.book.closed
execute if entity @s[tag=lectern_overhaul.book.writable_book] if score @s lectern_overhaul.page matches 0 run return run tag @s add lectern_overhaul.book.closed
execute if score @s lectern_overhaul.page <= @s lectern_overhaul.page_max run tag @s remove lectern_overhaul.book.closed

execute store result storage lectern_overhaul:temp book.page int 0.9999 run scoreboard players get @s lectern_overhaul.page 
function lectern_overhaul:book_logic/set_text/one_page/macro with storage lectern_overhaul:temp book