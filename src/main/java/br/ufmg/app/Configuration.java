package br.ufmg.app;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.FileReader;
import java.io.IOException;
import java.io.FileNotFoundException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.json.JSONException;

public class Configuration {

    private int concurrentBrowserInstancesNumber;
    private int pageTimeout;
    private int windowTimeout;
    private int maxRequestNumber;
    private Path configFilePath;
    private Path geckodriverBinPath;
    private Path whiteListPath;
    private Path blackListPath;
    private Path repositoryPath;
    private Path runtimeControllersPath;
    private Path logsDirPath;
    private Path screenshotsDirPath;

    public Configuration(String configFilePathStr) {
        // Get configuration file and the propoer filepaths to get the path of the
        // auxiliar files.
        Path currentWorkDir = Paths.get("").toAbsolutePath();
        Path localConfigFilePath = Paths.get(configFilePathStr);
        if (localConfigFilePath.isAbsolute()) {
            this.configFilePath = localConfigFilePath;
        } else {
            this.configFilePath = Paths.get(currentWorkDir.toString(), configFilePathStr).toAbsolutePath();
        }


        // Reading configuration JSON file
        try (FileReader reader = new FileReader(this.configFilePath.toString())) {
            JSONTokener jsonTokener = new JSONTokener(reader);
            JSONObject configObject = new JSONObject(jsonTokener);

            // Required parameters
            this.readConcurrentBrowserInstancesNumber(configObject);
            this.readPageTimeout(configObject);
            this.readWindowTimeout(configObject);
            this.readMaxRequestNumber(configObject);
            this.readRepositoryPath(configObject);
            this.readRuntimeControllersPath(configObject);
            this.readGeckodriverBinaryPath(configObject);
            this.readScreenshotsDirPath(configObject);

            // Optional parameters
            this.readLogsDirPath(configObject);
            this.readBlackListPath(configObject);
            this.readWhiteListPath(configObject);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.exit(-1);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        } catch (JSONException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private void readConcurrentBrowserInstancesNumber(JSONObject configObject) throws JSONException {
        this.concurrentBrowserInstancesNumber = configObject.getInt("concurrentBrowsers");
    }

    private void readPageTimeout(JSONObject configObject) throws JSONException {
        this.pageTimeout = configObject.getInt("pageTimeout");
    }

    private void readWindowTimeout(JSONObject configObject) throws JSONException {
        this.windowTimeout = configObject.getInt("windowTimeout");
    }

    private void readMaxRequestNumber(JSONObject configObject) throws JSONException {
        this.maxRequestNumber = configObject.getInt("maxRequests");
    }

    private void readRepositoryPath(JSONObject configObject) throws JSONException {
        Path specifiedRepository = Paths.get(configObject.getString("repositoryPath"));
        this.repositoryPath = readRelativeOrAbsolutePath(specifiedRepository);
    }

    private void readRuntimeControllersPath(JSONObject configObject) throws JSONException {
        Path specifiedAttribute = Paths.get(configObject.getString("runtimeControllersPath"));
        this.runtimeControllersPath = readRelativeOrAbsolutePath(specifiedAttribute);
    }

    private void readGeckodriverBinaryPath(JSONObject configObject) throws JSONException {
        Path specifiedAttribute = Paths.get(configObject.getString("geckodriverBinPath"));
        this.geckodriverBinPath = readRelativeOrAbsolutePath(specifiedAttribute);
    }

    private void readLogsDirPath(JSONObject configObject) throws JSONException {
        Path specifiedLogsDir = Paths.get(configObject.optString("logsDirPath"));
        this.logsDirPath = readRelativeOrAbsolutePath(specifiedLogsDir);
    }

    private void readScreenshotsDirPath(JSONObject configObject) throws JSONException {
        Path specifiedAttribute = Paths.get(configObject.getString("screenshotsPath"));
        this.screenshotsDirPath = readRelativeOrAbsolutePath(specifiedAttribute);
    }

    private void readWhiteListPath(JSONObject configObject) throws JSONException {
        if (configObject.has("whiteListPath")) {
            Path specifiedAttribute = Paths.get(configObject.getString("whiteListPath"));
            this.whiteListPath = readRelativeOrAbsolutePath(specifiedAttribute);
        } else {
            this.whiteListPath = null;
        }
    }

    private void readBlackListPath(JSONObject configObject) throws JSONException {
        if (configObject.has("blackListPath")) {
            Path specifiedAttribute = Paths.get(configObject.getString("blackListPath"));
            this.blackListPath = readRelativeOrAbsolutePath(specifiedAttribute);
        } else {
            this.blackListPath = null;
        }
    }

    private Path readRelativeOrAbsolutePath(Path pathAttribute) {
        if (pathAttribute.isAbsolute()) {
            return pathAttribute;
        } else {
            return this.configFilePath.getParent().resolve(pathAttribute).toAbsolutePath().normalize();
        }
    }

    public int getConcurrentBrowserInstancesNumber() {
        return this.concurrentBrowserInstancesNumber;
    }

    public int getPageTimeout() {
        return this.pageTimeout;
    }

    public int getWindowTimeout() {
        return this.windowTimeout;
    }

    public int getMaxRequestNumber() {
        return this.maxRequestNumber;
    }

    public Path getConfigFilePath() {
        return this.configFilePath;
    }

    public Path getGeckodriverBinPath() {
        return this.geckodriverBinPath;
    }

    public Path getWhiteListPath() {
        return this.whiteListPath;
    }

    public Path getBlackListPath() {
        return this.blackListPath;
    }

    public Path getRepositoryPath() {
        return this.repositoryPath;
    }

    public Path getLogsDirPath() {
        return this.logsDirPath;
    }

    public Path getRuntimeControllersPath() {
        return this.runtimeControllersPath;
    }

    public Path getScreenshotsDirPath() {
        return this.screenshotsDirPath;
    }
}
