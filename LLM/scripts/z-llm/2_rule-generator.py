import os
import xml.etree.ElementTree as ET

def extract_propagation_rules(file_path, output_file):
    """
    从文件中提取所有```xml和```之间的PropagationRule块，并保存到指定的文件中。

    :param file_path: 输入文件路径
    :param output_file: 输出文件路径
    """
    try:
        with open(file_path, 'r', encoding='utf-8') as file:
            content = file.read()

        # 定义标记
        start_tag = "```xml"
        end_tag = "```"
        rules = []

        # 搜索所有的PropagationRule块
        start_index = content.find(start_tag)
        while start_index != -1:
            end_index = content.find(end_tag, start_index + len(start_tag))
            if end_index == -1:
                break  # 没有结束标记，退出循环

            # 提取PropagationRule内容
            rule_content = content[start_index + len(start_tag):end_index].strip()
            # rule_content = rule_content.replace("&lt;init&gt;", "init")
            rules.append(rule_content)

            # 查找下一个块
            start_index = content.find(start_tag, end_index + len(end_tag))

        # 保存所有提取到的PropagationRule块到文件
        with open(output_file, 'w', encoding='utf-8') as output:
            for i, rule in enumerate(rules, start=1):
                output.write(rule + "\n\n")

        print(f"Successfully extracted {len(rules)} rules to {output_file}.")
    except FileNotFoundError:
        print(f"Error: The file {file_path} was not found.")
    except Exception as e:
        print(f"An error occurred: {e}")


if __name__ == "__main__":

    # 示例调用
    propagation_input_txt = "/Users/luca/dev/2025/pterosaur/llm/output/conversation-hutool-core.txt"  # 输入XML文件路径
    propagation_output_txt = "/Users/luca/dev/2025/pterosaur/llm/output/rules/propagation-rule-hutool-core.txt"  # 输出文件路径

    # 确保输入文件存在
    if not os.path.exists(propagation_input_txt):
        print(f"Error: The input file {propagation_input_txt} does not exist.")
    else:
        extract_propagation_rules(propagation_input_txt, propagation_output_txt)

