import copy
import json

from prettytable import PrettyTable
import time
import openai
from tqdm import tqdm
import itertools
import wandb
from tenacity import retry, stop_after_attempt, wait_exponential
import os
import parser

api_key = "sk-yOWN4OQzmCgLBemn8e6355340b9e4b438c13632aC7D6Ad37"  # enter your OpenAI API key here
EVAL_MODEL = "gpt-4o"

use_wandb = False  # set to True if you want to use wandb to log your config and results

use_portkey = False  # set to True if you want to use Portkey to log all the prompt chains and their responses Check https://portkey
# .ai/

EVAL_MODEL_TEMPERATURE = 0.2
EVAL_MODEL_MAX_TOKENS = 3000

from openai import OpenAI

client = OpenAI(
    # defaults to os.environ.get("OPENAI_API_KEY")
    base_url="https://api.zhiyungpt.com/v1",
    api_key=api_key,
)

prompts = [{
    "role": "system",
    "content": """
You are a professional program analysis aid to summarize what the target method is doing and
what kind of data flow there in relates to the target method. """},
    {"role": "user",
     "content": """

Please adhere to the following conventions in your summary:

(1)Parameter Notation: Use p1,p2,p3 to represent the first, second, third parameters of the method
E.g.: 'p1' represents 'a', 'p2' represents 'b', there are no 'p3 or p4'
private void assignValue(String a, String b){
    a = b
}

(2)Object Reference: Use this to refer to the current object instance.
E.g.: 'this' represents the current object instantiated through class A
public class A{
    String name;
    private void setValue(String a){
        this.name = a;
    }
}

(3)Field Access: Use dot notation to represent nested fields, e.g., this.labelA.labelB
refers to field labelB within labelA of this.
E.g.: this.foo.name
public class A{
    Foo foo;
    private void assignValue(String a){
        this.foo.name = a;
    }
    
    class Foo{
        String name;
    }
}


A summary for a leaf procedure has been provided as a comment on the
corresponding call-site line within the code. You should:
(1)Evaluate the Condition: First, determine whether the condition specified in the
comment is satisfied within the current code context.
(2)Generate Summary: If the condition is satisfied, use it to help generate a
summary for the current code block.
(3)Ignore Irrelevant Dataflows: If the condition is not satisfied, disregard this
dataflow in your summary.
(4)Handle Uncertainty: If you are unsure whether the condition is satisfied,
reorganize and reinterpret the condition in a manner that aligns with the input
states of the current code.




Here is a few examples，and the analysis result will provide a summary in the form of annotations above the method.
Data Flow Summary and conditions should only contains the flow about param, field of the param, this, filed of this, ret, 
field of the ret which should not include local variables in the method. （Please pay attention to this rule!!!）.

Also,InArguments and OutArguments will only contain one element. If there are multiple elements, please create a PropagationRule to represent them separately!!!


Example 1 :

Method to be analyzed: <com.test.A: java.lang.String call(java.lang.String,com.test.Foo,int)>

package com.test;
public class A{
    Flag flag;
    String call(String a, Foo b, int c){
        String tmp1 = a + "test1";
        String tmp2 = a + "test2";
        String finalStr = a + "user";
        if (!flag.value.equals("ABC")){
            if (flag.name.equals(b.name)){
                assignValueMethodA(tmp1, b); // This line involves a dataflow from 'p1(tmp1) to p2(b).name
            }
        }
        assignValueMethodB(tmp2, b); // This line involves a dataflow from p1(tmp2) to p2(b).content
        if (c != 0){
            this.flag.score = c;
        }else{
            this.flag.score = 1000;
        }
        return finalStr
    }
    class Flag{
        String value;
        String name;
        int score;
    }
}

package com.test;
public Foo{
    String name;
    String content;
}


[Positive Analysis Result]: Please pay more attention to !!! [If there are multiple parameter data streams, they should be recorded separately, such as p1-->this.f1, p2-->this.f2, there will be two PropagationRule xml records. <InArguments>This tag can only have one parameter</InArguments>] 

<PropagationRule>
            <RuleID>RULE-ID-PROPAGATION-JHKLIBGFFG000008</RuleID>  # This RuleID should be randomly generated.The prefix "RULE-ID-PROPAGATION-" remains unchanged.
            <FunctionIdentifier>
                <NamespaceName>
                    <Value>com.test</Value>
                </NamespaceName>
                <ClassName>
                    <Value>A</Value>
                </ClassName>
                <FunctionName>
                    <Value>call</Value>
                </FunctionName>
                <ApplyTo implements="true" overrides="true" extends="true"/>
            </FunctionIdentifier>
            <Parameters>
                <ParamType>java.lang.String</ParamType>
                <ParamType>com.test.Foo</ParamType>
                <ParamType>int</ParamType>
            </Parameters>
            <HasPropagation>true</HasPropagation>
            <Condition>!this.flag.value.equals(p1) && this.flag.name.equals(p1.name)</Condition>
            <InArguments>0</InArguments> # If there are multiple parameter data streams, they should be recorded separately, such as p1-->this.f1, p2-->this.f2, there will be two PropagationRule xml records
            <OutArguments>1.name</OutArguments>
            <Comment>
            - The data flow shows that `p1` (`a`) is transferred into the `tmp1`, and the `tmp1` is transferred into 'p2'('b').name  
            - The flow is conditional on !this.flag.value.equals("ABC") && this.flag.name.equals(p2.name)
            </Comment>
</PropagationRule>

<PropagationRule>
            <RuleID>RULE-ID-PROPAGATION-00000002</RuleID>
            <FunctionIdentifier>
                <NamespaceName>
                    <Value>com.test</Value>
                </NamespaceName>
                <ClassName>
                    <Value>A</Value>
                </ClassName>
                <FunctionName>
                    <Value>call</Value>
                </FunctionName>
                <ApplyTo implements="true" overrides="true" extends="true"/>
            </FunctionIdentifier>
            <Parameters>
                <ParamType>java.lang.String</ParamType>
                <ParamType>com.test.Foo</ParamType>
                <ParamType>int</ParamType>
            </Parameters>
            <HasPropagation>true</HasPropagation>
            <Condition>NOC</Condition>
            <InArguments>0</InArguments>
            <OutArguments>1.content</OutArguments>
            <Comment>
            - The data flow shows that `p1` (`a`) is transferred into the `tmp2`, and the `tmp2` is transferred into 'p2'('b').content
            - The flow is no condition
            </Comment>
</PropagationRule>

<PropagationRule>
            <RuleID>RULE-ID-PROPAGATION-00000003</RuleID>
            <FunctionIdentifier>
                <NamespaceName>
                    <Value>com.test</Value>
                </NamespaceName>
                <ClassName>
                    <Value>A</Value>
                </ClassName>
                <FunctionName>
                    <Value>call</Value>
                </FunctionName>
                <ApplyTo implements="true" overrides="true" extends="true"/>
            </FunctionIdentifier>
            <Parameters>
                <ParamType>java.lang.String</ParamType>
                <ParamType>com.test.Foo</ParamType>
                <ParamType>int</ParamType>
            </Parameters>
            <HasPropagation>true</HasPropagation>
            <Condition> p3!=0 </Condition> # p3 means the third parm
            <InArguments>2</InArguments>
            <OutArguments>this.flag.score</OutArguments>
            <Comment>
            - The data flow shows that `p3` (`c`) is transferred into this.flag.score
            - The flow is no condition
            </Comment>
</PropagationRule>


<PropagationRule>
            <RuleID>RULE-ID-PROPAGATION-00000003</RuleID>
            <FunctionIdentifier>
                <NamespaceName>
                    <Value>com.test</Value>
                </NamespaceName>
                <ClassName>
                    <Value>A</Value>
                </ClassName>
                <FunctionName>
                    <Value>call</Value>
                </FunctionName>
                <ApplyTo implements="true" overrides="true" extends="true"/>
            </FunctionIdentifier>
            <Parameters>
                <ParamType>java.lang.String</ParamType>
                <ParamType>com.test.Foo</ParamType>
                <ParamType>int</ParamType>
            </Parameters>
            <HasPropagation>true</HasPropagation>
            <Condition>NOC</Condition>
            <InArguments>0</InArguments>
            <OutArguments>return</OutArguments>
            <Comment>
            - The data flow shows that `p1` (`a`) is transferred into 'return'
            - The flow is no condition
            </Comment>
</PropagationRule>


Example 2 :

Method to be analyzed: <com.test:A void call(java.lang.String)>

package com.test;
public class A{
    void call(String a){
        String tmp1 = a + "test1";
        System.out.println(tmp1);
    }
}


[Positive Analysis Result]: 

<PropagationRule>
            <RuleID>RULE-ID-PROPAGATION-00000002</RuleID>
            <FunctionIdentifier>
                <NamespaceName>
                    <Value>com.test</Value>
                </NamespaceName>
                <ClassName>
                    <Value>A</Value>
                </ClassName>
                <FunctionName>
                    <Value>call</Value>
                </FunctionName>
                <ApplyTo implements="true" overrides="true" extends="true"/>
            </FunctionIdentifier>
            <Parameters>
                <ParamType>java.lang.String</ParamType>
            </Parameters>
            <HasPropagation>false</HasPropagation>
            <Comment>
            - There is no obvious data flow here as parameter p1 does not flow to other parameters, this, or return.
            </Comment>
</PropagationRule>

Now the final question, tell me the summary of this method.
(1)Please provide the analysis results strictly in this XML format according to the example. 
(2)Don't forget to merge the result (p1-->a-->b--->p2 should be p1 --> p2)
(3)Please think step by step and provide the detailed analysis results for each step. 

"""
     }]



