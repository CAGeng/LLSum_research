# Readme

### 执行路径
src/main/java/app/IFDSApplication.java

主分析调用层次： IFDSApplication -> VulDetector -> DataFlowTrack

### 目录结构
src/main/java/app springboot管理
src/main/java/basic 静态分析基础模块
src/main/java/detect 检测模块
- src/main/java/detect/knowledge 规则库
- src/main/java/detect/dataflow/DataFlowTrack.java 数据流分析核心

src/main/java/TranModel 控制流节点状态推导
- src/main/java/TranModel/Rules 推导规则

src/main/java/intelligent 和AI model结合（默认关闭）
src/main/resources/rules xml格式entry,src,sink,propagate规则合集

### 配置
#### application.yml
```
输入输出路径
soot:
  output:
    path: /xxx/restfuzz-sast/output/25-3-17

list:
  sootInputPaths:
    - /xxx/nexus/nexus-3.0.4/nexus
    # 被测项目class文件目录, 根目录下 org/xxx/yyy/zzz/ 类似的目录结构
```

```
导入规则
rules:
  core:
    path: rules/core.xml
    pathSwitcher: on
    thirdPartyPath: rules/java-core.xml
    thirdPartyPathSwitcher: on
  added:
    path: rules/risky_funcs.xml
    pathSwitcher: on
    thirdPartyPath: rules/risky_funcs.xml
    thirdPartyPathSwitcher: on
  extend:
    path: rules/extend.xml
    pathSwitcher: on
    thirdPartyPath: rules/java-extend.xml
    thirdPartyPathSwitcher: on
  entry: # 这里是entry规则
    path: rules/EntrySet.xml
    switcher: on
  conditional: on
  taintFlag: on
```

```
分析设置，first-run推荐如下设置
analysis:
  taintSanitizeSwitcher: off
  simpleDataPathSwitcher: on
  onlyDataflowCallStackInter: off # 只追踪src-sink的链路，旁路不展开
  fieldSensitiveSwitcher: on
  sparkSwitcher: on # 调用图构建算法，spark是一种准确但可能遗漏的方法
  conditionSummarySwitcher: on
  chainTrackMode: off
```

#### DataFlowTrack.java中的检测行为开关
```
public boolean waterFallMode = false; // 决定是否广泛展开分析，first-run建议false
public boolean hazardOpen = false; // llm相关的分析顺序决策，first-run建议false
public int waterFallDepth = 4; // 展开深度，waterFallMode = false时也生效，first-run建议4以下
```

```
由于这里内容会打印为json，condition（路径约束）信息较多，不利于阅读，first-run建议关闭，添加jsonignore
// 记录条件约束信息
@JsonIgnore
public LinkedList<ConditionNode> conditionNodes = new LinkedList<>();
@JsonIgnore
public LinkedList<Integer> conditionHashCodes = new LinkedList<>();
@JsonIgnore
public HashSet<UsedSiteRecord> usedSiteRecords = new HashSet<>();
```

#### IntelligentResolver.java中的检测行为开关
```
public static boolean ai_quick_dfg = false; // 决定是否开启llm摘要, first-run推荐false
```

#### VulDetector.java中的检测行为开关
```
public boolean processSrc = false; // 不处理源码目录，处理源码主要用于代码切片查询llm，并会落mysql库
```
不使用源码功能，可以不配mysql，并在application.yml中注释对应的jdbc配置

#### SinkDetector中的配置
```
public boolean process_dummy_switcher = false;
```
一般关闭，不启动发现boundary sink等dummy sink的功能