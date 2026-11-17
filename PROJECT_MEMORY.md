# Dify Token 查询工具项目记忆

## 项目定位

这是一个面向 Windows 的轻量级桌面工具，用于从用户粘贴的任意文本中自动解析 Dify API Token，并连接指定 Dify PostgreSQL 数据库查询对应应用信息。

## 技术栈

- Java 8
- Swing 桌面界面
- FlatLaf 外观
- Maven 构建
- PostgreSQL JDBC
- Jackson JSON 配置读写
- JUnit 4 单元测试

## 运行方式

- 使用 Maven 生成可运行 fat jar。
- 使用 `run.bat` 启动 `target\dify-token-query-tool.jar`。
- `run.bat` 会优先使用 `C:\Program Files\Java\jdk1.8.0_202\bin\java.exe`，不存在时退回系统 `java`。
- 开发和构建优先使用 `C:\Program Files\Java\jdk1.8.0_202`。

## 配置说明

程序配置文件保存到：

```text
%APPDATA%\DifyTokenQueryTool\config.json
```

首次启动如果配置不存在，会自动初始化默认数据源：

```text
名称：公司170dify pg库
JDBC URL：jdbc:postgresql://192.2.2.170:5432/dify
Dify 前端地址：http://192.2.2.170:8180
用户名：postgres
密码：difyai123456
```

首版数据库密码明文保存，便于内部工具快速使用和排查。

每条配置都可以维护 Dify 前端地址，程序会使用该地址拼接应用编排页链接：

```text
{Dify前端地址}/app/{app_id}/configuration
```

历史配置中如果内置默认数据源 `company-170-dify-pg` 缺少 Dify 前端地址，加载配置时会自动补齐为 `http://192.2.2.170:8180`。

## 核心流程

1. 用户在主界面粘贴任意文本。
2. 程序使用正则 `app-[A-Za-z0-9_-]{16,128}` 提取 token。
3. token 去重并保留原始出现顺序。
4. 程序连接当前选中的 Dify PostgreSQL 数据源。
5. 逐个 token 执行参数化查询：

```sql
select
    t.token,
    a.id,
    a.name,
    a.mode
from api_tokens t
join apps a on a.id = t.app_id
where t.token = ?
```

6. 查询结果展示到表格，并支持复制选中行或全部结果。
7. 成功查询到应用时，结果表会在“编排”列展示可点击链接，点击后用系统默认浏览器打开 Dify 编排页。
8. 结果表会在“复制链接”列展示可点击链接，点击后将对应 Dify 编排页 URL 写入系统剪贴板。

## 界面布局

- 主窗口会按当前屏幕尺寸放大到较宽的默认尺寸，并设置最小窗口尺寸，避免输入区和结果区过窄。
- Token 输入区默认保留多行高度，适合粘贴日志、SQL、JSON 或接口文本。
- 查询结果表格关闭自动列压缩，启用横向滚动，长 token、app_id、应用名称和错误信息优先完整展示。
- 主界面顶部配置入口显示为“Dify配置”，管理按钮显示为“管理Dify”；管理窗口仍包含数据库连接参数，同时新增 Dify 前端地址字段。
- 管理 Dify 窗口支持双击配置列表中的有效行，直接打开该配置的编辑窗口。

## 目录说明

- `src/main/java/com/company/difytokenquery/config`：本地配置模型和读写服务。
- `src/main/java/com/company/difytokenquery/db`：数据源模型和 Dify 查询服务。
- `src/main/java/com/company/difytokenquery/model`：查询结果模型和状态枚举。
- `src/main/java/com/company/difytokenquery/parser`：Token 解析逻辑。
- `src/main/java/com/company/difytokenquery/dify`：Dify Console API 客户端、DSL `.yml` 备份服务与模型批量替换引擎。
- `src/main/java/com/company/difytokenquery/ui`：Swing 界面。
- `src/test/java`：单元测试。

## 注意事项

- 项目没有数据库表结构变更，不需要生成 update SQL。
- 后续如果修改配置格式、核心查询 SQL、启动方式或 UI 主流程，需要同步更新本文档。

