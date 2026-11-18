<template>
  <div class="stack">
    <div class="row">
      <select v-model="category" style="width: 200px" :disabled="loading" @change="start">
        <option value="">全部栏目</option>
        <option v-for="c in categories" :key="c.name" :value="c.name">{{ c.name }}</option>
      </select>
      <button class="primary" :disabled="loading" @click="start">开始 / 重新抽题</button>
      <div class="spacer"></div>
      <span class="hint" v-if="queue.length">{{ index + 1 }} / {{ queue.length }}</span>
    </div>

    <div class="progress-bar" v-if="queue.length">
      <span :style="{ width: pct + '%' }"></span>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="categoriesError" class="error">{{ categoriesError }}</p>

    <div v-if="loading" class="empty">正在取题…</div>

    <div v-else-if="!queue.length && error" class="empty">
      <div class="empty-mark">!</div>
      取题失败，请按上方提示重试。
    </div>

    <div v-else-if="!queue.length" class="empty">
      <div class="empty-mark">—</div>
      这一轮没有到期的题 —— 去题库加几条，或者等复习时间到。
    </div>

    <div v-else-if="current" class="panel card-stage">
      <div class="row">
        <span class="chip">{{ current.category || '未分类' }}</span>
        <span class="hint">{{ dueText(current) }}</span>
      </div>

      <div class="card-question">{{ current.name }}</div>

      <div v-if="revealed" class="card-answer">
        <MarkdownView :source="current.definition || '_（这条题没有答案）_'" />
      </div>

      <div class="spacer"></div>

      <div class="row" v-if="!revealed">
        <div class="spacer"></div>
        <button class="primary" @click="revealed = true">显示答案</button>
      </div>

      <div v-else class="stack">
        <div class="grade-row">
          <button :disabled="grading" @click="grade(0)">
            没想起来<small>10 分钟后再来</small>
          </button>
          <button class="primary" :disabled="grading" @click="grade(1)">
            想起来了<small>正常推进</small>
          </button>
          <button :disabled="grading" @click="grade(2)">
            太简单<small>拉长间隔</small>
          </button>
        </div>
        <div class="kbd-hint">
          <span>快捷键：</span>
          <kbd>空格</kbd><span>显示答案</span>
          <span class="spacer" style="width: 8px"></span>
          <kbd>1</kbd><span>忘记</span>
          <kbd>2</kbd><span>想起</span>
          <kbd>3</kbd><span>简单</span>
        </div>
      </div>
    </div>

    <div v-else class="empty">
      <div class="empty-mark">✓</div>
      <div style="font-size: 17px; margin-bottom: 6px">这一轮复习完了</div>
      <div>共评分 {{ done }} 次</div>
    </div>
  </div>
</template>

<script>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { api } from '../api.js';
import { createLatestRequestGuard } from '../request-guard.js';
import { orderForStart } from '../quiz-order.js';
import { quizErrorText } from '../quiz-error.js';
import { humanDue } from '../markdown.js';
import MarkdownView from '../components/MarkdownView.vue';

export default {
  name: 'QuizView',
  components: { MarkdownView },
  setup() {
    const queue = ref([]);
    const index = ref(0);
    const revealed = ref(false);
    const loading = ref(false);
    const grading = ref(false);
    const error = ref('');
    // 栏目录取失败与取题失败分开，避免两者互相覆盖，也避免把空状态误标为取题失败
    const categoriesError = ref('');
    const category = ref('');
    const categories = ref([]);
    const done = ref(0);
    // 上一次成功抽到的首题，用于「重新抽题」时避重（不参与渲染，无需响应式）
    let previousFirstUuid = '';
    // 并发 start() 时只应用最后一次请求的响应
    const dueRequests = createLatestRequestGuard();

    const current = computed(() => queue.value[index.value] || null);
    const pct = computed(() =>
      queue.value.length ? Math.round((index.value / queue.value.length) * 100) : 0,
    );
    const dueText = (n) => humanDue(n);

    async function loadCategories() {
      try {
        const res = await api.categories();
        categories.value = res.items || [];
        categoriesError.value = '';
      } catch (e) {
        // 栏目失败不影响取题；单独提示，避免覆盖取题错误或误触发「取题失败」
        categoriesError.value = `栏目加载失败：${quizErrorText(e)}`;
      }
    }

    async function start() {
      const request = dueRequests.begin();
      loading.value = true;
      error.value = '';
      try {
        const res = await api.due({ category: category.value, limit: 100 });
        if (!dueRequests.isLatest(request)) return;
        const items = Array.isArray(res.items) ? res.items : [];
        // 本地洗牌 + 到期题 > 1 时避开上一次首题；老版服务端固定顺序也能生效
        queue.value = orderForStart(items, previousFirstUuid);
        previousFirstUuid = queue.value[0]?.uuid || '';
        index.value = 0;
        revealed.value = false;
        done.value = 0;
      } catch (e) {
        if (!dueRequests.isLatest(request)) return;
        error.value = quizErrorText(e);
      } finally {
        // 旧请求结束时不能把最新请求的 loading 提前关掉
        if (dueRequests.isLatest(request)) loading.value = false;
      }
    }

    async function grade(g) {
      if (!current.value) return;
      grading.value = true;
      error.value = '';
      try {
        await api.review(current.value.uuid, g);
        done.value += 1;
        // 打了「没想起来」的题会在十分钟后重来，本轮不再追问，避免死循环
        index.value += 1;
        revealed.value = false;
      } catch (e) {
        error.value = e.message;
      } finally {
        grading.value = false;
      }
    }

    // 键盘：空格显示答案，1/2/3 评分。焦点落在输入控件上时不抢键。
    function onKeydown(e) {
      const t = e.target;
      if (t && /^(INPUT|TEXTAREA|SELECT|BUTTON)$/.test(t.tagName)) return;
      if (!current.value) return;
      if (e.code === 'Space') {
        if (!revealed.value) {
          e.preventDefault();
          revealed.value = true;
        }
        return;
      }
      if (revealed.value && !grading.value) {
        if (e.key === '1') grade(0);
        else if (e.key === '2') grade(1);
        else if (e.key === '3') grade(2);
      }
    }

    onMounted(() => {
      loadCategories();
      start();
      window.addEventListener('keydown', onKeydown);
    });

    onUnmounted(() => {
      window.removeEventListener('keydown', onKeydown);
    });

    return {
      queue,
      index,
      revealed,
      loading,
      grading,
      error,
      categoriesError,
      category,
      categories,
      done,
      current,
      pct,
      dueText,
      start,
      grade,
    };
  },
};
</script>
