<template>
  <div class="stack">
    <div class="panel stack">
      <strong>AI 整理导入</strong>
      <p class="hint">
        Key 保存在服务器上（网页端只显示是否已配置）。手机端和网页端共用同一套设置时，整理出来的格式一致。
      </p>
      <div class="field">
        <label class="label">Base URL（OpenAI 兼容的 /chat/completions 地址）</label>
        <input v-model="form.baseUrl" placeholder="https://api.deepseek.com/chat/completions" />
      </div>
      <div class="field">
        <label class="label">模型</label>
        <input v-model="form.model" placeholder="deepseek-chat" />
      </div>
      <div class="field">
        <label class="label">API Key {{ hasKey ? '（已配置，留空表示不改）' : '（未配置）' }}</label>
        <input v-model="form.apiKey" type="password" placeholder="sk-…" autocomplete="off" />
      </div>
      <div class="row">
        <label class="row" style="gap: 6px">
          <input type="checkbox" v-model="form.webSearch" style="width: auto" />
          <span>请求里附带「优先联网搜索」提示</span>
        </label>
        <div class="spacer"></div>
        <button class="ghost" v-if="hasKey" @click="clearKey">清除 Key</button>
        <button class="primary" :disabled="saving" @click="save">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </div>
      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="notice" class="hint">{{ notice }}</p>
    </div>

    <div class="panel stack">
      <strong>整库导出</strong>
      <p class="hint">导出包含复习进度、星级和复习记录的完整快照，可用来备份或迁移。</p>
      <div class="row">
        <button :disabled="exporting" @click="exportAll">
          {{ exporting ? '导出中…' : '导出 JSON' }}
        </button>
      </div>
    </div>

    <div class="panel stack">
      <strong>访问令牌</strong>
      <p class="hint">换令牌后需要重新登录。令牌只存在这台浏览器的 localStorage 里。</p>
      <div class="row">
        <button class="danger" @click="$emit('logout')">退出登录</button>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue';
import { api } from '../api.js';

export default {
  name: 'SettingsView',
  emits: ['logout'],
  setup() {
    const form = ref({ baseUrl: '', model: '', apiKey: '', webSearch: false });
    const hasKey = ref(false);
    const saving = ref(false);
    const exporting = ref(false);
    const error = ref('');
    const notice = ref('');

    async function load() {
      try {
        const res = await api.aiSettings();
        form.value = {
          baseUrl: res.baseUrl || '',
          model: res.model || '',
          apiKey: '',
          webSearch: Boolean(res.webSearch),
        };
        hasKey.value = Boolean(res.hasKey);
      } catch (e) {
        error.value = e.message;
      }
    }

    async function save() {
      saving.value = true;
      error.value = '';
      notice.value = '';
      try {
        const res = await api.saveAiSettings(form.value);
        hasKey.value = Boolean(res.hasKey);
        form.value.apiKey = '';
        notice.value = '已保存';
      } catch (e) {
        error.value = e.message;
      } finally {
        saving.value = false;
      }
    }

    async function clearKey() {
      try {
        const res = await api.saveAiSettings({ clearKey: true });
        hasKey.value = Boolean(res.hasKey);
        notice.value = '已清除 Key';
      } catch (e) {
        error.value = e.message;
      }
    }

    async function exportAll() {
      exporting.value = true;
      error.value = '';
      try {
        const res = await api.pull(0, 5000);
        const payload = {
          version: 2,
          exportedAt: new Date().toISOString(),
          notes: res.notes || [],
          reviews: res.reviews || [],
        };
        const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `losnote-backup-${new Date().toISOString().slice(0, 10)}.json`;
        a.click();
        URL.revokeObjectURL(url);
      } catch (e) {
        error.value = e.message;
      } finally {
        exporting.value = false;
      }
    }

    onMounted(load);

    return { form, hasKey, saving, exporting, error, notice, save, clearKey, exportAll };
  },
};
</script>
