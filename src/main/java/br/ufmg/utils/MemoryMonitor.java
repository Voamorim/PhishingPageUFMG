package br.ufmg.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MemoryMonitor implements Runnable {
	private AtomicBoolean processesRestart;
	private static final Logger LOGGER = LogManager.getLogger();

	public MemoryMonitor(AtomicBoolean rp) {
		processesRestart = rp;
	}

	public void run() {
		int numberOfRestarts = 0;
		while (true) {
			try {
				TimeUnit.SECONDS.sleep(1);
			} catch (InterruptedException e1) {
				return;
			}

			if (processesRestart.get()){
				continue;
			}

			java.lang.Process p = null;
			try {
				p = new ProcessBuilder("free", "-t", "-m").start();
				p.waitFor();
			} catch (IOException e) {
				LOGGER.error("Error while running free command: {}" , e.getMessage());
				continue;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}

			try (BufferedReader buf = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
				String line;
				StringBuilder output = new StringBuilder();

				while((line = buf.readLine()) != null) {
					output.append(line).append("\n");
				}

				String[] lines = output.toString().split("\n");
				if (lines.length < 2) continue;

				String tokens = lines[1];
				String[] outputList = tokens.split("\\s+");

				if (outputList.length > 6) {
					double memoryPercent = ((Double.parseDouble(outputList[1]) - Double.parseDouble(outputList[6])) / Double.parseDouble(outputList[1])) * 100;

					if (memoryPercent > 90.0) {
						numberOfRestarts++;

						if(!processesRestart.get() && !Thread.currentThread().isInterrupted()) {
							LOGGER.warn("Restarting process {}...", numberOfRestarts);
							LOGGER.info("Memory usage: {}%", memoryPercent);
							processesRestart.set(true);
						}
					}
				}
			} catch (IOException | NumberFormatException e) {
				if (!Thread.currentThread().isInterrupted()){
					LOGGER.error("Error processing memory data: {}", e.getMessage());
				}
			} finally {
				if(p != null){
					p.destroy();
				}
			}
		}
	}
}
