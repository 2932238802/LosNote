// 复习取题失败时的可读文案（纯函数，便于单测）。
// 401 与网络失败是用户最常遇到的两种情况，其余透传服务端/接口的错误信息。
export function quizErrorText(error) {
  if (error?.status === 401) return '令牌无效或已失效，请到「设置」重新登录';
  const message = error?.message || '';
  if (/failed to fetch|load failed|networkerror/i.test(message)) {
    return '连不上服务器，请检查网络后重试';
  }
  return message || '取题失败，请重试';
}
