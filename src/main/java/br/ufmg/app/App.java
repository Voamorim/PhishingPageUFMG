package br.ufmg.app;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import br.ufmg.utils.FileWriter;
import br.ufmg.utils.LogsWriter;
import br.ufmg.utils.MemoryMonitor;
import br.ufmg.utils.Singleton;
import br.ufmg.utils.URLList;

public class App {

	private Configuration config;
	private LogsWriter logsWriter;
	private URLList whiteList;
	private URLList blackList;
	private File[] urlFiles;
	private BlockingQueue<String> urlsList;
	private AtomicBoolean restartProcesses;
	private AtomicBoolean killProcesses;
	private AtomicBoolean allUrlsProcessed;
	private static final Logger LOGGER = LogManager.getLogger();

	public App(Configuration config) {
		try {
			this.whiteList = new URLList(config.getWhiteListPath());
			this.blackList = new URLList(config.getBlackListPath());
		} catch (Exception e) {
			e.printStackTrace();
		}
		this.config = config;
		urlsList = new LinkedBlockingDeque<String>();
		restartProcesses = new AtomicBoolean(false);
		killProcesses = new AtomicBoolean(false);
		allUrlsProcessed = new AtomicBoolean(false);
	}

	public void run() throws FileNotFoundException, UnsupportedEncodingException, IOException, InterruptedException {
		this.startLogFiles();
		this.singletonSetup();
		this.getFiles();
		this.getURLs();
		this.manageProcesses();
	}

	private void startLogFiles() throws FileNotFoundException, UnsupportedEncodingException, IOException {
		this.logsWriter = new LogsWriter(config.getLogsDirPath(), config.getConcurrentBrowserInstancesNumber());
		this.logsWriter.createFiles();
	}

	private void singletonSetup() {
		Singleton.getInstance().setParameters(this.config.getWindowTimeout(), this.logsWriter);
	}

	/*
	 * Reads the files from the repository, sorting them by last modified time. If
	 * the repository is a file, it reads only that file.
	 */
	private void getFiles() {
		File repo = this.config.getRepositoryPath().toFile();
		if (repo.isDirectory()) {
			urlFiles = repo.listFiles();
			Arrays.sort(urlFiles, Comparator.comparingLong(File::lastModified));
		} else if (repo.isFile()) {
			urlFiles = new File[] { repo };
		} else {
			LOGGER.error("Inexistent URLs repository " + this.config.getRepositoryPath().toString());
			System.exit(-1);
		}

		if (urlFiles.length == 0) {
			LOGGER.error("Empty URLs repository" + this.config.getRepositoryPath().toString());
			System.exit(-1);
		}
	}

