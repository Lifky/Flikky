package com.example.flikky.server.dto

import kotlinx.serialization.json.Json

/**
 * 所有推给浏览器的 WebSocket payload 都必须用这个实例序列化。
 *
 * `encodeDefaults = true` 不是可选项，是正确性要求。kotlinx 的默认行为
 * （`Json` 伴生对象，`encodeDefaults = false`）会把**值等于声明默认值的字段整个从
 * JSON 里删掉**。而浏览器端多处用 `hasOwnProperty` 判断「这次推送有没有带这个字段」，
 * 字段缺席被读作「没变化，保留旧值」—— 于是「把某项改回默认值」这个方向永远同步不了。
 *
 * v1.19.0 实测到的四条（均为 PeerInfoDto 字段，用户报告）：
 *   - `sessionTimestampEnabled = false`：开得了，关不了
 *   - `messageActionStyle = "INLINE"`：切到 FLOATING 行，切回来不行
 *   - `allowPeerRecall = true`：关得了，重新开不了（方向相反，正因为默认值是 true）
 *   - `favoriteEnabled = false`：开得了，关不了
 * 同类第五条 `animationSpeed = "STANDARD"` 当时没被测到，成因完全相同。
 * 而 `recallEnabled` 没有声明默认值，所以从不缺席 —— 这正是它唯一表现正常的原因。
 *
 * HTTP 那条路（`GET /api/peer-info`，走 ContentNegotiation 的
 * `Json { encodeDefaults = true }`）字段一直是齐的，所以手动刷新页面就能「修好」：
 * 两条通道的编码策略不一致，才是这个缺陷的真身。
 *
 * 顺带消掉一个隐性耦合：`themeDark` / `amoled` / `bubbleCornerRadius` 这些字段之所以
 * 一直看着正常，是因为浏览器强制转换的兜底值**恰好**等于 Kotlin 侧的默认值 ——
 * 两套独立维护的默认值碰巧一致。字段齐发之后，浏览器再也不需要猜。
 */
internal val WireJson: Json = Json { encodeDefaults = true }
