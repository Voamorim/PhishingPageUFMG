# Web Phishing Monitoring Framework

## About the Project

This framework was developed to automate the analysis of phishing websites by collecting metadata and pages in real time. It enables large-scale monitoring of suspicious URLs using a controlled environment with Selenium, Firefox, and an HTTP proxy.

## Target Audience

### This project is useful for:

- Cybersecurity researchers

- Developers interested in phishing analysis automation

- Companies needing to monitor online threats

## Installation

This project requires OpenJDK 21, Maven and Firefox (with Geckodriver) to function correctly.

### Prerequisites

Follow the instructions for your specific operating system to set up the environment.

#### Linux (Ubuntu/Debian)

Run the following commands to install the core dependencies and set up the Firefox driver:

```bash
# Install OpenJDK, Maven and Firefox
sudo apt update && sudo apt install -y openjdk-21-jdk maven firefox

# Install Geckodriver (v0.36.0)
wget https://github.com/mozilla/geckodriver/releases/download/v0.36.0/geckodriver-v0.36.0-linux64.tar.gz
sudo tar -xzf geckodriver-v0.36.0-linux64.tar.gz -C /usr/local/bin/
rm geckodriver-v0.36.0-linux64.tar.gz
```

#### macOS

If you use [Homebrew](https://brew.sh), you can install everything with a single command:

```bash
brew install openjdk@21 maven firefox geckodriver
```

**Note:** To ensure the system recognizes the new JDK, you may need to symlink it:

`sudo ln -sfn $(brew --prefix openjdk@21)/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk`

#### Windows

1. **Java**: Download and install [OpenJDK 21.0.10](https://adoptium.net/pt-BR/temurin/releases?version=21)
2. **Maven**: Download [Maven 3.9.x](https://maven.apache.org/download.cgi) and add the `bin` folder to your system **PAHT**.
3. **Firefox**: Ensure [Mozilla Firefox](https://www.mozilla.org/firefox/new/) is installed.
4. **Geckodriver**: Download the Windows 64-bit version (v0.36.0) from the [Official Releases](https://github.com/mozilla/geckodriver/releases). Extract `geckodriver.exe` into a folder included in your system **PATH**.

## Build

This project is built using Maven 3 and Java 21.

To compile the project and package it into an executable JAR, navigate to the root directory and run:

```bash
mvn package
```

### Output Artifacts

After a successful build, the `target/` directory will contain:
* `WebPhishingFramework.jar`: The main executable framework.
* `lib/`: A subdirectory containing all external dependencies automatically managed by Maven.

## Run

This project is currently optimized for Selenium `4.24.0`.

Because the framework utilizes Mozilla Firefox for web inspection, you must ensure thet your local Firefox installation and Geckodriver are compatible with this version of Selenium.

1. Ensure Firefox and Geckodriver are installed as described in the [Installation](#installation) section.
2. Build the project using the steps in [Build](#build)
3. Run the application from the root directory by passing a JSON configuration file as an argument: 

```sh
java -jar target/WebPhishingFramework.jar path_to_config_file.json
```

The JSON file contains all parameters required for execution. To test the framework immediately, you can use the provided enviroment in the [example](example) folder:

```sh
java -jar target/WebPhishingFramework.jar ./example/config.json
```

For details on how to structure your JSON paramenters, see [Execution Environment](#execution-environment).

### Docker Execution

Using Docker simplifies dependency management by providing a pre-configured environment with **Firefox**, **Geckodriver 0.36.0**, and **Java 21** already installed.

1. **Build the Docker Image**

To build the image locally using the provided Dockerfile, run:
```bash
doker build -t web-phishing-framework:latest . 
```

2. **Run the Container**

We recommend mirroring the structure of the [example](example) directory. This allows you to mount a local directory containing your `config.json` and any required input files directly into the container.

Run the framework from your root directory using the following command:

```bash
docker run -it -v `pwd`/example:/root/environment web-phishing-framework:latest
```

**Container Details**
* **Base Image**: `selenium/standalone-firefox:135.0`
* **Java Version**: OpenJDK 21
* **Selenium Version**: 4.24.0
* **Pre-installed Driver**: Geckodriver 0.36.0 is located at `/usr/bin/geckodriver`.

## Execution Environment

### Configuration file

The framework is controlled via a JSON configuration file. All parameters with the suffix `Path` can be specified as absolute paths or relative paths relative to the directory containing the configuration file. 

* `concurrentBrowsers`: **\[REQUIRED\]** Number of concurrent browser instances to run.
* `pageTimeout`: **\[REQUIRED\]** Maximum time (ms) Selenium waits for a single page load. 
* `windowTimeout`: **\[REQUIRED\]** Time window for request limiting. 
* `imagesLoadTimeout`: **\[REQUIRED\]** Maximum time (ms) Selenium waits for JavaScript elements to load.
* `repositoryPath`: **\[REQUIRED\]** Path to a file or directory containing target URLs (one per line).
* `geckodriverBinPath`: **\[REQUIRED\]** Absolute path to the Geckodriver binary. 
* `runtimeControllersPath`: **\[REQUIRED\]** Path to directory containing [Runtime Control Files](#remote-controller-files).
* `logsDirPath`: Output directory for logs. Defaults to the current working directory. 
* `whiteListPath`: Path to a file or directory containing URLs (one per line) to be ignored by the framework (Trusted/Safe domains).
* `blackListPath`: Path to a file or directory containing URLs (one per line) to be flagged immediately (Known phishing/Malicious domains).
* `screenshotsPath`: Output directory for screenshots captured by the framework. 
* `downloadsPath`: Output directory for web content extraction. Each thread manages its own dedicated subfolder containing its downloaded files and corresponding URL manifests.

**Note on Lists**: If a path points to a directory, the framework recursively loads all files within it. 

See the example configuration file at [config.json](example/config.json).

### Runtime Control Files

The framework is designed to be managed dynamically without requiring a restart. It continuosly monitors files in the `runtimeControllersPath` for state changes.

* `running`: A control file containing a single character: `1` (active) or `0` (stop).
  * **Manual Shutdown**: To gracefully shut down the monitor before the queue is empty, use: 
    ```bash
    echo 0 > /path/to/runtime/running
    ```
  * **Automatic Shutdown**: The framework tracks the status of the URL repository. Once all URLs have been fully processed and the queue is empty, the framework will automatically toggle its state and shut down without manual intervention.

### Log Directories and Metrics 

The application outputs detailed telemetry to the `logsDirPath`. One of the primary data points is the performance of the URL processing pipeline:

* `time_urls`: This log contains four distinct timestamps (in milliseconds) for every URL processed:
  1. **Process Initiation**: When the URL is picked up from the repository.
  2. **Download Start**: When the WebDriver begins the request. 
  3. **Download Completion**: When the DOM is fully loaded. 
  4. **Processing End**: When the framework finishes analysis/inspection. 

## Technical Documentation

For in depth technical details about the project, please refer to our reference guide:

[Technical Reference Guide](./docs/REFERENCE.md)

## License
Distributed under MIT license.