	private void getURLs() {
		Charset charset = Charset.forName("UTF-8");
		for (File file : urlFiles) {
			try {
				List<String> lines = Files.readAllLines(file.toPath(), charset);
				for (String line : lines) {
					urlsList.add(line);
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (NullPointerException e) {
				e.printStackTrace();
			}
		}
	}

	/*
	 * Função que determina se a aplicação deve parar, realizando
	 * a leitura de um arquivo na pasta shellscripts/sys/operante.
	 */
	public boolean isAppRunning() {
		File runtimeFile = config.getRuntimeControllersPath().resolve("running").toFile();
		if (!runtimeFile.exists()) {
			LOGGER.error("The runtime file " + runtimeFile.toString() + " does not exists.");
			System.exit(-1);
		}
		try {
			BufferedReader br = new BufferedReader(new FileReader(runtimeFile.toString()));
			char isRunningBoolean = (char) br.read();
			br.close();

			if (isRunningBoolean == 0) {
				LOGGER.info("The runtime file " + runtimeFile.toString() + " indicates that the app must stop.");
				return false;
			}

			if (this.allUrlsProcessed.get()) {
				LOGGER.info("All URLs have been processed. The app will stop.");
				return false;
			}

			return true;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(-1);
		} catch (IOException e) {
			LOGGER.warn("Something has gone wrong with" + runtimeFile.toString()
					+ ". So, the app keeps running.");
		}
		return true;
	}

	/* Função principal. Administa o multithreading */
	private void manageProcesses() throws InterruptedException {
		MemoryMonitor memoryMonitor = new MemoryMonitor(restartProcesses);
		Thread monitor = new Thread(memoryMonitor);
		monitor.start();

		List<Thread> threadsList = new ArrayList<Thread>();

		long startTime = System.nanoTime();

		while (isAppRunning()) {
			if (killProcesses.get()) {
				break;
			}

			// Check if all URLs are processed and no more need to be added
			if (urlsList.isEmpty() && !this.allUrlsProcessed.get() && threadsList.size() > 0) {
				LOGGER.info("All URLs have been processed. Signaling threads to stop.");
				this.allUrlsProcessed.set(true);
			}

			if (restartProcesses.get()) {
				System.out.println("[INFO] Waiting...");
				for (Thread thread : threadsList) {
					try {
						thread.join(600000);
					} catch (InterruptedException e) {
						continue;
					}
				}
				try {
					Runtime.getRuntime().exec("pkill -9 firefox");
					Runtime.getRuntime().exec("pkill -9 geckodriver");

				} catch (IOException e) {
					e.printStackTrace();
				}
				restartProcesses.set(false);
			}

			if (threadsList.size() >= this.config.getConcurrentBrowserInstancesNumber()) {
				for (int i = 0; i < threadsList.size(); i++) {
					if (!threadsList.get(i).isAlive()) {
						br.ufmg.utils.Process r = new br.ufmg.utils.Process(
								urlsList, killProcesses, restartProcesses,
								i, this.logsWriter, whiteList, blackList,
								this.config.getPageTimeout(),
								this.config.getImagesLoadTimeout(),
								this.config.getGeckodriverBinPath().toString(),
								this.config.getScreenshotsDirPath().toString(),
								this.config.getDownloadsDirPath().toString());
						Thread t = new Thread(r);
						t.start();
						threadsList.set(i, t);
						LOGGER.info("Restarting thread " + Integer.toString(i));
					}
				}
				try {
					TimeUnit.SECONDS.sleep(1);
				} catch (InterruptedException e) {
					LOGGER.error("Error while sleeping the main thread: {}", e.getMessage(), e);
				}
			} else if (!urlsList.isEmpty() && !killProcesses.get()) {
				int threadIndex = threadsList.size();
				br.ufmg.utils.Process r = new br.ufmg.utils.Process(
						urlsList, killProcesses, restartProcesses,
						threadIndex, this.logsWriter, whiteList, blackList,
						this.config.getPageTimeout(),
						this.config.getImagesLoadTimeout(),
						this.config.getGeckodriverBinPath().toString(),
						this.config.getScreenshotsDirPath().toString(),
						this.config.getDownloadsDirPath().toString());
				Thread t = new Thread(r);
				threadsList.add(t);
				t.start();
				LOGGER.info("Starting thread " + Integer.toString(threadIndex));
			}

			// Check if all threads have finished
			if (this.allUrlsProcessed.get() && areAllThreadsFinished(threadsList)) {
				LOGGER.info("All threads have finished. Exiting main loop.");
				break;
			}

			try {
				TimeUnit.MILLISECONDS.sleep(500);
			} catch (InterruptedException e) {
				LOGGER.error("Error while sleeping the main thread: {}", e.getMessage(), e);
			}
		}

		for (Thread thread : threadsList) {
			try {
				thread.join(1000);
			} catch (InterruptedException e) {
				LOGGER.error("Error while waiting for thread to finish: {}", e.getMessage(), e);
			}
		}

		writeRemainingURLs();
		monitor.interrupt();
		System.gc();

		long finalTime = System.nanoTime();
		long spentTime = finalTime - startTime;
		String timeString = Long.toString(spentTime) + '\n';

		try {
			Path logDirPath = this.logsWriter.getLogDirPath();
			File timeFile = logDirPath.resolve(this.logsWriter.getStandardFileNameFromSuffix("time")).toFile();
			if (!timeFile.exists()) {
				timeFile.createNewFile();
			}
			Files.write(timeFile.toPath(), timeString.getBytes());

			this.logsWriter.closeFiles();
		} catch (IOException e) {
			LOGGER.error("Error writing time log file: {}", e.getMessage(), e);
		}
	}

	private void writeRemainingURLs() {
		try {
			FileWriter remaining = new FileWriter(this.config.getLogsDirPath()
					.resolve(this.logsWriter.getStandardFileNameFromSuffix("remaining_urls")), false);

			while (urlsList.isEmpty() == false) {
				try {
					String url = urlsList.take();
					remaining.write(url + "\n");
				} catch (InterruptedException e) {
					LOGGER.error("Error writing remaining URLs: {}", e.getMessage(), e);
				}
			}
			remaining.close();
		} catch (IOException e) {
			LOGGER.error("Error writing remaining URLs: {}", e.getMessage(), e);
		}
	}

	private boolean areAllThreadsFinished(List<Thread> threadsList) {
		for (Thread thread : threadsList) {
			if (thread.isAlive()) {
				return false;
			}
		}
		return true;
	}
}