data modify storage lectern_overhaul:temp cover.text set value [{"text":"\""},{"text":"a"},{"text":"\""},{"text":"\n"},{"text":"a","italic":true}]
data modify storage lectern_overhaul:temp cover.text[1]."text" set from entity @s data.book.components."minecraft:written_book_content".title.raw
data modify storage lectern_overhaul:temp cover.text[4]."text" set from entity @s data.book.components."minecraft:written_book_content".author

data modify entity @s text set from storage lectern_overhaul:temp cover.text
data modify entity @s alignment set value "center"
data modify entity @s line_width set value 250