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
用户名：postgres
密码：difyai123456
```

首版数据库密码明文保存，便于内部工具快速使用和排查。

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

## 界面布局

- 主窗口会按当前屏幕尺寸放大到较宽的默认尺寸，并设置最小窗口尺寸，避免输入区和结果区过窄。
- Token 输入区默认保留多行高度，适合粘贴日志、SQL、JSON 或接口文本。
- 查询结果表格关闭自动列压缩，启用横向滚动，长 token、app_id、应用名称和错误信息优先完整展示。

## 目录说明

- `src/main/java/com/company/difytokenquery/config`：本地配置模型和读写服务。
- `src/main/java/com/company/difytokenquery/db`：数据源模型和 Dify 查询服务。
- `src/main/java/com/company/difytokenquery/model`：查询结果模型和状态枚举。
- `src/main/java/com/company/difytokenquery/parser`：Token 解析逻辑。
- `src/main/java/com/company/difytokenquery/ui`：Swing 界面。
- `src/test/java`：单元测试。

## 注意事项

- 项目没有数据库表结构变更，不需要生成 update SQL。
- 后续如果修改配置格式、核心查询 SQL、启动方式或 UI 主流程，需要同步更新本文档。
