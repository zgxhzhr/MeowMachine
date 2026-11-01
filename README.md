# 喵呜机（Meow Machine）

Minecraft **1.20.1 Forge** 模组：内容审核模拟器。玩家用「投稿卷轴」写文本投稿，经过审核
（本地规则 / 人工 / AI 三路）给出结论，结论通过**梗图弹窗**反馈。梗图全部数据包驱动，加表情不用改代码。

- modid：`meow_machine`
- 版本：`0.10.1`（**开发测试阶段**：只走 0.x.y，修 bug 加末位；`1.0.0` 保留给正式发布时再升）
- 状态：可构建、可运行，`gradlew runServer` 冒烟测试通过

---

## 构建与自测

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
$env:GRADLE_USER_HOME='E:\gradle'
cd 'E:\Mod\其它项目\MeowMachine'
.\gradlew.bat build --offline          # 产物 build/libs/meow_machine-<ver>.jar

# 纯逻辑自测（不需要游戏）
& "$env:JAVA_HOME\bin\java.exe" "-Dfile.encoding=UTF-8" `
  -cp "build\classes\java\main" io.github.zgxhzhr.meowmachine.dev.SelfTest
```

> `-Dfile.encoding=UTF-8` **必须整体加引号**，否则 PowerShell 会拆成两个参数（报 `ClassNotFoundException`）。

**装到实例**：把 jar 拷进 `versions/<版本>/mods/`，**先删掉旧版本 jar**——两个同 modid 的 jar 会启动失败。

### 构建踩过的坑

1. `plugins.gradle.org` 会 303 重定向导致 gradle 失败 → 阿里云镜像放最前（`settings.gradle`）
2. Windows javac 默认 GBK → `options.encoding='UTF-8'` + `filteringCharset`
3. `mods.toml` 的 `${...}` 占位符必须在 `processResources` 里 `expand`，否则启动报 `corrupt or misconfigured toml`

### 开发端起服务端的已知坑

`gradlew runServer` 会在启动完成后崩在
`Attempted to load class net/minecraft/client/server/LanServerPinger for invalid dist DEDICATED_SERVER`。
这是 Forge 自己的 `DedicatedServer.initServer` 在 `advertiseDedicatedServerToLan=true` 时去 new 客户端类，
**与本 mod 无关**。把 `run/<世界>/serverconfig/forge-server.toml` 里该项改成 `false` 即可干净启动；
客户端开集成服务器不受影响。

---

## 审核流程

```
投稿
 ├─ ① 本地规则命中？→ 自动打回（附条号）
 └─ 否
     ├─ ② 有人能终审（在线 OP / 名册审核员）且没开「AI 接管」
     │    → 入队，返回 PENDING（等人工，不是「需要协改」）
     └─ 否
         ├─ ③ AI 可用？
         │    ├─ 可信结论、无违规 → 通过
         │    ├─ 可信结论、有违规 → 打回（附 AI 发现）
         │    └─ **AI 不可用**（网络/超时/解析失败）→ ④ 兜底打回
         └─ ④ 兜底打回（梗图 = fallback「要不直接打回算了」）
```

两条硬线：
- **AI 绝不阻塞**：异步 + 超时即放弃，流程必有确定结果。
- **AI 失败 ≠ 通过**：`AiReviewer` 用 `AiOutcome(available, findings)` 区分「没发现问题」与「没拿到结论」，
  后者走兜底打回，绝不自动过审。

挑梗图优先级：**规则专属图** → **结论状态图**（approved / revising / returned / submitted）→ **兜底图**。
规则图必须语义对得上（曾把「协改中」挂在第 12 条句末标点上，导致「被打回却显示协改中」）。

### 投稿端能看到的入口

- 编辑界面底部四颗按钮：新建投稿 / 提交审核 / **我的投稿** / 关闭
- 「我的投稿」列出当前草稿与人工队列里自己的稿子（短 id、标题、状态：草稿/待审/已通过/被打回）
- 命令 `/meow admin                                               OP（打开完整审核后台）
/meow my` 是同一份信息的聊天栏版本
- 正文是**富文本编辑器**（见下节），不再只支持纯文本


