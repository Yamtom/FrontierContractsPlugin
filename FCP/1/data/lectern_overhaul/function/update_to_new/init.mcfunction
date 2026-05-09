data modify storage lectern_overhaul:temp update.book set from entity @s data.book_item
function lectern_overhaul:update_to_new/update_old_book/init

data modify block ~ ~-0.5 ~ Book set from storage lectern_overhaul:temp update.book

scoreboard players set @s lectern_overhaul.settings.mode 1
scoreboard players set @s lectern_overhaul.settings.view_range 0
scoreboard players set @s lectern_overhaul.settings.sounds 1

function lectern_overhaul:place_book/start_summon

function lectern_overhaul:update_to_new/kill_old_book