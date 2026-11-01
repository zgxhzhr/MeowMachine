# Meow Machine

**A fan-made Minecraft mod about getting a wiki entry through editorial review — half teaching tool, half game.**

Meow Machine is an unofficial fan work (二次创作) based on **Meow Machine, the administrator of MCMOD (mcmod.cn)**, the Chinese Minecraft encyclopedia. It does not try to recreate the website; it takes the thing MCMOD is known for — its editing standards — and turns that into something you can actually play.

## What kind of mod is it

It is **half tutorial, half gameplay**.

* **The teaching half.** MCMOD's public editing standard is a numbered rule set. In this mod those rules are real, working checks: the ones a program can decide are handled by local rules, the semantic ones are handed to an AI model. Writing an entry and seeing exactly which rule you broke is the lesson.
* **The gameplay half.** Entries are written on an in-game scroll, enter a queue, get reviewed by designated players, and come back as verdicts, stickers, items and achievements. Writing well pays off in a way that goes beyond "the standards say so".

## What is in the mod

**A real editor, not a chat box.** An entry is written on a Submission Scroll with a rich-text editor. Bold, italic, unordered lists, ordered lists and tables are real structures, inserted from a toolbar and serialized to the same HTML the site itself uses. Headings stay as hand-typed `[hN=...]` tokens, because that is how editors really write them. A table can be sized row by row and column by column, with an optional header row.

**Three reviewers, one verdict.** A submitted entry can be judged in three ways: deterministic local rules, an AI reviewer connected to any OpenAI-compatible endpoint (DeepSeek by default), or human reviewers on the server roster. The server decides the balance — wait for a person, let the AI take over, or let rules and AI cover when nobody is around. The AI never blocks the pipeline, and an AI failure is never treated as a pass.

**Verdicts arrive as stickers.** There are 23 stickers, each with its own short in-game story and a matching item. Approved, revise requested, rejected, duplicated content, machine-translated text, flattened tables — every situation has a face. Collect eight different kinds and a hidden advancement unlocks. Sticker definitions are data-driven, so a pack can add its own.

**Approved entries stay editable.** Approved does not mean frozen: anyone can open the approved list, read an entry, and submit an edit that goes through review again, with the original author's credit preserved. Reviewers see a change summary, and an edit that guts the article is flagged as a regression instead of quietly passing.

**Smaller systems that tie it together.** Ten-plus advancements across writing, reviewing and collecting; a mystery crafting recipe that turns an approved sticker plus any item into a random item already craftable in your world; a role system where operators, reviewers and assistants have genuinely different powers; and AI settings stored per save and never sent to clients.

## The loop

Write an entry, submit it, get a verdict as a sticker, keep the sticker as an item, collect a set, unlock advancements — and along the way learn rule by rule why the entry passed or failed. Then come back and edit an approved article, and let the reviewer see exactly what you changed.

## Requirements

Minecraft 1.20.1 · Forge 47.x · Java 17.
Single-player works out of the box; on servers, manage reviewers with `/meow reviewers`.

*This is a fan project. It is not affiliated with MCMOD or with Meow Machine (the person).*

---

# 喵呜机

**一个关于「把条目送进百科的编辑审核」的 Minecraft 二创模组——一半教学，一半游戏。**

《喵呜机》是一个同人二创模组，原型是**中国 Minecraft 百科平台 MCMOD（mcmod.cn）的站长「喵呜机」**。它不去复刻那个网站，而是把 MCMOD 最有辨识度的东西——它的编辑规范——做成一套真的能玩、也真的能学的东西。

## 这是什么类型的模组

**半教学，半游戏。**

* **教学的那一半。** MCMOD 的公共编辑规范是一套带编号的条款。在这个模组里，这些条款是**真的会生效的检查**：能用程序判的交给本地规则，语义类的交给 AI 模型。写一篇稿子、看到自己违反了第几条，这就是教学。
* **游戏的那一半。** 稿子写在游戏内的卷轴上，进队列，由指定的玩家审核，结论以梗图、物品和成就的形式回来。「写得好」有实打实的回报，而不只是「规范这么要求」。

## 模组里有什么

**真正的编辑器，不是聊天框。** 条目写在投稿卷轴上，用一个富文本编辑器来写。粗体、斜体、无序列表、有序列表和表格都是**真结构**，从工具栏插入，序列化出的 HTML 与站点自己用的一致。标题则保持手打的 `[hN=...]` 标记，因为编辑者现实中就是这么写的。表格可以自选行列，还能开关首行表头。

**三路审核，一个结论。** 一条投稿可能被三种方式判：本地确定性规则、接任意 OpenAI 兼容接口的 AI 审核（默认 DeepSeek）、服务器名册里的人工审核员。服务器自己决定重心——等人判、交给 AI 接管，或者一个人都没有时由规则与 AI 依次兜底。AI 永远不阻塞流程，AI 失败也绝不会被当成通过。

**结论以梗图送达。** 共 23 张梗图，每张配一段游戏内小故事和一件对应物品。通过、要求协改、打回、内容重复、机翻痕迹、被压平的表格——每种情况都有对应的表情。集齐 8 种不同梗图会解锁一个隐藏成就。梗图由数据包定义，整合包可以自己加。

**已通过的条目还能改。** 通过不等于冻结：任何人都能打开已通过列表、读正文，再提交一次修改重新走审核，**原作者署名保留**。审核员能看到字数变化与被删的行；把条目改残的编辑会被标成「越改越倒退」，而不是悄悄放行。

**串起这一切的小系统。** 10+ 个成就，覆盖写作、审核与收集；一条神秘合成配方，把「已通过的梗图 + 任意物品」变成当前世界里本来就能合成的随机物品；一套身份系统，让 OP、审核员、助理拥有真正不同的权限；AI 配置按存档保存，从不下发客户端。

## 玩法循环

写一篇稿子 → 提交 → 拿到一张梗图结论 → 梗图变成物品 → 收集 → 解锁成就——同时在这个过程中一条条弄清楚它为什么过、为什么不过。之后再回头改一篇已经通过的条目，让审核员清楚看到你改了什么。

## 环境要求

Minecraft 1.20.1 · Forge 47.x · Java 17。
单人存档开箱即用；服务器上用 `/meow reviewers` 维护审核名册。

*本项目为同人作品，与 MCMOD 及站长本人均无隶属关系。*
