from collections import defaultdict
import os

def sort_methods_by_consistency(folder_path):
    # 定义输入和输出文件路径
    input_file = os.path.join(folder_path, 'tpl.txt')
    output_file = os.path.join(folder_path, 'tpl_sort.txt')

    # 创建一个字典，以 `--->` 前面的方法签名为键，将对应的行添加到列表
    method_groups = defaultdict(list)

    # 读取并处理 tpl.txt 文件内容
    with open(input_file, 'r') as file:
        lines = file.readlines()
        for line in lines:
            if '--->' in line:
                parts = line.split('--->')
                if len(parts) == 2:
                    after_signature = parts[1].strip()  # 获取 `--->` 前的方法签名
                    # 将行内容按 `before_signature` 归类
                    method_groups[after_signature].append(parts[1].strip() + "<---" + parts[0])

    # 按 `before_signature` 出现的次数从高到低排序
    sorted_methods = sorted(method_groups.items(), key=lambda x: len(x[1]), reverse=True)

    # 将排序后的结果写入 `tpl_sort.txt`
    with open(output_file, 'w') as file:
        for before_signature, grouped_lines in sorted_methods:
            for line in grouped_lines:
                file.write(line + '\n')

    print(f"已按一致性数量排序并写入文件: {output_file}")

def process_multiple_folders(base_folder_path):
    # 文件夹列表，列出所有需要处理的文件夹名称
    folder_names = [
        # 'aliyun-java-sdk-core',
        # 'amqp-client',
        # 'aws-java-sdk-core',
        # 'fastjson',
        # 'guava',
        'hutool-core'
        # 'joda-time',
        # 'kubernetes-client',
        # 'minio',
        # 'weixin-java-miniapp'
    ]

    for folder_name in folder_names:
        folder_path = os.path.join(base_folder_path, 'output', 'popular-components', folder_name, 'output')
        # 处理每个文件夹
        sort_methods_by_consistency(folder_path)

# 执行函数
if __name__ == '__main__':
    base_folder_path = '/Users/luca/dev/2025/pterosaur'
    process_multiple_folders(base_folder_path)
