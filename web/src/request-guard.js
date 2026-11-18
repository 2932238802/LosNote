// 取题请求的「最后一次生效」守卫。
// 并发 start() 时，只允许最新一次请求写入队列与错误状态，避免慢响应覆盖新结果。
export function createLatestRequestGuard() {
  let latest = 0;
  return {
    /** 开始一次新请求，返回本次请求的序号。 */
    begin() {
      latest += 1;
      return latest;
    },
    /** 判断序号是否仍是最新请求；旧请求应丢弃响应与错误。 */
    isLatest(token) {
      return token === latest;
    },
  };
}
