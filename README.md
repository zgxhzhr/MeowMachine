# 喵呜机（Meow Machine）

Minecraft **1.20.1 Forge** 模组：内容审核模拟器。玩家用「投稿卷轴」写文本投稿，经过审核
（本地规则 / 人工 / AI 三路）给出结论，结论通过**梗图弹窗**反馈。梗图全部数据包驱动，加表情不用改代码。

- modid：`meow_machine`
- 版本：`0.3.0`
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
     ├─ ② 有人能终审（在线 OP / 名册审核员 / 单人自审）且没开「AI 接管」
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

### 投稿的生命周期 / 多篇投稿

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

语义类交给 AI：`AI_ONLY_RULES = {1, 6, 7}`（主观词 / 机翻 / AIGC 痕迹），与 prompt 一一对应。
编号沿用 MC百科公共编辑规范 1~35，审核意见可直接引条号。

---

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
  "texture": "..."          // 可选；服务端算好路径随结果包下发，客户端据此渲染
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
| 单人自审 | OP 开关 | 作者可终审自己的稿（只看到自己那份待审） |

名册：`config/meow_machine_reviewers.json`（开局由 `FMLCommonSetupEvent` 加载，重启不丢）

## 命令

```
/meow reviewers grant <player> <reviewer|assistant>   OP
/meow reviewers revoke <player>                       OP
/meow reviewers list                                  OP（列名册 + 状态）
/meow selfreview <on|off>                             OP
/meow review list                                     审核员（自审玩家只看自己的）
/meow review open                                     审核员（打开审核界面）
/meow review decide <id前缀> <pass|revise|return>      审核员
/meow review give                                     审核员（发卷轴）
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
│   ├── ReviewerRoster    名册 + OP 判定 + selfReview（落盘 config/）
│   ├── ResultInbox       离线作者的待补发结论
│   ├── DraftStore / DraftSavedData   草稿（世界存档）
│   └── Submission / SubmissionState / Verdict / ReviewFinding / ReviewResult
├── sticker/              梗图定义与注册表（数据包加载）
├── ai/                   LlmConfig / LlmClient / AiReviewer
├── network/              10 个包，不用 MenuType（协议号 3）
├── client/               编辑 / 梗图 / 审核 / 后台四个 Screen
├── item/                 投稿卷轴、审核终端
├── command/MeowCommands  /meow 命令树
└── dev/SelfTest          手写断言器（43 条，jar 里已排除）
```

协议号改动时机：**包结构一变就 +1**（0.1.0=1，0.2.0=2，0.3.0=3），版本不一致的客户端会被 Forge 挡在连接阶段。

---

## 已知限制 / 未做

1. **离线结论信箱仍是内存态**（只有「裁决瞬间作者不在线 + 服务器立刻重启」才会丢）
2. **AI 未做真实 endpoint 验证**（代码完整、失败路径已加固，但没跑通真实 key）
3. **审核员管理只在命令里**，后台只显示名册人数
4. `helpless` / `kurushi` / `laowu` / `meowmeow` 四张梗图未挂载（启动会 warn）
5. 物品纹理是程序生成的占位图

---

## 仓库

- 本地：`E:\Mod\其它项目\MeowMachine`
- 远端：https://github.com/zgxhzhr/MeowMachine

历史改动（审计修复、每次的 bug 原因与修法）都在 `git log` 里，提交信息写的是「为什么」而不是「改了哪些行」。
