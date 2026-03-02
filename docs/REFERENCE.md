# Code Documentation - Class App

## Overview
The `App` class is part of the package `br.ufmg.app` and is responsible for managing URL processing in a multithreaded environment. It handles reading files containing URLs, manages concurrent browsing processes, and monitors system memory usage.

## Dependencies
The class depends on the following libraries and packages:
- `java.io` for file manipulation and data reading.
- `java.nio.file` for path and file handling.
- `java.util.concurrent` for queue and concurrency management.
- `org.apache.logging.log4j` for logging.
- Auxiliary project packages: `br.ufmg.utils`.

## Attributes
- `config`: Application configuration.
- `logsWriter`: Log file manager.
- `whiteList` and `blackList`: Lists of allowed and blocked URLs.
- `urlFiles`: Array of files containing URLs.
- `urlsList`: Concurrent queue storing URLs to be processed.
- `restartProcesses`, `killProcesses` and `allUrlsProcessed`: Atomic flags for process control.
- `LOGGER`: Log4j logger instance.

## Methods

### `App(Configuration config)`
**Description:**
Class constructor, initializes URL lists, configuration, and URL queue.

### `run()`
**Description:**
Executes the main application flow, calling the main methods.

### `startLogFiles()`
**Description:**
Initializes and creates necessary log files for application execution.

### `singletonSetup()`
**Description:**
Configures the Singleton instance of the system with initial parameters.

### `getFiles()`
**Description:**
Reads the directory or file specified in the configuration and stores the files containing URLs in `urlFiles`.

### `getURLs()`
**Description:**
Reads the URL files and adds the URLs to the `urlsList` queue.

### `isAppRunning()`
**Description:**
Evaluates the execution status by verifying two criteria: the presence of the external control signal (stop file) and the availability of pending items in the URL queue. Returns false if a manual stop is requested or if all tasks are completed.

### `manageProcesses()`
**Description:**
Manages concurrent execution processes, monitoring memory and controlling the execution of browsing threads.

### `writeRemainingURLs()`
**Description:**
Writes the URLs that were not processed to a log file.

## Final Considerations
The `App` class is the core of the application, efficiently managing concurrent process execution and log recording. For better maintainability, it would be interesting to modularize process management and file handling more effectively.


## Process.Java
# Class Documentation: Process

## Overview
The `Process` class is responsible for managing the capture and analysis of URL accesses using a proxy server and Selenium WebDriver with Firefox. This class implements the `Runnable` interface, allowing it to be executed in separate threads.

## Packages and Libraries Used
The class uses various external libraries and patterns, including:
- `org.apache.logging.log4j` for logging
- `org.jsoup` for HTML parsing
- `org.littleshoot.proxy` for HTTP request handling
- `org.openqa.selenium` for browser automation
- `net.lightbody.bmp` for HTTP traffic capture
- `org.apache.commons.codec.digest.DigestUtils` for content hashing

## Main Attributes
- `pid`: Process identifier.
- `timeout`: Timeout for page loading.
- `imagesLoadTimeout`: Extra timeout for loading JavaScript elements that could've been ignored by the default timeout.
- `proxy`: Instance of `BrowserMobProxyServer` for traffic interception.
- `seleniumProxy`: Proxy configuration for Selenium.
- `driver`: Instance of `FirefoxDriver` to automate web browsing.
- `listaUrls`: Queue of URLs to be processed.
- `killProcesses` and `restartProcesses`: Flags for process lifecycle control.
- `blockedDomains`: Map of blocked domains and access counts.
- `logsWriter`: Instance for writing custom logs.
- `whitelist` and `blacklist`: Lists of allowed and blocked domains.
- `screenshotsDirPath`: Output directory for screenshots captured by the framework.
- `downloadsDirPath`: Root path for web content extraction. To prevent data collisions, each thread manages its own dedicated subfolder containing its downloaded files and corresponding URL manifests.
- `geckoDriverBinaryPath`: Path to the geckodriver binary.
- `lock` and `notEmpty`: Synchronization mechanisms for concurrent access.

## Main Methods

### `public Process(...)`
Constructor that initializes the instance with the necessary parameters.

