<template>
  <div ref="root" class="markdown"></div>
</template>

<script>
import { ref, watch, onMounted } from 'vue';
import { renderInto } from '../markdown.js';

export default {
  name: 'MarkdownView',
  props: { source: { type: String, default: '' } },
  setup(props) {
    const root = ref(null);
    const draw = () => renderInto(root.value, props.source);
    onMounted(draw);
    watch(() => props.source, draw);
    return { root };
  },
};
</script>
