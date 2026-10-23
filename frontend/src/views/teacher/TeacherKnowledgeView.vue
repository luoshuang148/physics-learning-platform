<template>
  <AppShell subtitle="教学资料 · 讲义索引与引用管理">
    <main class="knowledge-page">
      <header><div><p class="eyebrow">AI KNOWLEDGE BASE</p><h1>班级知识库</h1><p>上传经过核对的讲义，供学生错题辅导检索引用。</p></div>
        <select v-model.number="classId" aria-label="选择班级" @change="loadDocuments"><option v-for="item in classroom.classes" :key="item.id" :value="item.id">{{ item.name }}</option></select>
      </header>
      <section class="upload-card"><h2>添加讲义</h2><form @submit.prevent="upload"><label>资料名称<input v-model="title" required maxlength="120" placeholder="例如：牛顿第二定律讲义" /></label><label>文件<input ref="fileInput" type="file" accept=".pdf,.txt,.md,application/pdf,text/plain,text/markdown" required /></label><button :disabled="busy || !classId">{{ busy ? '处理中…' : '上传并建立索引' }}</button><small>支持 PDF、TXT、Markdown；只上传本班教学资料。</small></form></section>
      <section class="docs-card"><div class="docs-heading"><h2>资料列表</h2><button @click="loadDocuments">刷新状态</button></div><p v-if="!classId">请先创建或选择班级。</p><p v-else-if="loading">正在读取资料…</p><p v-else-if="!documents.length">还没有教学资料。上传后索引状态会显示在这里。</p>
        <ul v-else><li v-for="doc in documents" :key="doc.documentId"><div><strong>{{ doc.title }}</strong><span>文档 #{{ doc.documentId }} · 修订版 {{ doc.revisionId }}</span></div><span class="status" :class="doc.status.toLowerCase()">{{ statusText(doc.status) }}</span><button v-if="doc.status !== 'WITHDRAWN'" class="withdraw" :disabled="busy" @click="withdraw(doc)">撤回</button></li></ul>
      </section><p v-if="error" class="error" role="alert">{{ error }}</p><p v-if="notice" class="notice" role="status">{{ notice }}</p>
    </main>
  </AppShell>
</template>

<script setup lang="ts">
import {onMounted,ref} from 'vue';
import AppShell from '@/layouts/AppShell.vue';
import {useClassroomStore} from '@/stores/classroom';
import {listKnowledgeDocumentsApi,uploadKnowledgeDocumentApi,withdrawKnowledgeDocumentApi} from '@/api/knowledge';
import type {KnowledgeDocumentStatus} from '@/types/knowledge';

const classroom=useClassroomStore();const classId=ref<number|null>(null);const documents=ref<KnowledgeDocumentStatus[]>([]);const title=ref('');const fileInput=ref<HTMLInputElement|null>(null);const loading=ref(false);const busy=ref(false);const error=ref('');const notice=ref('');
const statusText=(status:string)=>({UPLOADED:'待索引',INDEXING:'正在建索引',READY:'可用于辅导',FAILED:'索引失败',WITHDRAWN:'已撤回'}[status]??status);
const loadDocuments=async()=>{if(!classId.value)return;loading.value=true;error.value='';try{documents.value=await listKnowledgeDocumentsApi(classId.value);}catch(e:any){error.value=e?.message??'读取资料失败';}finally{loading.value=false;}};
const upload=async()=>{const file=fileInput.value?.files?.[0];if(!classId.value||!file)return;busy.value=true;error.value='';notice.value='';try{await uploadKnowledgeDocumentApi(classId.value,title.value.trim(),file);title.value='';if(fileInput.value)fileInput.value.value='';notice.value='已提交。索引完成后可用于学生辅导。';await loadDocuments();}catch(e:any){error.value=e?.message??'上传失败';}finally{busy.value=false;}};
const withdraw=async(doc:KnowledgeDocumentStatus)=>{busy.value=true;error.value='';try{await withdrawKnowledgeDocumentApi(doc.documentId);notice.value='资料已撤回，历史引用将显示为不可用。';await loadDocuments();}catch(e:any){error.value=e?.message??'撤回失败';}finally{busy.value=false;}};
onMounted(async()=>{await classroom.loadClasses();classId.value=classroom.activeClassId;await loadDocuments();});
</script>

<style scoped>
.knowledge-page{max-width:980px;margin:0 auto;padding:30px 24px 60px;color:#1b2a41}.knowledge-page header{display:flex;justify-content:space-between;align-items:center;gap:20px;margin-bottom:24px}.eyebrow{font-size:11px;letter-spacing:.14em;color:#5270a0}.knowledge-page h1{font-size:28px;margin:4px 0}.knowledge-page header p:last-child{color:#64748b}.knowledge-page select,.upload-card input{border:1px solid #ccd5e2;border-radius:9px;padding:10px;background:white}.upload-card,.docs-card{background:white;border:1px solid #e2e8f0;border-radius:16px;padding:22px;margin:16px 0;box-shadow:0 8px 24px #1d35570a}.upload-card h2,.docs-card h2{font-size:17px;margin:0}.upload-card form{display:grid;grid-template-columns:1fr 1.4fr auto;align-items:end;gap:14px;margin-top:16px}.upload-card label{display:grid;gap:7px;font-size:13px}.upload-card button,.docs-heading button{border:0;border-radius:9px;background:#2456a6;color:white;padding:11px 15px;cursor:pointer}.upload-card button:disabled{opacity:.55}.upload-card small{grid-column:1/-1;color:#718096}.docs-heading{display:flex;justify-content:space-between;align-items:center}.docs-heading button{padding:8px 12px}.docs-card ul{list-style:none;margin:14px 0 0;padding:0}.docs-card li{display:flex;align-items:center;gap:14px;border-top:1px solid #edf0f5;padding:14px 2px}.docs-card li div{display:grid;gap:4px;flex:1;min-width:0}.docs-card li strong{overflow-wrap:anywhere}.docs-card li span{font-size:12px;color:#718096}.status{border-radius:999px;padding:5px 9px;background:#eef2f7;white-space:nowrap}.status.ready{color:#147544;background:#e5f7ed}.status.failed{color:#a23939;background:#fff0f0}.withdraw{border:0;background:transparent;color:#a23c3c;cursor:pointer}.error{color:#a23939}.notice{color:#147544}@media(max-width:700px){.knowledge-page{padding:20px 14px}.knowledge-page header{align-items:flex-start;flex-direction:column}.upload-card form{grid-template-columns:1fr}.upload-card small{grid-column:auto}.docs-card li{flex-wrap:wrap}.docs-card li div{flex-basis:65%}}
</style>
