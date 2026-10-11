def process_text(text):
    # 分割文本为行
    lines = text.strip().split('\n')
    key_count = {}

    for line in lines:
        if '<---' in line:
            key, value = line.split('<---')
            key = key.strip()
            # 统计键的出现次数
            if key in key_count:
                key_count[key] += 1
            else:
                key_count[key] = 1

    return key_count

def calculate_statistics(key_count):
    total_keys = len(key_count)
    single_occurrences = sum(1 for count in key_count.values() if count == 1)

    if total_keys > 0:
        percentage_single = (single_occurrences / total_keys) * 100
    else:
        percentage_single = 0

    return total_keys, single_occurrences, percentage_single

if __name__ == "__main__":
    # 替换为你的文件路径列表
    file_paths = [
        '/Users/luca/dev/2025/pterosaur/output/popular-components/aliyun-java-sdk-core/output/tpl_sort.txt',
        '/Users/luca/dev/2025/pterosaur/output/popular-components/amqp-client/output/tpl_sort.txt',
        '/Users/luca/dev/2025/pterosaur/output/popular-components/fastjson/output/tpl_sort.txt',
        '/Users/luca/dev/2025/pterosaur/output/popular-components/guava/output/tpl_sort.txt',
        '/Users/luca/dev/2025/pterosaur/output/popular-components/hutool-core/output/tpl_sort.txt',
        '/Users/luca/dev/2025/pterosaur/output/popular-components/joda-time/output/tpl_sort.txt',
        '/Users/luca/dev/2025/pterosaur/output/popular-components/kubernetes-client/output/tpl_sort.txt',
        '/Users/luca/dev/2025/pterosaur/output/popular-components/minio/output/tpl_sort.txt',
        '/Users/luca/dev/2025/pterosaur/output/popular-components/weixin-java-miniapp/output/tpl_sort.txt',
        '/Users/luca/dev/2025/pterosaur/output/popular-components/aws-java-sdk-core/output/tpl_sort.txt'
    ]

    for file_path in file_paths:
        try:
            # 从文件中读取文本内容
            with open(file_path, 'r', encoding='utf-8') as file:
                text_content = file.read()

            key_count = process_text(text_content)
            total_keys, single_occurrences, percentage_single = calculate_statistics(key_count)

            print(f"文件: {file_path}")
            print(f"总键数: {total_keys}")
            print(f"只出现一次的键数: {single_occurrences}")
            print(f"只出现一次的键占总键数的百分比: {percentage_single:.2f}%")
            print("-" * 40)  # 分隔线

        except Exception as e:
            print(f"处理文件 {file_path} 时发生错误: {e}")