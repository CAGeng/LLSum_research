# LLSum

LLSum is a Java static analysis prototype for inter-procedural taint tracking and vulnerability detection. The codebase combines traditional static analysis, summary reuse, optional LLM-assisted data-flow summarization, and a Spring Boot API layer for triggering analysis jobs.

## What LLSum Does

Static taint analysis reports a vulnerability when data from an untrusted source can reach a dangerous sink. In large Java systems, precise inter-procedural analysis is expensive, while imprecise summaries can propagate false positives across many calling contexts.

LLSum combines three components:

1. Static analysis and summary reuse: builds call graphs/control-flow graphs with Soot, tracks taint facts, aliases, field-sensitive access paths, path constraints, and reuses method summaries.
2. Lazy summarization and incoming-state collection: delays summarization at selected call sites so a method can be summarized after enough caller contexts have been collected.
3. Selective LLM summarization: uses differentiated incoming states and semantic inlining to prompt an LLM for conditional data-flow summaries, then validates them through lightweight dependency and def-use analysis.

## Repository Layout

```text
.
├── alligator-ifds/       # Core LLSum static analysis and LLM-assisted summarization prototype
├── alligator-service/    # Service layer wrapping IFDS analysis for the web module
├── alligator-web/        # Spring Boot API for triggering analysis and retrieving results
├── alligator-base/       # Shared Maven module
├── Container/            # Runtime containers used by instrumentation/fuzzing-side code
├── Instrumentor/         # Bytecode instrumentation utilities
├── Testcase/             # Java test application and SQL resources
├── docs/                 # Notes
└── python/               # Result analysis scripts/notebooks
```

The main implementation is in `alligator-ifds`.

```text
alligator-ifds/src/main/java
├── app/
│   ├── IFDSApplication.java          # Spring Boot entry point
│   ├── configuration/                # Soot/input/output/analysis configuration
│   └── runner/                       # CommandLineRunner entry points
├── basic/                            # CFG/CG, Soot initialization, data-flow primitives, utilities
├── detect/
│   ├── VulDetector.java              # Main vulnerability detection orchestration
│   ├── Summarizer.java               # Standalone summary generation path
│   ├── dataflow/                     # DataFlowTrack, DataFlowSummarize, SummaryReuse
│   ├── knowledge/                    # Entry/source/sink/constraint knowledge
│   └── summary/                      # CG segmentation, differentiation, hazard snapshots
├── TranModel/                        # Transfer model, taint nodes, symbolic state and propagation rules
├── intelligent/                      # LLM/RPC integration, prompt-side parsing, condition handling
└── sourceprocess/                    # Optional source-code database indexing
```

## Analysis Workflow

The default command-line analysis path is:

```text
IFDSApplication
  -> VulDetectRunner
  -> VulDetector
  -> DataFlowTrack
```

At a high level, the workflow is:

1. Build analysis context: initialize Soot input paths, load entry/source/sink/propagation rules, and construct call graph information.
2. Detect candidate source-to-sink chains: `VulDetector` organizes vulnerability-specific detection contexts.
3. Track inter-procedural data flow: `DataFlowTrack` propagates taint facts, aliases, field-sensitive access paths, and path conditions.
4. Reuse summaries: `SummaryReuse` applies previously generated summaries when a call can be resolved without re-analysis.
5. Optionally apply lazy/LLM summarization: when hazard mode and LLM switches are enabled, the analyzer collects incoming states, differentiates contexts, predicts complexity, queries the LLM, parses conditional summaries, verifies/refines them, and reintegrates them into the static analysis.

## Module Responsibilities

