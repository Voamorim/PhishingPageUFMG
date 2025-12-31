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

			java.lang.Process p;
			try {
				p = Runtime.getRuntime().exec("free -t -m");
			} catch (IOException e) {
				e.printStackTrace();
				continue;
			}

			try {
				p.waitFor();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				continue;
			}

			BufferedReader buf = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line = "";
			String output = "";
			String tokens = "";

			try {
				while ((line = buf.readLine()) != null) {
					output += line + "\n";
				}
				tokens = output.split("\n")[1];
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				continue;
			}
			String[] outputList = tokens.split("\\s+");
			double memoryPercent = ((Double.parseDouble(outputList[1]) - Double.parseDouble(outputList[6]))
					/ Double.parseDouble(outputList[1])) * 100;
			if (memoryPercent > 90.0) { // Caso esteja utilizando mais de 90% da memória, reinicia o geckodriver e o
										// firefox
				numberOfRestarts++;
				LOGGER.warn("Restarting process " + numberOfRestarts + "...");
				LOGGER.info(memoryPercent);
				processesRestart.set(true);	
			}
		}
	}
}