## 成就（0.17.0）

一条主线 + 两个分支，全部挂在 `meow_machine:root`（拿到投稿卷轴即点亮）：

| 成就 | 触发 |
|---|---|
| 喵呜机（根） | 背包里出现投稿卷轴 |
| 初次投稿 | 第一次提交 |
| 过审了（目标） | 第一次通过审核 |
| 打回也是一种成长 | 第一次被打回 |
| 百炼成钢（挑战） | 被打回后改到通过 |
| 火眼金睛（目标） | 第一次终裁别人的投稿 |
| 好为人师 | 写下第一条审核意见 |
| 审核员上任（目标） | 被任命为审核员 / 助理 |
| 梗图收藏家（挑战） | 收集 8 张不同梗图 |

### 梗图物品（0.18.0）

18 张梗图每张都是一个物品（贴图直接用 `textures/sticker/<id>.png`）。**收到审核结果时，作者会拿到结果对应的那张梗图物品**；
悬浮物品能看到一句该梗图的游戏内小故事（`item.meow_machine.sticker_<id>.story`）。
已收集的种类记在玩家持久数据里，凑够 8 种发「梗图收藏家」。

除根节点用 `inventory_changed` 自动触发外，其余都是 `minecraft:impossible` + 业务代码授奖。

## 物品获取与权限分级（0.15.0）

| 物品 | 获取 | 用途 |
|---|---|---|
| 投稿卷轴 | **1 张纸无序合成** | 右键打开投稿编辑器（草稿/新稿） |
| 审核终端 | 有审核权限的人**首次登录自动发一个**（另见 `/meow` 命令） | 右键按权限打开不同后台 |

终端按权限分档（服务端校验）：

| 身份 | 能做什么 |
|---|---|
| OP | 完整后台：AI 设置、审核员管理、待审列表、裁决 |
| 审核员 | 待审列表 + 裁决（通过 / 协改 / 打回）+ 写意见 |
| 助理 | 待审列表 + 查看 + **写意见**，不能终裁 |
| 其他人 | 无权限提示（不发终端） |

**审核意见**：助理（和审核员）都能在详情页「写意见」，每篇最多 6 条、每条 200 字；
终裁时这些意见会并入给作者的结果说明里，然后清空。

审核员管理不再手打名字：下半屏列出**在线玩家**，点「审核员 / 助理」直接任命；名册在上半屏，可撤销。

## 我的投稿：历史 + 点回编辑（0.13.0 / 0.14.0）

已通过 / 已打回的稿子现在留在**历史**里（世界存档 `meow_machine_history.dat`），
列表里能看到状态；**每行都有「查看」**（任何状态都能读正文），打回的还能点「编辑」拉回来改，
通过的是只读。


「我的投稿」每行有「编辑」：当前草稿直接打开；还在人工队列里的会先**撤回**再打开
（作者要改，就不该同时让审核员审一份要变的稿子）。底部还加了「待审列表」入口，
审核员不用退回命令也能进列表。

## 正文富文本编辑器（0.9.0）

正文按原生 MC百科编辑器的**输入层**实现：编辑者能做出来的只有五种操作，标题靠手打 `[hN=标题]` 文字。

| 工具栏 | 行为 |
|---|---|
| `B` | 粗体：有选区作用于选区；无选区切换「悬挂样式」，之后输入的字带上 |
| `I` | 斜体，同上 |
| `•` | 无序列表：光标段落 ↔ 列表互转，多段选中整段切换，再点一次撤销 |
| `1.` | 有序列表，同上 |
| `⊞` | 插入 2×3 表格（首行表头），插入后光标进第一格 |

键盘：`Enter` 拆段 / 列表内新建项（空项回车退出列表）/ 表格内换行；`Tab`/`Shift+Tab` 表格跳格；`Ctrl+B/I`、`Ctrl+Z` 撤销、`Ctrl+A` 全选、`Ctrl+C` 复制、`Ctrl+V` 粘贴（粘贴会剥掉外部标签）；方向键移动、`Shift+方向键` 扩选、鼠标拖选。