| Responsibility | Code location |
| --- | --- |
| Static taint analysis | `detect/dataflow/DataFlowTrack.java`, `TranModel/Rules/` |
| Method summary | `basic/DefaultDetector/MethodSummary.java`, `detect/dataflow/DataFlowSummarize.java` |
| Summary reuse | `detect/dataflow/SummaryReuse.java` |
| Conditional summary | `TranModel/datastruct/ConditionNode.java`, `TranModel/Rules/ConditionRule.java`, `intelligent/condition/` |
| Incoming-state differentiation | `detect/summary/Differentiation.java` |
| Topological hazards / lazy summarization | `detect/summary/CGSegmenter.java`, `detect/summary/datastructure/Snapshot.java`, `DataFlowTrack.hazardOpen` |
| Complexity prediction | `intelligent/predict/ComplexityCalculate.java`, `intelligent/predict/ComplexityProcessor.java` |
| LLM query and parsing | `intelligent/GptRpcProxy.java`, `intelligent/IntelligentResolver.java`, `intelligent/PropagationRuleParser.java` |
| Rule knowledge base | `alligator-ifds/src/main/resources/rules/`, `entryrule/` |

## Requirements

- JDK 8
- Maven 3.x
- A Java bytecode input directory for the target project. The configured path should contain package-style class directories, for example `org/example/...`.
- Optional: MySQL, only if source-code indexing or the web result database is used.
- Optional: an XML-RPC LLM service at `http://localhost:8000`, only if LLM-assisted summarization is enabled.

## Build

Build the Maven modules managed by the root `pom.xml`:

```bash
mvn -DskipTests package
```

Build only the core analyzer and its required modules:

```bash
mvn -pl alligator-ifds -am -DskipTests package
```

`Container/` and `Instrumentor/` are separate Maven modules with their own parent coordinates and are not included in the root parent build.

## Configure the Analyzer

The primary configuration file is:

```text
alligator-ifds/src/main/resources/application.yml
```

Important fields:

```yaml
soot:
  output:
    path: /path/to/output
    filename: Analyze_result.json
    insFilename: Instructions.json

list:
  sootInputPaths:
    - /path/to/target/classes-or-unpacked-jar
  scannedMybatisXmlDirs:
    - /path/to/mybatis/xmls
  sourceCode: /path/to/source-code

rules:
  core:
    path: rules/core.xml
    pathSwitcher: on
    thirdPartyPath: rules/java-core.xml
    thirdPartyPathSwitcher: on
  added:
    path: rules/risky_funcs.xml
    pathSwitcher: on
  extend:
    path: rules/extend.xml
    pathSwitcher: on
  entry:
    path: rules/EntrySet.xml
    switcher: on
  conditional: on
  taintFlag: on

analysis:
  taintSanitizeSwitcher: off
  simpleDataPathSwitcher: on
  onlyDataflowCallStackInter: off
  fieldSensitiveSwitcher: on
  sparkSwitcher: on
  conditionSummarySwitcher: on
  chainTrackMode: off
```

For a first run, keep source-code database processing and LLM summarization disabled unless the supporting services are configured.

Useful code-level switches:

```java
// detect/dataflow/DataFlowTrack.java
public boolean waterFallMode = false;
public static boolean hazardOpen = false;
public int waterFallDepth = 4;

// intelligent/IntelligentResolver.java
public static boolean ai_quick_dfg = false;

// detect/VulDetector.java
public boolean processSrc = false;

// detect/knowledge/sink/SinkDetector.java
public boolean process_dummy_switcher = false;
```

## Run

Run the command-line analyzer:

```bash
mvn -pl alligator-ifds -am spring-boot:run
```

This starts `app.IFDSApplication`; `VulDetectRunner` is enabled by default and calls `VulDetector.detect()`.

To run the web API:

```bash
mvn -pl alligator-web -am spring-boot:run
```

Main API endpoints:

```text
POST /sat/analysis/result
POST /sat/analysis/result_con
GET  /sat/analysis/result/{id}
GET  /sat/analysis/result/json/{id}
GET  /sat/triggerpoint
```

`POST /sat/analysis/result` accepts an `AnalysisRequest` with a comma-separated `projectPath` and optional `vulType`, then returns JSON analysis results.

## Optional LLM Service

LLM calls are routed through `intelligent/GptRpcProxy.java` to:

```text
http://localhost:8000
```