def get_analusis_code():
    analysis_code = """
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.rabbitmq.client;

public class Envelope {
    public long getDeliveryTag() {
        return this._deliveryTag;
    }

}
"""
    return analysis_code

def get_code_from_file(file_path):
    try:
        with open(file_path, 'r') as file:
            code = file.read()
            return code
    except FileNotFoundError:
        print(f"Error: The file {file_path} was not found.")
        return None
    except Exception as e:
        print(f"An error occurred while reading the file: {e}")
        return None


import time
import copy

def generate(output_file, code_file, batch_size=1):
    """
    批量处理 methods 列表，将指定数量的 analysis_code 拼接到 prompts 中进行 GPT 调用。
    :param batch_size: 每次拼接的 analysis_code 数量，默认是 1。
    """
    methods = parser.parse_methods_by_file(code_file)

    # 初始化原始 prompts
    original_prompts = prompts

    # 统计变量
    total_methods = len(methods)  # 总方法数
    processed_count = 0          # 成功处理的计数
    error_count = 0              # 异常发生的计数

    # 遍历 methods 列表，按 batch_size 分组
    for batch_index in range(0, total_methods, batch_size):
        retries = 0  # 重试计数
        success = False  # 标记当前批次处理是否成功

        while retries < 3 and not success:  # 最多重试 2 次
            try:
                # 获取当前批次的 analysis_code
                batch_methods = methods[batch_index:batch_index + batch_size]

                # 深拷贝 prompts，确保每次循环独立
                conversation = copy.deepcopy(original_prompts)

                # 拼接所有 analysis_code 到 prompts 的第二个元素的 content 后面
                analysis_code_combined = "\n".join(batch_methods)
                conversation[1]["content"] += analysis_code_combined

                # 调用 GPT 模型
                response = client.chat.completions.create(
                    model=EVAL_MODEL,
                    messages=conversation,
                    max_tokens=EVAL_MODEL_MAX_TOKENS,  # 限制返回值大小
                    temperature=EVAL_MODEL_TEMPERATURE  # 提高准确性和简洁性
                ).choices[0].message.content

                # 在控制台输出返回值
                print(f"Processing batch {batch_index + 1}-{min(batch_index + batch_size, total_methods)}/{total_methods}")
                print(f"Response from GPT:\n{response}\n")

                # 保存当前对话到文件
                conversation.append(
                    {
                        "role": "assistant",
                        "content": response
                    }
                )
                save_conversation_to_file(output_file, conversation)

                # 统计已处理数
                processed_count += len(batch_methods)
                success = True  # 标记当前批次处理成功

            except Exception as e:
                # 捕获异常并输出
                print(f"Error processing batch {batch_index + 1}-{min(batch_index + batch_size, total_methods)}: {e}")

                # 统计错误次数
                error_count += len(batch_methods)
                retries += 1  # 增加重试计数

                # 如果还有重试机会，等待一段时间
                if retries < 3:
                    print(f"Retrying... ({retries}/2)")
                    time.sleep(5)  # 等待 2 秒再重试

        # 输出当前统计信息
        print(f"Progress: {processed_count}/{total_methods} methods processed successfully.")
        print(f"Errors encountered so far: {error_count}\n")



def save_conversation_to_file(filename, conversation):
    with open(filename, 'a', encoding='utf-8') as f:  # 使用 'a' 模式
        for entry in conversation:
            f.write(f"=== {entry['role']} ===\n")
            f.write(f"{entry['content']}\n")


if __name__ == "__main__":
    output_file = '/Users/luca/dev/2025/pterosaur/llm/output/conversation-hutool-core.txt'
    code_file = "/Users/luca/dev/2025/pterosaur/llm/input/code/IR-hutool-core.txt"
    generate(output_file, code_file, 1)
