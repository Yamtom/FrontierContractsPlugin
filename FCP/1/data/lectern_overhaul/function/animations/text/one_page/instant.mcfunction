function lectern_overhaul:book_logic/set_text/one_page/init
scoreboard players reset @s lectern_overhaul.animation_timer

data modify storage lectern_overhaul:temp animation.progress set value 10
execute if score @s[tag=lectern_overhaul.book.writable_book] lectern_overhaul.page matches 0 run data modify storage lectern_overhaul:temp animation.progress set value 0
execute if score @s lectern_overhaul.page > @s lectern_overhaul.page_max run data modify storage lectern_overhaul:temp animation.progress set value 0
function lectern_overhaul:animations/text/one_page/appear/macro with storage lectern_overhaul:temp animation
