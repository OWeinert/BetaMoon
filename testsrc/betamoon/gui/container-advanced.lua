local ui = betamoon.client.ui
fault = false
openHelp = false
ui.components:add {
    key = 'fixture:container_counter', size = {width=68,height=24},
    state = {clicks={type='integer',default=0}},
    events = {chosen={count={type='integer',default=0}}},
    paint = function(view)
        preparedCalls = (preparedCalls or 0) + 1
        return {
            {op='rect',width=68,height=24,color='#304860'},
            {op='text',x=4,y=8,text='Count '..view.state:get('clicks'),color='#FFFFFF'},
            {op='hitRect',name='body',width=68,height=24}
        }
    end,
    input = function(ctx,event)
        if event.type == 'pointer_down' and event.button == 0 then
            if openHelp then
                help = ctx.screen:popover('fixture:container_help','first')
            else
                local count = ctx.state:get('clicks') + 1
                ctx.state:set('clicks',count)
                ctx:emit('chosen',{count=count})
                ticket = ctx.container:request('start')
            end
            return {consume=true,focus=true}
        end
        return false
    end, focusable=true
}
ui.components:add {
    key='fixture:container_raw',size={width=52,height=24},
    rawDraw=function(frame)
        rawCalls=(rawCalls or 0)+1
        frame.gl:target('scratch');frame.gl:clear(0xFF102030);frame.gl:target(nil)
        frame.gl:enable('texture',false)
        frame.gl:program('tint');frame.gl:uniform('color',0.2,0.6,0.8,1)
        frame.gl:buffer('quad');frame.gl:program(nil)
        if fault then error('expected container raw fault') end
    end
}
function skin(color)
    ui.themes:add {key='fixture:container_skin',base='minimal',templates={button={parts={'content','focus'},
        paint=function(view)
            templateCalls=(templateCalls or 0)+1
            return {{op='rect',width={source='width'},height={source='height'},color=color},
                {op='text',x=4,y=6,text=view.props.text,color='#FFFFFF'}}
        end}}}
end
skin('#305070')
ui.screens:add {key='fixture:container_help',root={type='button',id='nested',width=120,height=20,text='More help',
    on={activate=function(ctx) nestedHelp=ctx.screen:popover('fixture:container_help2','nested') end}}}
ui.screens:add {key='fixture:container_help2',root={type='button',width=100,height=20,text='Close help',
    on={activate=function(ctx)ctx.screen:close()end}}}
local tile=betamoon.tileEntities:add {name='advanced',inventory={slots={input={index=0}}}}
local container=betamoon.containers:add {name='advanced',tileEntity=tile,
    slots={{slot='input',x=232,y=118}},playerInventory={x=8,y=150},
    controls={start={type='action',onActivate=function()starts=(starts or 0)+1 end}}}
local records={}
for i=1,4096 do records[i]={key='r'..i,label='Row '..i} end
gui=betamoon.containerGuis:add {name='advanced',container=container,layout={width=256,height=232},
    theme='fixture:container_skin',state={visits={type='integer',default=0}},
    resources={buffers={quad={mode='quads',vertices={0,0,52,0,52,24,0,24}}},
        programs={tint={vertex='void main(){gl_Position=gl_ModelViewProjectionMatrix*gl_Vertex;}',
            fragment='uniform vec4 color;void main(){gl_FragColor=color;}',uniforms={color=4}}},
        targets={scratch={width=16,height=16}}},
    elements={{type='item',slot='input',x=232,y=90,showCount=true}},
    root={type='overlay',width=256,height=232,anchorX=0,anchorY=0,children={
        {type='row',x=8,y=8,width=220,height=24,gap=8,children={
            {component='fixture:container_counter',id='first',key='left',on={chosen=function(ctx,e)
                left=e.payload.count;ctx.state:set('visits',ctx.state:get('visits')+1) end}},
            {component='fixture:container_counter',id='second',key='right',on={chosen=function(ctx,e)
                right=e.payload.count;ctx.state:set('visits',ctx.state:get('visits')+1) end}},
            {component='fixture:container_raw',id='raw'} }},
        {type='list',id='rows',x=8,y=40,width=104,height=54,rowHeight=18,overscan=0,records=records,
            row=function(row) rowCalls=(rowCalls or 0)+1;return{type='textbox',height=18,text=row.label}end},
        {type='textarea',id='notes',x=120,y=40,width=96,height=54,text='Notes'},
        {type='dropdown',id='choice',x=8,y=100,width=96,height=20,options={{key='a',label='Alpha'},{key='b',label='Beta'}}},
        {type='stepper',id='step',x=120,y=100,width=96,height=20,minimum=0,maximum=10,value=2},
        {type='numberbox',id='number',x=8,y=124,width=104,height=20,minimum=0,maximum=10,value=2}
    }}}
return gui
