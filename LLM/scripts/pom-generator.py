import os

def generate_dependencies(folder_path):
    # 读取 pilots.txt 文件
    input_file_path = os.path.join(folder_path, 'pilots.txt')
    with open(input_file_path, 'r') as input_file:
        lines = input_file.readlines()

    # 写入生成的依赖到 pom.xml
    output_file_path = os.path.join(folder_path, 'pom.xml')
    with open(output_file_path, 'w') as output_file:
        # 写入外层 <dependencies> 起始标签
        output_file.write("<dependencies>\n")

        for line in lines:
            line = line.strip()
            if not line:
                continue  # 跳过空行

            # 按照冒号分割每一行，分别提取 group, artifact 和 version
            parts = line.split(':')
            group = parts[0] if len(parts) > 0 else ''
            artifact = parts[1] if len(parts) > 1 else ''
            version = parts[2] if len(parts) > 2 else 'LATEST'  # 缺少版本时使用 "latest"

            # 写入到 pom.xml 文件中，格式化成 Maven 依赖格式
            dependency = f"""    <dependency>
        <groupId>{group}</groupId>
        <artifactId>{artifact}</artifactId>
        <version>{version}</version>
    </dependency>
"""
            output_file.write(dependency)

        # 写入外层 <dependencies> 结束标签
        output_file.write("</dependencies>\n")

    print(f"已生成 {output_file_path} 文件")

def process_multiple_folders(base_folder_path):
    # 列出所有需要处理的文件夹名称
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

    for folder_name in folder_names:
        folder_path = os.path.join(base_folder_path, 'scripts', folder_name)
        generate_dependencies(folder_path)

# 执行函数
if __name__ == '__main__':
    base_folder_path = '/Users/luca/dev/2025/pterosaur'
    process_multiple_folders(base_folder_path)
