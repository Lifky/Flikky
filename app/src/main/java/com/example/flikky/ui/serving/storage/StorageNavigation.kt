package com.example.flikky.ui.serving.storage

/**
 * 目录导航的状态迁移。纯函数，无协程无 Android。
 *
 * ## 为什么要有这个对象
 *
 * v1.20.0 首版把 `list()` 同步跑在主线程上，于是大目录（几千个文件，每条 3 次 stat）
 * 点进去后界面整体冻住：用户点了没反应，以为没点上，又去点别的文件夹；等第一次列举
 * 终于返回，界面又跳回刚才那个大目录（装机验收实测）。
 *
 * 修法是成熟文件管理器（Google Files / Windows Explorer / Solid Explorer）的同一套：
 * **立即切换 + 显示进度 + 取消旧请求**。三条缺一不可——
 * - 只挪到后台线程：界面不卡了，但点击到列表出现之间仍然「看起来没反应」。
 * - 只加进度：旧请求的结果还会后到，把用户已经离开的目录重新画上来。
 * - 只取消：没有进度指示，用户还是不知道正在做事。
 *
 * 迁移逻辑抽成纯函数是因为它有三个容易写错的点，而它们都难在真机上复现：
 * 失败时退回哪里、loading 什么时候清、以及**选择集合必须跨导航存活**。
 */
object StorageNavigation {

    /**
     * 点击的那一瞬间。
     *
     * 路径**立即**前进（面包屑马上就动，这是「有反应」的来源），列表清空，置 loading。
     * 清空而不是留着旧列表是刻意的：把上一个目录的内容画在新目录的面包屑下面是在骗人，
     * 用户会以为点错了。
     *
     * 选择集合原样保留——跨目录累积是它的设计（上游 D5）。
     */
    fun begin(current: LocalStorageState, requested: String): LocalStorageState =
        LocalStorageState(
            path = requested.trim().trim('/'),
            entries = emptyList(),
            selected = current.selected,
            loading = true,
        )

    /**
     * 路径已确认可读（流式列举的第一步）。
     *
     * 把路径换成服务端/文件系统规范化后的那个，并清空列表准备接收第一批。
     * 仍然保持 loading —— 条目还没来。
     */
    fun head(current: LocalStorageState, path: String): LocalStorageState =
        current.copy(path = path, entries = emptyList(), lastBatchStart = 0, loading = true)

    /**
     * 追加一批条目。
     *
     * [LocalStorageState.lastBatchStart] 记下这一批的起始下标，UI 据此给这批行
     * 逐行入场的阶梯序号。追加**不清 loading**：后面还有批次，进度条要一直在。
     *
     * 批次到达时顺序已经是全局有序的（[com.example.flikky.util.DirectoryScan] 先排完再切批），
     * 所以这里只做拼接，绝不重排——重排会让已经画出来的行在用户眼前跳位。
     */
    fun append(current: LocalStorageState, batch: List<LocalEntry>): LocalStorageState =
        current.copy(
            entries = current.entries + batch,
            lastBatchStart = current.entries.size,
            loading = true,
        )

    /**
     * 流正常结束。只清 loading，内容一个字不动。
     *
     * 与 [settle] 的区别：那个是「一次性列举的结果落地或失败退回」，
     * 这个是「已经逐批落地完了」。分开是因为流式路径下失败可能发生在
     * 第一批之前（退回 fallback）或之后（保留已到的部分并告知不完整）。
     */
    fun complete(current: LocalStorageState): LocalStorageState =
        current.copy(loading = false)

    /**
     * 列举结束。
     *
     * [listed] 为 null 表示进不去（路径非法 / 不存在 / 系统沙箱）。此时**退回
     * [fallback]**（最后一次成功的位置）而不是停在半路：`begin` 已经把路径改成了
     * 请求的那个，停在原地会让面包屑指向一个根本没进去的目录，而列表是空的——
     * 看起来像「这个文件夹是空的」，而事实是「没能打开」。
     *
     * 两个分支都必须清 loading。少清一个，进度条会一直转（失败那条尤其容易漏，
     * 因为它在真机上很难复现）。
     */
    fun settle(
        current: LocalStorageState,
        listed: LocalStorageState?,
        fallback: LocalStorageState,
    ): LocalStorageState {
        val base = listed ?: fallback
        return base.copy(selected = current.selected, loading = false)
    }
}
