scoreboard players operation @s lectern_overhaul.previous_page = @s lectern_overhaul.page
$scoreboard players $(operation) @s lectern_overhaul.page 2
$scoreboard players $(operation) @s lectern_overhaul.left_page 2
tag @s add lectern_overhaul.book.start_animation
$tag @s add lectern_overhaul.book.animation_$(operation)