存储：正文以站点同款 HTML 保存（`<p>`、`<strong>`、`<em>`、`<ul>/<ol><li><p>`、`<table><th>/<td valign="top">`），
提交时服务端先 `stripTags` 再交给规则/AI，所以第 26 条的标题结构检查仍然看得到 `[hN=]`。

## 投稿的生命周期 / 多篇投稿

- 草稿是「每个玩家当前那一篇」，存在**世界存档**里（`<世界>/data/meow_machine_drafts.dat`）
- 提交后：本地规则/AI 直接出结论；走人工则入队（队列也落盘，重启不丢）
- 再开卷轴：可编辑（DRAFT / SUBMITTED / RETURNED）就打开当前这篇；**已通过则自动开一份新草稿**
- 想主动换一篇：编辑界面左下角「新建投稿」；旧稿若还在队列里会继续被审（结论按 id 匹配）

---

## 本地规则（`LocalRules`）

| 条 | 检查项 |
|---|---|
| 3 | emoji / 颜文字（符号类 + `T_T`/`ToT`；不误伤「（Open）」） |
| 12 | 句末缺标点 |
| 18 | 正文太短（灌水） |
| 19 | 标点堆叠（`。。。`） |
| 22 | 字符拼的分割线 |
| 23 | 提了版本但没写具体版本号 |
| 24 | `PS.` / `注：` 前缀 |
| 29 | 冗余句（「欢迎补充」等） |
| 31 | 删除线 |
| 10 | 重复内容（整段逐字相同、≥20 字、≥6 汉字） |
| 22 | Markdown 残留（## / **粗体** / 手打 - 列表 / |表格| / ``` 围栏） |
| 26 | 标题结构（层级不能跳；正文 ≥200 字却没有 [h1=] 等结构也算） |
| 26 | 百科标题结构：用了 `[hN=]` 就必须从 `[h1=]` 开始、不许跨级（0.6.0） |

语义类交给 AI：`AI_ONLY_RULES = {1, 6, 7, 18}`（主观词 / 机翻 / AIGC 痕迹 / 内容不实或乱码），与 prompt 一一对应。
编号沿用 MC百科公共编辑规范 1~35，审核意见可直接引条号。

**方括号标记不会被误判**：`[h1=概述]`、`[ban:title_menu]`、`[icon:health=20,点]` 是站点语法，
本地规则不把它们当乱码（第 12 条的句末标点白名单包含 `]`），AI prompt 里也专门写了一段
「百科语法常识」告诉模型不要按第 18/7 条判这些标记。真正会被判的是**结构错**：
标题从 `[h2=]` 开始、`[h1=]` 直接跳到 `[h3=]`，走本地第 26 条。

---

## 接管模式：AI 优先于本地规则（0.12.0）

开「AI 接管」后，流程变成 **AI 先判、本地规则只当线索**：

- 本地规则命中的条款会作为「疑点」交给 AI，它可以确认、纠正或忽略
- AI 的结论直接生效（通过 / 打回），并可以写一句**附言**（`note`），作者在弹窗里看得到
- AI 返回可选 `sticker` 指定梗图；「通过」只接受 `approved_` 系列，情绪化的打回图不会挂到通过上
- AI 不可用时才退回本地规则（有命中按本地打回，否则兜底打回）
- **确定性格式错误（第 22 条 Markdown 残留、第 26 条标题结构）不让 AI 放行**：
  实测模型会把 `## 核心玩法` / 手打 `- 列表` 判成「格式没有明显问题」，这两条本地必检、
  接管时也会并进结论（AI 仍负责其余条款、附言与选图）

四张原本没有触发条件的图（`helpless`/`kurushi`/`laowu`/`meowmeow`）现在也进了 AI 的目录，
标注「仅 AI 可选」——打回时按情绪挑（无奈 / 难受 / 崩溃求救 / 卖萌敷衍）。

## 梗图：AI 自己选图（0.11.0）

判定交给 AI 时，prompt 里会带上**梗图目录**（每张图一行：id / 触发条件 / `desc` 一句话说明），
模型可以在输出 JSON 里加 `"sticker": "<id>"` 指定用哪张图；服务端校验该 id 存在才采用，
否则退回「按规则号 → 状态图 → 兜底图」的本地顺序。

