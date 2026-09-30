# NbtGen

网易版 Minecraft **NBT 盒子生成器**（Android 版）。

纯 Kotlin 实现，内置迷你 HTTP 服务 + WebView 界面，无 Python 运行时、无第三方依赖。

## 功能

- 三种模式：**命令盒** / **装备盒** / **村民鱼桶**
- 三级命名（盒子名 / 物品名 / 矿车名），命令列表可折叠
- 导入已有 NBT 自动识别并反向解析编辑
- 物品 ID 中文对照 + 可搜索选择器，附魔 ID 对照
- 日志系统（文件 + 网页查看 + 导出）

## 构建

```bash
./gradlew assembleDebug        # 生成 app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # 运行单元测试（与 Python 版对拍）
./build_pkg.sh                 # 打包并归档到 包发布/（自动递增版本号）
```

需要 Android SDK（`local.properties` 里的 `sdk.dir`）和 JDK 17+。

## 工程结构

```
app/src/main/
├─ assets/index.html         网页 UI
├─ assets/物品ID清单.txt      物品 ID 表（ID 空格 中文名）
└─ java/cn/nbtgen/app/
    ├─ Snbt.kt               SNBT 类型系统（b/s/f/d 序列化）
    ├─ NbtBuilder.kt         三种模式生成
    ├─ SnbtParser.kt         递归下降解析 + 导入
    ├─ MiniHttpServer.kt     内置 HTTP 服务（127.0.0.1）
    └─ MainActivity.kt       WebView 壳 + JS 桥
```

## 说明

- 服务器仅监听 `127.0.0.1`，WebView 加载 `http://127.0.0.1:<随机端口>/`
- 下载/复制通过 `AndroidBridge` JS 桥，文件保存到
  `/sdcard/Android/data/cn.nbtgen.app/files/nbt/`
- 日志：`/sdcard/Android/data/cn.nbtgen.app/files/nbt_app.log`

## License

仅供学习交流使用。
