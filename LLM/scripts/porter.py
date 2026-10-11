import os
import requests
from xml.etree import ElementTree as ET

def get_latest_version(group_id, artifact_id):
    group_path = group_id.replace('.', '/')
    metadata_url = f"https://repo1.maven.org/maven2/{group_path}/{artifact_id}/maven-metadata.xml"

    response = requests.get(metadata_url)
    if response.status_code == 200:
        # Parse the XML to find the latest version
        xml_content = response.content
        root = ET.fromstring(xml_content)
        latest_version = root.find('./versioning/latest').text
        return latest_version
    else:
        print(f"Failed to retrieve metadata for {group_id}:{artifact_id}. HTTP status code: {response.status_code}")
        return None

def download_jar_from_maven_central(group_id, artifact_id, version, output_dir):
    group_path = group_id.replace('.', '/')
    jar_url = f"https://repo1.maven.org/maven2/{group_path}/{artifact_id}/{version}/{artifact_id}-{version}.jar"

    os.makedirs(output_dir, exist_ok=True)
    jar_path = os.path.join(output_dir, f"{artifact_id}-{version}.jar")

    response = requests.get(jar_url, stream=True)
    if response.status_code == 200:
        with open(jar_path, 'wb') as file:
            for chunk in response.iter_content(chunk_size=1024):
                file.write(chunk)
        print(f"Downloaded {artifact_id}-{version}.jar to {output_dir}")
    else:
        print(f"Failed to download {artifact_id}-{version}.jar:", response.status_code)

def main():
    folder_name = "hutool-core"  # Define your folder name
    # Use replace to substitute 'minio' with folder_name dynamically
    input_file = f"/Users/luca/dev/2025/pterosaur/scripts/{folder_name}/pilots.txt"  # Dynamic path for the input file
    output_dir = f"/Users/luca/dev/2025/pterosaur/output/popular-components/{folder_name}/downstream"  # Dynamic path for the output directory

    # Read the input file
    with open(input_file, 'r') as file:
        for line in file:
            # Strip the line ending and split the GAV information
            gav = line.strip()
            if gav:
                parts = gav.split(':')
                if len(parts) >= 2:
                    group_id, artifact_id = parts[0], parts[1]
                    version = parts[2] if len(parts) == 3 else None  # Get the version if specified

                    if version:
                        print(f"Downloading {group_id}:{artifact_id}:{version}...")
                        download_jar_from_maven_central(group_id, artifact_id, version, output_dir)
                    else:
                        print(f"Getting the latest version for {group_id}:{artifact_id}...")
                        latest_version = get_latest_version(group_id, artifact_id)
                        if latest_version:
                            print(f"Downloading {group_id}:{artifact_id}:{latest_version}...")
                            download_jar_from_maven_central(group_id, artifact_id, latest_version, output_dir)
                else:
                    print(f"Invalid GAV format in line: {gav}")

if __name__ == "__main__":
    main()
