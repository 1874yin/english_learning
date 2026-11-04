# Cadence · 英语精听与跟读

一个类似 VOA 学习页面的英语听说练习应用。以 MP3 音频为主，页面同步展示逐句台词，底部常驻播放器，支持切换素材、精听、单句循环和跟读录音。

素材不再写死在代码里：通过内置的简易后台添加 MP3 和纯文本台词，后端会自动分句，并按音频实际时长估算逐句起止时间。仓库默认不带示例课程，首次启动后素材库为空。

## 运行

环境要求：

- Java 21
- Maven 3.6+

开发模式启动：

```bash
mvn spring-boot:run
```

打开：

- 学习页：<http://localhost:8081>
- 素材后台：<http://localhost:8081/admin>

打包后运行：

```bash
mvn package
java -jar target/english_learning-1.0-SNAPSHOT.jar
```

运行测试：

```bash
mvn test
node --test src/test/js/*.test.js
```

> 麦克风录音在 `localhost` 或 HTTPS 下可用。Chrome / Edge 等现代浏览器支持最好。

## 添加素材

1. 打开 `/admin`，输入管理密码登录。未配置环境变量时默认密码是 `cadence-admin`。
2. 填写英文标题，可选填中文标题、分类、难度、简介。分类可以从下拉框选择已有分类，也可以直接输入新名称后点“添加”保存到可选列表；难度仍固定为初级 / 中级 / 高级。
3. 上传 MP3（最大 50 MB），粘贴台词纯文本。整段或分段落都可以。
4. 可选填写 “Words in this Story”：纯文本，一行写一个词条，换行会原样显示在学习页字幕下方；留空则不显示该栏目。
5. 可选上传封面（JPG、PNG、GIF 或 AVIF，最大 5 MB）。不上传时使用默认封面。AVIF 由现代浏览器负责解码；后端只做文件结构校验，不做转码。
6. 点击“添加素材”。后端读取 MP3 实际时长、按标点分句、估算逐句时间轴，同时自动翻译整篇台词并把中文填入每句下方，然后写入本地素材库。翻译失败不会阻止保存，下次启动会自动重试。
7. 在左侧“已有素材”中点击“编辑”可以修改；不重新选择音频或封面时会保留原文件，词汇纯文本也会回填到编辑器。

“自动分片”指**自动分句并生成逐句时间轴**，不会切出多个 MP3 文件，也不依赖 ffmpeg。时间轴是根据音频总时长、句子单词/音节数与标点估算的，适合跟读和高亮定位，但不是单词级或音素级的强制对齐。音频与台词严重不匹配时，越靠后的句子偏差可能越明显。

当前后台只提供单集素材的新增和修改，不提供删除和批量导入，避免误操作。

## 数据存储

M1 默认使用 MySQL + MongoDB（`APP_PERSISTENCE_MODE=database`）。MySQL 保存课程、分类和用户账号；MongoDB 的 `user_listening` 集合按用户 ID 保存收听记录、播放流、收藏、统计聚合和学习画像。Flyway 管理 MySQL schema，默认在应用启动时执行迁移。音频与封面仍存放在本地文件系统，不属于本次数据库迁移。

迁移前的 JSON 快照保留在数据目录中，作为一次性导入源和只读回滚源：

```text
data/
├─ lessons.json          迁移源：课程、逐句台词、译文、词汇与时间轴
├─ categories.json       迁移源：自定义分类建议
├─ users.json            迁移源：账号与 BCrypt 密码哈希
├─ user-data/{userId}.json 迁移源：该用户的收听数据与画像
├─ translation-state.json 翻译提供方标记（仍由文件保存）
├─ audio/                上传的 MP3（文件名带唯一后缀）
└─ covers/               上传的封面图片（文件名带唯一后缀）
```