用途：四张「通过」系列不再随机——有进步用 `approved_progress`、简短合规用 `approved_short`、
有小瑕疵用 `approved_fix`；打回时也能挑更贴切的那张（灌水 / AI 味 / 机翻 / 样式崩）。

## 梗图（数据包驱动，零硬编码）

```
assets/meow_machine/textures/sticker/<id>.png     # 建议 512×512
data/meow_machine/stickers/<id>.json
```

```json
{
  "state": "approved",      // 或
  "rule": 18,               // 或
  "fallback": true,
  "priority": 10,
  "texture": "...",         // 可选；服务端算好路径随结果包下发，客户端据此渲染
  "desc": "又是灌水"         // 可选；给 AI 选图时看的一句话说明
}
```

- 同状态多张时随机取（4 张「通过」不会总出同一张）
- 加载时会对「没有 state/rule/fallback 的图」打 warn（这类图永远不会被选中）
- 加新表情：丢 png + json，**不用改 Java**

---

## 权限

| 角色 | 获取 | 能力 |
|---|---|---|
| OP | 服务器管理员 | 天然可终审；开审核后台；任命/撤销审核员 |
| 审核员 | OP 任命 | 可终审（PASS/REVISE/RETURN） |
| 审核助理 | OP 任命 | 只能标记，不能终审 |

名册：`config/meow_machine_reviewers.json`（开局由 `FMLCommonSetupEvent` 加载，重启不丢）
名册条目录的是 **uuid + 玩家名 + 角色**（旧格式只存 uuid→角色的也能读，界面会显示短 uuid）。

### 审核员管理界面

审核终端 → 右键 → 后台 → **「审核员管理」**：

- 列出名册（名字 + 角色），每行一个「撤销」
- 输入**在线玩家名** + 「设为审核员 / 设为助理」即可任命（服务端按名字解析，离线会提示）
- 名单超过一屏时会显示「还有 N 人未显示」，用命令处理

### 审核列表看得到正文（0.10.0）

待审列表每行多了「查看」按钮，进入详情页能看到**投稿正文**（服务端剥好标签的纯文本，可滚动），
并可直接在详情页点「通过 / 协改 / 打回」；「返回列表」会重新拉一次列表。
列表本身只显示短 id / 作者 / 标题——以前审核员在列表里根本读不到内容，只能盲判。

### 裁决原因（0.5.0 起）

审核界面点「打回 / 协改」会先进入原因选择：

- 选**规则号**（1/6/7/12/18/19/22/23/24/29/31）或「说不出依据」
- 可补一句话说明（会显示给作者）
- 规则号决定用哪张梗图（第 18 条 →「又是灌水」），作者那边看到「命中：第 N 条」+ 你的说明

## 中文输入（IMBlocker 兼容）

很多中文玩家装了 **IMBlocker**，它只对「白名单屏幕/认识的文本框」打开输入法，
本模组的自定义界面（尤其自绘正文区）它不认识 → 整个界面中文打不进去。

0.5.0 起 `client/ImeCompat` 用反射接入 IMBlocker 的 `FocusContainer.MINECRAFT.setPreferredState(boolean)`：
打开编辑/后台/名册/裁决界面时主动要输入法，关闭时还回去。**没装 IMBlocker 就是空操作**，不影响原版行为。

## 命令

```
/meow reviewers grant <player> <reviewer|assistant>   OP
/meow reviewers revoke <player>                       OP
/meow reviewers list                                  OP（列名册 + 状态）
/meow review list                                     审核员（自审玩家只看自己的）
/meow review open                                     审核员（打开审核界面）
/meow review decide <id前缀> <pass|revise|return>      审核员
/meow review give                                     审核员（发卷轴）
/meow my                                               任何玩家（看自己投稿状态/待审）
```

---

## AI 配置

默认接 **DeepSeek**（OpenAI 兼容）。游戏内「审核终端 → 右键 → 后台」填 key 即可：

