data modify storage lectern_overhaul:temp update.old_pages set from storage lectern_overhaul:temp update.book.components."minecraft:written_book_content".pages
data modify storage lectern_overhaul:temp update.new_pages set value []

data modify storage lectern_overhaul:temp update.page set from storage lectern_overhaul:temp update.old_pages[0].raw
function lectern_overhaul:update_to_new/update_old_book/loop with storage lectern_overhaul:temp update

data modify storage lectern_overhaul:temp update.book.components."minecraft:written_book_content".pages set from storage lectern_overhaul:temp update.new_pages