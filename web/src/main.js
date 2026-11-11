import { createApp } from 'vue';
import App from './App.vue';
import './styles.css';

// 尽早应用主题，避免首屏闪一下错误的配色（module 脚本在渲染前执行）
const saved = localStorage.getItem('losnote.theme');
const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
document.documentElement.dataset.theme =
  saved || (prefersDark ? 'dark' : 'light');

createApp(App).mount('#app');
