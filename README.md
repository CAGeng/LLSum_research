## Directory Structure

* /LLM/: llm code
* /sast/: static analysis code
* /output/vul-cases/: discoverd vulnerabilities
* /output/raw-output/: raw output
* LLM/README.md & sast/alligator-ifds/readme.md: readme details

## Main Configuration Sections:

Input/Output Paths:
```Yaml
soot:
  output:
    path: /your/output/path/here # Replace with your output directory

list:
  sootInputPaths:
    - /path/to/your/project/classes # Root directory of the compiled class files for the target project
Import Analysis Rules:
Yaml
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
  entry: # Entry point (source) rules for vulnerability reports
    path: rules/EntrySet.xml
    switcher: on
  conditional: on
  taintFlag: on
 ```
Analysis Settings (Recommended for first run):
```Yaml
analysis:
  taintSanitizeSwitcher: off
  simpleDataPathSwitcher: on
  onlyDataflowCallStackInter: off # Turn off to track the complete src-sink path, including side-path methods
  fieldSensitiveSwitcher: on # Enable field-sensitive analysis
  sparkSwitcher: on # Call graph construction algorithm, spark is more accurate but may miss some edges
  conditionSummarySwitcher: on
  chainTrackMode: off
 ```
### Execution
Configure the entry (source) and exit (sink) rules for vulnerability reports in the directory path-recovery/src/main/resources/rules/.
Run the main class: path-recovery/src/main/java/app/IFDSApplication.java.
### Output Files
#### Main Results:
[soot.output.path]/[project_name]/Analyze_result.json

e.g. output/raw-output/hertzbeat/Analyze_result.json
#### Logs containing various types of analysis details.
* Dataflow Summaries:
[out_path]/All-summaries.json
* Missing Methods:
[out_path]/logs/emptyMethod.txt
[out_path]/logs/calledLibMethods.txt
* Polymorphism Ambiguities (Type Insensitivity):
[out_path]/logs/cg/CGOpt.txt
[out_path]/uncertain_call_edge.json