```
接口地址 https://api.deepseek.com/v1
API 密钥 sk-...
模型     deepseek-chat        （推理模型用 deepseek-reasoner）
```

落盘 `config/meow_machine_ai.json`；空值会自动补默认地址/模型。配置有内存缓存，不会每份投稿都读盘。
「AI 接管」开关打开后跳过人工优先，直接走 AI。

**密钥处理**（0.5.0 起）：

- 服务端**从不把 key 下发给客户端**，`AdminStatePacket` 只带「是否已配置」
- 后台输入框永远显示为空、输入时打码；**留空保存 = 不修改**（改地址/模型不用重填 key）
- 磁盘上的 `config/meow_machine_ai.json` 仍是明文（Minecraft 模组的常规做法，权限靠文件系统）；公共服建议用低额度 key

AI 的语义规则集：`1 主观词句` / `6 机翻` / `7 AIGC` / `18 内容不实或凑字数（含乱码、无意义字符、与标题无关）`
—— 与 `LocalRules.AI_ONLY_RULES` 一一对应。第 18 条的「太短」由本地规则量判，语义部分交给 AI。

---

## 代码结构

```
src/main/java/io/github/zgxhzhr/meowmachine/
├── MeowMachine           主类（物品/标签/网络 + 开局加载审核员名册）
├── MeowMachineEvents     梗图重载 / 命令 / 服务端启动恢复队列 / 登录补发离线结论
├── review/               审核域（除 SavedData 外都是纯 Java，可脱机测试）
│   ├── LocalRules        9 条纯代码检查
│   ├── ReviewService     流程编排（本地规则 → 人工 → AI → 兜底）
│   ├── ReviewActions     裁决公共实现（命令与界面共用）
│   ├── SubmissionQueue   人工队列（写穿 QueueSavedData）
│   ├── ReviewerRoster    名册 + OP 判定（落盘 config/）
│   ├── ResultInbox       离线作者的待补发结论（写穿 InboxSavedData）
│   ├── QueueSavedData    队列的世界存档（<世界>/data/meow_machine_queue.dat）
│   ├── InboxSavedData    离线结论的世界存档（<世界>/data/meow_machine_inbox.dat）
│   ├── richtext/         富文本模型 / HTML 解析序列化 / 编辑器内核（纯 Java）
│   ├── DraftStore / DraftSavedData   草稿（世界存档）
│   └── Submission / SubmissionState / Verdict / ReviewFinding / ReviewResult
├── sticker/              梗图定义与注册表（数据包加载）
├── ai/                   LlmConfig / LlmClient / AiReviewer
├── network/              17 个包，不用 MenuType（协议号 12）
├── client/               编辑 / 梗图 / 审核 / 后台 / 审核员管理 五个 Screen
├── item/                 投稿卷轴、审核终端
├── command/MeowCommands  /meow 命令树
└── dev/SelfTest          手写断言器（43 条，jar 里已排除）
```

协议号改动时机：**包结构一变就 +1**（0.1.0=1，0.2.0=2，0.3.0=3，0.4.0=4，0.5.0=5，0.7.0=6，0.9.0=7，0.10.0=8，0.13.0=9，0.14.0=10，0.15.0=11，0.16.0=12），版本不一致的客户端会被 Forge 挡在连接阶段。

---

## 已知限制 / 未做

1. **AI 未做真实 endpoint 验证**（代码完整、失败路径已加固；DeepSeek 默认配置已就绪）
2. **名册界面只能任命「在线玩家」**（GUI 没有实体选择器，按名字解析；离线任命仍走命令）
3. `helpless` / `kurushi` / `laowu` / `meowmeow` 四张梗图未挂载（启动会 warn）
4. 物品纹理是程序生成的占位图
5. 富文本只做了五种（粗/斜/两种列表/表格），表格固定 2×3、不支持合并单元格与字色/上标

---

## 仓库

- 本地：`E:\Mod\其它项目\MeowMachine`
- 远端：https://github.com/zgxhzhr/MeowMachine

历史改动（审计修复、每次的 bug 原因与修法）都在 `git log` 里，提交信息写的是「为什么」而不是「改了哪些行」。
