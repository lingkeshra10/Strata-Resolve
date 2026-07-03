import{Ct as e,E as t,L as n,S as r,_ as i,c as a,d as o,f as s,g as c,h as l,l as u,t as d,tt as f,u as p}from"./api-bviekfJi.js";import{t as m}from"./useQuery-l0xoAqfb.js";import{t as h}from"./useMutation-B3kBeJk7.js";import{t as g}from"./useQueryClient-Dx-J5sZ6.js";import{p as _,t as v}from"./button-BTGtnKIz.js";import{a as y,t as b}from"./index-SEYfJu9D.js";import{n as x,t as S}from"./column-_H7xCD7Z.js";import{t as C}from"./tag-D_Qp4QKe.js";import{t as w}from"./message-V8xzXmcC.js";async function T(e){return(await d.get(`/properties/${e}/work-orders/my-work-orders`)).data}async function E(e,t){return(await d.put(`/properties/${e}/work-orders/${t}/accept`)).data}async function D(e,t,n){return(await d.put(`/properties/${e}/work-orders/${t}/status`,{status:n})).data}var O=y.extend({name:`progressspinner`,style:`
    .p-progressspinner {
        position: relative;
        margin: 0 auto;
        width: 100px;
        height: 100px;
        display: inline-block;
    }

    .p-progressspinner::before {
        content: '';
        display: block;
        padding-top: 100%;
    }

    .p-progressspinner-spin {
        height: 100%;
        transform-origin: center center;
        width: 100%;
        position: absolute;
        top: 0;
        bottom: 0;
        left: 0;
        right: 0;
        margin: auto;
        animation: p-progressspinner-rotate 2s linear infinite;
    }

    .p-progressspinner-circle {
        stroke-dasharray: 89, 200;
        stroke-dashoffset: 0;
        stroke: dt('progressspinner.colorOne');
        animation:
            p-progressspinner-dash 1.5s ease-in-out infinite,
            p-progressspinner-color 6s ease-in-out infinite;
        stroke-linecap: round;
    }

    @keyframes p-progressspinner-rotate {
        100% {
            transform: rotate(360deg);
        }
    }
    @keyframes p-progressspinner-dash {
        0% {
            stroke-dasharray: 1, 200;
            stroke-dashoffset: 0;
        }
        50% {
            stroke-dasharray: 89, 200;
            stroke-dashoffset: -35px;
        }
        100% {
            stroke-dasharray: 89, 200;
            stroke-dashoffset: -124px;
        }
    }
    @keyframes p-progressspinner-color {
        100%,
        0% {
            stroke: dt('progressspinner.color.one');
        }
        40% {
            stroke: dt('progressspinner.color.two');
        }
        66% {
            stroke: dt('progressspinner.color.three');
        }
        80%,
        90% {
            stroke: dt('progressspinner.color.four');
        }
    }
`,classes:{root:`p-progressspinner`,spin:`p-progressspinner-spin`,circle:`p-progressspinner-circle`}}),k={name:`ProgressSpinner`,extends:{name:`BaseProgressSpinner`,extends:_,props:{strokeWidth:{type:String,default:`2`},fill:{type:String,default:`none`},animationDuration:{type:String,default:`2s`}},style:O,provide:function(){return{$pcProgressSpinner:this,$parentInstance:this}}},inheritAttrs:!1,computed:{svgStyle:function(){return{"animation-duration":this.animationDuration}}}},A=[`fill`,`stroke-width`];function j(e,n,i,a,o,c){return t(),s(`div`,r({class:e.cx(`root`),role:`progressbar`},e.ptmi(`root`)),[(t(),s(`svg`,r({class:e.cx(`spin`),viewBox:`25 25 50 50`,style:c.svgStyle},e.ptm(`spin`)),[u(`circle`,r({class:e.cx(`circle`),cx:`50`,cy:`50`,r:`20`,fill:e.fill,"stroke-width":e.strokeWidth,strokeMiterlimit:`10`},e.ptm(`circle`)),null,16,A)],16))],16)}k.render=j;var M={class:`p-6`},N={class:`flex items-center justify-between mb-6`},P={key:0,class:`flex items-center justify-center py-12`},F={key:2,class:`text-center py-12 text-gray-500`},I={class:`font-mono text-sm`},L={class:`font-medium`},R={class:`flex gap-1`},z={key:3,class:`text-sm text-gray-400 italic`},B=i({__name:`WorkOrderListView`,setup(r){let i=b(),d=g(),_=a(()=>i.currentPropertyId??``),{data:y,isLoading:O,isError:A,refetch:j}=m({queryKey:[`my-work-orders`,_],queryFn:()=>T(_.value),enabled:a(()=>!!_.value)}),{mutate:B,isPending:V}=h({mutationFn:e=>E(_.value,e),onSuccess:()=>{d.invalidateQueries({queryKey:[`my-work-orders`]})}}),{mutate:H,isPending:U}=h({mutationFn:({workOrderId:e,status:t})=>D(_.value,e,t),onSuccess:()=>{d.invalidateQueries({queryKey:[`my-work-orders`]})}});function W(e){B(e.id)}function G(e){H({workOrderId:e.id,status:`IN_PROGRESS`})}function K(e){H({workOrderId:e.id,status:`COMPLETED`})}function q(e){return{CREATED:`info`,ACCEPTED:`warn`,IN_PROGRESS:`warn`,COMPLETED:`success`,CANCELLED:`danger`}[e]}function J(e){return{LOW:`secondary`,NORMAL:`info`,HIGH:`warn`,URGENT:`danger`,EMERGENCY:`danger`}[e]}function Y(e){return e.replace(/_/g,` `).toLowerCase().replace(/\b\w/g,e=>e.toUpperCase())}function X(e){return new Date(e).toLocaleDateString()}return(r,i)=>(t(),s(`div`,M,[u(`div`,N,[i[1]||=u(`h1`,{class:`text-2xl font-bold`},`My Work Orders`,-1),c(f(v),{icon:`pi pi-refresh`,severity:`secondary`,outlined:``,rounded:``,"aria-label":`Refresh`,onClick:i[0]||=()=>f(j)()})]),f(O)?(t(),s(`div`,P,[c(f(k))])):f(A)?(t(),p(f(w),{key:1,severity:`error`,closable:!1},{default:n(()=>[...i[2]||=[l(` Failed to load work orders. Please try again. `,-1)]]),_:1})):!f(y)||f(y).length===0?(t(),s(`div`,F,[...i[3]||=[u(`i`,{class:`pi pi-inbox text-4xl mb-4 block`},null,-1),u(`p`,null,`No work orders assigned to you.`,-1)]])):(t(),p(f(x),{key:3,value:f(y),"striped-rows":``,paginator:``,rows:10,"rows-per-page-options":[5,10,20],"data-key":`id`,"responsive-layout":`scroll`},{default:n(()=>[c(f(S),{field:`ticket.referenceNumber`,header:`Reference`,sortable:``},{body:n(({data:t})=>[u(`span`,I,e(t.ticket?.referenceNumber??`—`),1)]),_:1}),c(f(S),{field:`ticket.title`,header:`Issue`,sortable:``},{body:n(({data:t})=>[u(`span`,L,e(t.ticket?.title??`—`),1)]),_:1}),c(f(S),{field:`ticket.category`,header:`Category`,sortable:``},{body:n(({data:t})=>[l(e(t.ticket?Y(t.ticket.category):`—`),1)]),_:1}),c(f(S),{field:`ticket.priority`,header:`Priority`,sortable:``},{body:n(({data:e})=>[e.ticket?(t(),p(f(C),{key:0,value:e.ticket.priority,severity:J(e.ticket.priority)},null,8,[`value`,`severity`])):o(``,!0)]),_:1}),c(f(S),{field:`status`,header:`Status`,sortable:``},{body:n(({data:e})=>[c(f(C),{value:e.status,severity:q(e.status)},null,8,[`value`,`severity`])]),_:1}),c(f(S),{field:`createdAt`,header:`Assigned`,sortable:``},{body:n(({data:t})=>[l(e(X(t.createdAt)),1)]),_:1}),c(f(S),{header:`Actions`,style:{width:`12rem`}},{body:n(({data:n})=>[u(`div`,R,[n.status===`CREATED`?(t(),p(f(v),{key:0,label:`Accept`,size:`small`,severity:`info`,loading:f(V),onClick:e=>W(n)},null,8,[`loading`,`onClick`])):o(``,!0),n.status===`ACCEPTED`?(t(),p(f(v),{key:1,label:`Start`,size:`small`,severity:`warn`,loading:f(U),onClick:e=>G(n)},null,8,[`loading`,`onClick`])):o(``,!0),n.status===`IN_PROGRESS`?(t(),p(f(v),{key:2,label:`Complete`,size:`small`,severity:`success`,loading:f(U),onClick:e=>K(n)},null,8,[`loading`,`onClick`])):o(``,!0),n.status===`COMPLETED`||n.status===`CANCELLED`?(t(),s(`span`,z,e(n.status===`COMPLETED`?`Done`:`Cancelled`),1)):o(``,!0)])]),_:1})]),_:1},8,[`value`]))]))}});export{B as default};