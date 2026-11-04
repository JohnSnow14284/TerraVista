# Eclipse 的 TV Vulkan 补丁

本分支把工作分成两层：TV 接入 Vitrail；光影接入 TV。OpenGL 对应 Voxy + Iris，Vulkan
对应 TerraVista + Vitrail。Eclipse 原有的 Voxy 着色逻辑是参考实现，运行时不要求 Voxy 或 Iris。

当前核对的源代码：Eclipse `Unstable`，提交 `5636edf`；Vitrail
`7a3cdcbe1dfd7e04fb2b3021ee8352d341e7647e`，0.13.0-beta；Minecraft 26.3、NeoForge
26.3.0.76、Sodium 0.9.3、Java 25。这些是本地验证版本，不代表已经确认 GitHub 最新版本。

## 使用

安装本分支构建的 TV、Vitrail 和 Sodium，在 Vitrail 中选择 Eclipse。
TV 按源码中的输出契约识别 Eclipse，在读取光影时应用适配；不会改写原光影文件。
只有 Vitrail 存在且 Distant Horizons 未安装时启用此路径。

也可以生成单独的 Eclipse-TV Vulkan ZIP。在 TV 仓库根目录执行：

```powershell
.\gradlew.bat exportEclipsePatch `
  '-PeclipseSource=D:\path\Eclipse-Shader' `
  '-PeclipseOutput=D:\output\Eclipse-TV-Vulkan.zip'
```

输入是包含 `shaders` 的原始 Eclipse 目录。输出必须是原目录以外的新 ZIP 路径；工具拒绝覆盖
已有 ZIP。把生成文件放到游戏的 `shaderpacks` 中，再从 Vitrail 选择它。ZIP 保留原包的资源、
许可证和署名，加入补丁说明；它仍然依赖本分支的 TV。仓库仅包含适配代码和生成工具，不内嵌
Eclipse 的完整源代码。原 Eclipse 仓库与 Vitrail 仓库均保持原样。

## 两层契约

| 责任 | TV 接入 Vitrail | Eclipse 接入 TV |
| --- | --- | --- |
| 几何 | 保持 8 字节四边形，复用模型/几何 SSBO 和 GPU 间接命令 | TV 顶点入口读取颜色、面、光照和材质 |
| 材质 | 模型保留稳定方块状态索引，按光影代际刷新材质映射表 | 使用当前 `block.properties` ID 选择 SSS、水、冰等材质行为 |
| 深度 | 提供远景投影、主世界遮挡，以及透明前/后的两份远景深度 | 反射、雾、时域和透明着色改用 TV 名称及对应深度 |
| 阶段 | 借用 Vitrail DISTANT 系列，保留 `dh_terrain` / `dh_water` 调度入口 | 替换这两个入口的着色程序和输出附件 |
| 生命周期 | 开关、缩放、重载、维度切换、缓冲生命周期和失败降级 | 适配缓存随每次打开的光影源实例释放 |

Eclipse 使用 `TERRAVISTA` / `TERRAVISTA_PROGRAM` 条件、`TerraVistaFragmentParameters`
和 `terravista_emitFragment`。完整导出的 ZIP 还把源文件名改为 `tv_opaque.glsl`、
`tv_translucent.glsl`、`tv_solid.glsl`，移除不再使用的 `voxy.json`。
读取时自动适配保留原包物理文件路径，以便其 includes 能继续定位文件；着色器标识符使用 TV 名称。

`tvProjection` / `tvDepthTexOpaque` 等输入指向 Vitrail 的真实矩阵和纹理。
`tvLodProjection` / `tvLodDepthTexOpaque` 是 Eclipse 内部的中间别名，两者保持不同名称，
避免宏自引用。透明前深度对应 `dhDepthTex1`，透明后深度对应 `dhDepthTex`。
`tvNearPlane`、`tvFarPlane`、`tvRenderDistance` 在 properties 中从实际 DH 供应值计算；
GLSL 选择 TV 着色分支，properties 保留 DH 调度分支。此处 DH 名称是宿主现有的传输接口。

不透明输出为 `[1,8]`，水面为 `[16,7,11,14]`。水面保留原有混合因子，其中 `colortex11`
关闭混合。原本从 JSON 注入的 SSBO 改为显式包含光影自身声明，保留其条件布局。
光照使用 `(level + 0.5) / 16`，由 Eclipse 自己完成后续归一化。纹理平均颜色和生物群系染色
分别传入，避免重复染色。`lightningBoltPosition` 保持包含存在标志的 `vec4`。