Expected XML-RPC methods include:

```text
gptapi_dfg
gptapi_conditional_sum
gptapi_pointto
gptapi_rankexp
```

The conditional-summary path uses `gptapi_conditional_sum(sourceCode, csTaints, conditionDiffs)`. LLSum then parses the response with `PropagationRuleParser` and verifies/refines the result before reuse.

## Outputs

Analysis output is written under `soot.output.path`, with a timestamped subdirectory created at startup. Typical files include:

```text
Analyze_result.json
Instructions.json
logs/
summaries.json
```

The exact output depends on the selected runner and analysis switches.

## Feature Map

The implementation contains the following analysis features:

- conditional summary generation;
- semantic inlining and prompt construction;
- summary verification through static analysis;
- lazy summarization with topological hazards;
- condition differentiation over incoming states;
- state-driven complexity prediction for selective LLM invocation.

## Algorithm Mapping

In the analysis algorithm, `segment` corresponds to `DetectContext` in the implementation.

| Pseudocode subprocedure | Relative path + line |
| --- | --- |
| `analyze()` | `alligator-ifds/src/main/java/detect/VulDetector.java:83` |
| `buildCallGraph()` | `alligator-ifds/src/main/java/detect/VulDetector.java:220`; `alligator-ifds/src/main/java/basic/cg/CG.java:178` |
| `preprocessSegments(cg)` | `alligator-ifds/src/main/java/detect/VulDetector.java:431`; `alligator-ifds/src/main/java/detect/summary/CGSegmenter.java:29` |
| `initializeWorklist(segments)` | `alligator-ifds/src/main/java/detect/summary/CGSegmenter.java:133` |
| `nextSegment(worklist)` | `alligator-ifds/src/main/java/detect/summary/CGSegmenter.java:140`; `alligator-ifds/src/main/java/detect/VulDetector.java:614` |
| `analyzeSegment(segment, worklist)` | `alligator-ifds/src/main/java/detect/dataflow/DataFlowTrack.java:116`; `alligator-ifds/src/main/java/detect/dataflow/DataFlowTrack.java:241` |
| `buildCFG(segment)` | `alligator-ifds/src/main/java/detect/dataflow/DataFlowTrack.java:308`; `alligator-ifds/src/main/java/basic/cfg/CFG.java:109` |
| `scheduler(segment, input_states)` | `alligator-ifds/src/main/java/detect/dataflow/DataFlowTrack.java:657`; `alligator-ifds/src/main/java/intelligent/predict/ComplexityCalculate.java:18` |
| `LLMProcess(segment)` | `alligator-ifds/src/main/java/detect/dataflow/DataFlowTrack.java:722`; `alligator-ifds/src/main/java/intelligent/IntelligentResolver.java:97` |
| `RPOOrder(cfg)` | `alligator-ifds/src/main/java/TranModel/TranUtil.java:29` |
| `CallEdges` / `edge.target` | `alligator-ifds/src/main/java/detect/dataflow/DataFlowTrack.java:365`; `alligator-ifds/src/main/java/basic/cg/CG.java:82` |
| `addToWorklist(callee.segment)` | `alligator-ifds/src/main/java/detect/summary/CGSegmenter.java:158` |
| `applyHazard(node, edge)` | `alligator-ifds/src/main/java/detect/dataflow/DataFlowTrack.java:611` |
| `saveSnapshot(next)` | `alligator-ifds/src/main/java/detect/dataflow/DataFlowTrack.java:617`; `alligator-ifds/src/main/java/detect/summary/datastructure/Snapshot.java:14` |
| `applySummary(edge)` | `alligator-ifds/src/main/java/detect/dataflow/DataFlowTrack.java:466`; `alligator-ifds/src/main/java/detect/dataflow/SummaryReuse.java:19` |
| `reusePreciseSummaries(segment)` | `alligator-ifds/src/main/java/detect/VulDetector.java:629`; `alligator-ifds/src/main/java/detect/dataflow/DataFlowTrack.java:1708` |
