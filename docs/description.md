# Meow Machine

**A fan-made Minecraft mod about passing a wiki's editorial review — half teaching tool, half game.**

Meow Machine (喵呜机) is an unofficial fan work (二次创作) based on **Meow Machine, the administrator of MCMOD (mcmod.cn)** — the Chinese Minecraft encyclopedia. The mod does not try to recreate the website; it takes the thing MCMOD is famous for, its editorial standards, and turns it into something you can actually play.

## What kind of mod is it

It is a **half-tutorial, half-gameplay** mod.

* **The teaching half**: MCMOD's public editing standard is a numbered rule set. In the mod those rules are real, working checks — deterministic ones run in code, semantic ones are handed to an AI model. Writing an entry and seeing exactly which rule you broke is the tutorial.
* **The game half**: entries are written on an in-game scroll, go through a queue, get reviewed by designated players, and come back as verdicts, stickers, items and achievements. There is a reason to write well beyond "the rule says so".

## What's in the mod

**A real editor, not a chat box.** Entries are written on a Submission Scroll with a rich-text editor: bold, italic, unordered and ordered lists, and tables are actual structures inserted from a toolbar, serialized to the same HTML the site itself uses. Headings stay as hand-typed `[hN=...]` tokens, because that is how editors really write them. Tables can be sized row by row and column by column, with an optional header row.

**Three reviewers, one verdict.** A submitted entry can be judged three ways: deterministic local rules, an AI reviewer connected to any OpenAI-compatible endpoint (DeepSeek by default), or human reviewers from the server roster. Servers choose the balance — wait for a person, let the AI take over, or let rules and AI cover when nobody is around. The AI never blocks the pipeline, and an AI failure is never treated as a pass.

**Verdicts arrive as stickers.** There are 23 stickers, each with its own short in-game story and a matching item. Approve, revise, reject, duplicate content, machine translation, flattened tables — every situation has a face. Collect eight kinds and a hidden advancement unlocks. Sticker definitions are data-driven, so a pack can add its own.

**Approved entries stay editable.** Approved does not mean frozen: anyone can browse the approved list and submit an edit that goes through review again, with the original author's credit preserved. Reviewers see a change summary, and an edit that guts the article is flagged as a regression instead of quietly passing.

**Smaller systems that tie it together.** Ten-plus advancements across writing, reviewing and collecting; a mystery crafting recipe that turns an approved sticker plus any item into a random item already craftable in your world; a role system where operators, reviewers and assistants have genuinely different powers; and AI settings stored per save, never sent to clients.

## The loop

Write an entry, submit it, get a verdict as a sticker, keep the sticker as an item, collect a set, unlock advancements — and along the way learn why the entry passed or failed, rule by rule. Then come back and edit an approved article, and watch the reviewer see exactly what you changed.

## Requirements

Minecraft 1.20.1 · Forge 47.x · Java 17.
Single-player works out of the box; on servers, manage reviewers with `/meow reviewers`.

*This is a fan project. MCMOD and Meow Machine (the person) are not affiliated with it.*

---

# 喵呜机

**一个关于「把投稿送去审核」的 Minecraft 二创模组——一半教学，一半游戏。**

喵呜机是**中国 Minecraft 百科平台 MCMOD（mcmod.cn）的站长「喵呜机」**的同人二创模组。它不是去复刻那个网站，而是把 MCMOD 最有辨识度的东西——它的编辑规范——做成了一套真的能玩、也真的能学的东西。

## 这是什么类型的模组

**半教学、半游戏性。**

* **教学那一半**：MCMOD 的公共编辑规范是一套带编号的条款。在这个模组里，这些条款是**真的会生效的检查**——能用代码判的条款由本地规则判，语义类的交给 AI 模型。写一篇稿子、看到自己违反了第几条，这就是教学。
* **游戏那一半**：稿子写在游戏内的卷轴上，进队列，由指定的玩家审核，结论以梗图、物品和成就的形式回来。「写得好」不只是为了合规，而是有实际回报。

## 模组里有什么

**真正的编辑器，不是聊天框。** 投稿卷轴上带一个富文本编辑器：粗体、斜体、无序列表、有序列表、表格都是**真结构**，用工具栏插入，导出的 HTML 与站点自己用的一致。标题则保持手打的 `[hN=...]` 标记，因为编辑者现实中就是这么写的。表格可以自选行列，还能开关首行表头。

**三路审核，一个结论。** 一条投稿可能被三种方式判：本地确定性规则、接任意 OpenAI 兼容接口的 AI 审核（默认 DeepSeek）、服务器名册里的人工审核员。服务器自己决定重心：等人判、交给 AI 接管、或者一个人都没有时由规则与 AI 依次兜底。AI 永远不阻塞流程，AI 失败也绝不会被当成通过。

**结论以梗图送达。** 共 23 张梗图，每张配一段游戏内小故事和一件对应物品。通过、协改、打回、内容重复、机翻痕迹、被压平的表格——每种情况都有对应的表情。集齐 8 种解锁隐藏成就。梗图由数据包定义，整合包可以自己加。

**已通过的条目还能改。** 通过不等于冻结：任何人都能打开已通过列表，读正文，再提交一次修改重新走审核，**原作者署名保留**。审核员能看到字数变化与被删的行；把条目改残的编辑会被标成「越改越倒退」，而不是悄悄放行。

**串起这一切的小系统。** 10+ 个成就覆盖写作、审核与收集；一条神秘合成配方把「已通过的梗图 + 任意物品」变成当前世界里本来就能合成的随机物品；身份系统让 OP、审核员、助理拥有真正不同的权限；AI 配置按存档保存，从不下发客户端。

## 玩法循环

写一篇稿子 → 提交 → 拿到一张梗图结论 → 梗图变成物品 → 收集 → 解锁成就——同时在这个过程中一条条弄清楚它为什么过、为什么不过。之后再回头去改一篇已经通过的条目，看审核员清楚地看到你改了哪里。

## 环境要求

Minecraft 1.20.1 · Forge 47.x · Java 17。
单人存档开箱即用；服务器上用 `/meow reviewers` 维护审核名册。

*本项目为同人作品，与 MCMOD 及站长本人无隶属关系。*
