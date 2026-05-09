scoreboard players reset @s lectern_overhaul.set_settings

data modify storage lectern_overhaul:temp settings.tellraw set value {enabled_on:true,enabled_off:false,mode_one:true,mode_two:false,view_range_unlimited:true,view_range_4:false,view_range_8:false,view_range_12:false,view_range_16:false,sounds_on:true,sounds_off:false,anim_speed_default:true,anim_speed_2x:false,anim_speed_instant:false}

execute if score @s lectern_overhaul.settings.enabled matches 0 run data modify storage lectern_overhaul:temp settings.tellraw merge value {enabled_on:false,enabled_off:true}

execute if score @s lectern_overhaul.settings.mode matches 2 run data modify storage lectern_overhaul:temp settings.tellraw merge value {mode_one:false,mode_two:true}

execute if score @s lectern_overhaul.settings.view_range matches 1 run data modify storage lectern_overhaul:temp settings.tellraw merge value {view_range_unlimited:false,view_range_4:true}
execute if score @s lectern_overhaul.settings.view_range matches 2 run data modify storage lectern_overhaul:temp settings.tellraw merge value {view_range_unlimited:false,view_range_8:true}
execute if score @s lectern_overhaul.settings.view_range matches 3 run data modify storage lectern_overhaul:temp settings.tellraw merge value {view_range_unlimited:false,view_range_12:true}
execute if score @s lectern_overhaul.settings.view_range matches 4 run data modify storage lectern_overhaul:temp settings.tellraw merge value {view_range_unlimited:false,view_range_16:true}

execute if score @s lectern_overhaul.settings.sounds matches 0 run data modify storage lectern_overhaul:temp settings.tellraw merge value {sounds_on:false,sounds_off:true}

execute if score @s lectern_overhaul.settings.animations_speed matches 1 run data modify storage lectern_overhaul:temp settings.tellraw merge value {anim_speed_default:false,anim_speed_2x:true}
execute if score @s lectern_overhaul.settings.animations_speed matches 2 run data modify storage lectern_overhaul:temp settings.tellraw merge value {anim_speed_default:false,anim_speed_instant:true}


function lectern_overhaul:settings/chat_settings/tellraw with storage lectern_overhaul:temp settings.tellraw