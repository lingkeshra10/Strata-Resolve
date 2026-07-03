import{Ct as e,E as t,H as n,L as r,O as i,S as a,_ as o,a as s,bt as c,c as l,d as u,f as d,g as f,h as p,l as m,q as h,tt as g,u as _}from"./api-bviekfJi.js";import{t as ee}from"./useMutation-B3kBeJk7.js";import{a as v,o as y,t as b,v as x}from"./button-BTGtnKIz.js";import{Gt as te,a as S,r as ne}from"./index-SEYfJu9D.js";import{t as C}from"./select-Dtod6GlV.js";import{t as w}from"./message-V8xzXmcC.js";import{a as T,i as E,n as D,o as re,r as O,t as ie}from"./vee-validate-zod-TmTXXUKC.js";import{i as ae}from"./ticket-DLOzxwkm.js";var k=S.extend({name:`textarea`,style:`
    .p-textarea {
        font-family: inherit;
        font-feature-settings: inherit;
        font-size: 1rem;
        color: dt('textarea.color');
        background: dt('textarea.background');
        padding-block: dt('textarea.padding.y');
        padding-inline: dt('textarea.padding.x');
        border: 1px solid dt('textarea.border.color');
        transition:
            background dt('textarea.transition.duration'),
            color dt('textarea.transition.duration'),
            border-color dt('textarea.transition.duration'),
            outline-color dt('textarea.transition.duration'),
            box-shadow dt('textarea.transition.duration');
        appearance: none;
        border-radius: dt('textarea.border.radius');
        outline-color: transparent;
        box-shadow: dt('textarea.shadow');
    }

    .p-textarea:enabled:hover {
        border-color: dt('textarea.hover.border.color');
    }

    .p-textarea:enabled:focus {
        border-color: dt('textarea.focus.border.color');
        box-shadow: dt('textarea.focus.ring.shadow');
        outline: dt('textarea.focus.ring.width') dt('textarea.focus.ring.style') dt('textarea.focus.ring.color');
        outline-offset: dt('textarea.focus.ring.offset');
    }

    .p-textarea.p-invalid {
        border-color: dt('textarea.invalid.border.color');
    }

    .p-textarea.p-variant-filled {
        background: dt('textarea.filled.background');
    }

    .p-textarea.p-variant-filled:enabled:hover {
        background: dt('textarea.filled.hover.background');
    }

    .p-textarea.p-variant-filled:enabled:focus {
        background: dt('textarea.filled.focus.background');
    }

    .p-textarea:disabled {
        opacity: 1;
        background: dt('textarea.disabled.background');
        color: dt('textarea.disabled.color');
    }

    .p-textarea::placeholder {
        color: dt('textarea.placeholder.color');
    }

    .p-textarea.p-invalid::placeholder {
        color: dt('textarea.invalid.placeholder.color');
    }

    .p-textarea-fluid {
        width: 100%;
    }

    .p-textarea-resizable {
        overflow: hidden;
        resize: none;
    }

    .p-textarea-sm {
        font-size: dt('textarea.sm.font.size');
        padding-block: dt('textarea.sm.padding.y');
        padding-inline: dt('textarea.sm.padding.x');
    }

    .p-textarea-lg {
        font-size: dt('textarea.lg.font.size');
        padding-block: dt('textarea.lg.padding.y');
        padding-inline: dt('textarea.lg.padding.x');
    }
`,classes:{root:function(e){var t=e.instance,n=e.props;return[`p-textarea p-component`,{"p-filled":t.$filled,"p-textarea-resizable ":n.autoResize,"p-textarea-sm p-inputfield-sm":n.size===`small`,"p-textarea-lg p-inputfield-lg":n.size===`large`,"p-invalid":t.$invalid,"p-variant-filled":t.$variant===`filled`,"p-textarea-fluid":t.$fluid}]}}}),A={name:`BaseTextarea`,extends:y,props:{autoResize:Boolean},style:k,provide:function(){return{$pcTextarea:this,$parentInstance:this}}};function j(e){"@babel/helpers - typeof";return j=typeof Symbol==`function`&&typeof Symbol.iterator==`symbol`?function(e){return typeof e}:function(e){return e&&typeof Symbol==`function`&&e.constructor===Symbol&&e!==Symbol.prototype?`symbol`:typeof e},j(e)}function M(e,t,n){return(t=N(t))in e?Object.defineProperty(e,t,{value:n,enumerable:!0,configurable:!0,writable:!0}):e[t]=n,e}function N(e){var t=P(e,`string`);return j(t)==`symbol`?t:t+``}function P(e,t){if(j(e)!=`object`||!e)return e;var n=e[Symbol.toPrimitive];if(n!==void 0){var r=n.call(e,t);if(j(r)!=`object`)return r;throw TypeError(`@@toPrimitive must return a primitive value.`)}return(t===`string`?String:Number)(e)}var F={name:`Textarea`,extends:A,inheritAttrs:!1,observer:null,mounted:function(){var e=this;this.autoResize&&(this.observer=new ResizeObserver(function(){requestAnimationFrame(function(){e.resize()})}),this.observer.observe(this.$el))},updated:function(){this.autoResize&&this.resize()},beforeUnmount:function(){this.observer&&this.observer.disconnect()},methods:{resize:function(){if(this.$el.offsetParent){var e=this.$el.style.height,t=parseInt(e)||0,n=this.$el.scrollHeight;t&&n<t?(this.$el.style.height=`auto`,this.$el.style.height=`${this.$el.scrollHeight}px`):(!t||n>t)&&(this.$el.style.height=`${n}px`)}},onInput:function(e){this.autoResize&&this.resize(),this.writeValue(e.target.value,e)}},computed:{attrs:function(){return a(this.ptmi(`root`,{context:{filled:this.$filled,disabled:this.disabled}}),this.formField)},dataP:function(){return x(M({invalid:this.$invalid,fluid:this.$fluid,filled:this.$variant===`filled`},this.size,this.size))}}},I=[`value`,`name`,`disabled`,`aria-invalid`,`data-p`];function L(e,n,r,i,o,s){return t(),d(`textarea`,a({class:e.cx(`root`),value:e.d_value,name:e.name,disabled:e.disabled,"aria-invalid":e.invalid||void 0,"data-p":s.dataP,onInput:n[0]||=function(){return s.onInput&&s.onInput.apply(s,arguments)}},s.attrs),null,16,I)}F.render=L;var R=[`PLUMBING`,`ELECTRICAL`,`LIFT`,`DRAINAGE`,`SECURITY`,`CLEANING`,`STRUCTURAL`,`ACCESS_CONTROL`,`COMMON_FACILITIES`,`PARKING`,`LANDSCAPING`,`OTHER`],z=[`LOW`,`NORMAL`,`HIGH`,`URGENT`,`EMERGENCY`],oe=O({title:E().min(1,`Title is required`).max(200,`Title must not exceed 200 characters`),description:E().min(1,`Description is required`).max(5e3,`Description must not exceed 5000 characters`),category:D(R,{required_error:`Category is required`}),location:E().min(1,`Location is required`),priority:D(z).optional()}),se={class:`p-6 max-w-3xl mx-auto`},ce={class:`flex flex-col gap-1`},B={key:0,class:`text-red-500`},V={class:`flex flex-col gap-1`},H={key:0,class:`text-red-500`},U={class:`text-surface-500`},W={class:`grid grid-cols-1 md:grid-cols-2 gap-4`},G={class:`flex flex-col gap-1`},le={key:0,class:`text-red-500`},ue={class:`flex flex-col gap-1`},de={class:`flex flex-col gap-1`},fe={key:0,class:`text-red-500`},pe={class:`flex flex-col gap-2`},me={key:0,class:`flex flex-col gap-2 mt-2`},he={class:`flex items-center gap-3 min-w-0`},ge={class:`min-w-0`},K={class:`text-sm font-medium text-surface-800 truncate`},_e={class:`text-xs text-surface-500`},ve={class:`flex items-center gap-3 pt-4 border-t border-surface-200`},ye=10*1024*1024,q=5,J=o({__name:`TicketCreateView`,setup(a){let o=ne(),{handleSubmit:y,errors:x,resetForm:S}=re({validationSchema:ie(oe),initialValues:{title:``,description:``,category:void 0,location:``,priority:void 0}}),{value:E}=T(`title`),{value:D}=T(`description`),{value:O}=T(`category`),{value:k}=T(`location`),{value:A}=T(`priority`),j=h([]),M=h([]),N=h(!1),P=[`image/jpeg`,`image/png`,`application/pdf`],I=[`.jpg`,`.jpeg`,`.png`,`.pdf`],L=R.map(e=>({label:e.replace(/_/g,` `),value:e})),J=z.map(e=>({label:e,value:e})),{mutate:be,isPending:Y,error:X}=ee({mutationFn:e=>ae(e),onSuccess:e=>{S(),j.value=[],o.push({name:`ticket-detail`,params:{id:e.id}})}}),Z=l(()=>X.value?X.value?.response?.data?.message||`Failed to submit ticket. Please try again.`:null);function xe(e){if(!P.includes(e.type)){let t=e.name.split(`.`).pop()?.toLowerCase();if(!t||!I.includes(`.${t}`))return`"${e.name}" is not an allowed file type. Only JPEG, PNG, and PDF files are accepted.`}return e.size>ye?`"${e.name}" exceeds the 10MB size limit.`:null}function Q(e){M.value=[];let t=Array.from(e);if(j.value.length+t.length>q){M.value.push(`Maximum ${q} files allowed. You can add ${q-j.value.length} more.`);return}for(let e of t){let t=xe(e);t?M.value.push(t):j.value.some(t=>t.name===e.name&&t.size===e.size)||j.value.push(e)}}function Se(e){j.value.splice(e,1),M.value=[]}function Ce(e){e.preventDefault(),N.value=!0}function we(){N.value=!1}function Te(e){e.preventDefault(),N.value=!1,e.dataTransfer?.files&&Q(e.dataTransfer.files)}function Ee(e){let t=e.target;t.files&&(Q(t.files),t.value=``)}function De(e){return e<1024?`${e} B`:e<1024*1024?`${(e/1024).toFixed(1)} KB`:`${(e/(1024*1024)).toFixed(1)} MB`}let $=y(e=>{be({...e,attachments:j.value.length>0?j.value:void 0})});return(a,l)=>(t(),d(`div`,se,[l[16]||=m(`div`,{class:`mb-6`},[m(`h1`,{class:`text-2xl font-bold text-surface-900`},`Submit a Ticket`),m(`p`,{class:`text-surface-600 mt-1`},` Report a maintenance issue for your property. Provide as much detail as possible. `)],-1),Z.value?(t(),_(g(w),{key:0,severity:`error`,class:`mb-4`,closable:!1},{default:r(()=>[p(e(Z.value),1)]),_:1})):u(``,!0),m(`form`,{onSubmit:l[7]||=te((...e)=>g($)&&g($)(...e),[`prevent`]),class:`flex flex-col gap-6`},[m(`div`,ce,[l[8]||=m(`label`,{for:`title`,class:`font-medium text-surface-700`},[p(` Title `),m(`span`,{class:`text-red-500`},`*`)],-1),f(g(v),{id:`title`,modelValue:g(E),"onUpdate:modelValue":l[0]||=e=>n(E)?E.value=e:null,placeholder:`Brief summary of the issue`,invalid:!!g(x).title,class:`w-full`},null,8,[`modelValue`,`invalid`]),g(x).title?(t(),d(`small`,B,e(g(x).title),1)):u(``,!0)]),m(`div`,V,[l[9]||=m(`label`,{for:`description`,class:`font-medium text-surface-700`},[p(` Description `),m(`span`,{class:`text-red-500`},`*`)],-1),f(g(F),{id:`description`,modelValue:g(D),"onUpdate:modelValue":l[1]||=e=>n(D)?D.value=e:null,placeholder:`Describe the issue in detail — what happened, when, and how it affects you`,rows:`5`,invalid:!!g(x).description,class:`w-full`},null,8,[`modelValue`,`invalid`]),g(x).description?(t(),d(`small`,H,e(g(x).description),1)):u(``,!0),m(`small`,U,e((g(D)||``).length)+` / 5000 characters`,1)]),m(`div`,W,[m(`div`,G,[l[10]||=m(`label`,{for:`category`,class:`font-medium text-surface-700`},[p(` Category `),m(`span`,{class:`text-red-500`},`*`)],-1),f(g(C),{id:`category`,modelValue:g(O),"onUpdate:modelValue":l[2]||=e=>n(O)?O.value=e:null,options:g(L),optionLabel:`label`,optionValue:`value`,placeholder:`Select category`,invalid:!!g(x).category,class:`w-full`},null,8,[`modelValue`,`options`,`invalid`]),g(x).category?(t(),d(`small`,le,e(g(x).category),1)):u(``,!0)]),m(`div`,ue,[l[11]||=m(`label`,{for:`priority`,class:`font-medium text-surface-700`},[p(` Priority `),m(`span`,{class:`text-surface-400`},`(optional)`)],-1),f(g(C),{id:`priority`,modelValue:g(A),"onUpdate:modelValue":l[3]||=e=>n(A)?A.value=e:null,options:g(J),optionLabel:`label`,optionValue:`value`,placeholder:`Select priority`,showClear:``,class:`w-full`},null,8,[`modelValue`,`options`]),l[12]||=m(`small`,{class:`text-surface-500`},`Suggested priority — the manager may adjust this.`,-1)])]),m(`div`,de,[l[13]||=m(`label`,{for:`location`,class:`font-medium text-surface-700`},[p(` Location `),m(`span`,{class:`text-red-500`},`*`)],-1),f(g(v),{id:`location`,modelValue:g(k),"onUpdate:modelValue":l[4]||=e=>n(k)?k.value=e:null,placeholder:`e.g., Unit 12-03, Lobby, Car Park Level B2`,invalid:!!g(x).location,class:`w-full`},null,8,[`modelValue`,`invalid`]),g(x).location?(t(),d(`small`,fe,e(g(x).location),1)):u(``,!0)]),m(`div`,pe,[l[15]||=m(`label`,{class:`font-medium text-surface-700`},`Attachments`,-1),m(`p`,{class:`text-sm text-surface-500`},` Upload photos or documents as evidence. Accepted: JPEG, PNG, PDF (max 10MB each, up to `+e(q)+` files). `),m(`div`,{class:c([`border-2 border-dashed rounded-lg p-6 text-center cursor-pointer transition-colors`,N.value?`border-primary bg-primary/5`:`border-surface-300 hover:border-primary hover:bg-surface-50`]),onDragover:Ce,onDragleave:we,onDrop:Te,onClick:l[5]||=e=>a.$refs.fileInput?.click()},[...l[14]||=[m(`i`,{class:`pi pi-cloud-upload text-3xl text-surface-400 mb-2`},null,-1),m(`p`,{class:`text-surface-600`},[p(` Drag & drop files here, or `),m(`span`,{class:`text-primary font-medium`},`browse`)],-1),m(`p`,{class:`text-xs text-surface-400 mt-1`},`JPEG, PNG, PDF — Max 10MB per file`,-1)]],34),m(`input`,{ref:`fileInput`,type:`file`,multiple:``,accept:`.jpg,.jpeg,.png,.pdf`,class:`hidden`,onChange:Ee},null,544),(t(!0),d(s,null,i(M.value,(n,i)=>(t(),_(g(w),{key:i,severity:`warn`,class:`mt-1`,closable:!0,onClose:e=>M.value.splice(i,1)},{default:r(()=>[p(e(n),1)]),_:2},1032,[`onClose`]))),128)),j.value.length>0?(t(),d(`ul`,me,[(t(!0),d(s,null,i(j.value,(n,r)=>(t(),d(`li`,{key:`${n.name}-${n.size}`,class:`flex items-center justify-between p-3 bg-surface-50 rounded-lg border border-surface-200`},[m(`div`,he,[m(`i`,{class:c([`pi text-lg`,n.type===`application/pdf`?`pi-file-pdf text-red-500`:`pi-image text-blue-500`])},null,2),m(`div`,ge,[m(`p`,K,e(n.name),1),m(`p`,_e,e(De(n.size)),1)])]),f(g(b),{icon:`pi pi-times`,severity:`danger`,text:``,rounded:``,size:`small`,onClick:e=>Se(r),"aria-label":`Remove file`},null,8,[`onClick`])]))),128))])):u(``,!0)]),m(`div`,ve,[f(g(b),{type:`submit`,label:`Submit Ticket`,icon:`pi pi-send`,loading:g(Y),disabled:g(Y)},null,8,[`loading`,`disabled`]),f(g(b),{type:`button`,label:`Cancel`,severity:`secondary`,outlined:``,onClick:l[6]||=e=>g(o).push({name:`tickets`}),disabled:g(Y)},null,8,[`disabled`])])],32)]))}});export{J as default};