<template>
  <div class="stack">
    <div class="row">
      <input
        v-model="keyword"
        placeholder="搜索题目或答案…"
        style="flex: 1 1 220px"
        @keyup.enter="load"
      />
      <select v-model="category" style="width: 180px" @change="load">
        <option value="">全部栏目</option>
        <option v-for="c in categories" :key="c.name" :value="c.name">
          {{ c.name }}（{{ c.total }}）
        </option>
      </select>
      <button class="ghost" :disabled="loading" @click="load">刷新</button>
      <button class="primary" @click="openCreate">新建</button>
    </div>

    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="loading" class="empty">加载中…</div>
    <div v-else-if="!items.length" class="empty">
      还没有题目。<button class="ghost" @click="openCreate">写第一条</button>
    </div>

    <div v-else class="note-list">
      <div v-for="n in items" :key="n.uuid" class="note-card" @click="openView(n)">
        <div class="note-title">{{ n.name }}</div>
        <div v-if="n.definition" class="note-preview">{{ summary(n.definition) }}</div>
        <div class="note-meta">
          <span class="chip">{{ n.category || '未分类' }}</span>
          <span>{{ dueText(n) }}</span>
          <span v-if="n.stars">★ {{ n.stars }}</span>
          <span>{{ n.reviewCount }} 次复习</span>
        </div>
      </div>
    </div>

    <p class="hint" v-if="items.length">共 {{ items.length }} 条（上限 {{ limit }} 条，缩小搜索范围可看更多）</p>

    <!-- 查看 / 编辑 -->
    <div v-if="active" class="modal-mask" @click.self="close">
      <div class="modal">
        <template v-if="mode === 'view'">
          <div class="row" style="margin-bottom: 12px">
            <span class="chip">{{ active.category || '未分类' }}</span>
            <span class="hint">{{ dueText(active) }}</span>
            <div class="spacer"></div>
            <button class="ghost" @click="openEdit(active)">编辑</button>
            <button class="danger" @click="remove(active)">删除</button>
            <button class="ghost" @click="close">关闭</button>
          </div>
          <h2 style="margin: 0 0 12px; font-size: 20px">{{ active.name }}</h2>
          <MarkdownView :source="active.definition" />
        </template>

        <template v-else>
          <h3 style="margin: 0 0 14px">{{ mode === 'create' ? '新建题目' : '编辑题目' }}</h3>
          <div class="field">
            <label class="label">题目</label>
            <input v-model="form.name" placeholder="一句话提问" />
          </div>
          <div class="field">
            <label class="label">栏目</label>
            <input v-model="form.category" placeholder="未分类" list="category-options" />
            <datalist id="category-options">
              <option v-for="c in categories" :key="c.name" :value="c.name" />
            </datalist>
          </div>
          <div class="field">
            <label class="label">答案（支持 Markdown）</label>
            <textarea v-model="form.definition" rows="10" placeholder="支持代码块、表格、列表"></textarea>
          </div>
          <div class="field">
            <label class="label">预览</label>
            <div class="preview-box"><MarkdownView :source="form.definition" /></div>
          </div>
          <p v-if="error" class="error">{{ error }}</p>
          <div class="row">
            <div class="spacer"></div>
            <button class="ghost" @click="close">取消</button>
            <button class="primary" :disabled="saving || !form.name.trim()" @click="save">
              {{ saving ? '保存中…' : '保存' }}
            </button>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue';
import { api } from '../api.js';
import { plainSummary, humanDue } from '../markdown.js';
import MarkdownView from '../components/MarkdownView.vue';

export default {
  name: 'BankView',
  components: { MarkdownView },
  setup() {
    const items = ref([]);
    const categories = ref([]);
    const keyword = ref('');
    const category = ref('');
    const limit = 300;
    const loading = ref(false);
    const saving = ref(false);
    const error = ref('');
    const active = ref(null);
    const mode = ref('view');
    const form = ref({ name: '', definition: '', category: '' });

    const summary = (s) => plainSummary(s, 140);
    const dueText = (n) => humanDue(n);

    async function loadCategories() {
      try {
        const res = await api.categories();
        categories.value = res.items || [];
      } catch (e) {
        error.value = e.message;
      }
    }

    async function load() {
      loading.value = true;
      error.value = '';
      try {
        const res = await api.notes({ q: keyword.value.trim(), category: category.value, limit });
        items.value = res.items || [];
      } catch (e) {
        error.value = e.message;
      } finally {
        loading.value = false;
      }
    }

    function close() {
      active.value = null;
      error.value = '';
    }

    function openView(n) {
      active.value = n;
      mode.value = 'view';
    }

    function openCreate() {
      form.value = { name: '', definition: '', category: category.value };
      mode.value = 'create';
      active.value = { placeholder: true };
      error.value = '';
    }

    function openEdit(n) {
      form.value = { name: n.name, definition: n.definition, category: n.category };
      mode.value = 'edit';
      active.value = n;
      error.value = '';
    }

    async function save() {
      saving.value = true;
      error.value = '';
      const payload = {
        name: form.value.name.trim(),
        definition: form.value.definition,
        category: form.value.category.trim() || '未分类',
      };
      try {
        if (mode.value === 'create') {
          await api.createNote(payload);
        } else {
          await api.updateNote(active.value.uuid, payload);
        }
        close();
        await Promise.all([load(), loadCategories()]);
      } catch (e) {
        error.value = e.message;
      } finally {
        saving.value = false;
      }
    }

    async function remove(n) {
      if (!window.confirm(`删除「${n.name}」？手机上也会同步删除。`)) return;
      try {
        await api.deleteNote(n.uuid);
        close();
        await Promise.all([load(), loadCategories()]);
      } catch (e) {
        error.value = e.message;
      }
    }

    onMounted(() => {
      load();
      loadCategories();
    });

    return {
      items,
      categories,
      keyword,
      category,
      limit,
      loading,
      saving,
      error,
      active,
      mode,
      form,
      summary,
      dueText,
      load,
      close,
      openView,
      openCreate,
      openEdit,
      save,
      remove,
    };
  },
};
</script>
