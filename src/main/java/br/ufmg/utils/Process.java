package br.ufmg.utils;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersAdapter;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.client.ClientUtil;
import net.lightbody.bmp.core.har.HarEntry;

public class Process implements Runnable {
    private int pid;
    private int timeout;
    private int imagesLoadTimeout;
    private BrowserMobProxy proxy;
    private Proxy seleniumProxy;
    private FirefoxDriver driver;
    private final BlockingQueue<String> listaUrls;
    private AtomicBoolean killProcesses;
    private AtomicBoolean restartProcesses;
    private Map<String, Integer> blockedDomains;
    private LogsWriter logsWriter;
    private URLList whitelist;
    private URLList blacklist;
    private final String screenshotsDirPath;
    private final String downloadsDirPath;
    private final String geckoDriverBinaryPath;
    private static final Logger LOGGER = LogManager.getLogger("File");

    public Process(BlockingQueue<String> urlsList,
            AtomicBoolean killProcesses,
            AtomicBoolean restartProcesses,
            int id,
            LogsWriter logsWriter,
            URLList whitelist,
            URLList blacklist,
            int timeout,
            int imagesLoadTimeout,
            String geckoDriverBinaryPath,
            String screenshotsDirPath,
            String downloadsDirPath) {

        this.timeout = timeout;
        this.imagesLoadTimeout = imagesLoadTimeout;
        this.whitelist = whitelist;
        this.blacklist = blacklist;
        this.listaUrls = urlsList;
        pid = id;
        this.logsWriter = logsWriter;
        blockedDomains = new HashMap<String, Integer>();
        this.killProcesses = killProcesses;
        this.restartProcesses = restartProcesses;
        this.geckoDriverBinaryPath = geckoDriverBinaryPath;
        this.screenshotsDirPath = screenshotsDirPath;
        this.downloadsDirPath = downloadsDirPath + "/pid" + this.pid + "_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    public void getProxyServer() {
        LOGGER.info("Initializing proxy server...");
        try {
            proxy = new BrowserMobProxyServer();
            proxy.addRequestFilter((request, contents, messageInfo) -> {
                String urlReq = io.netty.handler.codec.http.HttpHeaders.getHost(request);
                String dom = urlReq.split(":")[0];
                LOGGER.info("Processing request for domain: {}", dom);

                if (!dom.contains("firefox") && !dom.contains("mozilla") && !dom.contains("proxy")) {
                    if (Singleton.getInstance().isInDict(dom)) {
                        int numRequests = Singleton.getInstance().getNumeroReq(dom);
                        LOGGER.info("Number of requests for {}: {}", dom, numRequests);
                    } else {
                        LOGGER.warn("Domain {} not found in Singleton, adding now.", dom);
                    }
                }

                if (request.getMethod().equals(HttpMethod.POST) || dom.contains(".gov") || blacklist.has(dom)) {
                    LOGGER.warn("Blocking POST request or to restricted domain: {}", dom);
                    return new DefaultHttpResponse(request.getProtocolVersion(), HttpResponseStatus.valueOf(405));
                }
                return null;
            });

            proxy.addLastHttpFilterFactory(new HttpFiltersSourceAdapter() {
                @Override
                public HttpFilters filterRequest(HttpRequest originalRequest) {
                    return new HttpFiltersAdapter(originalRequest) {
                        @Override
                        public HttpResponse proxyToServerRequest(HttpObject httpObject) {
                            if (httpObject instanceof HttpRequest) {
                                ((HttpRequest) httpObject).headers().remove("VIA");
                            }
                            return null;
                        }
                    };
                }
            });

            proxy.setTrustAllServers(true);
            proxy.start(0);
        } catch (Exception e) {
            LOGGER.error("Error initializing proxy server: {}", e.getMessage(), e);
        }
    }

