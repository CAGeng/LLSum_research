import sys


def parse_methods_has_context(file_path):
    """
    从指定文件中读取内容，按"Method to be analyzed:"分割成多个段落，并返回这些段落的列表。
    """
    try:
        with open(file_path, 'r') as file:
            content = file.read()
            # 分割内容，但保留"Method to be analyzed:"作为每个段落的开始
            segments = content.split("Method to be analyzed:")
            # 移除空字符串段，并在每个段落前添加"Method to be analyzed:"标志
            methods = ["Method to be analyzed:" + segment.strip() for segment in segments if segment.strip()]
            return methods
    except FileNotFoundError:
        print(f"Error: The file {file_path} was not found.")
        return []
    except Exception as e:
        print(f"An error occurred: {e}")
        return []

def parse_methods_no_context(file_path, max_methods=30):
    """
    从指定文件中读取内容，按"Method to be analyzed:"分割成多个段落，
    并筛选出具有唯一签名的段落（去重保留第一个出现的段落），
    同时截取每段落的前max_methods个Method，保留完整内容。

    :param file_path: 文件路径
    :param max_methods: 每个段落保留的最大Method数量，默认为3
    :return: 保留指定数量Method块的段落列表
    """
    try:
        with open(file_path, 'r') as file:
            content = file.read()
            # 分割内容，并保留"Method to be analyzed:"作为每个段落的开头
            segments = content.split("Method to be analyzed:")
            # 去掉空段落，并在每个段落前添加"Method to be analyzed:"
            methods = ["Method to be analyzed:" + segment.strip() for segment in segments if segment.strip()]

            # 使用字典来筛选出唯一签名的段落
            unique_methods = {}
            for method in methods:
                # 提取签名 (即 "Method to be analyzed:" 后的第一行)
                first_line = method.split('\n', 1)[0].strip()
                if first_line not in unique_methods:
                    # 处理当前段落，截取前max_methods个Method及其内容
                    lines = method.split('\n')
                    truncated_lines = []
                    method_count = 0
                    within_method_block = False

                    for line in lines:
                        truncated_lines.append(line)
                        # 检测并计数Method块
                        if line.startswith("Method:"):
                            method_count += 1
                            within_method_block = True
                        elif within_method_block and line.strip() == "":
                            # Method块结束
                            within_method_block = False

                        # 如果已经截取了max_methods个Method块，则跳出
                        if method_count >= max_methods and not within_method_block:
                            break

                    # 将截取的内容保存为字符串
                    truncated_method = "\n".join(truncated_lines)
                    unique_methods[first_line] = truncated_method

            # 返回保留顺序的唯一段落列表
            return list(unique_methods.values())
    except FileNotFoundError:
        print(f"Error: The file {file_path} was not found.")
        return []
    except Exception as e:
        print(f"An error occurred: {e}")
        return []

def parse_methods_by_file(file_path):
    # 解析方法并存储在列表中
    methods = parse_methods_no_context(file_path)
    return methods


if __name__ == "__main__":
    path = "/Users/luca/dev/2025/pterosaur/llm/input/code/IR-kubernetes-client.txt"
    parse_methods_by_file(path)
