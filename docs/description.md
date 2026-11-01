# Meow Machine

**A Minecraft mod that turns wiki writing into a review workflow you can play.**

Meow Machine recreates the experience of submitting to a community encyclopedia — inside Minecraft. Players write wiki-style entries on a Submission Scroll using a built-in rich-text editor, submit them, and watch them pass through a real review pipeline. Every verdict comes back as a collectible sticker, complete with a short in-game story.

## Three kinds of review, one verdict

An entry can be judged three different ways, in the same order a real submission site would use:

* **Deterministic rules** — 13 local checks catch the mechanical problems instantly: missing sentence punctuation, stacked punctuation, filler text, Markdown that leaked in from a paste, flattened tables and lists, broken heading structure, duplicated paragraphs.
* **AI review** — an OpenAI-compatible model (DeepSeek by default) reads the entry against the full 35-rule editorial standard, writes a human-readable note, and can even pick the sticker that fits the mood.
* **Human review** — designated reviewers on the server's roster open the queue, read the entry, see AI opinions and local pre-checks side by side, and make the final call with Approve / Revise / Reject.

Servers decide the balance: with a reviewer online, submissions wait for a person; with AI takeover enabled, the model rules directly; with nobody available, the deterministic rules and AI fall back gracefully. The AI never blocks the flow, and AI failure is never treated as approval.

## Writing that actually looks like a wiki

The editor is not a plain text box. Bold, italic, unordered and ordered lists, and tables are real structures — inserted with a toolbar and serialized to the same HTML the encyclopedia itself uses. Headings stay as typed `[hN=...]` tokens, exactly as editors write them by hand. Tables can be sized row by row and column by column, with an optional header row.

## Stickers, not error codes

Verdicts are delivered as stickers: 23 of them, each with its own in-game short story and a matching item. Get one for finishing a review, one for a typo-free short entry, one for a spectacularly broken layout. Collect eight different kinds and a hidden advancement unlocks. Sticker definitions live in data packs, so a pack can add its own without touching code.

## Approved entries are alive

Approved entries are not frozen. Any player can browse the approved list, read an entry, and submit an edit through review again — with the original author's credit preserved. Reviewers see a change summary (word delta, removed lines), and an edit that guts the article is flagged as "worse than before" instead of quietly passing.

## Extras

* **10+ advancements** covering submissions, decisions, revisions and sticker collecting.
* **Mystery crafting** — combine an approved sticker with any item to draw a random item that already exists as a crafting result in your world; the odds favour vanilla items and the result stays hidden until you open it.
* **Roles that mean something** — OP, reviewer and assistant have distinct powers; an assistant can read and comment but never make the final call, even if they are an operator.
* **Per-save AI configuration** — API key, endpoint, model and timeout are stored inside the world save, never sent to clients, and never printed to logs or chat.

## Requirements

Minecraft 1.20.1 · Forge 47.x · Java 17.
Works on a single player world with no setup; on a server, refine the reviewer roster with `/meow reviewers`.

---
# 喵呜机

**把百科投稿做成能在 Minecraft 里玩的一套流程的模组。**

喵呜机把「向社区百科投稿」这件事搬进了 Minecraft。玩家用投稿卷轴和内置富文本编辑器写好一条百科式条目，提交后会走一遍真正的审核流程，结论以**梗图**的形式发回来——每张梗图都配一段游戏内小故事。

## 三路审核，一个结论

一条条目可能被三种方式判：

* **确定性规则** —— 13 条本地检查立刻拦下机械性错误：句末缺标点、标点堆叠、凑字数、从网页粘贴带进来的 Markdown、被压平的表格与列表、标题层级错乱、整段重复。
* **AI 审核** —— 接 OpenAI 兼容接口（默认 DeepSeek），按完整的 35 条编辑规范读稿，写一句人话附言，还能顺手挑一张贴合情绪的梗图。
* **人工审核** —— 名册里的审核员打开队列，正文、AI 意见、本地预检并排看，最后用「通过 / 协改 / 打回」拍板。

服务器可以自己决定重心：有审核员在线就等人判；开了「AI 接管」就由模型直接裁决；一个人都没有时，本地规则与 AI 会依次兜底。AI 永远不阻塞流程，AI 失败也绝不会被当成通过。

## 写得像百科，而不是像聊天记录

编辑器不是纯文本框：粗体、斜体、无序列表、有序列表、表格都是**真结构**，用工具栏插入，导出的 HTML 与站点自己用的一致。标题则是照编辑习惯手打的 `[hN=...]` 标记。表格可以自选行列，还能开关首行表头。

## 反馈是梗图，不是错误码

结论以梗图送达：共 23 张，每张配一段游戏内小故事和一件对应物品。审完一篇有、干净短稿有、排版崩到离谱也有。集齐 8 种会解锁一个隐藏成就。梗图本身由数据包定义，整合包加图不用改代码。

## 已通过的条目是活的

通过不等于冻结。任何玩家都能打开已通过列表、读正文、再提交一次修改重新走审核，**原作者署名会保留**。审核员能看到字数变化与被删的行；把条目改残的编辑会被标成「越改越倒退」，而不是悄悄过审。

## 其它

* **10+ 个成就**，覆盖投稿、裁决、修改与梗图收集。
* **神秘合成**：用一张「已通过」系列梗图加任意物品，抽出当前世界配方表里已有的随机物品；概率偏向原版物品，结果在你打开之前保持未知。
* **身份真的有用**：OP、审核员、助理权限各不相同；助理只能看与写意见，**即使是 OP 也不能终裁**。
* **AI 配置按存档保存**：密钥、接口、模型、超时都存在世界存档里，从不下发客户端，也不会出现在日志或聊天栏。

## 环境要求

Minecraft 1.20.1 · Forge 47.x · Java 17。
单人存档开箱即用；服务器上用 `/meow reviewers` 维护审核名册。