    public void getSeleniumProxy() {
        LOGGER.info("Configuring Selenium Proxy...");
        seleniumProxy = ClientUtil.createSeleniumProxy(proxy);
        try {
            String hostIp = Inet4Address.getLocalHost().getHostAddress();
            LOGGER.info("Local IP address obtained: {}", hostIp);
            seleniumProxy.setHttpProxy(hostIp + ":" + proxy.getPort());
            seleniumProxy.setSslProxy(hostIp + ":" + proxy.getPort());
        } catch (UnknownHostException e) {
            LOGGER.error("Error obtaining local IP address: {}", e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.error("Unexpected error configuring Selenium Proxy: {}", e.getMessage(), e);
        }
    }

    public void getFirefoxDriver(DesiredCapabilities capabilities) {
        LOGGER.info("Setting up Firefox Driver...");
        try {
            FirefoxProfile profile = new FirefoxProfile();

            // Customizes download options
            profile.setPreference("browser.download.folderList", 2);

            new File(this.downloadsDirPath).mkdirs();
            profile.setPreference("browser.download.dir", this.downloadsDirPath);

            profile.setPreference("browser.helperApps.neverAsk.saveToDisk",
                    "application/pdf,application/octet-stream,text/csv,application/vnd.ms-excel");
            profile.setPreference("pdfjs.disabled", true);
            profile.setPreference("browser.download.viewableInternally.enabledTypes", "");
            profile.setPreference("browser.download.manager.showWhenStarting", false);

            // Changes user agent to avoid detection
            String userAgent = "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:141.0) Gecko/20100101 Firefox/141.0";
            profile.setPreference("general.useragent.override", userAgent);

            FirefoxOptions options = new FirefoxOptions();
            options.setProfile(profile);

            options.setProxy(seleniumProxy);
            options.addArguments("--headless");
            options.addArguments("--window-size=1920,1080");
            options.setBinary("/usr/bin/firefox");
            options.setPageLoadStrategy(PageLoadStrategy.NORMAL);

            options.merge(capabilities);

            driver = new FirefoxDriver(options);
            driver.manage().window().maximize();
        } catch (WebDriverException e) {
            LOGGER.error("Error initializing Firefox Driver: {}", e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.error("Unexpected error initializing Firefox Driver: {}", e.getMessage(), e);
        }
    }

    public Response accessURL(String composedURL) {
        if (composedURL == null || composedURL.trim().isEmpty()) {
            LOGGER.warn("Composed URL is null or empty.");
            return new Response(true, false, "URL inválida");
        }

        LOGGER.info("Accessing URL: {}", composedURL);
        String[] temp = composedURL.split("  ");
        String url = temp[0];
        String dom = "";

        if (url.contains("http")) {
            dom = url.split("/")[2];
        } else {
            dom = url.split("/")[0];
        }

        if (blockedDomains.get(dom) != null && blockedDomains.get(dom) >= 10) {
            LOGGER.warn("Blocked domain: {}. Attempt limit exceeded.", dom);
            String out = composedURL.replace("\n", "") + "  BLOCKED  0\n";
            return new Response(true, false, out);
        }

        proxy.newHar("url_" + pid);

        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(timeout));
        String finalUrl = "about:blank";
        try {
            logsWriter.writeTimeURLs(pid, "URL: " + url + " ");
            logsWriter.writeTimeURLs(pid, Long.toString(System.currentTimeMillis()) + " ");
            driver.get(url);
            logsWriter.writeTimeURLs(pid, Long.toString(System.currentTimeMillis()) + " ");
            finalUrl = driver.getCurrentUrl();
            LOGGER.info("URL accessed successfully: {}", finalUrl);

            try {
                Files.write(
                        Paths.get(this.downloadsDirPath + "/" + "urls.txt"),
                        (finalUrl + "\n").getBytes(),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            } catch (IOException e) {
                LOGGER.error("Error writing to URLs file: {}", e.getMessage(), e);
            }

            // Waits for the visual elements of the page to load
            try {
                new WebDriverWait(driver, Duration.ofSeconds(imagesLoadTimeout)).until(webDriver -> {
                    JavascriptExecutor js = (JavascriptExecutor) webDriver;
                    return (Boolean) js.executeScript("return typeof jQuery !== 'undefined' && jQuery.active == 0");
                });
            } catch (Exception e) {
                Thread.sleep(Duration.ofSeconds(imagesLoadTimeout).toMillis());
            }

            // Scrolls the page and then takes the screenshot
            JavascriptExecutor js = (JavascriptExecutor) driver;
            long pageHeight = (long) js.executeScript("return document.body.scrollHeight");
            int increment = 1080;
            int pos = 0;
            int numScrolls = 0;
            int maxScrolls = 3;
            while (pos + increment < pageHeight && numScrolls < maxScrolls) {
                js.executeScript("window.scrollBy(0, " + increment + ")");
                pos += increment;

                try {
                    new WebDriverWait(driver, Duration.ofSeconds(imagesLoadTimeout)).until(webDriver -> {
                        return (Boolean) js.executeScript("return typeof jQuery !== 'undefined' && jQuery.active == 0");
                    });
                } catch (Exception e) {
                    Thread.sleep(Duration.ofSeconds(imagesLoadTimeout).toMillis());
                }

                numScrolls += 1;
            }

            js.executeScript("window.scrollTo(0, 0)"); // Returns to the top of the page

            String screenshotFileName = Base64Parser.encode(url);
            takeScreenshot(screenshotFileName);
        } catch (WebDriverException e) {
            if (e instanceof NoSuchSessionException) {
                LOGGER.error("WebDriver session does not exist or is not active when accessing {}: {}", composedURL,
                        e.getMessage(), e);

                // Restarts the Firefox Driver in case of session issues, which can be caused by
                // memory problems or crashes
                getFirefoxDriver(new DesiredCapabilities());
                return createResponseWithException(composedURL, e);
            } else {
                LOGGER.error("WebDriver error when accessing {}: {}", composedURL, e.getMessage(), e);
                handleBlockedDomain(dom);
                logFirefoxException(composedURL, e);
                return createResponseWithException(composedURL, e);
            }
        } catch (Exception e) {
            LOGGER.error("Unexpected error when accessing {}: {}", composedURL, e.getMessage(), e);
            return new Response(true, false, "Unexpected error");
        }

        if (!finalUrl.equals("about:blank")) {
            return handleSuccessfulUrlAccess(finalUrl, composedURL, dom);
        }

        LOGGER.warn("Final URL is about:blank, indicating a potential issue with page load or access: {}", composedURL);
        return new Response(true, false, "wtf");
    }

    private void takeScreenshot(String screenshotFileName) {
        File screenshotFile = driver.getFullPageScreenshotAs(OutputType.FILE);

        String screenshotStrPath = screenshotsDirPath + "/" + screenshotFileName + ".png";
        Path destinationPath = new File(screenshotStrPath).toPath();

        try {
            Files.copy(screenshotFile.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
            screenshotFile.delete();

            LOGGER.info("Screenshot taken successfully for URL: {}. Saved to: {}", screenshotFileName,
                    destinationPath.toString());
        } catch (IOException e) {
            LOGGER.error("Error saving screenshot for URL {}: {}", screenshotFileName, e.getMessage(), e);
        }
    }

    private void handleBlockedDomain(String dom) {
        if (blockedDomains.get(dom) == null) {
            blockedDomains.put(dom, 1);
        } else {
            int value = blockedDomains.get(dom);
            value += 1;
            blockedDomains.replace(dom, value);
        }
    }

    private void logFirefoxException(String composedURL, Exception e) {
        try {
            logsWriter.writeFirefoxException(pid, composedURL + e.toString());
        } catch (IOException e1) {
            LOGGER.error("Error writing Firefox exception to log for URL {}: {}", composedURL, e1.getMessage(), e1);
        }
    }

    private Response createResponseWithException(String composedURL, Exception e) {
        String executionName = e.getClass().getSimpleName();
        String out = composedURL.replace("\n", "") + "  " + executionName + "  0\n";
        return new Response(true, false, out);
    }

    private Response handleSuccessfulUrlAccess(String finalUrl, String composedURL, String dom) {
        InetAddress ip = null;
        String ipString = null;
        try {
            String hostname = new URL(finalUrl).getHost();
            ip = InetAddress.getByName(hostname);
            ipString = ip.getHostAddress();
            LOGGER.info("Hostname resolved: {} -> {}", hostname, ipString);
        } catch (MalformedURLException e) {
            LOGGER.error("Malformed URL {}: {}", composedURL, e.getMessage(), e);
            finalUrl = "-";
            ipString = "0";
        } catch (UnknownHostException e) {
            LOGGER.warn("Unknown host for URL {}: {}", composedURL, e.getMessage(), e);
            handleBlockedDomain(dom);
            finalUrl = "-";
            ipString = "0";
        } catch (Exception e) {
            LOGGER.error("Unexpected error resolving hostname for URL {}: {}", composedURL, e.getMessage(), e);
            finalUrl = "-";
            ipString = "0";
        }

        String out = composedURL.replace("\n", "") + "  " + finalUrl + "  " + ipString + "\n";
        String hash;
        String page;
        String tag; // For tagging the type of page loaded (complete, partial, error, empty)

        try {
            String html = driver.getPageSource();
            Document document = Jsoup.parse(html);
            page = document.toString();
            hash = DigestUtils.md5Hex(page);

            // Obtains the status code of the first HarEntry
            int statusCode = proxy.getHar().getLog().getEntries().get(0).getResponse().getStatus();

            // Verifies the type of page for tagging
            if (page.isEmpty()) {
                tag = "EMPTY PAGE";
            } else if (statusCode >= 400 && statusCode < 600) {
                tag = "ERROR PAGE"; // Default error page (404, 403, 5XX)
            } else if (page.length() < 500) {
                tag = "PARTIAL PAGE"; // Page partially loaded, assuming complete content would be longer
            } else {
                tag = "COMPLETE PAGE";
            }

            LOGGER.info("Page accessed successfully. Tag: {}, Generated hash: {}", tag, hash);
        } catch (Exception e) {
            LOGGER.error("Error obtaining font of the page for URL {}: {}", composedURL, e.getMessage(), e);
            page = "";
            hash = "EMPTYPAGE";
            tag = "Empty Page"; // Assume empty in case of error
        }

        // Adds the tag to the log
        String url8 = out.replace("\n", "") + "  " + hash + "  " + tag + "\n";
        try {
            logsWriter.writeSourcePage(pid, url8);
            logsWriter.writeSourcePage(pid, page);
            logsWriter.writeSourcePage(pid, "\n*!-@x!x@-!*\n");
        } catch (IOException e) {
            LOGGER.error("Error writing page source to log for URL {}: {}", composedURL, e.getMessage(), e);
        }

        return new Response(false, false, out, proxy.getHar().getLog().getEntries());
    }

    public void run() {
        LOGGER.info("Starting process with PID: {}", pid);
        System.setProperty("webdriver.gecko.driver", this.geckoDriverBinaryPath);
        DesiredCapabilities capabilities = new DesiredCapabilities();

        // Initializes the proxy server and configures Selenium to use it
        getProxyServer();
        getSeleniumProxy();
        capabilities.setCapability("marionette", true);
        getFirefoxDriver(capabilities);

        while (!killProcesses.get()) {
            try {
                if (restartProcesses.get()) {
                    LOGGER.warn("Restart signal received for process PID: {}. Restarting Firefox Driver and Proxy...",
                            pid);
                    break;
                }
                long startTime = System.currentTimeMillis();
                logsWriter.writeTimeURLs(pid, Long.toString(startTime) + " ");
                String composedURL = listaUrls.poll(5, java.util.concurrent.TimeUnit.SECONDS);

                if (composedURL == null) {
                    // Timeout occurred, check if we should exit
                    if (killProcesses.get()) {
                        LOGGER.info("Process PID {} finalizing. Stop signal received.", pid);
                        break;
                    }
                    continue;
                }

                if (composedURL.equals("http://poison_pill.com")) {
                    LOGGER.info("Poison Pill received for process PID: {}. Finalizing process.", pid);
                    killProcesses.compareAndSet(false, true);
                    break;
                }

                LOGGER.info("Process PID {} - URL obtained from queue: {}", pid, composedURL);
                Response response = accessURL(composedURL);

                String urlLog = response.getUrlLog();
                logsWriter.writeAccessLog(pid, urlLog);
                logsWriter.writeTcp(pid, urlLog.replace("\n", ""));
                logsWriter.writeCadeiaURLs(pid, urlLog);

                Set<String> ipsSet = new HashSet<>();
                if (!response.getException() && !response.getBlocked()) {
                    List<HarEntry> entries = response.getHar();
                    for (HarEntry entry : entries) {
                        String ip = entry.getServerIPAddress();
                        int statusCode = entry.getResponse().getStatus();
                        ipsSet.add(ip);

                        String initialURLString = entry.getRequest().getUrl();
                        String finalURLString = entry.getResponse().getRedirectURL();

                        if (!finalURLString.contains("mozilla") && !initialURLString.contains("mozilla")
                                && !finalURLString.contains("firefox") && !initialURLString.contains("firefox")) {
                            if (!finalURLString.isEmpty()) {
                                String timeStamp = entry.getStartedDateTime().toString();
                                logsWriter.writeCadeiaURLs(pid, timeStamp.replace(" ", "") + "  "
                                        + initialURLString + "  " + finalURLString + "  " + statusCode);
                            } else {
                                if (!initialURLString.isEmpty()) {
                                    String timeStamp = entry.getStartedDateTime().toString();
                                    logsWriter.writeCadeiaURLs(pid, timeStamp.replace(" ", "") + "  "
                                            + initialURLString + "  -" + statusCode + "\n");
                                }
                            }
                        }
                    }
                    String ipsChain = String.join(",", ipsSet);
                    LOGGER.info("IPs chain extracted: {}", ipsChain);
                    logsWriter.writeTcp(pid, "  " + ipsChain);
                }

                // Marks the end of the log for this URL access
                logsWriter.writeTcp(pid, "\n*!-@x!x@-!*\n");
                logsWriter.writeCadeiaURLs(pid, "*!-@x!x@-!*\n");
                long finalTime = System.currentTimeMillis();
                logsWriter.writeTimeURLs(pid, Long.toString(finalTime) + "\n");
            } catch (NoSuchSessionException e) {
                LOGGER.error("WebDriver session does not exist or is not active when processing PID {}: {}", pid,
                        e.getMessage(), e);
                getFirefoxDriver(new DesiredCapabilities());
            } catch (WebDriverException e) {
                LOGGER.error("WebDriver error in process PID {}: {}", pid, e.getMessage(), e);
            } catch (InterruptedException e) {
                LOGGER.error("Process PID {} interrupted: {}", pid, e.getMessage(), e);
            } catch (IOException e) {
                LOGGER.error("I/O error in process PID {}: {}", pid, e.getMessage(), e);
            }
        }

        try {
            if (driver != null) {
                driver.close();
                LOGGER.info("Firefox Driver closed successfully for PID: {}", pid);
            }
            proxy.stop();
            LOGGER.info("Proxy server stopped successfully for PID: {}", pid);
        } catch (Exception e) {
            LOGGER.error("Error finalizing process PID {}: {}", pid, e.getMessage(), e);
        }
    }
}