### `public void getProxyServer()`
Initializes a proxy server and adds filters for request handling.
- Filters and blocks requests to disallowed domains.
- Removes the `VIA` header to avoid proxy detection.
- Logs events and processed requests.

### `public void getSeleniumProxy()`
Configures the Selenium proxy based on the `BrowserMobProxyServer`.
- Obtains the local IP address.
- Sets HTTP and SSL proxy for the WebDriver.

### `public void getFirefoxDriver(DesiredCapabilities capabilities)`
Initializes the Firefox driver with configuration options.
- Sets the proxy.
- Enables headless mode.
- Adds arguments to optimize page loading.
- Handles WebDriver initialization errors.
- Changes the default UserAgent. 
- Changes the default folder for downloaded content. 

### `public Response accessURL(String composedURL)`
Accesses a URL and returns a `Response` object with the results.
- Validates the URL before access.
- Applies domain blocking rules.
- Captures access timing logs.
- Attempts to load all page's DOM elements, styles, and media assets.
- Captures a screenshot of the page as it appeared.
- Retrieves the page and generates an MD5 hash of the content.
- Categorizes the page as empty, error, partial, or complete.
- Handles exceptions such as lost session or unreachable browser.

### `private void takeScreenshot(String screenshotFileName)`
Captures a visual snapshot of the current browser state and persists it as an image file. The screenshotFileName is appended to the globally defined screenshotsDirPath to determine the final storage location.

### `private void handleBlockedDomain(String dom)`
Increments the attempt count for a blocked domain.

### `private void logFirefoxException(String composedURL, Exception e)`
Logs WebDriver exceptions to the log file.

### `private Response createResponseWithException(String composedURL, Exception e)`
Creates a `Response` object containing information about a failure to access the URL.

### `private Response handleSuccessfulUrlAccess(String finalUrl, String composedURL, String dom)`
Processes successfully accessed URLs.
- Obtains the IP address of the accessed domain.
- Applies hashing to the HTML content of the page.
- Classifies the page based on HTTP status code and content size.

## Usage Example
```java
BlockingQueue<String> urlQueue = new ArrayBlockingQueue<>(100);
AtomicBoolean killFlag = new AtomicBoolean(false);
AtomicBoolean restartFlag = new AtomicBoolean(false);
LogsWriter logsWriter = new LogsWriter();
URLList whitelist = new URLList();
URLList blacklist = new URLList();
int timeout = 30;
int imagesLoadTimeout = 10;
String geckoPath = "/path/to/geckodriver";
String screenshotsDirPath = "/path/to/screenshots/output/directory";
String downloadsDirPath = "/path/to/downloads/output/directory";

Process process = new Process(urlQueue, killFlag, restartFlag, 1, logsWriter, whitelist, blacklist, timeout, imagesLoadTimeout, geckoPath, screenshotsDirPath, downloadsDirPath);
new Thread(process).start();
```

## Final Considerations
This class is essential for monitoring and automating web accesses, using a proxy to capture and manipulate HTTP traffic. It is recommended to review exception handling and efficiently manage WebDriver memory and processes to avoid leaks.

# Class Documentation: Singleton

## Package
The class belongs to the package `br.ufmg.utils`.

## Overview
The `Singleton` class implements the Singleton design pattern, ensuring there is only one instance of the class during program execution. The class manages a dictionary of requests made to domains, keeping a temporal record of these requests and providing functionalities to manipulate this data.

## Attributes

### Private Attributes
- `static private Singleton _instance;`
  - The unique instance of the Singleton class.
- `private Map<String, List<Long>> requestsDict;`
  - Dictionary storing lists of request timestamps grouped by domain.
- `private long startTime;`
  - Initialization time of the Singleton.
- `private LogsWriter logsWriter;`
  - Object responsible for handling logs.
- `private int requestsWindow;`
  - Time window to consider a request as recent.

## Methods

### Constructor
- `private Singleton()`
  - Private constructor that initializes the data structure by calling `initGlobals()`.

### Initialization Methods
- `private void initGlobals()`
  - Initializes `requestsDict` and sets `startTime` with the current timestamp.

