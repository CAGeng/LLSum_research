# pterosaur

java -jar /Users/luca/dev/2025/pterosaur/target/pterosaur-0.0.1-SNAPSHOT.jar
/Users/luca/dev/2025/pterosaur/output/popular-components/amqp-client/output/tpl_sort.txt
/Users/luca/dev/2025/pterosaur/output/popular-components/amqp-client/downstream/amqp-client-5.22.0.jar,/Users/luca/dev/2025/pterosaur/output/popular-components/amqp-client/downstream/vertx-rabbitmq-client-4.5.10.jar
/Users/luca/dev/2025/pterosaur/llm/input/code/pilot-test.txt 0

java -jar /Users/luca/dev/2025/pterosaur/target/pterosaur-0.0.1-SNAPSHOT.jar
/Users/luca/dev/2025/pterosaur/output/popular-components/amqp-client/output/tpl_sort.txt
/Users/luca/dev/2025/pterosaur/output/popular-components/amqp-client/downstream
/Users/luca/dev/2025/pterosaur/llm/input/code/pilot-test.txt 0

# 1.get the top upstream jars & applications

(1)rank website
https://libraries.io/search?order=desc&platforms=Maven&sort=rank
![img_1.png](img_1.png)

(2)run script to get top star apps in github
[GitHubTopProjectsFetcher.java](src%2Fmain%2Fjava%2Fedu%2Ffudan%2Fpterosaur%2Fscript%2FGitHubTopProjectsFetcher.java)
The result will be stored in the output/tpl.txt
![img_2.png](img_2.png)

# 2.get the downstream jars

(1)usage in maven central
https://mvnrepository.com/artifact/com.alibaba/fastjson/2.0.53
![img.png](img.png)

(2)add the GAV in the txt which will be used in (2)
[pilots.txt](scripts%2Ffastjson%2Fpilots.txt)
![img_3.png](img_3.png)

(3)run script to get the jars
[porter.py](scripts%2Fporter.py)
[downstream](output%2Fpopular-components%2Ffastjson%2Fdownstream)
![img_4.png](img_4.png)

# 3.get the call chains from downstream jar to upstream jar(TPL)

[TPLDetector.java](src%2Fmain%2Fjava%2Fedu%2Ffudan%2Fpterosaur%2Fdetector%2FTPLDetector.java)
(1)config class path
the path should be the downstream jars folder
![img_5.png](img_5.png)

(2)config tpl package
the package name should be the TPL package name
![img_6.png](img_6.png)

(3)run script to the get call
[tpl.txt](output%2Ftpl.txt)
![img_7.png](img_7.png)

# 4.further processing for tpl.txt

(1)copy the result of call chains into the corresponding folder
[tpl.txt](output%2Fpopular-components%2Ffastjson%2Foutput%2Ftpl.txt)

(2)sort the result
[1_sort.py](scripts%2F1_sort.py)
[tpl_sort.txt](output%2Fpopular-components%2Ffastjson%2Foutput%2Ftpl_sort.txt)
![img_8.png](img_8.png)

(3)filter the result
[2_filter.py](scripts%2F2_filter.py)
![img_9.png](img_9.png)

# 5.generate the prompt for LLM

(1)get the k-dep IR
[CodeProvider.java](src%2Fmain%2Fjava%2Fedu%2Ffudan%2Fpterosaur%2Fscript%2FCodeProvider.java)

parm0 is the file path of the tpl_sort.txt
parm1 is the soot clas path (downstream jars folder path)
parm2 is the output path
parm3 controls whether to check all entries, it can be always 0(false)
parm4 is the dep of the call stack

e.g.
nohup /home/luca/env/jdk-17.0.13/bin/java -jar /home/luca/pterosaur-0.0.1-SNAPSHOT.jar
/home/luca/lib/pterosaur/output/popular-components/fastjson/output/tpl_sort.txt
/home/luca/lib/pterosaur/output/popular-components/fastjson/downstream
/home/luca/lib/pterosaur/llm/input/code/IR-fastjson.txt 0 3 > output-fastjson.log 2>&1 &

[IR-fastjson.txt](llm%2Finput%2Fcode%2FIR-fastjson.txt)
![img_10.png](img_10.png)

(2)construct prompt for LLM
[summary-generator.py](scripts%2Fz-llm%2Fsummary-generator.py)

the result will be stored in this file "conversation-fastjson.txt"
[conversation-fastjson.txt](llm%2Foutput%2Fconversation-fastjson.txt)
![img_11.png](img_11.png)

(3)get the propagation rules from the conversation
[rule-generator.py](scripts%2Fz-llm%2Frule-generator.py)

the result will be stored in this file "propagation-rule-fastjson.txt"
[propagation-rule-fastjson.txt](llm%2Foutput%2Frules%2Fpropagation-rule-fastjson.txt)
![img_12.png](img_12.png)




