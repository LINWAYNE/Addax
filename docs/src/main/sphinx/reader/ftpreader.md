# Addax FtpReader 说明

## 1 快速介绍

FtpReader提供了读取远程FTP文件系统数据存储的能力。在底层实现上，FtpReader获取远程FTP文件数据，并转换为Addax传输协议传递给Writer。

**本地文件内容存放的是一张逻辑意义上的二维表，例如CSV格式的文本信息。**

## 2 功能与限制

FtpReader实现了从远程FTP文件读取数据并转为Addax协议的功能，远程FTP文件本身是无结构化数据存储，对于Addax而言，FtpReader实现上类比TxtFileReader，有诸多相似之处。目前FtpReader支持功能如下：

1. 支持且仅支持读取TXT的文件，且要求TXT中shema为一张二维表。

2. 支持类CSV格式文件，自定义分隔符。

3. 支持多种类型数据读取(使用String表示)，支持列裁剪，支持列常量

4. 支持递归读取、支持文件名过滤。

5. 支持文本压缩，现有压缩格式为zip、gzip、bzip2。

6. 多个File可以支持并发读取。

我们暂时不能做到：

1. 单个File支持多线程并发读取，这里涉及到单个File内部切分算法。二期考虑支持。

2. 单个File在压缩情况下，从技术上无法支持多线程并发读取。

## 3 功能说明

### 3.1 配置样例

```json
{
  "job": {
    "setting": {
      "speed": {
        "channel": 2,
        "bytes": -1
      }
    },
    "content": [
      {
        "reader": {
          "name": "ftpreader",
          "parameter": {
            "protocol": "sftp",
            "host": "127.0.0.1",
            "port": 22,
            "username": "xx",
            "password": "xxx",
            "path": [
              "/var/pub/ftpReaderTest/data"
            ],
            "column": [
              {
                "index": 0,
                "type": "long"
              },
              {
                "index": 1,
                "type": "boolean"
              },
              {
                "index": 2,
                "type": "double"
              },
              {
                "index": 3,
                "type": "string"
              },
              {
                "index": 4,
                "type": "date",
                "format": "yyyy.MM.dd"
              }
            ],
            "encoding": "UTF-8",
            "fieldDelimiter": ","
          }
        },
        "writer": {
          "name": "ftpWriter",
          "parameter": {
            "path": "/var/ftp/FtpWriter/result",
            "fileName": "shihf",
            "writeMode": "truncate",
            "format": "yyyy-MM-dd"
          }
        }
      }
    ]
  }
}
```

### 3.2 参数说明

| 配置项            | 是否必须 | 默认值         | 描述                                                                   |
| :---------------- | :------: | -------------- | --------------------------------------------------------------------|
| protocol          |    是    | 无             | ftp服务器协议，目前支持传输协议有ftp和sftp                                |
| host              |    是    | 无             | ftp服务器地址                                                          |
| port              |    否    | 22/21          | 若传输协议是sftp协议，默认值是22；若传输协议是标准ftp协议，默认值是21        |
| timeout           |    否    | 60000          | 连接ftp服务器连接超时时间，单位毫秒(ms)                                  |
| connectPattern    |    否    | PASV           | 连接模式，仅支持 `PORT`, `PASV` 模式。该参数只在传输协议是标准ftp协议时使用 |
| username          |    是    | 无             | ftp服务器访问用户名                                                    |
| password          |    是    | 无             | ftp服务器访问密码                                                      |
| path              |    是    | 无             | 远程FTP文件系统的路径信息，注意这里可以支持填写多个路径，详细描述见下        |
| column            |    是    | 默认String类型 | 读取字段列表，type指定源数据的类型，详见下文                                 |
| fieldDelimiter    |    是    | `,`            | 描述：读取的字段分隔符                                                  |
| compress          |    否    | 无             | 文本压缩类型，默认不填写意味着没有压缩。支持压缩类型为zip、gzip、bzip2       |
| encoding          |    否    | utf-8          | 读取文件的编码配置                                                     |
| skipHeader        |    否    | false          | 类CSV格式文件可能存在表头为标题情况，需要跳过。默认不跳过                    |
| nullFormat        |    否    | `\N`           | 定义哪些字符串可以表示为null                                             |
| maxTraversalLevel |    否    | 100            | 允许遍历文件夹的最大层数                                                |
| csvReaderConfig   |    否    | 无             | 读取CSV类型文件参数配置，Map类型。不配置则使用默认值,详见下文 |

#### path

远程FTP文件系统的路径信息，注意这里可以支持填写多个路径。

- 当指定单个远程FTP文件，FtpReader暂时只能使用单线程进行数据抽取。二期考虑在非压缩文件情况下针对单个File可以进行多线程并发读取 = 当指定多个远程FTP文件，FtpReader支持使用多线程进行数据抽取。线程并发数通过通道数指定
- 当指定通配符，FtpReader尝试遍历出多个文件信息。例如: 指定 `/*` 代表读取/目录下所有的文件，指定 `/bazhen/*` 代表读取 bazhen 目录下游所有的文件。目前只支持 `*` 作为文件通配符。

特别需要注意的是，Addax会将一个作业下同步的所有Text File视作同一张数据表。用户必须自己保证所有的File能够适配同一套schema信息。读取文件用户必须保证为类CSV格式，并且提供给Addax权限可读。 特别需要注意的是，如果Path指定的路径下没有符合匹配的文件抽取，Addax将报错。

#### column

读取字段列表，type指定源数据的类型，index指定当前列来自于文本第几列(以0开始)，value指定当前类型为常量，不从源头文件读取数据，而是根据value值自动生成对应的列。

默认情况下，用户可以全部按照String类型读取数据，配置如下：

```json
    "column": ["*"]
```

用户可以指定Column字段信息，配置如下：

```json
{
  "type": "long",
  "index": 0
  //从远程FTP文件文本第一列获取int字段
},
{
"type": "string",
"value": "alibaba"  //从FtpReader内部生成alibaba的字符串字段作为当前字段
}
```

对于用户指定Column信息，type必须填写，index/value必须选择其一。

#### csvReaderConfig

常见配置：

```json
"csvReaderConfig":{
"safetySwitch": false,
"skipEmptyRecords": false,
"useTextQualifier": false
}
```

所有配置项及默认值,配置时 csvReaderConfig 的map中请**严格按照以下字段名字进行配置**：

```ini
boolean caseSensitive = true;
char textQualifier = 34;
boolean trimWhitespace = true;
boolean useTextQualifier = true;//是否使用csv转义字符
char delimiter = 44;//分隔符
char recordDelimiter = 0;
char comment = 35;
boolean useComments = false;
int escapeMode = 1;
boolean safetySwitch = true;//单列长度是否限制100000字符
boolean skipEmptyRecords = true;//是否跳过空行
boolean captureRawRecord = true;
```

### 3.3 类型转换

远程FTP文件本身不提供数据类型，该类型是Addax FtpReader定义：

| Addax 内部类型 | 远程FTP文件 数据类型 |
| -------------- | -------------------- |
|                |
| Long           | Long                 |
| Double         | Double               |
| String         | String               |
| Boolean        | Boolean              |
| Date           | Date                 |

其中：

- Long 是指远程FTP文件文本中使用整形的字符串表示形式，例如"19901219"。
- Double 是指远程FTP文件文本中使用Double的字符串表示形式，例如"3.1415"。
- Boolean 是指远程FTP文件文本中使用Boolean的字符串表示形式，例如"true"、"false"。不区分大小写。
- Date 是指远程FTP文件文本中使用Date的字符串表示形式，例如"2014-12-31"，Date可以指定format格式。