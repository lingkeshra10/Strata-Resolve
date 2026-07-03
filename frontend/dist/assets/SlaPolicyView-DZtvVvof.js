import{Ct as e,E as t,L as n,S as r,_ as i,bt as a,c as o,d as s,f as c,g as l,h as u,k as d,l as f,q as p,t as m,tt as h,u as g}from"./api-bviekfJi.js";import{t as ee}from"./useQuery-l0xoAqfb.js";import{t as _}from"./useMutation-B3kBeJk7.js";import{t as te}from"./useQueryClient-Dx-J5sZ6.js";import{s as v,t as y,v as b}from"./button-BTGtnKIz.js";import{a as x,t as ne}from"./index-SEYfJu9D.js";import{i as S,n as re,t as C}from"./column-_H7xCD7Z.js";import{t as w}from"./select-Dtod6GlV.js";import{t as T}from"./message-V8xzXmcC.js";import{t as E}from"./dialog-DTO5qUgy.js";var D=x.extend({name:`toggleswitch`,style:`
    .p-toggleswitch {
        display: inline-block;
        width: dt('toggleswitch.width');
        height: dt('toggleswitch.height');
    }

    .p-toggleswitch-input {
        cursor: pointer;
        appearance: none;
        position: absolute;
        top: 0;
        inset-inline-start: 0;
        width: 100%;
        height: 100%;
        padding: 0;
        margin: 0;
        opacity: 0;
        z-index: 1;
        outline: 0 none;
        border-radius: dt('toggleswitch.border.radius');
    }

    .p-toggleswitch-slider {
        cursor: pointer;
        width: 100%;
        height: 100%;
        border-width: dt('toggleswitch.border.width');
        border-style: solid;
        border-color: dt('toggleswitch.border.color');
        background: dt('toggleswitch.background');
        transition:
            background dt('toggleswitch.transition.duration'),
            color dt('toggleswitch.transition.duration'),
            border-color dt('toggleswitch.transition.duration'),
            outline-color dt('toggleswitch.transition.duration'),
            box-shadow dt('toggleswitch.transition.duration');
        border-radius: dt('toggleswitch.border.radius');
        outline-color: transparent;
        box-shadow: dt('toggleswitch.shadow');
    }

    .p-toggleswitch-handle {
        position: absolute;
        top: 50%;
        display: flex;
        justify-content: center;
        align-items: center;
        background: dt('toggleswitch.handle.background');
        color: dt('toggleswitch.handle.color');
        width: dt('toggleswitch.handle.size');
        height: dt('toggleswitch.handle.size');
        inset-inline-start: dt('toggleswitch.gap');
        margin-block-start: calc(-1 * calc(dt('toggleswitch.handle.size') / 2));
        border-radius: dt('toggleswitch.handle.border.radius');
        transition:
            background dt('toggleswitch.transition.duration'),
            color dt('toggleswitch.transition.duration'),
            inset-inline-start dt('toggleswitch.slide.duration'),
            box-shadow dt('toggleswitch.slide.duration');
    }

    .p-toggleswitch.p-toggleswitch-checked .p-toggleswitch-slider {
        background: dt('toggleswitch.checked.background');
        border-color: dt('toggleswitch.checked.border.color');
    }

    .p-toggleswitch.p-toggleswitch-checked .p-toggleswitch-handle {
        background: dt('toggleswitch.handle.checked.background');
        color: dt('toggleswitch.handle.checked.color');
        inset-inline-start: calc(dt('toggleswitch.width') - calc(dt('toggleswitch.handle.size') + dt('toggleswitch.gap')));
    }

    .p-toggleswitch:not(.p-disabled):has(.p-toggleswitch-input:hover) .p-toggleswitch-slider {
        background: dt('toggleswitch.hover.background');
        border-color: dt('toggleswitch.hover.border.color');
    }

    .p-toggleswitch:not(.p-disabled):has(.p-toggleswitch-input:hover) .p-toggleswitch-handle {
        background: dt('toggleswitch.handle.hover.background');
        color: dt('toggleswitch.handle.hover.color');
    }

    .p-toggleswitch:not(.p-disabled):has(.p-toggleswitch-input:hover).p-toggleswitch-checked .p-toggleswitch-slider {
        background: dt('toggleswitch.checked.hover.background');
        border-color: dt('toggleswitch.checked.hover.border.color');
    }

    .p-toggleswitch:not(.p-disabled):has(.p-toggleswitch-input:hover).p-toggleswitch-checked .p-toggleswitch-handle {
        background: dt('toggleswitch.handle.checked.hover.background');
        color: dt('toggleswitch.handle.checked.hover.color');
    }

    .p-toggleswitch:not(.p-disabled):has(.p-toggleswitch-input:focus-visible) .p-toggleswitch-slider {
        box-shadow: dt('toggleswitch.focus.ring.shadow');
        outline: dt('toggleswitch.focus.ring.width') dt('toggleswitch.focus.ring.style') dt('toggleswitch.focus.ring.color');
        outline-offset: dt('toggleswitch.focus.ring.offset');
    }

    .p-toggleswitch.p-invalid > .p-toggleswitch-slider {
        border-color: dt('toggleswitch.invalid.border.color');
    }

    .p-toggleswitch.p-disabled {
        opacity: 1;
    }

    .p-toggleswitch.p-disabled .p-toggleswitch-slider {
        background: dt('toggleswitch.disabled.background');
    }

    .p-toggleswitch.p-disabled .p-toggleswitch-handle {
        background: dt('toggleswitch.handle.disabled.background');
    }
`,classes:{root:function(e){var t=e.instance,n=e.props;return[`p-toggleswitch p-component`,{"p-toggleswitch-checked":t.checked,"p-disabled":n.disabled,"p-invalid":t.$invalid}]},input:`p-toggleswitch-input`,slider:`p-toggleswitch-slider`,handle:`p-toggleswitch-handle`},inlineStyles:{root:{position:`relative`}}}),O={name:`ToggleSwitch`,extends:{name:`BaseToggleSwitch`,extends:v,props:{trueValue:{type:null,default:!0},falseValue:{type:null,default:!1},readonly:{type:Boolean,default:!1},tabindex:{type:Number,default:null},inputId:{type:String,default:null},inputClass:{type:[String,Object],default:null},inputStyle:{type:Object,default:null},ariaLabelledby:{type:String,default:null},ariaLabel:{type:String,default:null}},style:D,provide:function(){return{$pcToggleSwitch:this,$parentInstance:this}}},inheritAttrs:!1,emits:[`change`,`focus`,`blur`],methods:{getPTOptions:function(e){return(e===`root`?this.ptmi:this.ptm)(e,{context:{checked:this.checked,disabled:this.disabled}})},onChange:function(e){if(!this.disabled&&!this.readonly){var t=this.checked?this.falseValue:this.trueValue;this.writeValue(t,e),this.$emit(`change`,e)}},onFocus:function(e){this.$emit(`focus`,e)},onBlur:function(e){var t,n;this.$emit(`blur`,e),(t=(n=this.formField).onBlur)==null||t.call(n,e)}},computed:{checked:function(){return this.d_value===this.trueValue},dataP:function(){return b({checked:this.checked,disabled:this.disabled,invalid:this.$invalid})}}},k=[`data-p-checked`,`data-p-disabled`,`data-p`],A=[`id`,`checked`,`tabindex`,`disabled`,`readonly`,`aria-checked`,`aria-labelledby`,`aria-label`,`aria-invalid`],j=[`data-p`],M=[`data-p`];function N(e,n,i,a,o,s){return t(),c(`div`,r({class:e.cx(`root`),style:e.sx(`root`)},s.getPTOptions(`root`),{"data-p-checked":s.checked,"data-p-disabled":e.disabled,"data-p":s.dataP}),[f(`input`,r({id:e.inputId,type:`checkbox`,role:`switch`,class:[e.cx(`input`),e.inputClass],style:e.inputStyle,checked:s.checked,tabindex:e.tabindex,disabled:e.disabled,readonly:e.readonly,"aria-checked":s.checked,"aria-labelledby":e.ariaLabelledby,"aria-label":e.ariaLabel,"aria-invalid":e.invalid||void 0,onFocus:n[0]||=function(){return s.onFocus&&s.onFocus.apply(s,arguments)},onBlur:n[1]||=function(){return s.onBlur&&s.onBlur.apply(s,arguments)},onChange:n[2]||=function(){return s.onChange&&s.onChange.apply(s,arguments)}},s.getPTOptions(`input`)),null,16,A),f(`div`,r({class:e.cx(`slider`)},s.getPTOptions(`slider`),{"data-p":s.dataP}),[f(`div`,r({class:e.cx(`handle`)},s.getPTOptions(`handle`),{"data-p":s.dataP}),[d(e.$slots,`handle`,{checked:s.checked})],16,M)],16,j)],16,k)}O.render=N;var ie={name:`InputSwitch`,extends:O,mounted:function(){console.warn(`Deprecated since v4. Use ToggleSwitch component instead.`)}},ae={class:`p-6 space-y-6`},oe={class:`flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4`},se={key:2,class:`bg-white rounded-lg border border-gray-200`},ce={class:`font-mono text-sm`},le={class:`font-mono text-sm`},P={key:0,class:`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-700`},F={class:`flex items-center gap-1`},I={class:`flex flex-col gap-5 pt-2`},L={class:`flex flex-col gap-1`},R={class:`flex flex-col gap-1`},z={class:`flex flex-col gap-1`},B={key:0,class:`text-red-500`},V={key:1,class:`text-gray-500`},H={class:`flex flex-col gap-1`},U={key:0,class:`text-red-500`},W={key:1,class:`text-gray-500`},ue={class:`flex items-center gap-3`},de={class:`flex items-center justify-end gap-2`},fe={class:`flex items-start gap-3`},pe={key:0,class:`text-sm text-gray-500 mt-1`},me={class:`flex items-center justify-end gap-2`},G=i({__name:`SlaPolicyView`,setup(r){let i=ne(),d=te(),v=p(!1),b=p(!1),x=p(!1),D=p(null),O=p(null),k=p({category:null,priority:null,acknowledgementHours:null,resolutionHours:null,isDefault:!1}),A=p({}),j=p(null),M=o(()=>i.currentPropertyId),{data:N,isLoading:G,error:he}=ee({queryKey:[`sla-policies`,M],queryFn:async()=>M.value?(await m.get(`/properties/${M.value}/sla-policies`)).data:[],enabled:o(()=>!!M.value)}),{mutate:ge,isPending:_e}=_({mutationFn:async e=>(await m.post(`/properties/${M.value}/sla-policies`,e)).data,onSuccess:()=>{d.invalidateQueries({queryKey:[`sla-policies`]}),q()},onError:e=>{j.value=Q(e)}}),{mutate:ve,isPending:ye}=_({mutationFn:async({id:e,data:t})=>(await m.put(`/properties/${M.value}/sla-policies/${e}`,t)).data,onSuccess:()=>{d.invalidateQueries({queryKey:[`sla-policies`]}),q()},onError:e=>{j.value=Q(e)}}),{mutate:be,isPending:K}=_({mutationFn:async e=>{await m.delete(`/properties/${M.value}/sla-policies/${e}`)},onSuccess:()=>{d.invalidateQueries({queryKey:[`sla-policies`]}),b.value=!1,O.value=null},onError:e=>{j.value=Q(e)}}),xe=[{label:`All Categories (Default)`,value:null},{label:`Plumbing`,value:`PLUMBING`},{label:`Electrical`,value:`ELECTRICAL`},{label:`Lift`,value:`LIFT`},{label:`Drainage`,value:`DRAINAGE`},{label:`Security`,value:`SECURITY`},{label:`Cleaning`,value:`CLEANING`},{label:`Structural`,value:`STRUCTURAL`},{label:`Access Control`,value:`ACCESS_CONTROL`},{label:`Common Facilities`,value:`COMMON_FACILITIES`},{label:`Parking`,value:`PARKING`},{label:`Landscaping`,value:`LANDSCAPING`},{label:`Other`,value:`OTHER`}],Se=[{label:`All Priorities (Default)`,value:null},{label:`Low`,value:`LOW`},{label:`Normal`,value:`NORMAL`},{label:`High`,value:`HIGH`},{label:`Urgent`,value:`URGENT`},{label:`Emergency`,value:`EMERGENCY`}];function Ce(){x.value=!1,D.value=null,k.value={category:null,priority:null,acknowledgementHours:null,resolutionHours:null,isDefault:!1},A.value={},j.value=null,v.value=!0}function we(e){x.value=!0,D.value=e.id,k.value={category:e.category,priority:e.priority,acknowledgementHours:e.acknowledgementHours,resolutionHours:e.resolutionHours,isDefault:e.isDefault},A.value={},j.value=null,v.value=!0}function Te(e){O.value=e,b.value=!0}function q(){v.value=!1,A.value={},j.value=null}function J(){let e={};return(k.value.acknowledgementHours===null||k.value.acknowledgementHours<1)&&(e.acknowledgementHours=`Acknowledgement hours must be at least 1`),(k.value.resolutionHours===null||k.value.resolutionHours<1)&&(e.resolutionHours=`Resolution hours must be at least 1`),k.value.acknowledgementHours!==null&&k.value.resolutionHours!==null&&k.value.acknowledgementHours>k.value.resolutionHours&&(e.acknowledgementHours=`Acknowledgement hours cannot exceed resolution hours`),A.value=e,Object.keys(e).length===0}function Ee(){J()&&(x.value&&D.value?ve({id:D.value,data:k.value}):ge(k.value))}function Y(e){return e?e.replace(/_/g,` `):`All (Default)`}function X(e){return e||`All (Default)`}function Z(e){if(e<24)return`${e}h`;let t=Math.floor(e/24),n=e%24;return n>0?`${t}d ${n}h`:`${t}d`}function Q(e){if(e&&typeof e==`object`&&`response`in e){let t=e.response;if(t?.data?.message)return t.data.message}return`An unexpected error occurred. Please try again.`}let $=o(()=>_e.value||ye.value);return(r,i)=>(t(),c(`div`,ae,[f(`div`,oe,[i[10]||=f(`div`,null,[f(`h1`,{class:`text-2xl font-bold text-gray-900`},`SLA Policy Management`),f(`p`,{class:`text-sm text-gray-500 mt-1`},` Configure acknowledgement and resolution target times for different ticket categories and priorities. `)],-1),l(h(y),{label:`New Policy`,icon:`pi pi-plus`,onClick:Ce,disabled:!M.value},null,8,[`disabled`])]),M.value?s(``,!0):(t(),g(h(T),{key:0,severity:`warn`,closable:!1},{default:n(()=>[...i[11]||=[u(` Please select a property to manage SLA policies. `,-1)]]),_:1})),h(he)?(t(),g(h(T),{key:1,severity:`error`,closable:!1},{default:n(()=>[...i[12]||=[u(` Failed to load SLA policies. Please try again. `,-1)]]),_:1})):s(``,!0),M.value?(t(),c(`div`,se,[l(h(re),{value:h(N)??[],loading:h(G),stripedRows:``,responsiveLayout:`scroll`,class:`p-datatable-sm`},{empty:n(()=>[...i[13]||=[f(`div`,{class:`text-center py-8 text-gray-500`},[f(`i`,{class:`pi pi-clock text-4xl text-gray-300 mb-3 block`}),f(`p`,{class:`font-medium`},`No SLA policies configured`),f(`p`,{class:`text-sm mt-1`},`Create your first policy to start monitoring response times.`)],-1)]]),default:n(()=>[l(h(C),{field:`category`,header:`Category`},{body:n(({data:t})=>[f(`span`,{class:a({"text-gray-400 italic":!t.category})},e(Y(t.category)),3)]),_:1}),l(h(C),{field:`priority`,header:`Priority`},{body:n(({data:t})=>[f(`span`,{class:a({"text-gray-400 italic":!t.priority})},e(X(t.priority)),3)]),_:1}),l(h(C),{field:`acknowledgementHours`,header:`Ack. Target`},{body:n(({data:t})=>[f(`span`,ce,e(Z(t.acknowledgementHours)),1)]),_:1}),l(h(C),{field:`resolutionHours`,header:`Resolution Target`},{body:n(({data:t})=>[f(`span`,le,e(Z(t.resolutionHours)),1)]),_:1}),l(h(C),{field:`isDefault`,header:`Default`},{body:n(({data:e})=>[e.isDefault?(t(),c(`span`,P,` Default `)):s(``,!0)]),_:1}),l(h(C),{header:`Actions`,style:{width:`120px`}},{body:n(({data:e})=>[f(`div`,F,[l(h(y),{icon:`pi pi-pencil`,text:``,rounded:``,size:`small`,severity:`info`,onClick:t=>we(e),"aria-label":`Edit policy`},null,8,[`onClick`]),l(h(y),{icon:`pi pi-trash`,text:``,rounded:``,size:`small`,severity:`danger`,onClick:t=>Te(e),"aria-label":`Delete policy`},null,8,[`onClick`])])]),_:1})]),_:1},8,[`value`,`loading`])])):s(``,!0),l(h(E),{visible:v.value,"onUpdate:visible":i[6]||=e=>v.value=e,header:x.value?`Edit SLA Policy`:`Create SLA Policy`,modal:``,closable:!$.value,style:{width:`500px`}},{footer:n(()=>[f(`div`,de,[l(h(y),{label:`Cancel`,severity:`secondary`,outlined:``,onClick:q,disabled:$.value},null,8,[`disabled`]),l(h(y),{label:x.value?`Update`:`Create`,icon:x.value?`pi pi-check`:`pi pi-plus`,loading:$.value,onClick:Ee},null,8,[`label`,`icon`,`loading`])])]),default:n(()=>[f(`div`,I,[j.value?(t(),g(h(T),{key:0,severity:`error`,closable:!0,onClose:i[0]||=e=>j.value=null},{default:n(()=>[u(e(j.value),1)]),_:1})):s(``,!0),f(`div`,L,[i[14]||=f(`label`,{for:`sla-category`,class:`font-medium text-sm text-gray-700`},`Category`,-1),l(h(w),{id:`sla-category`,modelValue:k.value.category,"onUpdate:modelValue":i[1]||=e=>k.value.category=e,options:xe,optionLabel:`label`,optionValue:`value`,placeholder:`All Categories (applies as default)`,class:`w-full`},null,8,[`modelValue`]),i[15]||=f(`small`,{class:`text-gray-500`},`Leave empty to apply to all categories without a specific policy.`,-1)]),f(`div`,R,[i[16]||=f(`label`,{for:`sla-priority`,class:`font-medium text-sm text-gray-700`},`Priority`,-1),l(h(w),{id:`sla-priority`,modelValue:k.value.priority,"onUpdate:modelValue":i[2]||=e=>k.value.priority=e,options:Se,optionLabel:`label`,optionValue:`value`,placeholder:`All Priorities (applies as default)`,class:`w-full`},null,8,[`modelValue`]),i[17]||=f(`small`,{class:`text-gray-500`},`Leave empty to apply to all priorities without a specific policy.`,-1)]),f(`div`,z,[i[18]||=f(`label`,{for:`sla-ack-hours`,class:`font-medium text-sm text-gray-700`},[u(` Acknowledgement Target (hours) `),f(`span`,{class:`text-red-500`},`*`)],-1),l(h(S),{id:`sla-ack-hours`,modelValue:k.value.acknowledgementHours,"onUpdate:modelValue":i[3]||=e=>k.value.acknowledgementHours=e,min:1,max:720,placeholder:`e.g., 4`,invalid:!!A.value.acknowledgementHours,class:`w-full`},null,8,[`modelValue`,`invalid`]),A.value.acknowledgementHours?(t(),c(`small`,B,e(A.value.acknowledgementHours),1)):(t(),c(`small`,V,`Hours within which the ticket must be acknowledged.`))]),f(`div`,H,[i[19]||=f(`label`,{for:`sla-res-hours`,class:`font-medium text-sm text-gray-700`},[u(` Resolution Target (hours) `),f(`span`,{class:`text-red-500`},`*`)],-1),l(h(S),{id:`sla-res-hours`,modelValue:k.value.resolutionHours,"onUpdate:modelValue":i[4]||=e=>k.value.resolutionHours=e,min:1,max:2160,placeholder:`e.g., 48`,invalid:!!A.value.resolutionHours,class:`w-full`},null,8,[`modelValue`,`invalid`]),A.value.resolutionHours?(t(),c(`small`,U,e(A.value.resolutionHours),1)):(t(),c(`small`,W,`Hours within which the ticket must be resolved.`))]),f(`div`,ue,[l(h(ie),{modelValue:k.value.isDefault,"onUpdate:modelValue":i[5]||=e=>k.value.isDefault=e,inputId:`sla-default`},null,8,[`modelValue`]),i[20]||=f(`label`,{for:`sla-default`,class:`text-sm text-gray-700`},` Mark as default policy (applies when no specific policy matches) `,-1)])])]),_:1},8,[`visible`,`header`,`closable`]),l(h(E),{visible:b.value,"onUpdate:visible":i[9]||=e=>b.value=e,header:`Delete SLA Policy`,modal:``,style:{width:`400px`}},{footer:n(()=>[f(`div`,me,[l(h(y),{label:`Cancel`,severity:`secondary`,outlined:``,onClick:i[7]||=e=>b.value=!1,disabled:h(K)},null,8,[`disabled`]),l(h(y),{label:`Delete`,severity:`danger`,icon:`pi pi-trash`,loading:h(K),onClick:i[8]||=e=>O.value&&h(be)(O.value.id)},null,8,[`loading`])])]),default:n(()=>[f(`div`,fe,[i[22]||=f(`i`,{class:`pi pi-exclamation-triangle text-2xl text-amber-500 mt-0.5`},null,-1),f(`div`,null,[i[21]||=f(`p`,{class:`text-gray-700`},`Are you sure you want to delete this SLA policy?`,-1),O.value?(t(),c(`p`,pe,e(Y(O.value.category))+` / `+e(X(O.value.priority))+` — `+e(Z(O.value.acknowledgementHours))+` ack / `+e(Z(O.value.resolutionHours))+` resolution `,1)):s(``,!0)])])]),_:1},8,[`visible`])]))}});export{G as default};