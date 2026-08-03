package com.example.flikky.session

/**
 * 软删除的待提交台账：消息先从 UI 隐藏（内存移除 / 排除集），落库提交要么由
 * Snackbar 撤销窗口超时触发，要么在 ViewModel 销毁时通过 [drainIds] 兜底。
 *
 * 引入动机（v1.16 验收缺陷）：提交此前只挂在 Screen 的 Snackbar 等待协程上，
 * 协程随 composition 取消——撤销窗口内退出页面，删除就永远不落库，消息在
 * History 复活且磁盘文件泄漏。台账让"谁负责提交"与页面生命周期解耦。
 *
 * 语义约定：
 * - 撤销只作用于最近一次 [stage]（与单条 Snackbar 的 UI 语义一致）；更早软删的
 *   条目保持待提交。
 * - [undoLatest] 无论快照是否为 null 都消费掉该条目——快照缺失说明内存里本来
 *   就没这条消息，既无从恢复也不该再提交。
 *
 * 非线程安全：只在主线程（ViewModel 回调）使用。
 */
class PendingMessageDeletes {
    private val staged = LinkedHashMap<Long, Message?>()

    /** 软删除入账：记录待提交 id 与用于撤销恢复的快照（找不到消息时为 null）。 */
    fun stage(id: Long, snapshot: Message?) {
        // 重复入账同一 id 时挪到队尾，保证它成为"最近一次"可撤销条目。
        staged.remove(id)
        staged[id] = snapshot
    }

    /** 撤销最近一次软删除：消费该条目并返回快照供 UI 恢复；无待撤销条目返回 null。 */
    fun undoLatest(): Message? {
        val lastId = staged.keys.lastOrNull() ?: return null
        return staged.remove(lastId)
    }

    /** 该 id 已提交落库，出账。 */
    fun commit(id: Long) {
        staged.remove(id)
    }

    /** 取出全部仍未提交的 id 并清空台账——ViewModel 销毁时兜底提交用。 */
    fun drainIds(): List<Long> {
        val ids = staged.keys.toList()
        staged.clear()
        return ids
    }
}
