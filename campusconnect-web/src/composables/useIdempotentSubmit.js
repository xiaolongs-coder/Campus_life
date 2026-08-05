import { ref } from 'vue'
import { v4 as uuidv4 } from 'uuid'  // 如果项目没有uuid，回退用 crypto.randomUUID()

/**
 * 幂等提交 Composable
 *
 * 作用：防止用户重复点击导致的重复提交
 *
 * 三层防护：
 * 1. 前端：按钮置灰 + loading 状态（用户体验层）
 * 2. 请求层：X-Idempotency-Key Header（API 幂等层）
 * 3. 数据库：唯一索引（最终兜底层）
 *
 * 使用方式：
 * ```
 * const { submitting, idemKey, withIdempotent } = useIdempotentSubmit()
 *
 * async function handlePublish() {
 *   await withIdempotent(async () => {
 *     await request.post('/api/posts', data, {
 *       headers: { 'X-Idempotency-Key': idemKey.value }
 *     })
 *   })
 * }
 * ```
 */
export function useIdempotentSubmit() {
  const submitting = ref(false)
  const idemKey = ref('')

  /**
   * 生成新的幂等 Key 并执行异步操作
   * 操作期间 submitting=true，按钮自动置灰
   */
  async function withIdempotent(asyncFn) {
    if (submitting.value) {
      console.warn('[幂等] 操作进行中，忽略重复点击')
      return { rejected: true, reason: 'submitting' }
    }

    // 生成唯一幂等 Key
    idemKey.value = generateIdemKey()

    submitting.value = true
    try {
      return await asyncFn()
    } finally {
      // 延迟重置，防止用户极快双击
      setTimeout(() => {
        submitting.value = false
        idemKey.value = ''
      }, 600)
    }
  }

  /**
   * 重置状态（手动释放）
   */
  function reset() {
    submitting.value = false
    idemKey.value = ''
  }

  return {
    /** 是否正在提交（绑定到按钮 :disabled） */
    submitting,
    /** 当前幂等 Key（绑定到请求 Header） */
    idemKey,
    /** 包裹异步提交函数 */
    withIdempotent,
    /** 手动重置 */
    reset
  }
}

function generateIdemKey() {
  // 优先使用 crypto.randomUUID()，不支持时回退到时间戳+随机数
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  return Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10)
}
