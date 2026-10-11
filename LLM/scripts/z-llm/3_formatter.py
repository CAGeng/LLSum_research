import re

def escape_condition(condition):
    """转义Condition标签中的特殊字符"""
    condition = condition.replace("<=", "&lt;=").replace(">=", "&gt;=")
    condition = condition.replace("&", "&amp;")
    condition = re.sub(r'(?<=\s)<(?=\s)', '&lt;', condition)
    condition = re.sub(r'(?<=\s)>(?=\s)', '&gt;', condition)
    return condition

def replace_special_tags(content):
    """替换特殊标签"""
    content = content.replace("&lt;init&gt;", "init")
    content = content.replace("<init>", "&lt;init&gt;")
    return content

def is_valid_out_argument(out_argument):
    """检查 OutArguments 是否有效"""
    valid_pattern = r'^(return|this|return\.[a-zA-Z_][\w.]*|this\.[a-zA-Z_][\w.]*)$'
    return re.match(valid_pattern, out_argument) is not None

def is_valid_in_argument(in_argument):
    """检查 InArguments 是否有效"""
    valid_pattern = r'^(this(\.[a-zA-Z_][\w.]*)*|[0-2])$'
    return re.match(valid_pattern, in_argument) is not None

def check_out_arguments(content):
    """检查传入的 XML 内容中的 OutArguments 和 InArguments，并返回有问题的 RuleID"""
    problematic_rule_ids = []

    lines = content.splitlines()
    for line in lines:
        rule_id_match = re.search(r'<RuleID>(.*?)</RuleID>', line)
        if rule_id_match:
            rule_id = rule_id_match.group(1)

        # 查找 <OutArguments> 和 </OutArguments> 标签中的内容
        match_out = re.search(r'<OutArguments>(.*?)</OutArguments>', line)
        if match_out:
            out_arguments = match_out.group(1)
            if not is_valid_out_argument(out_arguments):
                problematic_rule_ids.append(rule_id)

        # 查找 <InArguments> 和 </InArguments> 标签中的内容
        match_in = re.search(r'<InArguments>(.*?)</InArguments>', line)
        if match_in:
            in_arguments = match_in.group(1)
            if not is_valid_in_argument(in_arguments):
                problematic_rule_ids.append(rule_id)

    return problematic_rule_ids

def update_rule_ids(file_path, start_number):
    """更新文件中所有的 RuleID，根据给定的起始数字递增"""
    try:
        with open(file_path, 'r', encoding='utf-8') as file:
            content = file.read()

        pattern = re.compile(r'RULE-ID-PROPAGATION-(\d{8})')

        def replacement(match):
            nonlocal start_number
            new_rule_id, start_number = increment_rule_id(match, start_number)
            return new_rule_id

        updated_content = re.sub(pattern, replacement, content)
        updated_content = re.sub(r'<Condition>(.*?)</Condition>', lambda m: f'<Condition>{escape_condition(m.group(1))}</Condition>', updated_content)
        updated_content = replace_special_tags(updated_content)

        # 检查 OutArguments 和 InArguments
        problematic_ids = check_out_arguments(updated_content)
        if problematic_ids:
            print("有问题的 RuleID:", problematic_ids)

        with open(file_path, 'w', encoding='utf-8') as file:
            file.write(updated_content)

    except Exception as e:
        print(f"处理文件时发生错误: {e}")

def increment_rule_id(match, start_number):
    """根据给定的起始数字递增 RuleID"""
    current_id = int(match.group(1))
    new_id = start_number
    start_number += 1
    return f'RULE-ID-PROPAGATION-{new_id:08d}', start_number

def main():
    file_path = "/Users/luca/dev/2025/pterosaur/llm/output/rules/propagation-rule-hutool-core.txt"  # 这里可以修改为你的文件路径
    start_number = 1  # 设置开始的 RuleID 数字

    update_rule_ids(file_path, start_number)
    print(f"File '{file_path}' has been updated with RuleIDs starting from {start_number}.")

if __name__ == '__main__':
    main()