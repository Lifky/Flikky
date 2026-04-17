# 与 Claude Code + superpowers 协作的经验

## 整体工作流

`brainstorming → writing-plans → subagent-driven-development`

- **brainstorming** 产出 `docs/superpowers/specs/YYYY-MM-DD-<topic>-design.md`（设计文档）
- **writing-plans** 产出 `docs/superpowers/plans/YYYY-MM-DD-<feature>.md`（33 个任务 + 完整代码示例 + 命令）
- **subagent-driven-development** 按 plan 派 subagent 实施，review，提交

## 什么时候 subagent 值得，什么时候不值得

**值得：**
- TDD 任务（M2 Core Logic）：subagent 按"写测试 → 跑 → 实现 → 跑 → commit" 的节奏严格执行，比我自己更容易漏步骤
- 含判断的整合任务（M3 服务器）：subagent 遇到 Ktor API 版本差异会自行适配，报告"这里和 plan 不一样，我改成了 X，原因 Y"
- Plan 已经把代码贴得非常具体的任务：subagent 就是做"体力活"，不消耗我的上下文

**不值得：**
- 纯机械拷贝（M1 Scaffolding 5 个文件）：直接写更快
- 简单 UI 布局（M5/M6）：一次性写完 7 个文件，subagent 的调度开销反而更大
- 遇到 API 限流后（M3c 卡住），与其重新派再冒风险，直接写省心

**结论：** "TDD + 设计判断" 值得派 subagent；"机械填代码" 直接写。

## 让 subagent 工作得更好

1. **不要让 subagent 读 plan 文件** —— 把该任务的完整文本（含代码、命令、预期输出）直接贴在 prompt 里。subagent 的 context 独立于我，它读不到我的对话历史
2. **每批 ≤3 个任务** —— M3 第一批 3 个任务就已经用了 56k token，再加一批触发限流
3. **把已知的坑提前告诉它** —— 例如 JAVA_HOME 的绕法、Ktor 3.x 的 API 差异。否则它会踩一遍再自己修，浪费 token
4. **要求它报告任何偏离** —— "Report any Ktor API deviations + what you changed"；这样我能快速判断是否接受这些修改
5. **给它退出口** —— "If you can't figure out in one retry, stop and escalate"；避免它越陷越深

## 处理 subagent 结果的原则

- **Trust but verify** —— subagent 说"20 个测试全过"要自己 `git log` + 读一两个关键文件确认
- **Spec 偏离要判断是 bug 还是 feature** —— 例如 PinAuth 锁定期间计数问题，subagent 的修复比我原 spec 更严密，接受
- **不要为了"走完流程"派 review subagent** —— 纯拷贝任务 review 是浪费；有判断的任务才 review

## 计划文档的写作心得

- **每个任务给完整代码** —— 不写 `// 实现同前` 或 `// add error handling`
- **给具体命令 + 期望输出** —— `./gradlew testDebugUnitTest --tests "X" → expect PASS`
- **每步 commit，消息用英文** —— 自解释；未来回看历史能快速定位
- **列出已知风险** —— 让 subagent 遇到时能识别而不是惊慌

## 下一次我会改的几件事

1. **plan 阶段加"真机跑一遍"的迭代点** —— 不要等 M7 才装机，M3 末尾就应该有一次端到端验证。这次是 CSS/JS 没加载一路埋到 rc1 才发现
2. **subagent 子批更小** —— M3 拆到每批 2 个任务可能更稳
3. **commit 只用精确路径 add** —— 永远不用 `-A`
4. **设计文档里对平台 API 假设要标注"待验证"** —— 比如 `staticResources` 能否读 Android assets，不验证不写进 plan
