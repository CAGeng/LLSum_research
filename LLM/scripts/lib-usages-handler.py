import requests
from bs4 import BeautifulSoup
import time
import random

def fetch_artifacts(url):
    # 使用 Session 以便保持会话
    session = requests.Session()

    # 设置请求头
    headers = {
        'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36',
        'Accept-Language': 'en-US,en;q=0.9',
        'Accept-Encoding': 'gzip, deflate, br',
        'Connection': 'keep-alive',
        'Referer': 'https://mvnrepository.com/',
        'DNT': '1'
    }

    try:
        # 发送请求获取页面内容
        response = session.get(url, headers=headers)

        # 检查请求是否成功
        if response.status_code == 200:
            # 解析HTML内容
            soup = BeautifulSoup(response.text, 'html.parser')

            # 找到所有 artifact 的链接
            links = soup.find_all('a', href=True)

            # 提取并合并符合要求的 artifact 名称
            artifacts = []
            for link in links:
                # 只处理包含 "/artifact/" 的链接
                if '/artifact/' in link['href']:
                    # 获取 groupId 和 artifactId
                    href = link['href']
                    parts = href.split('/')[2:4]  # 提取出groupId和artifactId
                    if len(parts) == 2:
                        artifact = f"{parts[0]}:{parts[1]}"
                        artifacts.append(artifact)

            # 返回前10个结果
            return artifacts[:10]
        else:
            print(f"请求失败，状态码：{response.status_code}")
            return []

    except requests.exceptions.RequestException as e:
        print(f"请求异常：{e}")
        return []

def main():
    # 设置要抓取的页面 URL
    url = "https://mvnrepository.com/artifact/cn.hutool/hutool-core/usages"

    # 获取前10个 artifact
    artifacts = fetch_artifacts(url)

    # 输出结果
    if artifacts:
        print("前10个 artifact:")
        for artifact in artifacts:
            print(artifact)
    else:
        print("没有找到任何 artifact")

    # 随机延迟，避免频繁请求导致被封禁
    time.sleep(random.uniform(1, 3))

if __name__ == "__main__":
    main()
