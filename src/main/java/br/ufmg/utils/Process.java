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
import java.nio.file.StandardCopyOption;
import java.time.Duration;
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
    private int requestsLimit;
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
            int requestsLimit,
            String geckoDriverBinaryPath,
            String screenshotsDirPath) {

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
        this.requestsLimit = requestsLimit;
        this.geckoDriverBinaryPath = geckoDriverBinaryPath;
        this.screenshotsDirPath = screenshotsDirPath;
    }

    public void getProxyServer() {
        LOGGER.info("Inicializando o servidor de proxy..."); // INFO
        try {
            proxy = new BrowserMobProxyServer();
            proxy.addRequestFilter((request, contents, messageInfo) -> {
                String urlReq = io.netty.handler.codec.http.HttpHeaders.getHost(request);
                String dom = urlReq.split(":")[0];
                LOGGER.info("Processando requisição para domínio: {}", dom); // INFO

                if (!dom.contains("firefox") && !dom.contains("mozilla") && !dom.contains("proxy")) {
                    if (Singleton.getInstance().isInDict(dom)) {
                        int numRequisicoes = Singleton.getInstance().getNumeroReq(dom);
                        LOGGER.info("Número de requisições para {}: {}", dom, numRequisicoes); // INFO
                    } else {
                        LOGGER.warn("Domínio {} não encontrado no Singleton, adicionando agora.", dom); // WARN
                    }
                }

                // Caso de bloqueio
                if (request.getMethod().equals(HttpMethod.POST) || dom.contains(".gov") || blacklist.has(dom)) {
                    LOGGER.warn("Bloqueando requisição POST ou para domínio restrito: {}", dom); // WARN
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
            LOGGER.error("Erro ao iniciar o servidor de proxy: {}", e.getMessage(), e); // ERROR
        }
    }

    public void getSeleniumProxy() {
        LOGGER.info("Configurando Selenium Proxy..."); // INFO
        seleniumProxy = ClientUtil.createSeleniumProxy(proxy);
        try {
            String hostIp = Inet4Address.getLocalHost().getHostAddress();
            LOGGER.info("Obtendo endereço de IP local: {}", hostIp); // INFO
            seleniumProxy.setHttpProxy(hostIp + ":" + proxy.getPort());
            seleniumProxy.setSslProxy(hostIp + ":" + proxy.getPort());
        } catch (UnknownHostException e) {
            LOGGER.error("Erro ao obter IP local: {}", e.getMessage(), e); // ERROR
        } catch (Exception e) {
            LOGGER.error("Erro inesperado ao configurar o Selenium Proxy: {}", e.getMessage(), e); // ERROR
        }
    }

    public void getFirefoxDriver(DesiredCapabilities capabilities) {
        LOGGER.info("Inicializando o Firefox Driver..."); // INFO
        try {
            FirefoxOptions options = new FirefoxOptions();
            options.setProxy(seleniumProxy);
            options.addArguments("--headless");
            options.addArguments("--window-size=1920,1080");
            options.setBinary("/usr/bin/firefox");
            options.merge(capabilities);

            // Altera o user-agent padrão
            String userAgent = "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:141.0) Gecko/20100101 Firefox/141.0";
            options.addPreference("general.useragent.override", userAgent);

            options.setPageLoadStrategy(PageLoadStrategy.NORMAL);

            driver = new FirefoxDriver(options);
            driver.manage().window().maximize();
        } catch (WebDriverException e) {
            LOGGER.error("Erro ao inicializar o Firefox Driver: {}", e.getMessage(), e); // ERROR
        } catch (Exception e) {
            LOGGER.error("Erro inesperado ao inicializar o Firefox Driver: {}", e.getMessage(), e); // ERROR
        }
    }

    public Response accessURL(String composedURL) {
        if (composedURL == null || composedURL.trim().isEmpty()) {
            LOGGER.warn("URL composta é nula ou vazia."); // WARN
            return new Response(true, false, "URL inválida");
        }

        LOGGER.info("Acessando a URL: {}", composedURL); // INFO
        String[] temp = composedURL.split("  ");
        String url = temp[0];
        String dom = "";

        if (url.contains("http")) {
            dom = url.split("/")[2];
        } else {
            dom = url.split("/")[0];
        }

        if (blockedDomains.get(dom) != null && blockedDomains.get(dom) >= 10) {
            LOGGER.warn("Domínio bloqueado: {}. Limite de tentativas excedido.", dom); // WARN
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
            LOGGER.info("URL acessada com sucesso: {}", finalUrl);

            // Aguarda com que os elementos visuais da página carreguem
            try {
                new WebDriverWait(driver, Duration.ofSeconds(imagesLoadTimeout)).until(webDriver -> {
                    JavascriptExecutor js = (JavascriptExecutor) webDriver;
                    return (Boolean) js.executeScript("return typeof jQuery !== 'undefined' && jQuery.active == 0");
                });
            } catch (Exception e) {
                Thread.sleep(Duration.ofSeconds(imagesLoadTimeout).toMillis());
            }

            // Screenshot antes de se movimentar na página
            String screenshotFileName = Base64Parser.encode(url);
            takeScreenshot(screenshotFileName  + "_0");

            // Screenshot depois de se movimentar na página 
            JavascriptExecutor js = (JavascriptExecutor) driver;
            long pageHeight = (long) js.executeScript("return document.body.scrollHeight");
            int increment = 1080;
            int pos = 0;
            int numScrolls = 0;
            int maxScrolls = 3;
            while(pos + increment < pageHeight && numScrolls < maxScrolls){
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
            takeScreenshot(screenshotFileName + "_1");

        } catch (WebDriverException e) {
            if (e instanceof NoSuchSessionException) {
                LOGGER.error("Sessão do WebDriver não existe ou não está ativa ao acessar {}: {}", composedURL,
                        e.getMessage(), e);
                getFirefoxDriver(new DesiredCapabilities()); // Reinicializa o driver
                return createResponseWithException(composedURL, e);
            } else {
                LOGGER.error("Erro no WebDriver ao acessar {}: {}", composedURL, e.getMessage(), e); // ERROR
                handleBlockedDomain(dom);
                logFirefoxException(composedURL, e);
                return createResponseWithException(composedURL, e);
            }
        } catch (Exception e) {
            LOGGER.error("Erro inesperado ao acessar a URL: {}", e.getMessage(), e); // ERROR
            return new Response(true, false, "Erro inesperado");
        }

        if (!finalUrl.equals("about:blank")) {
            return handleSuccessfulUrlAccess(finalUrl, composedURL, dom);
        }

        LOGGER.warn("URL final inválida: about:blank"); // WARN
        return new Response(true, false, "wtf");
    }

    private void takeScreenshot(String screenshotFileName) {
        File screenshotFile = driver.getFullPageScreenshotAs(OutputType.FILE);

        String screenshotStrPath = screenshotsDirPath + "/" + screenshotFileName + ".png";
        Path destinationPath = new File(screenshotStrPath).toPath();

        try {
            // Salva a captura de tela da página
            Files.copy(screenshotFile.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
            screenshotFile.delete();

            LOGGER.info("Screenshot salva com sucesso em: {}", destinationPath.toString());
        } catch (IOException e) {
            LOGGER.error("Erro ao salvar a captura de tela da página: {}", e.getMessage(), e);
        }
    }

    private void handleBlockedDomain(String dom) {
        if (blockedDomains.get(dom) == null) {
            blockedDomains.put(dom, 1);
        } else {
            int valor = blockedDomains.get(dom);
            valor += 1;
            blockedDomains.replace(dom, valor);
        }
    }

    private void logFirefoxException(String composedURL, Exception e) {
        try {
            logsWriter.writeFirefoxException(pid, composedURL + e.toString());
        } catch (IOException e1) {
            LOGGER.error("Erro ao registrar exceção do Firefox: {}", e1.getMessage(), e1); // ERROR
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
            LOGGER.info("Endereço IP do host {}: {}", hostname, ipString); // INFO
        } catch (MalformedURLException e) {
            LOGGER.error("Erro ao processar URL malformada: {}", e.getMessage(), e); // ERROR
            finalUrl = "-";
            ipString = "0";
        } catch (UnknownHostException e) {
            LOGGER.warn("Erro ao resolver o domínio {}: {}", dom, e.getMessage(), e); // WARN
            handleBlockedDomain(dom);
            finalUrl = "-";
            ipString = "0";
        } catch (Exception e) {
            LOGGER.error("Erro inesperado ao obter IP: {}", e.getMessage(), e); // ERROR
            finalUrl = "-";
            ipString = "0";
        }

        String out = composedURL.replace("\n", "") + "  " + finalUrl + "  " + ipString + "\n";
        String hash;
        String page;
        String tag; // Para marcar o tipo da página

        try {
            String html = driver.getPageSource();
            Document document = Jsoup.parse(html);
            page = document.toString();
            hash = DigestUtils.md5Hex(page);

            // Obtém o status code do primeiro HarEntry
            int statusCode = proxy.getHar().getLog().getEntries().get(0).getResponse().getStatus();

            // Verifica o tipo de página para tageamento
            if (page.isEmpty()) {
                tag = "EMPTY PAGE"; // Página vazia
            } else if (statusCode >= 400 && statusCode < 600) {
                tag = "ERROR PAGE"; // Página padrão de erro (404, 403, 5XX)
            } else if (page.length() < 500) {
                tag = "PARTIAL PAGE"; // Página parcialmente baixada, supõe-se que conteúdo completo seja mais longo
            } else {
                tag = "COMPLETE PAGE"; // Página completa
            }

            LOGGER.info("Página acessada com sucesso. Tag: {}, Hash gerado: {}", tag, hash); // INFO

        } catch (Exception e) {
            LOGGER.error("Erro ao obter fonte da página: {}", e.getMessage(), e); // ERROR
            page = "";
            hash = "EMPTYPAGE";
            tag = "Empty Page"; // Assume-se vazia em caso de erro
        }

        // Adiciona o tag ao log
        String url8 = out.replace("\n", "") + "  " + hash + "  " + tag + "\n";
        try {
            logsWriter.writeSourcePage(pid, url8);
            logsWriter.writeSourcePage(pid, page);
            logsWriter.writeSourcePage(pid, "\n*!-@x!x@-!*\n");
        } catch (IOException e) {
            LOGGER.error("Erro ao gravar a página fonte: {}", e.getMessage(), e); // ERROR
        }

        return new Response(false, false, out, proxy.getHar().getLog().getEntries());
    }

    public void run() {
        LOGGER.info("Iniciando o processo com PID: {}", pid); // INFO
        System.setProperty("webdriver.gecko.driver", this.geckoDriverBinaryPath);
        DesiredCapabilities capabilities = new DesiredCapabilities();

        getProxyServer();
        getSeleniumProxy();
        capabilities.setCapability("marionette", true);
        getFirefoxDriver(capabilities);

        while (!killProcesses.get()) {
            try {
                if (restartProcesses.get()) {
                    LOGGER.warn("Reiniciando o processo com PID: {}", pid); // WARN
                    break;
                }
                long startTime = System.currentTimeMillis();
                logsWriter.writeTimeURLs(pid, Long.toString(startTime) + " ");
                String composedURL = listaUrls.take();

                if (composedURL.equals("http://poison_pill.com")) {
                    LOGGER.info("Processo PID {} finalizando. Poison Pill recebido.", pid); // INFO
                    killProcesses.compareAndSet(false, true);
                    break;
                }

                LOGGER.info("Acessando a próxima URL na lista para o PID {}: {}", pid, composedURL); // INFO
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
                    LOGGER.info("Cadeia de IPs processados: {}", ipsChain); // INFO
                    logsWriter.writeTcp(pid, "  " + ipsChain);
                }

                logsWriter.writeTcp(pid, "\n*!-@x!x@-!*\n");
                logsWriter.writeCadeiaURLs(pid, "*!-@x!x@-!*\n");
                long finalTime = System.currentTimeMillis();
                logsWriter.writeTimeURLs(pid, Long.toString(finalTime) + "\n");
            } catch (NoSuchSessionException e) {
                LOGGER.error("Sessão do WebDriver não existe ou não está ativa: {}", e.getMessage(), e);
                getFirefoxDriver(new DesiredCapabilities());
            } catch (WebDriverException e) {
                LOGGER.error("Erro no WebDriver: {}", e.getMessage(), e);
            } catch (InterruptedException e) {
                LOGGER.error("Processo interrompido com erro: {}", e.getMessage(), e);
            } catch (IOException e) {
                LOGGER.error("Erro de I/O no processo PID {}: {}", pid, e.getMessage(), e);
            }
        }

        try {
            if (driver != null) {
                driver.close();
                LOGGER.info("Firefox Driver fechado com sucesso."); // INFO
            }
            proxy.stop();
            LOGGER.info("Proxy finalizado com sucesso para o PID: {}", pid); // INFO
        } catch (Exception e) {
            LOGGER.error("Erro ao finalizar o processo PID {}: {}", pid, e.getMessage(), e); // ERROR
        }
    }
}