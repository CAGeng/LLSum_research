import re
import os
from collections import defaultdict

def filter_parameter_methods(pattern, file_path, folder_path):
    # 打开并读取 tpl_sort.txt 文件内容
    input_file_path = os.path.join(folder_path, 'tpl_sort.txt')
    with open(input_file_path, 'r') as input_file:
        lines = input_file.readlines()

    # 打开指定的输出文件用于写入过滤后的内容
    output_file_path = os.path.join(folder_path, file_path)
    with open(output_file_path, 'w') as output_file:
        for line in lines:
            # 将行分割为 "<---" 前后的两部分
            parts = line.split('<---')
            if len(parts) < 2:
                continue  # 如果没有 <---，跳过该行

            # 仅检查 "<---" 前面的部分是否包含指定的参数类型
            left_part = parts[0]
            if re.search(pattern, left_part):
                output_file.write(line)

    print(f"已完成文件 {file_path} 的过滤并输出到 {output_file_path}")

def process_multiple_folders(base_folder_path):
    # 文件夹列表，列出所有需要处理的文件夹名称
    folder_names = [
        'aliyun-java-sdk-core',
        'amqp-client',
        'aws-java-sdk-core',
        'fastjson',
        'guava',
        'hutool-core',
        'joda-time',
        'kubernetes-client',
        'minio',
        'weixin-java-miniapp'
    ]

    # 正则表达式和输出文件名的配对列表
    patterns_and_files = [
        (r"\((?:[^)]*,\s*)*(?:java\.lang\.Boolean|boolean)(?:\s*,[^)]*)*\)", "filter_boolean.txt"),
        (r"\((?:[^)]*,\s*)*(?:java\.lang\.Integer|int)(?:\s*,[^)]*)*\)", "filter_int.txt"),
        (r"\((?:[^)]*,\s*)*(?:java\.lang\.Byte|byte)(?:\s*,[^)]*)*\)", "filter_byte.txt"),
        (r"\([^\)]*-Enum[^\)]*\)", "filter_enum.txt"),
        (r"\([^\)]*-InterfaceClass[^\)]*\)", "filter_interface_class.txt"),
        (r"\([^\)]*-SuperClass[^\)]*\)", "filter_super_class.txt")
    ]

    for folder_name in folder_names:
        folder_path = os.path.join(base_folder_path, 'output', 'popular-components', folder_name, 'output')

        # 对每个文件夹中的每个正则表达式和输出文件名进行过滤
        for pattern, file_name in patterns_and_files:
            filter_parameter_methods(pattern, file_name, folder_path)

# 执行函数
if __name__ == '__main__':
    base_folder_path = '/Users/luca/dev/2025/pterosaur'
    process_multiple_folders(base_folder_path)