模型仍为 64 字节，四边形仍为 8 字节；不生成逐区块 DH 网格或 CPU 绘制列表。新增材质表为
每个稳定方块状态 4 字节，当前原版注册表为 142,892 字节，每个 TV 渲染器只分配一份。
保留状态身份会减少模型去重；大量模组方块的模型容量和开销仍需单独测量。

## 验证

2026-11-04，在 RTX 4070 Ti SUPER 上以 Vulkan 实际运行；证据位于仓库外
`D:/MCMod/项目资料/TerraVista/vitrail-adaptation/`。

- 完整 Gradle 构建和 16 项源码契约检查通过。检查包括维度标记、材质/光照、附件、混合、
  两种预处理作用域、深度身份、宏递归和修改范围。
- 原始 Eclipse 自动适配通过开关光影、窗口调整、75% 渲染、资源重载，以及不透明/水面前各一次
  强制上传缓冲轮换。11,220 次 counted indirect，零 indirect fallback。
- 独立导出的 Eclipse-TV ZIP 重复通过同样流程：11,703 次 counted indirect，重载后远景深度
  183,642 个非清空像素，零 mesh failure，未执行无消费者的 TV 私有时域绘制。
- 独立 ZIP 通过主世界→下界→末地→主世界，以及 9 次位置移动、最远到 x=896 后返回。
  四个阶段均有真实远景深度；返回后有 42,127 个像素的视空间深度超过 128 格，原版视距为
  4 区块。远景覆盖沿实际加载路线增长，未加载区域留空属于测试世界覆盖范围。
  41,370 次 counted indirect，零 indirect fallback，模型 220、几何约 8 MiB，零 mesh failure。
  各阶段 TV GPU 缓冲数量/容量保持 28 / 2,368,032,424 字节。
- 故意破坏水面 GLSL，在 opaque 已执行后触发宿主拒绝，TV 正确转入基础远景降级；配对图像
  读回确认有实际颜色输出。光影切换、缩放和资源重载均继续工作。参见 `eclipse-refusal-01/refusal-02.log`。
- 通用 Complementary 路径在新增材质表后再次通过完整生命周期，参见主接入文档。

截图和 draw 计数仅证明所述场景的可见输出及路由。性能采样需关闭 VSync/帧率限制，并单独记录
帧间隔与 GPU 时间；30 FPS 的生命周期测试不作为性能结论。

另以同一存档和相机快照，在 1080p 下按 A-B-B-A 顺序运行 Vitrail 基线、TV、TV、Vitrail 基线；
每次编译就绪后预热 20 秒、采样 30 秒，关闭 VSync/帧率限制。基线仅关闭 Eclipse 针对 Iris 的
全屏诊断。TV 使用独立导出的 ZIP，新增真实远景和着色，因此这是增量成本比较，非相同图像的
优化对比，也不能拆成单独的兼容层成本。

| 合并两次采样 | 平均帧间隔 | p95 帧间隔 | 平均 GPU 时间 | GPU p95 |
| --- | ---: | ---: | ---: | ---: |
| Vitrail + 原始 Eclipse，无 TV | 1.966 ms | 2.136 ms | 1.949 ms | 2.103 ms |
| TV + Vitrail + Eclipse-TV | 3.017 ms | 3.192 ms | 2.998 ms | 3.138 ms |

两次 TV 平均帧间隔为 3.018 / 3.015 ms，合计 19,923 帧，均为零 mesh failure。该静态场景使用
181 模型、358 节点、约 5 MiB 几何；计时区间内没有截图/深度读回或强制上传轮换。
这些是 RTX 4070 Ti SUPER 的场景样本；本组未测整机显存峰值，也未测持续移动时的帧时间。
原始 CPU/GPU CSV、脚本、快照配置和截图见 `eclipse-performance-01/`。

## 当前边界

Eclipse 的 TV 路径支持远景材质着色、水面、深度消费者和时域入口，但没有完成所有硬件、光影
选项及模组材质的验收。Vitrail 尚未供应的一些 Eclipse 参数仍使用其零值处理，例如默认关闭的
`cumulonimbusStrength`；本补丁不会冒充 Iris Extension 已安装。仅将针对 Iris GL 地形回归的
最终全屏诊断限定在相应渲染环境，其他诊断保持原意。

TV 远景投射阴影尚未接入，Distant Horizons 与 TV 同时渲染未验收。大规模世界、长时间飞行、
显存峰值、更多光影选项、视距接缝及水下视角仍需扩大测试，不能称为完整的 Voxy + Iris 功能等价。