- 默认目录是启动时工作目录下的 `data/`，可用 `CADENCE_DATA_DIR` 指定绝对路径。
- 正常数据库模式不会回写上述 JSON 数据快照；数据迁移前请保留这些 JSON 文件和媒体源文件。
- 上传音频通过 `/media/{filename}` 提供，带长缓存和 Range 支持；封面通过 `/covers/{filename}` 提供。替换文件时会生成新文件名，避免浏览器读到旧缓存。
- 样式表和脚本的 URL 带构建时间戳（`?v=...`，由 Maven 打包时写入），静态资源响应统一带 `Cache-Control: no-cache`。重新打包部署后，浏览器会在下次刷新时重新校验并拿到新版前端文件，不会继续用旧样式表渲染新页面。若前面有 Nginx 等反向代理，请确保它不把 `/` 和 `/index.html` 的响应缓存超过一个版本周期。
- 封面支持 JPG、PNG、GIF、AVIF。JDK 自带的 ImageIO 不能解码 AVIF，因此 AVIF 只校验 ISO-BMFF 文件头与尺寸（`ispe`），实际解码交给浏览器，并由独立的 `/covers` 控制器返回 `image/avif`。样式表等旧环境若不支持 AVIF，会显示默认占位效果，建议对兼容性要求高时使用 JPG/PNG。
- `data/` 已加入 `.gitignore`。

### M1 数据库迁移与回滚

#### 配置 MySQL 和 MongoDB

准备 MySQL 8.4（或兼容版本）数据库 `cadence` 和 MongoDB 8.0（或兼容版本）。`database` 模式不会在数据库不可用时回退到 JSON；数据库连接失败会直接表现为启动或请求失败。显式选择 `json-read-only` 时只读取旧 JSON 快照，并跳过 Flyway 迁移，因此可在 MySQL 不可用时启动回滚服务。

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `APP_PERSISTENCE_MODE` | `database` | `database` 使用 MySQL + MongoDB；`json-read-only` 只读迁移前的 JSON 快照。 |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3306/cadence?...` | MySQL JDBC URL；数据库需预先创建。 |
| `SPRING_DATASOURCE_USERNAME` | `cadence` | MySQL 用户名。 |
| `SPRING_DATASOURCE_PASSWORD` | 空 | MySQL 密码；通过环境变量或安全配置注入，不要提交到仓库。 |
| `SPRING_DATA_MONGODB_URI` | `mongodb://localhost:27017/cadence` | MongoDB 连接 URI。 |
| `SPRING_FLYWAY_ENABLED` | `true` | 是否在应用启动时应用 Flyway 迁移。数据库模式默认启用；`json-read-only` 模式自动跳过迁移，不要求 MySQL 可用。 |
| `APP_DATA_IMPORT_MODE` | `disabled` | `dry-run` 只校验并报告；`execute` 执行一次性导入。 |
| `APP_DATA_IMPORT_BACKUP_DIR` | 空 | 执行导入前，完整备份的**独立目录**路径。 |
| `APP_DATA_IMPORT_CONFIRM` | `false` | 必须显式设为 `true` 才允许执行导入。 |

迁移前先停止旧实例，并将整个 `CADENCE_DATA_DIR`（包括 JSON、音频和封面）复制到数据目录之外。导入器会逐文件比较源目录和备份目录的相对路径及 SHA-256；不完整、路径相同或内容不一致的备份会被拒绝。执行前也必须确认目标 MySQL 表及 MongoDB `user_listening` 集合为空。

#### 预检与执行

确保 MySQL/MongoDB 已启动，环境变量连接到专用的空目标库，Flyway 已启用。先运行只读预检：

```bash
APP_PERSISTENCE_MODE=database \
SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/cadence?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' \
SPRING_DATASOURCE_USERNAME='cadence' SPRING_DATASOURCE_PASSWORD='change-me' \
SPRING_DATA_MONGODB_URI='mongodb://localhost:27017/cadence' \
SPRING_FLYWAY_ENABLED=true \
java -jar target/english_learning-1.0-SNAPSHOT.jar \
  --spring.main.web-application-type=none \
  --app.translation.enabled=false --app.user-analysis.enabled=false \
  --app.data-import.mode=dry-run
```

