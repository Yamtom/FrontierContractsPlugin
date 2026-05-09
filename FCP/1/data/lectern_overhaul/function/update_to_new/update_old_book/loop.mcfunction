$data modify storage lectern_overhaul:temp update.new_pages append value {raw:$(page)}

data remove storage lectern_overhaul:temp update.page
data remove storage lectern_overhaul:temp update.old_pages[0]
data modify storage lectern_overhaul:temp update.page set from storage lectern_overhaul:temp update.old_pages[0].raw
execute if data storage lectern_overhaul:temp update.page run function lectern_overhaul:update_to_new/update_old_book/loop with storage lectern_overhaul:temp update