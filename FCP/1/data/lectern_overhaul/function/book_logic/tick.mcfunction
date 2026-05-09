#text animations when one-page mode or two-page mode and it's only one page/cover page needs to be displayed
execute if score @s lectern_overhaul.settings.mode matches 1 if score @s lectern_overhaul.animation_timer matches -2147483648..2147483647 run function lectern_overhaul:animations/text/one_page/init
execute unless score @s lectern_overhaul.settings.animations_speed matches 2 if score @s lectern_overhaul.settings.mode matches 2 run function lectern_overhaul:book_logic/two_page_with_one_page

#text animations for two-page mode
execute unless entity @s[tag=lectern_overhaul.book.no_two_page_mode_logic] if score @s lectern_overhaul.settings.mode matches 2 if score @s lectern_overhaul.animation_timer matches -2147483648..2147483647 run function lectern_overhaul:animations/text/two_pages/init

tag @s[tag=lectern_overhaul.book.no_two_page_mode_logic] remove lectern_overhaul.book.no_two_page_mode_logic

#page and covers animations
scoreboard players operation #SPEED lectern_overhaul.settings.animations_speed = @s lectern_overhaul.settings.animations_speed

execute as @e[tag=lectern_overhaul.book.page,distance=..0.1,limit=1,type=item_display] if score @s lectern_overhaul.animation_timer matches -2147483648..2147483647 run function lectern_overhaul:animations/page/init
execute as @e[tag=lectern_overhaul.book.left_cover,distance=..0.1,limit=1,type=item_display] if score @s lectern_overhaul.animation_timer matches -2147483648..2147483647 run function lectern_overhaul:animations/left_cover/init
execute as @e[tag=lectern_overhaul.book.right_cover,distance=..0.1,limit=1,type=item_display] if score @s lectern_overhaul.animation_timer matches -2147483648..2147483647 run function lectern_overhaul:animations/right_cover/init

#book destroying
execute if block ~ ~-0.5 ~ lectern[has_book=true] run function lectern_overhaul:book_logic/replace_lectern with entity @s data
execute unless block ~ ~-0.5 ~ lectern run function lectern_overhaul:book_logic/kill with entity @s data
execute positioned ~-0.5 ~ ~-0.5 run function lectern_overhaul:book_logic/book_interact