预检报告包含 `manifestId`、每个源 JSON 的 SHA-256、课程/分类/账号/用户数据条数和校验结果。它会拒绝无法解析的 JSON、重复课程 ID/编号、重复的大小写不敏感账号或分类、缺失/损坏的用户收听文件，以及超过 MongoDB 16 MiB 文档上限的用户数据；预检失败时不会写入任一目标库。仔细检查报告与来源数据后，准备完整备份并执行：

```bash
APP_PERSISTENCE_MODE=database \
SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/cadence?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' \
SPRING_DATASOURCE_USERNAME='cadence' SPRING_DATASOURCE_PASSWORD='change-me' \
SPRING_DATA_MONGODB_URI='mongodb://localhost:27017/cadence' \
SPRING_FLYWAY_ENABLED=true \
java -jar target/english_learning-1.0-SNAPSHOT.jar \
  --spring.main.web-application-type=none \
  --app.translation.enabled=false --app.user-analysis.enabled=false \
  --app.data-import.mode=execute --app.data-import.confirm=true \
  --app.data-import.backup-dir=/absolute/path/to/cadence-data-backup
```

导入会保留课程编号、顺序、完整 BCrypt 哈希和用户 ID，在 MySQL 写入课程/分类/账号，在 MongoDB 写入每用户收听文档；导入清单记录各 JSON 文件哈希和计数。完成前会重新读取数据库并核对源/目标数量及完整内容，包括课程嵌套台词、账号哈希和收听数据。相同来源的已完成 `manifestId` 可安全重试且不会重复写入；目标非空、来源已改变或存在上次未完成清单时会拒绝执行，不会猜测性覆盖。若跨库导入中途失败，清单会标为 `FAILED`；在人工清理或从备份恢复目标前不要重试。

完成后以 `APP_PERSISTENCE_MODE=database` 正常启动。若必须回滚，可让应用指向迁移时保存的 JSON 快照并设 `APP_PERSISTENCE_MODE=json-read-only`；此模式会自动跳过 Flyway 数据库迁移，MySQL 不可用也不妨碍快照服务启动。它只读取快照，所有数据写接口返回 HTTP 503，绝不会把写入落回 JSON；它代表迁移时的旧快照，如果数据库迁移后已有新写入，回滚视图会过期且不包含这些新数据。不要把它当作可双写或实时同步模式。


### 配置

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `ADMIN_PASSWORD` | `cadence-admin` | 后台登录密码。**对外部署前必须修改。** |
| `ADMIN_ALLOW_LOCAL_REQUESTS` | `true` | 是否允许本机 IP 免登录访问后台接口。 |
| `CADENCE_DATA_DIR` | `data` | JSON 迁移快照、音频与封面的本地目录。 |
| `TRANSLATION_ENABLED` | `true` | 是否在新增/修改和启动时自动翻译台词。设为 `false` 可完全关闭。 |
| `TRANSLATION_DEEPSEEK_API_KEY` | 空 | DeepSeek API Key；也兼容 `DEEPSEEK_API_KEY`。为空时自动翻译关闭。 |
| `TRANSLATION_DEEPSEEK_URL` | `https://api.deepseek.com/chat/completions` | DeepSeek 对话接口地址。 |
| `TRANSLATION_DEEPSEEK_MODEL` | `deepseek-chat` | 使用的模型。 |
| `TRANSLATION_DEEPSEEK_MAX_OUTPUT_TOKENS` | `8192` | 单次翻译请求的最大输出 token 数。 |
| `TRANSLATION_DEEPSEEK_TEMPERATURE` | `0.2` | 翻译采样温度，越低越稳定。 |
| `TRANSLATION_MAX_BATCH_CHARS` | `6000` | 每批发送的英文台词总字符上限。 |
| `TRANSLATION_CONNECT_TIMEOUT_MS` | `10000` | 连接 DeepSeek 的超时时间。 |
| `TRANSLATION_READ_TIMEOUT_MS` | `120000` | 等待 DeepSeek 响应的超时时间。 |

