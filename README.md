# 中国象棋 · Chinese Chess

**Minecraft 1.21.1 / NeoForge 21.1.259** 模组：在世界里放下一副 2×2 的中国象棋棋盘，两名玩家坐下来下完一整局。

## 合成与放置

- **合成**：第一排 `红染料 / 木制按钮 / 黑染料`，第二排 `任意木台阶 ×3` → 棋盘 ×1
- **放置**：放在任意完整方块上（四块都要有完整上表面），按放置者朝向铺开 2×2

## 玩法

- **右击棋盘**入座：先到者执红，后到者执黑，第三人旁观（可配置）
- 点自己的棋子 → 点目标交叉点走子；`重置` 清空双方棋盘；`认输` 结束对局
- **ESC** 退出界面但保留棋局（再坐下可续弈）；**胜负已判 5 秒后**自动退出并复位
- **空闲 3 分钟**无人对局自动复位（按真实时间计，世界暂停也算）；**破坏棋盘**立即复位

## 规则

走子按中国象棋竞赛规则：蹩马腿、塞象眼、炮翻山、飞将（将帅对脸）、过河兵可横走、兵卒不得后退。

判定：将死、困毙（判负，与国象不同）、长将判负、三次重复（和）、自然限着 120 半回合（和）、双方无取胜可能（和）。

**未实现**：长捉、长杀（目前按三次重复判和）。

## 配置

`config/chinese_chess-common.toml`，常用项：

| 键 | 含义 |
|---|---|
| `autoCloseSeconds` | 胜负已判后结果停留秒数（默认 5） |
| `idleTimeoutSeconds` | 无人对局后复位棋盘的秒数（默认 180） |
| `allowSpectators` | 允许旁观 |
| `protectDuringMatch` | 对局中保护棋盘不被破坏 |
| `maxMatchesPerWorld` | 每个维度最多记录棋盘数 |
| `renderSyncIntervalTicks` | 棋盘状态自检间隔 |

客户端显示项：`rotateBoardForBlack`、`highlightLegalMoves`、`showLastMove`、`renderBoardInWorld`。

## 构建

需要 **JDK 21**：

```bat
set JAVA_HOME=C:\path\to\jdk-21
gradlew.bat build          :: 产物 build/libs/chinese_chess-1.0.0.jar
gradlew.bat runClient      :: 开发客户端
gradlew.bat runServer      :: 开发服务端（--nogui）
```

离线构建：把 NeoForge 21.1.259 的依赖放进 `libs/maven`（该镜像约 8.9 MB，未随仓库提供）后加 `--offline`。

## 工具（`tools/`）

| 工具 | 用途 |
|---|---|
| `perft/Perft.java` | 离线验证走子引擎（对照公开 perft 计数）；`moves "<FEN>"` 列出某局面全部合法着法 |
| `preview_gui.py` / `preview_selection.py` | 不启动游戏直接渲染界面预览 |
| `read_board_screenshot.py` | 从界面截图读出局面并转成 FEN |
| `generate_textures.py` | 生成字形图集、棋盘与物品贴图 |

## 工程结构

| 包 | 职责 |
|---|---|
| `board/` | 纯规则引擎（不依赖 Minecraft） |
| `match/` | 服务端权威对局层、`SavedData` 持久化、巡检自愈 |
| `block/` `blockentity/` | 棋盘方块、渲染状态与三维绘制 |
| `entity/` | 棋子实体（与棋盘状态对账） |
| `network/` | 客户端与服务端之间的负载 |
| `client/` | 棋盘界面、棋子与棋盘渲染器 |
| `mixin/` | 全局方块实体通道的距离剔除 |

## 已知限制

- 长捉 / 长杀未实现（判和）
- 无 AI、无计时器、无记谱导入导出

## 许可

MIT，见 [LICENSE](LICENSE)。
