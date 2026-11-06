<template>
  <div class="stack">
    <div class="panel stack">
      <div class="row">
        <div style="flex: 1 1 200px">
          <label class="label">默认栏目</label>
          <input v-model="category" placeholder="未分类" />
        </div>
        <div style="flex: 0 0 auto; align-self: flex-end">
          <label class="label">从文件导入</label>
          <input type="file" accept=".json,.txt,.md" @change="loadFile" />
        </div>
      </div>

      <div>
        <label class="label">
          粘贴内容 —— 每行「题目 | 答案 | 栏目」，或每块以单独一行 @@@ 分隔（首行题目，其余为答案）
        </label>
        <textarea
          v-model="text"
          rows="12"
          placeholder="闭包是什么 | 函数与词法环境的组合 | JavaScript&#10;@@@&#10;TCP 三次握手&#10;SYN → SYN+ACK → ACK"
        ></textarea>
      </div>

      <div class="row">
        <button :disabled="busy || !text.trim()" @click="parseLocal">批量解析</button>
        <button :disabled="busy || !text.trim()" @click="aiFormat">
          {{ busy === 'ai' ? 'AI 整理中…' : 'AI 整理导入' }}
        </button>
        <div class="spacer"></div>
        <span class="hint" v-if="preview.length">预览 {{ preview.length }} 条，确认后提交</span>
      </div>

      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="notice" class="hint">{{ notice }}</p>
    </div>

    <div v-if="preview.length" class="panel stack">
      <div class="row">
        <strong>预览</strong>
        <div class="spacer"></div>
        <button class="ghost" @click="preview = []">清空</button>
        <button class="primary" :disabled="busy === 'commit'" @click="commit">
          {{ busy === 'commit' ? '提交中…' : `提交 ${preview.length} 条` }}
        </button>
      </div>
      <div>
        <div v-for="(n, i) in preview" :key="i" class="import-row">
          <input v-model="n.name" placeholder="题目" />
          <textarea v-model="n.definition" rows="2" placeholder="答案（Markdown）"></textarea>
          <div class="row" style="gap: 4px">
            <input v-model="n.category" placeholder="栏目" />
            <button class="ghost" title="移除" @click="preview.splice(i, 1)">✕</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref } from 'vue';
import { api } from '../api.js';

/** 备份文件的特征：带 version、足迹，或题目里带 uuid / SRS 字段 */
function looksLikeBackup(data) {
  if (data.version || Array.isArray(data.dailyLogs)) return true;
  return (data.notes || []).some(
    (n) => n && (n.uuid || n.reviewCount != null || n.intervalDays != null || n.stars != null)
  );
}

export default {
  name: 'ImportView',
  setup() {
    const text = ref('');
    const category = ref('未分类');
    const preview = ref([]);
    const busy = ref('');
    const error = ref('');
    const notice = ref('');

    function reset() {
      error.value = '';
      notice.value = '';
    }

    async function loadFile(event) {
      const file = event.target.files?.[0];
      if (!file) return;
      reset();
      const raw = await file.text();
      if (file.name.toLowerCase().endsWith('.json')) {
        let data;
        try {
          data = JSON.parse(raw);
        } catch (e) {
          error.value = `JSON 解析失败：${e.message}`;
          event.target.value = '';
          return;
        }
        // 备份文件（v1/v2）：直接按备份导入，保住 uuid 与复习进度。
        // 走预览会丢 SRS，导回来等于把学过的东西清零。
        if (data && !Array.isArray(data) && Array.isArray(data.notes) && looksLikeBackup(data)) {
          busy.value = 'backup';
          try {
            const res = await api.importBackup(data);
            notice.value = `已按备份导入 ${res.notes} 题、${res.dailyLogs} 条足迹（保留复习进度）${
              res.skipped ? `，跳过 ${res.skipped} 条` : ''
            }`;
            preview.value = [];
          } catch (e) {
            error.value = `备份导入失败：${e.message}`;
          } finally {
            busy.value = '';
          }
          event.target.value = '';
          return;
        }
        const list = Array.isArray(data) ? data : data.notes;
        if (!Array.isArray(list)) {
          error.value = 'JSON 里没有 notes 数组';
          event.target.value = '';
          return;
        }
        preview.value = list
          .map((o) => ({
            name: String(o?.name || '').trim(),
            definition: String(o?.definition || ''),
            category: String(o?.category || category.value || '未分类').trim(),
          }))
          .filter((o) => o.name);
        notice.value = `已从 ${file.name} 读入 ${preview.value.length} 条`;
      } else {
        text.value = raw;
        notice.value = `已把 ${file.name} 的内容放进输入框，点「批量解析」`;
      }
      event.target.value = '';
    }

    async function parseLocal() {
      reset();
      busy.value = 'parse';
      try {
        const res = await api.parse(text.value, category.value.trim() || '未分类');
        preview.value = res.notes || [];
        notice.value = `解析出 ${preview.value.length} 条`;
      } catch (e) {
        error.value = e.message;
      } finally {
        busy.value = '';
      }
    }

    async function aiFormat() {
      reset();
      busy.value = 'ai';
      try {
        const res = await api.aiFormat(text.value, category.value.trim() || '未分类');
        preview.value = res.notes || [];
        notice.value = `AI 整理出 ${preview.value.length} 条，检查后再提交`;
      } catch (e) {
        error.value = e.message;
      } finally {
        busy.value = '';
      }
    }

    async function commit() {
      reset();
      busy.value = 'commit';
      try {
        const res = await api.commit(preview.value, category.value.trim() || '未分类');
        notice.value = `已导入 ${res.count} 条`;
        preview.value = [];
        text.value = '';
      } catch (e) {
        error.value = e.message;
      } finally {
        busy.value = '';
      }
    }

    return {
      text,
      category,
      preview,
      busy,
      error,
      notice,
      loadFile,
      parseLocal,
      aiFormat,
      commit,
    };
  },
};
</script>
