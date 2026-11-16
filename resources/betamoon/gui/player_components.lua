-- Engine-owned examples of the public component grammar. Builders/bindings run only at publication.
local definitions = {}
for _, stat in ipairs({"health", "armor", "breath"}) do
    local source = stat
    definitions[#definitions + 1] = {
        key="betamoon:hud/" .. source, requires={"hud_observation"}, exports={bounds="meter"},
        props={
            presentation={type="string", default="icons"}, skin={type="string", default=""},
            nativeEffects={type="boolean", default=true}, columns={type="integer", default=0, min=0, max=256},
            iconWidth={type="integer", default=0, min=0, max=256},
            iconHeight={type="integer", default=0, min=0, max=256},
            pitchX={type="integer", default=0, min=0, max=512}, pitchY={type="integer", default=0, min=0, max=512},
            width={type="integer", default=81, min=1, max=16384},
            height={type="integer", default=9, min=1, max=16384}
        },
        build=function(props)
            if props.presentation ~= "icons" and props.presentation ~= "bar" then
                error("HUD stat presentation must be icons or bar")
            end
            local air = source == "breath"
            local maximum = air and 300 or 20
            local node = {id="meter", anchorX=0, anchorY=0}
            node.bind=function(ctx)
                local stats = ctx.hud:stats()
                return {value=stats[air and "air" or source] or 0,
                    visible=stats.showSurvivalStats == true and
                        (source == "health" or (air and stats.showBreath == true) or
                            (source == "armor" and (stats.armor or 0) > 0))}
            end
            if props.presentation == "bar" then
                node.type="progress"
                node.maximum=maximum
                node.width=props.width
                node.height=props.height
            else
                node.type="iconmeter"
                node.minimum=0
                node.maximum=maximum
                node.unitsPerIcon=air and 30 or 2
                node.maxIcons=10
                node.skin=props.skin ~= "" and props.skin or
                    (source == "health" and "minecraft:hearts" or source == "armor" and "minecraft:armor" or "minecraft:air")
                node.fillOrder=source == "armor" and "right_to_left" or "left_to_right"
                node.partialMode="steps"
                node.hudSource=source
                node.nativeEffects=props.nativeEffects
                node.columns=props.columns
                node.iconWidth=props.iconWidth
                node.iconHeight=props.iconHeight
                node.pitchX=props.pitchX
                node.pitchY=props.pitchY
            end
            return node
        end
    }
end

definitions[#definitions + 1] = {
    key="betamoon:hud/crosshair", requires={"hud_observation"}, exports={bounds="reticle"},
    props={image={type="string", default=""}, size={type="integer", default=16, min=1, max=1024}},
    build=function(props)
        return {type="image", id="reticle", width=props.size, height=props.size,
            nativeSprite=props.image == "" and "minecraft:crosshair" or "", image=props.image}
    end
}

definitions[#definitions + 1] = {
    key="betamoon:hud/hotbar", requires={"hud_observation", "native_items"}, exports={bounds="bar"},
    props={
        layout={type="string", default="horizontal"}, columns={type="integer", default=3, min=1, max=9},
        showCount={type="boolean", default=true}, showDurability={type="boolean", default=true},
        pickupAnimation={type="boolean", default=true}, showFrame={type="boolean", default=true}
    },
    build=function(props)
        if props.layout ~= "horizontal" and props.layout ~= "vertical" and props.layout ~= "grid" then
            error("HUD hotbar layout must be horizontal, vertical or grid")
        end
        local columns = props.layout == "horizontal" and 9 or props.layout == "vertical" and 1 or props.columns
        local rows = math.ceil(9 / columns)
        local children = {}
        if props.showFrame then
            if props.layout == "horizontal" then
                children[#children+1] = {type="image", width=182, height=22, anchorX=0, anchorY=0,
                    nativeSprite="minecraft:hotbar"}
            else
                for i=1,9 do
                    children[#children+1] = {type="image", width=22, height=22, anchorX=0, anchorY=0,
                        x=((i-1)%columns)*20, y=math.floor((i-1)/columns)*20, nativeSprite="minecraft:hotbar-cell"}
                end
            end
        end
        children[#children+1] = {type="image", id="selection", width=24, height=22,
            anchorX=0, anchorY=0, nativeSprite="minecraft:hotbar-selection", transition=false, bind=function(ctx)
                local slot = (ctx.hud:hotbar().selectedSlot or 1)-1
                return {x=(slot%columns)*20-1, y=math.floor(slot/columns)*20-1}
            end}
        for i=1,9 do
            children[#children+1] = {type="item", id="slot"..i, key="slot"..i,
                anchorX=0, anchorY=0, x=((i-1)%columns)*20+3, y=math.floor((i-1)/columns)*20+3,
                width=16, height=16, source={hotbarSlot=i}, showCount=props.showCount,
                showDurability=props.showDurability, pickupAnimation=props.pickupAnimation}
        end
        return {type="overlay", id="bar", width=columns*20+2, height=rows*20+2, children=children}
    end
}
return definitions
