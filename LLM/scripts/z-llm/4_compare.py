import json

def compare_json_files(file1, file2, output_file):
    # 读取第一个 JSON 文件
    with open(file1, 'r') as f1:
        data1 = json.load(f1)

    # 读取第二个 JSON 文件
    with open(file2, 'r') as f2:
        data2 = json.load(f2)

    # 将第一个 JSON 数据转换为以 callStackMethods 为键的字典
    data1_dict = {tuple(item["callStackMethods"]): item for item in data1["Test"]}

    # 将第二个 JSON 数据转换为以 callStackMethods 为键的字典
    data2_dict = {tuple(item["callStackMethods"]): item for item in data2["Test"]}

    # 初始化一个列表来存储不匹配的结果
    mismatched_results = []
    matched_count = 0
    total_count = len(data2_dict)  # 以第二个文件的总项数为基准

    # 遍历第二个 JSON 数据
    for item2 in data2["Test"]:
        call_stack_methods = tuple(item2["callStackMethods"])

        # 检查第一个 JSON 数据中是否存在相同的 callStackMethods
        if call_stack_methods in data1_dict:
            item1 = data1_dict[call_stack_methods]

            # 比较 hasDataFlow 的值
            has_data_flow_1 = item1["dataFlowResult"]["hasDataFlow"]
            has_data_flow_2 = item2["dataFlowResult"]["hasDataFlow"]

            if has_data_flow_1 != has_data_flow_2:
                # 如果不匹配，保存 callStackMethods 和 hasDataFlow 的结果
                mismatched_results.append({
                    "callStackMethods": call_stack_methods,
                    "hasDataFlow_file1": has_data_flow_1,
                    "hasDataFlow_file2": has_data_flow_2
                })
            else:
                matched_count += 1  # 计入匹配数

    # 将不匹配的结果写入新的 JSON 文件
    with open(output_file, 'w') as f:
        json.dump(mismatched_results, f, indent=4)

    # 计算不匹配数和匹配率
    unmatched_count = total_count - matched_count
    matching_ratio = (matched_count / total_count * 100) if total_count > 0 else 0

    # 打印报表
    print(f'Total Count (基于第二个文件): {total_count}')
    print(f'Matched Count: {matched_count}')
    print(f'Unmatched Count: {unmatched_count}')
    print(f'Matching Ratio: {matching_ratio:.2f}%')
    print(f'Mismatched results saved to {output_file}')


def main():
    file1 = '/Users/luca/dev/research/output/Analyze_result-weixin-java-miniapp-tpl.json'
    file2 = '/Users/luca/dev/research/output/Analyze_result-weixin-java-miniapp-tplno-summary.json'
    output_file = '/Users/luca/dev/research/output/mismatched_results_weixin-java-miniapp.json'

    # 调用比较函数
    compare_json_files(file1, file2, output_file)


if __name__ == "__main__":
    main()

