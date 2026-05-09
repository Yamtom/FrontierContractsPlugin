execute if block ~ ~-0.5 ~ lectern[facing=north] run data modify storage lectern_overhaul:temp place_book set value {rotation:180f,facing:"north",view_range:1f,interpolation_duration:1}
execute if block ~ ~-0.5 ~ lectern[facing=south] run data modify storage lectern_overhaul:temp place_book set value {rotation:0f,facing:"south",view_range:1f,interpolation_duration:1}
execute if block ~ ~-0.5 ~ lectern[facing=west] run data modify storage lectern_overhaul:temp place_book set value {rotation:90f,facing:"west",view_range:1f,interpolation_duration:1}
execute if block ~ ~-0.5 ~ lectern[facing=east] run data modify storage lectern_overhaul:temp place_book set value {rotation:-90f,facing:"east",view_range:1f,interpolation_duration:1}

execute unless score @s lectern_overhaul.settings.view_range matches 0 store result storage lectern_overhaul:temp place_book.view_range float 0.03125 run scoreboard players get @s lectern_overhaul.settings.view_range
execute if score @s lectern_overhaul.settings.animations_speed matches 2 run data modify storage lectern_overhaul:temp place_book.interpolation_duration set value 0

function lectern_overhaul:place_book/summon with storage lectern_overhaul:temp place_book