自动翻译使用 DeepSeek 的 JSON 输出模式：一次发送一批英文句子，要求模型返回同样数量、同样顺序的中文数组，因此逐句对齐比旧的机器翻译接口可靠得多；如果模型返回的数量不一致，后台会退化为逐句重试。单批超过 `TRANSLATION_MAX_BATCH_CHARS` 时按句子边界拆成多批。翻译失败的课程仍会保存，下次启动继续补翻。

第一次从旧翻译接口升级时，启动后的后台任务会删除 `lessons.json` 中已有的旧中文译文，写入 `translation-state.json` 标记，然后逐课重新翻译。想强制全部重译时，停止应用、删除 `data/translation-state.json` 再启动即可。升级前建议先备份整个 `data/` 目录。

补翻在后台单线程执行，不阻塞应用启动；翻译完一课就立即落盘。停止应用会中断任务，本次已完成的课程会保留，下次启动从剩余课程继续。素材库较大时首次补翻可能需要较长时间。

启动后可以打开 `/api/admin/translation/status`（本机免登录）确认当前提供方、旧译文是否已清理，以及已翻译/未翻译的句数进度。登录素材后台后，页面顶部也会显示同一份数据的可视化卡片；补翻未完成时每 15 秒自动刷新，页面切到后台时暂停。

当前端口固定为 `8081`，可在 `src/main/resources/application.properties` 中修改。

PowerShell 示例：

```powershell
$env:ADMIN_PASSWORD = "change-me"
$env:CADENCE_DATA_DIR = "D:\cadence-data"
java -jar target/english_learning-1.0-SNAPSHOT.jar
```

Bash 示例：

```bash
ADMIN_PASSWORD='change-me' CADENCE_DATA_DIR='/var/lib/cadence' \
  java -jar target/english_learning-1.0-SNAPSHOT.jar
```

后台只使用一个共享密码和 12 小时会话 Cookie，适合个人或小范围使用。不要在未启用 HTTPS 的公开网络上暴露后台；密码为空时后台登录会一直失败。

从本机（回环地址 `127.0.0.1` / `::1`）发出的后台接口请求无需登录，打开 `/admin` 会直接进入管理面板。该判断使用 TCP 连接的对端地址，不读取 `X-Forwarded-For` 等可伪造的请求头。如果应用通过同机的 Nginx 等反向代理对外提供服务，外部请求的对端地址也会是本机，此时必须设置 `ADMIN_ALLOW_LOCAL_REQUESTS=false` 关闭免登录，否则后台相当于没有鉴权。

## 功能

- **固定底部播放器**：播放/暂停、播放进度拖动、后退/前进 10 秒、上一期/下一期、时间预览。
- **逐句同步台词**：播放时自动高亮当前句；点击任意句子（或句首时间）从该句开始播放。
- **自动中文翻译**：新增或修改台词时自动翻译整篇英文，再按句填到每句英文下方；启动时后台补齐素材库中缺翻译的课程。译文由 DeepSeek 逐句生成，可用“中文释义”开关或 `T` 隐藏。
- **Words in this Story**：在字幕下方按原样显示后台填写的词汇纯文本，保留换行；素材没有词汇时整块隐藏。
- **自动跟随**：学习长文本时，台词列表会随着音频滚动到当前句。
- **精听辅助**：0.5×–1.5× 倍速（保持音高）、单句循环、音量/静音。
- **跟读练习**：打开任意句子的跟读面板，听原声、录音、回放；录音最长 30 秒，只保存在当前页面内存中，不上传服务器。
- **字幕 / 播放列表切换**：台词区固定高度，内容超出时在区域内上下滚动；下方两个按钮在“字幕”和“播放列表”之间切换。选择某期后自动回到字幕，切换后会自动记忆上次素材。
- **标题搜索**：播放列表顶部可按关键词搜索英文或中文标题，输入即筛选；多个词用空格分隔，每个词都命中即可，顺序不限，忽略大小写、标点和变音符号；可与分类、难度筛选叠加。搜索词只在当前页面内生效，刷新后清空。
- **按分类和难度筛选**：播放列表顶部提供分类、难度两组筛选标签，可以组合筛选并随时清除；分类来自素材库与后台自定义列表，难度保持初级 / 中级 / 高级三档。
- **历史记录**：页头左上角的“历史记录”入口打开右侧抽屉，按最近看过倒序列出听过的素材，点击即可回到那一期继续重听；播放列表中已听过的条目文字颜色变淡。历史只保存在浏览器本地，可一键清空。
- **自定义封面**：后台可上传单集封面，学习页、播放器和音频列表同步使用；不上传时使用默认封面。
- **空素材引导**：素材库为空时学习页显示引导，不会报错，并提供进入后台的入口。
- **进度保存**：播放进度、完成状态、语速、音量、跟随设置保存在浏览器 `localStorage` 中。
- **响应式布局**：桌面、平板、手机均可使用；手机端播放器固定在底部并压缩为两行。
- **可访问性**：语义化按钮、键盘操作、可见焦点、`aria-live` 状态提示、系统“减少动态效果”适配。
- **系统媒体控制**：支持浏览器 Media Session（媒体键、系统播放器控制）。

