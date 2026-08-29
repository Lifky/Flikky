package com.example.flikky.server.routes

import com.example.flikky.server.dto.StorageListDto
import java.io.File

/**
 * 存储访问的结果，四态与 spec 4.4 的错误表 1:1 对应。
 *
 * 不用 `T?` 加异常：null 无法区分「路径非法」「不存在」「系统限制」，而这三者在前端要给
 * 不同的引导文案。把区分做成类型，调用方就不可能忘记处理其中一种。
 */
sealed interface StorageResult<out T> {
    data class Ok<T>(val value: T) : StorageResult<T>
    data object InvalidPath : StorageResult<Nothing>
    data object NotFound : StorageResult<Nothing>
    data object Restricted : StorageResult<Nothing>
}

/** 下载一个文件所需的全部信息。没有消费者的字段就是负担，故只有这三个。 */
data class StorageFileHandle(val file: File, val fileName: String, val mime: String)

/**
 * 共享存储的只读视图。
 *
 * 只认相对路径与自有 DTO，**不认 `Context`、不认 `Uri`** —— 红线「不把 Android Context
 * 穿透到 server/ 包」。实现在 `data/SharedStorageBrowser`，由 `di/ServiceLocator` 装配。
 */
interface StorageBrowser {
    fun list(relative: String): StorageResult<StorageListDto>
    fun open(relative: String): StorageResult<StorageFileHandle>
}