### Static Instance Method
- `public static Singleton getInstance()`
  - Implements the Singleton pattern with double-checked locking to ensure creation of a single instance.

### Configuration
- `synchronized public void setParameters(int requestsWindow, LogsWriter logsWriter)`
  - Sets the time window (`requestsWindow`) and the `logsWriter` object.

### Request Dictionary Manipulation
- `synchronized public boolean isInDict(String domain)`
  - Checks if a domain is present in the dictionary.
- `synchronized public int getNumeroReq(String domain)`
  - Returns the number of recent requests for a domain. Before counting, removes expired requests.
- `synchronized public void setRequestsNumber(String domain, long value)`
  - Adds a new request timestamp to the specified domain or creates a new entry if the domain does not exist.

### Printing and Logging Requests
- `synchronized public void printHighestScores()`
  - Collects and sorts request data, saving it to a log file. In case of error, prints exceptions and terminates the program.

## Exception Handling
The method `printHighestScores()` may throw exceptions when handling files, including:
- `FileNotFoundException`
- `UnsupportedEncodingException`
- `IOException`

These exceptions are caught and printed to the console but are not handled more elaborately, which may lead to abrupt program termination.

## Possible Improvements
1. **Better Exception Handling**: Instead of just printing `e.printStackTrace()`, the program could log errors and continue execution.
2. **Avoid Direct Call to `System.exit(0)`**: A more controlled mechanism could be used to handle failures.
3. **Use More Efficient Structures**: Depending on data volume, `requestsDict` could be optimized for faster search and removal.
4. **Optimized Synchronization**: Some synchronized operations could be refined to avoid unnecessary locking of the entire class.

## Conclusion
The `Singleton` class is responsible for managing and recording domain requests. Although it correctly follows the Singleton pattern, it can be improved in terms of efficiency and robustness in error handling.

# Class Documentation: Response

## Overview
The `Response` class represents a response of an intercepted HTTP request, indicating whether it was blocked, if an exception occurred, and if it was successfully processed. It can also store HAR (`HarEntry`) entries, which contain detailed information about the HTTP response.

## Attributes

- `blocked` (`boolean`): Indicates if the response was blocked.
- `exception` (`boolean`): Indicates if an exception occurred in the request.
- `processed` (`boolean`): Indicates if the response was successfully processed.
- `urlLog` (`String`): URL associated with the response.
- `entries` (`List<HarEntry>`): List of HAR entries associated with the response.

## Constructors

### `Response(boolean blocked, boolean exception, String urlLog)`
Creates a response indicating if it was blocked or if an exception occurred. This response is not marked as processed by default.

**Parameters:**
- `blocked`: Defines if the response was blocked.
- `exception`: Defines if an exception occurred.
- `urlLog`: URL corresponding to the response.

### `Response(boolean blocked, boolean exception, String urlLog, List<HarEntry> entries)`
Creates a processed response, including HAR entries.

**Parameters:**
- `blocked`: Defines if the response was blocked.
- `exception`: Defines if an exception occurred.
- `urlLog`: URL corresponding to the response.
- `entries`: List of HAR entries associated.

## Methods

### `public Boolean getBlocked()`
Returns whether the response was blocked.

### `public Boolean getException()`
Returns whether an exception occurred in the response.

### `public String getUrlLog()`
Returns the URL associated with the response.

### `public List<HarEntry> getHar()`
Returns the list of HAR entries associated with the response.

### `public boolean getProcessed()`
Returns whether the response was processed.

### `public List<HarEntry> getResult()`
Returns the list of HAR entries if the response was processed; otherwise, returns `null`.

## Possible Improvements

1. **Avoid null values in `entries`**:
   - The method `getHar()` can return `null` if the response was not processed. It could be changed to return an empty list by default.

2. **Make `processed` more intuitive**:
   - The `processed` field is inferred by the presence of `entries`, but it could be an internally derived attribute instead of being explicitly set.

3. **Add `toString()` for debugging**:
   - Implement a `toString()` method to facilitate displaying the state of a `Response` object in logs.

4. **Use `Optional<List<HarEntry>>`**:
   - This would improve safety by avoiding `null` for `entries`, making the code more robust.