### 快捷键

| 按键 | 功能 |
| --- | --- |
| `Space` | 播放 / 暂停 |
| `←` / `→` | 后退 / 前进 5 秒 |
| `J` / `L` | 后退 / 前进 10 秒 |
| `↑` / `↓` | 音量 +5% / −5% |
| `R` | 开启 / 关闭单句循环 |
| `T` | 显示 / 隐藏中文释义 |
| `S` | 打开 / 关闭跟读练习 |
| `?` | 打开快捷键说明 |
| `Esc` | 关闭弹窗、倍速菜单或跟读面板 |

快捷键在输入框、文本编辑位置不会触发；通过键盘聚焦按钮时，`Space` 仍保留按钮的原生激活行为，鼠标操作后 `Space` 回到全局播放/暂停。弹窗打开时不会触发后台播放控制。

## 项目结构

```text
src/main/java/com/xiaojie/englishlearning/
├─ EnglishLearningApplication.java   Spring Boot 启动类
├─ admin/
│  ├─ AdminController.java           登录、素材列表与新增/修改接口
│  ├─ AdminCategoryController.java   自定义分类的新增/删除接口
│  ├─ AdminAuthInterceptor.java      后台会话校验
│  ├─ AdminExceptionHandler.java     后台错误响应
│  ├─ AdminLessonSummary.java        后台列表摘要
│  └─ TranslationStatusController.java 翻译状态诊断接口
├─ category/
│  ├─ CategoryStore.java             分类持久化边界
│  ├─ CategoryService.java           分类建议与素材在用分类的合并
│  └─ CategoryController.java        公开分类接口
├─ config/WebConfig.java             /media 静态映射、后台拦截器、/admin 跳转
├─ media/CoverController.java        提供上传封面并返回正确 MIME（含 image/avif）
├─ lesson/
│  ├─ Lesson.java                    素材模型
│  ├─ LessonInput.java               后台表单输入
│  ├─ LessonService.java             新增/修改、分页、时间轴生成与翻译流程
│  ├─ LessonSummary.java             列表页摘要投影（不含台词等大字段）
│  ├─ LessonPage.java                分页响应（items/page/size/total/hasMore）
│  ├─ LessonStore.java               MySQL 元数据边界与本地音频/封面操作
│  ├─ TranscriptProcessor.java       自动分句与时间轴估算
│  ├─ LessonController.java          公开只读接口
│  ├─ TranscriptLine.java            单句台词模型（含中文译文）
│  └─ KeyPhrase.java                 重点表达模型
├─ persistence/
│  ├─ mysql/                         MyBatis-Plus entities、mappers、MySQL adapters
│  ├─ mongodb/                       每用户收听文档、乐观并发更新与大小校验
│  ├─ json/                          显式选择的 JSON 只读快照 adapters
│  └─ *Persistence.java              domain persistence ports
├─ migration/                         JSON 预检、导入与清单
├─ user/
│  ├─ UserStore.java                 MySQL 账号存储与认证
│  ├─ ListeningStore.java            MongoDB 收听数据与画像计算
│  └─ UserAnalysisService.java       使用独立分析服务凭据生成画像
├─ translation/
│  ├─ TranslationService.java        分批翻译、逐句对齐与单句重试
│  ├─ DeepSeekTranslationProvider.java  DeepSeek JSON 批量翻译
│  ├─ TranslationState.java          记录译文提供方，一次性清理旧译文
│  ├─ TranslationStatus.java         翻译状态快照
│  ├─ TranslationStatusService.java  翻译进度统计
│  ├─ TranslationConfig.java         RestTemplate、接口地址与超时配置
│  └─ TranslationStartupRunner.java  启动时后台补翻历史课程
└─ storage/StoragePaths.java         快照和本地媒体目录初始化

src/main/resources/
├─ application.properties
└─ static/
   ├─ index.html                     学习页
   ├─ admin.html                     素材后台
   ├─ css/styles.css                 学习页样式
   ├─ css/admin.css                  后台样式
   ├─ js/app.js                      播放器、台词同步、跟读录音
   ├─ js/search.js                   播放列表标题搜索的匹配逻辑
   ├─ js/admin.js                    登录、素材列表与表单交互
   └─ images/                        默认封面与站点图标

src/test/java/.../LessonApiTest.java           MockMvc 冒烟测试
src/test/java/.../LessonServicePaginationTest.java 列表分页与摘要字段测试
src/test/java/.../TranscriptProcessorTest.java 分句与时间轴单元测试
src/test/java/.../LessonServiceTranslationTest.java 自动翻译与启动补翻测试
src/test/java/.../translation/TranslationServiceTest.java 分批、对齐与单句重试测试
src/test/java/.../translation/DeepSeekTranslationProviderTest.java DeepSeek 请求与响应解析测试
src/test/java/.../translation/TranslationStateTest.java 旧译文清理标记测试
src/test/java/.../translation/TranslationStatusServiceTest.java 翻译进度统计测试
src/test/java/.../admin/TranslationStatusApiTest.java 状态接口与鉴权测试
```

