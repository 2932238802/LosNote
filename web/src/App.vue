<template>
  <div v-if="!token" class="shell auth">
    <div class="panel stack">
      <div class="brand">
        <span class="dot"></span>
        LosNote
      </div>
      <p class="sub">输入服务器上配置的访问令牌（LOSNOTE_TOKEN）。</p>
      <input v-model="input" type="password" placeholder="访问令牌" @keyup.enter="login" />
      <button class="primary" :disabled="checking" @click="login">
        {{ checking ? '校验中…' : '进入' }}
      </button>
      <p v-if="error" class="error">{{ error }}</p>
    </div>
  </div>

  <div v-else class="shell">
    <div class="topbar">
      <span class="brand">
        <span class="dot"></span>
        LosNote
      </span>
      <span class="hint" v-if="dueCount">{{ dueCount }} 条待复习</span>
      <div class="spacer"></div>
      <button
        class="ghost icon"
        :title="isDark ? '切到浅色' : '切到深色'"
        :aria-label="isDark ? '切到浅色' : '切到深色'"
        @click="toggleTheme"
      >
        <svg v-if="!isDark" viewBox="0 0 24 24" fill="none" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
          <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
        </svg>
        <svg v-else viewBox="0 0 24 24" fill="none" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="4" />
          <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41" />
        </svg>
      </button>
      <button class="ghost" @click="refresh">刷新</button>
    </div>

    <div class="tabs" role="tablist">
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
import { ref, onMounted, watchEffect } from 'vue';
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

    const theme = ref(document.documentElement.dataset.theme || 'light');
    const isDark = ref(theme.value === 'dark');

    // 主题切换：写回 <html data-theme> 并持久化
    watchEffect(() => {
      document.documentElement.dataset.theme = theme.value;
      isDark.value = theme.value === 'dark';
    });

    function toggleTheme() {
      theme.value = isDark.value ? 'light' : 'dark';
      try {
        localStorage.setItem('losnote.theme', theme.value);
      } catch {
        /* 隐私模式下写 localStorage 可能失败，不影响切换 */
      }
    }

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

    return { token, input, error, checking, view, dueCount, isDark, toggleTheme, login, logout, refresh };
  },
};
</script>
