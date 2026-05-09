function lectern_overhaul:book_logic/set_text/two_pages/init
scoreboard players reset @s lectern_overhaul.animation_timer

data modify storage lectern_overhaul:temp animation.progress set value 10
execute if score @s[tag=lectern_overhaul.book.writable_book] lectern_overhaul.page matches 0 run data modify storage lectern_overhaul:temp animation.progress set value 0
execute if score @s lectern_overhaul.left_page > @s lectern_overhaul.page_max run data modify storage lectern_overhaul:temp animation.progress set value 0
execute if score @s lectern_overhaul.page matches 0 run return run function lectern_overhaul:animations/text/two_pages/instant_animation/one_page
execute if score @s lectern_overhaul.page > @s lectern_overhaul.page_max run return run function lectern_overhaul:animations/text/two_pages/instant_animation/one_page
function lectern_overhaul:animations/text/two_pages/instant_animation/two_pages