## API

公开接口无需登录：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/lessons?page=0&size=24` | 分页返回素材摘要，供首页列表使用；不含台词、译文、简介、重点表达与音频地址 |
| `GET` | `/api/lessons/{id}` | 返回单节素材的完整详情（含 `summary`、`transcript`、`wordsText`、`keyPhrases`、`audio` 等）；不存在时 404 |
| `GET` | `/api/categories` | 返回分类建议，含素材正在使用的自定义分类 |
| `GET` | `/media/{filename}` | 播放上传的 MP3 |
| `GET` | `/covers/{filename}` | 读取上传的封面；按扩展名返回对应 `image/*`，含 `image/avif` |

素材列表的 `page` 从 0 开始，`size` 默认 24、限制在 1–100；负数 `page` 按 0 处理。响应示例：

```json
{
  "items": [
    {
      "id": "a1b2c3",
      "number": 1,
      "title": "The Quiet Power of a Slow Morning",
      "titleZh": "慢早晨的安静力量",
      "category": "生活",
      "level": "中级",
      "duration": 92.5,
      "cover": "/covers/cover-1.jpg"
    }
  ],
  "page": 0,
  "size": 24,
  "total": 514,
  "hasMore": true
}
```

首页先只取第一页摘要并立即渲染，点击“加载更多”时再取下一页；只有选中某一期时才请求 `/api/lessons/{id}` 获取台词、译文、重点表达和音频地址，并缓存在浏览器内存中。搜索、筛选和“换一期”需要完整目录时，会按需依次拉取剩余分页摘要（每页约 15 KB，不含台词），因此不会重新引入整库载荷。

后台接口除登录/退出外都需要会话；本机回环地址的请求可免登录（见“配置”）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/admin/login` | JSON `{"password":"..."}`；成功 204，失败 401 |
| `DELETE` | `/api/admin/logout` | 退出并销毁会话，返回 204 |
| `GET` | `/api/admin/lessons` | 素材摘要列表；未登录 401 |
| `GET` | `/api/admin/lessons/{id}` | 单节完整素材 |
| `POST` | `/api/admin/lessons` | `multipart/form-data` 新增，返回 201 |
| `PUT` | `/api/admin/lessons/{id}` | `multipart/form-data` 修改 |
| `POST` | `/api/admin/categories` | JSON `{"name":"Travel"}` 添加自定义分类，返回 201 和最新分类列表 |
| `DELETE` | `/api/admin/categories?name=Travel` | 从建议列表删除分类；分类仍被素材使用时返回 409 |
| `GET` | `/api/admin/translation/status` | 翻译状态快照：是否启用、提供方、模型、旧译文清理标记、已翻译/未翻译句数与课数 |

`/api/admin/translation/status` 返回示例：

```json
{
  "enabled": true,
  "provider": "deepseek",
  "model": "deepseek-chat",
  "stateCurrent": true,
  "storedProvider": "deepseek",
  "storedVersion": 1,
  "totalLessons": 265,
  "totalLines": 10368,
  "translatedLines": 126,
  "missingLines": 10242,
  "translatedLessons": 3,
  "missingLessons": 262
}
```

- `enabled`：自动翻译是否真正生效（`TRANSLATION_ENABLED=true` 且 API Key 非空）。
- `stateCurrent`：旧译文是否已按当前提供方清理。`false` 表示下次启动会先清空旧译文。
- `storedProvider` / `storedVersion`：`data/translation-state.json` 中记录的值；文件不存在时省略。
- `translatedLines` / `missingLines`：补翻进度。刚清理完时为 0，后台任务运行中持续增长，全部完成后等于 `totalLines`。
- 本机可直接访问：<http://localhost:8081/api/admin/translation/status>；非本机请求需要先登录后台。

新增/修改表单字段：`audio`（MP3，新增必填）、`cover`（JPG/PNG/GIF/AVIF，可选）、`title`（必填）、`titleZh`、`category`、`level`、`summary`、`summaryZh`、`transcriptText`（必填）、`wordsText`（可选，纯文本，最多 5000 字符；不提交该字段时修改操作保留原内容，提交空白则清空）。

## 数据与隐私

- 播放进度、偏好和历史记录只存在浏览器本地：`cadence.settings.v1`、`cadence.progress.v1`、`cadence.last-lesson.v1`、`cadence.history.v1`。
- 跟读录音通过 `MediaRecorder` 保存在页面内存中，刷新或关闭页面即丢失，不会上传，也没有服务端接口。
- 上传的音频、封面和台词保存在服务器的 `data/` 目录；后台密码以环境变量形式配置。
- 自动翻译会把**台词文本**发送给 DeepSeek。音频、封面、标题和简介不会发送。如果台词内容敏感，请设置 `TRANSLATION_ENABLED=false`。
- 页面和素材均来自本地，不依赖 VOA 的商标、音频或原文内容。

## 后续可扩展方向

- **删除与批量导入**：素材删除、批量上传 MP3 + SRT/VTT。
- **词级时间轴**：用强制对齐（如 WhisperX / MFA）生成单词时间戳，做卡拉 OK 式高亮和点词查义。
- **重点表达与生词本**：后台录入重点短语，配合间隔重复和例句听写。
- **多用户**：把共享密码升级为账号体系，并记录素材的创建者和修改时间。
- **A-B 复读**：自定义片段循环、自动停顿跟读、无操作跟读模式。
- **录音对比**：波形对比、原声与录音 A/B 切换；如需评分，应基于真实 ASR/强制对齐给出音素级反馈，而不是伪造分数。
- **词汇复习**：把重点表达加入生词本，配合间隔重复和例句听写。
- **PWA / 离线下载**：Service Worker 缓存课程音频与台词，支持安装到桌面。
