execute unless entity @s[tag=lectern_overhaul.book.writable_book] if score @s lectern_overhaul.page matches 0 run return run function lectern_overhaul:book_logic/set_text/two_pages/cover_text
data modify entity @s alignment set value "left"
data modify entity @s line_width set value 114

execute if score @s lectern_overhaul.left_page > @s lectern_overhaul.page_max run return run tag @s add lectern_overhaul.book.closed
execute if entity @s[tag=lectern_overhaul.book.writable_book] if score @s lectern_overhaul.page matches 0 run return run tag @s add lectern_overhaul.book.closed
execute if score @s lectern_overhaul.left_page <= @s lectern_overhaul.page_max run tag @s remove lectern_overhaul.book.closed
execute if score @s lectern_overhaul.page > @s lectern_overhaul.page_max run tag @s add lectern_overhaul.book.no_second_text
execute if score @s lectern_overhaul.page <= @s lectern_overhaul.page_max run tag @s remove lectern_overhaul.book.no_second_text

execute store result storage lectern_overhaul:temp book.right_page int 0.9999 run scoreboard players get @s lectern_overhaul.page
execute store result storage lectern_overhaul:temp book.left_page int 0.9999 run data get storage lectern_overhaul:temp book.right_page
execute unless entity @s[tag=lectern_overhaul.book.writable_book] run function lectern_overhaul:book_logic/set_text/two_pages/macro_written with storage lectern_overhaul:temp book
execute if entity @s[tag=lectern_overhaul.book.writable_book] run function lectern_overhaul:book_logic/set_text/two_pages/macro_writable with storage lectern_overhaul:temp book