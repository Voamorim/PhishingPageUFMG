import psutil
import os
import shutil
from pathlib import Path
import json
from datetime import datetime
import time
import pandas as pd

DOWNLOADS_FOLDER_PATH = ''
SCREENSHOTS_FOLDER_PATH = ''

def get_configuration(logs_dir_path, threads, page_timeout, images_load_timeout, window_timeout, max_requests):
    return {
        'concurrentBrowsers': threads,
        'pageTimeout': page_timeout,
        'imagesLoadTimeout': images_load_timeout,
        'logsDirPath': str(logs_dir_path),
        'windowTimeout': window_timeout,
        'maxRequests': max_requests,
        'repositoryPath': '../../example/repo',
        'geckodriverBinPath': "/usr/local/bin/geckodriver",
        'runtimeControllersPath': '../runtime_params',
        'screenshotsPath': '../../example/screenshots',
        'downloadsPath': '../../example/downloads',
    }

def clear_log_files(log_dir_path):
    to_be_deleted = set(["access_log", "cadeia_urls", "firefox_exception", 
                         "http", "http_exception", "source_page", "tcp", 
                         "inicio", "null", "time", "requests"])

    for file_name in os.listdir(log_dir_path):
        if file_name.find('.') >= 0:
            middle_name = file_name.split('.')[1]
            if middle_name in to_be_deleted:
                os.remove(log_dir_path.joinpath(file_name))

def collect_process_data(process):
    data = {
        'cpu_percent': 0,
        'memory_percent': 0,
        'io_counters': None,
        'threads': 0,
        'network_connections': 0,
        'cpu_times': None,
    }
    
    try:
        t1 = time.time()
        s1 = psutil.net_io_counters(pernic=True)['enp5s0']

        time.sleep(1)

        t2 = time.time()
        s2 = psutil.net_io_counters(pernic=True)['enp5s0']

        dt = t2 - t1

        download_speed = (s2.bytes_recv - s1.bytes_recv) / dt
        upload_speed = (s2.bytes_sent - s1.bytes_sent) / dt

        # Converts to Mbps
        data['download_speed'] = (download_speed * 8) / (1024 ** 2)
        data['upload_speed'] = (upload_speed * 8) / (1024 ** 2)

        with process.oneshot():
            data['cpu_percent'] = process.cpu_percent()
            data['memory_percent'] = process.memory_percent(memtype="rss")
            data['io_counters'] = process.io_counters()._asdict() if process.io_counters() else None
            data['threads'] = process.num_threads()
            data['network_connections'] = len(process.net_connections())
            data['cpu_times'] = process.cpu_times()._asdict()

            children = process.children(recursive=True)
            for child in children:
                try:
                    with child.oneshot():
                        data['cpu_percent'] += child.cpu_percent()
                        data['memory_percent'] += child.memory_percent(memtype="rss")
                        if child.io_counters():
                            child_io = child.io_counters()
                            if data['io_counters']:
                                data['io_counters']['read_count'] += child_io.read_count
                                data['io_counters']['write_count'] += child_io.write_count
                                data['io_counters']['read_bytes'] += child_io.read_bytes
                                data['io_counters']['write_bytes'] += child_io.write_bytes
                            else:
                                data['io_counters'] = child_io._asdict()
                        data['threads'] += child.num_threads()
                        data['network_connections'] += len(child.net_connections())
                except (psutil.NoSuchProcess, psutil.AccessDenied, psutil.ZombieProcess):
                    pass
    except (psutil.NoSuchProcess, psutil.AccessDenied, psutil.ZombieProcess):
        pass

    return data

def run_framework(current_workdir, config_file_path, logs_dir_path):
    benchmarks_log_file = open(logs_dir_path.joinpath('out.log'), 'w')
    web_phishing_framework = psutil.Popen(
        ['java', '-jar', '../target/WebPhishingFramework.jar', str(config_file_path)],
        cwd=str(current_workdir), stdout=benchmarks_log_file, stderr=benchmarks_log_file, text=True
    )
    return web_phishing_framework, benchmarks_log_file

def collect_data_during_execution(process):
    collected_data = {
        'cpu_percent': [], 'memory_percent': [], 'io_counters': [],
        'threads': [], 'network_connections': [], 'cpu_times': [], 'timestamps': [],
        'download_speed': [], 'upload_speed': []
    }
    
    while process.poll() is None:
        data = collect_process_data(process)
        for key, value in data.items():
            collected_data[key].append(value)
        collected_data['timestamps'].append(str(datetime.now()))
        time.sleep(30)  # Pode reduzir para teste

    time.sleep(30)  # Espera final
    return collected_data

def save_collected_data(collected_data, logs_dir_path):
    df = pd.DataFrame(collected_data)
    df['timestamps'] = pd.to_datetime(df['timestamps'])
    for key, value in collected_data.items():
        with open(logs_dir_path.joinpath(f'{key}.json'), 'w') as f:
            json.dump(value, f)
    return df

def execute_configuration(current_workdir, logs_dir, threads, page_timeout, images_load_timeout, window_timeout, max_requests):
    logs_dir_path = current_workdir.joinpath(logs_dir)
    if not logs_dir_path.exists():
        os.mkdir(logs_dir_path)

    config = get_configuration(logs_dir_path, threads, page_timeout, images_load_timeout, window_timeout, max_requests)
    config_file_path = logs_dir_path.joinpath('config.json')
    with open(config_file_path, 'w') as f:
        json.dump(config, f)

    print(f"Iniciando configuração: {logs_dir}")
    process, log_file = run_framework(current_workdir, config_file_path, logs_dir_path)
    collected_data = collect_data_during_execution(process)
    process.wait()
    log_file.close()

    save_collected_data(collected_data, logs_dir_path)
    print(f"Finalizada configuração: {logs_dir}")

def main():
    current_filepath = Path(__file__).parent.resolve().absolute()
    runtime_params_dir = current_filepath.joinpath("runtime_params")
    if not runtime_params_dir.exists():
        os.mkdir(runtime_params_dir)

    conc_brow = [1, 2, 4, 6, 8]
    window_timeout = 15 
    max_requests = 3 

    for concurrent_browsers in conc_brow:
        for page_timeout in range(15, 61, 15):
            for images_timeout in [1, 3, 5, 7]:                
                # Removes all contents from screenshots/
                shutil.rmtree(SCREENSHOTS_FOLDER_PATH)
                os.makedirs(SCREENSHOTS_FOLDER_PATH)

                # Removes all contents from downloads/
                os.system(f"rm -rf {DOWNLOADS_FOLDER_PATH}/*")

                logs_dir = f"{concurrent_browsers}-{page_timeout}-{images_timeout}-{window_timeout}-{max_requests}"

                count = 1
                while current_filepath.joinpath(Path(logs_dir)).is_dir():
                    logs_dir = logs_dir.split('(')[0]
                    logs_dir += f'({count})'
                    count += 1

                execute_configuration(current_filepath, logs_dir, concurrent_browsers, page_timeout, images_timeout, window_timeout, max_requests)

if __name__ == '__main__':
    main()
