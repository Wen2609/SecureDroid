# 安卫安全助手(SecureDroid)

一款开源的 Android 安全应用,从零构建,使用 Kotlin + Jetpack
(Material 3 / MVVM / Room)开发。

> **开源许可:本项目基于 [Apache License 2.0](LICENSE) 开源。**
> 欢迎学习、修改与贡献;二次分发请保留版权与许可声明,商用需遵守许可证条款。

## 功能特性

| 模块 | 说明 |
| --- | --- |
| 病毒扫描 | 计算已安装 APK 的 SHA-256,与本地特征库精确比对;结合敏感权限权重输出风险评分;扫描结果落库(Room) |
| 权限审计 | 枚举全部应用,按敏感权限(短信、通讯录、定位、麦克风、安装包等)权重累加评分,分级展示 |
| 实时防护 | 前台服务监听应用安装 / 更新广播,新应用自动扫描,命中特征时发送高优先级告警通知;开机自启 |
| 应用锁 | 加密存储 PIN(仅存加盐 SHA-256);无障碍服务检测前台应用,受保护应用启动时弹出 PIN 锁屏 |

## 技术栈

- Kotlin 1.9.24 / AGP 8.5.2 / Gradle 8.7 / JDK 17
- minSdk 26,targetSdk 34
- ViewBinding + Material 3,MVVM(LiveData + Coroutines)
- Room(扫描历史)、EncryptedSharedPreferences(PIN 与锁定列表)
- KSP(Room 编译器)

## 工程结构

    SecureDroid/
    ├─ app/src/main/java/com/armorlab/securedroid/
    │  ├─ SecureGuardApp.kt          # Application:通知渠道初始化
    │  ├─ MainActivity.kt            # 底部导航宿主
    │  ├─ data/                      # Room 实体 / DAO / 数据库
    │  ├─ scan/                      # 扫描引擎、特征库、扫描结果适配器
    │  ├─ permissions/               # 权限风险审计
    │  ├─ realtime/                  # 实时防护前台服务、开机自启
    │  ├─ lock/                      # 应用锁:安全存储、无障碍服务、锁屏
    │  └─ ui/                        # 仪表盘 / 扫描 / 审计 / 应用锁页面
    ├─ app/src/main/res/             # 布局、字符串、图标、无障碍配置
    ├─ app/build.gradle.kts
    └─ LICENSE                       # Apache-2.0 开源许可

## 构建步骤

1. 安装 Android Studio(建议 Ladybug 或更新)与 JDK 17;
2. 用 Android Studio 打开本目录,等待 Gradle Sync 完成;
3. 若命令行构建需先生成 Wrapper(仓库不含二进制 jar):

       gradle wrapper --gradle-version 8.7
       ./gradlew assembleDebug

4. 产物位于 app/build/outputs/apk/debug/app-debug.apk。

## 发布版签名与混淆

- release 构建已启用 R8 混淆与资源压缩,规则见 app/proguard-rules.pro;
- 正式发布前在 local.properties 或 CI 变量中配置签名密钥(keystore 不入库)。

## 注意事项

- QUERY_ALL_PACKAGES:该权限不允许上架 Google Play(私有分发 / 企业侧载不受限),
  如需上架需改为 <queries> 白名单;
- 应用锁依赖无障碍服务,首次使用需引导用户在系统设置中开启;
- 特征库:内置演示特征仅用于自检。可在应用私有目录放置 signatures.txt
  (格式:hash|名称|级别0-3|描述)进行本地扩充;真实产品应接入
  自有云端特征下发通道(带签名校验),此处未包含以避免公开攻击面;
- 解锁后 60 秒内再次进入受保护应用不再要求 PIN(简单宽限窗口,
  生产版建议改为"灭屏即失效")。

## 后续路线建议

- 云端特征库与增量更新(签名校验 + Certificate Pinning);
- APK 静态深度检测(DEX 指令特征、嵌入子 APK、证书链异常);
- 网络流量与 DNS 防护(VpnService);
- 反钓鱼短信 / 骚扰拦截模块;
- Crash 与安全事件上报(私有后端)。