## 数据库浏览与 SQL 查询

- 主窗口“数据库查询”打开独立数据库浏览窗口，使用当前 Dify 配置读取 PostgreSQL 数据库列表。
- 树形结构按数据库、schema、表懒加载；单击表读取列序号、列名、类型、可空、默认值和备注等表结构信息。
- 双击数据库会新建 SQL 查询页，双击表会生成 `select * from "schema"."table"` 查询。
- SQL 查询页支持执行全部文本或编辑器选中文本；SELECT/WITH/VALUES/SHOW/EXPLAIN 使用 LIMIT/OFFSET 分页，每页最多 1000 条，当前页和总量分别异步获取。非查询语句显示影响行数。
- SQL 文件默认保存到 `%APPDATA%\\DifyTokenQueryTool\\queries`，窗口启动时自动载入已有 `.sql` 文件；“保存/另存为”和 `Ctrl+S` 可继续编辑固化文件。
- 结果表链接在选中行时使用白色高对比度链接色，避免蓝色文字与蓝色选中背景混淆。

## 批量替换应用模型

- 主窗口顶部新增“批量换模型”入口，打开独立工作台 `BatchModelReplaceFrame`，若当前结果表存在已查询到的应用会自动带入勾选 `app_id`。
- 数据源配置 `DatasourceConfig` 新增可选字段 `difyUsername` 与 `difyPassword`（保存于 `config.json`，兼容旧版配置缺失情况），用于在工作台打开或操作时登录 Dify Console API。
- 采用“PostgreSQL 检索应用 + Dify Console API 加载模型并执行更新”的混合模式：
  - 通过 `DifyAppModelRepository` 从 PG 查询 `apps`、`app_model_configs`、`workflows`（优先草稿 `version = 'draft'`，缺省回退 `apps.workflow_id`）及 `api_tokens`。
  - 通过 `DifyConsoleClient` 调用 `POST /console/api/login`、`/console/api/workspaces/current/models/model-types/llm`、`/console/api/apps/{appId}/export?include_secret=false`、`/console/api/apps/{appId}/model-config`、`/console/api/apps/{appId}/workflows/draft` 与 `/console/api/apps/{appId}/workflows/publish`。
- `ModelReplacementEngine` 与 `BatchModelReplaceFrame` 支持三种配置方式：单条“原模型 -> 目标模型”、顶部“多规则映射队列”（一次批量替换多种模型），以及右侧“当前选中应用的节点模型配置”（支持多节点工作流逐节点勾选、独立指定目标模型与参数策略）；覆盖基础应用 `app_model_configs.model` 及工作流 `llm`、`parameter-extractor`、`question-classifier`、`agent`（`agent_parameters.model.value`）、`knowledge-retrieval` 单路召回（`single_retrieval_config.model`），跳过 `reranking_model`；当工作流 `llm` 节点在 `completion` 与 `chat` 模式间切换时自动同步转换 `prompt_template` 结构；“原模型”下拉框仅列出各应用/工作流/智能体实际使用到的模型；`completion_params` 默认仅保留通用参数（`temperature`、`top_p`、`max_tokens`、`stop`），可选清空或全保留，并支持全局与逐节点配置新模型的思考层级（`enable_thinking` 开关与 `reasoning_effort` 思考强度，自动移除关闭思考时残留的 `reasoning_effort`，同时按目标模型查询 `/parameter-rules` 提示支持情况）。
- 正式写入前强制执行 `AppBackupService` 备份：先导出应用 DSL `.yml`（若工作流存在已发布版本且勾选了自动发布，则额外导出 `_published.yml`）及原始 JSON 快照与 `manifest.json` 到 `%APPDATA%\DifyTokenQueryTool\backups\<yyyyMMdd-HHmmss>\`（界面可自选备份目录）；备份失败即中止保存该应用，防止无备份修改。
- 工作流应用默认仅更新草稿，勾选“工作流更新后自动发布”后才会同步调用发布接口；批量执行前支持 Dry-run 变更预览，执行过程中单应用异常隔离。