<template>
  <div v-if="!token" class="shell" style="max-width: 420px; padding-top: 60px">
    <div class="panel stack">
      <div class="brand">LosNote</div>
      <p class="hint">输入服务器上配置的访问令牌（LOSNOTE_TOKEN）。</p>
      <input v-model="input" type="password" placeholder="访问令牌" @keyup.enter="login" />
      <button class="primary" :disabled="checking" @click="login">
        {{ checking ? '校验中…' : '进入' }}
      </button>
      <p v-if="error" class="error">{{ error }}</p>
    </div>
  </div>

  <div v-else class="shell">
    <div class="topbar">
      <span class="brand">LosNote</span>
      <span class="hint" v-if="dueCount">{{ dueCount }} 条待复习</span>
      <div class="spacer"></div>
      <button class="ghost" @click="refresh">刷新</button>
    </div>

    <div class="tabs">
      <button :class="{ active: view === 'bank' }" @click="view = 'bank'">题库</button>
      <button :class="{ active: view === 'quiz' }" @click="view = 'quiz'">复习</button>
      <button :class="{ active: view === 'import' }" @click="view = 'import'">导入</button>
      <button :class="{ active: view === 'settings' }" @click="view = 'settings'">设置</button>
    </div>

    <BankView v-if="view === 'bank'" />
    <QuizView v-else-if="view === 'quiz'" />
    <ImportView v-else-if="view === 'import'" />
    <SettingsView v-else-if="view === 'settings'" @logout="logout" />
  </div>
</template>

<script>
import { ref, onMounted } from 'vue';
import { api, getToken, setToken, clearToken } from './api.js';
import BankView from './views/BankView.vue';
import QuizView from './views/QuizView.vue';
import ImportView from './views/ImportView.vue';
import SettingsView from './views/SettingsView.vue';

export default {
  name: 'App',
  components: { BankView, QuizView, ImportView, SettingsView },
  setup() {
    const token = ref(getToken());
    const input = ref('');
    const error = ref('');
    const checking = ref(false);
    const view = ref('bank');
    const dueCount = ref(0);

    async function loadDue() {
      if (!token.value) return;
      try {
        const res = await api.due({ limit: 1 });
        dueCount.value = Array.isArray(res.items) ? res.items.length : 0;
      } catch {
        /* 顶栏计数失败不打扰 */
      }
    }

    async function login() {
      const value = input.value.trim();
      if (!value) return;
      checking.value = true;
      error.value = '';
      setToken(value);
      try {
        await api.categories();
        token.value = value;
        input.value = '';
        loadDue();
      } catch (e) {
        clearToken();
        error.value = e.status === 401 ? '令牌不对' : e.message;
      } finally {
        checking.value = false;
      }
    }

    function logout() {
      clearToken();
      token.value = '';
    }

    function refresh() {
      window.location.reload();
    }

    onMounted(loadDue);

    return { token, input, error, checking, view, dueCount, login, logout, refresh };
  },
};
</script>
