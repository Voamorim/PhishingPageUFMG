package br.ufmg.utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class Singleton {
	static private Singleton _instance;
	private Map<String, List<Long>> requestsDict;
	private long startTime;
	private LogsWriter logsWriter;
	private int requestsWindow;

	// methods and attributes for Singleton pattern
	private Singleton() {
		initGlobals();
	}

	private void initGlobals() {
		requestsDict = new HashMap<String, List<Long>>();
		startTime = System.currentTimeMillis();
	}

	public static Singleton getInstance() {
		if (_instance == null) {
			synchronized (Singleton.class) {
				if (_instance == null)
					_instance = new Singleton();
			}
		}
		return _instance;
	}

	// synchronized methods to set and get the global parameters
	synchronized public void setParameters(int requestsWindow, LogsWriter logsWriter) {
		this.logsWriter = logsWriter;
		this.requestsWindow = requestsWindow;
	}

	synchronized public boolean isInDict(String domain) {
		return requestsDict.containsKey(domain);
	}

	synchronized public int getNumeroReq(String domain) {
		if (((System.currentTimeMillis() - this.startTime)) / 1000 > this.requestsWindow) {
			printHighestScores();
		}
		Predicate<Long> spentTime = timeBefore -> (((System.currentTimeMillis() - timeBefore))
				/ 1000 > this.requestsWindow);
		this.requestsDict.get(domain).removeIf(spentTime);
		return this.requestsDict.get(domain).size();
	}

	synchronized public void setRequestsNumber(String domain, long value) {
		if (isInDict(domain)) {
			this.requestsDict.get(domain).add(value);
		} else {
			List<Long> newList = new ArrayList<Long>();
			newList.add(value);
			this.requestsDict.put(domain, newList);
		}
	}

	synchronized public void printHighestScores() {
		List<Pair> domainRequestsList = new ArrayList<Pair>();
		for (Map.Entry<String, List<Long>> entry : this.requestsDict.entrySet()) {
			List<Long> requestsList = entry.getValue();
			for (Long req : requestsList) {
				Long res = req - this.startTime;
				Pair temp = new Pair(entry.getKey(), res.floatValue() / 1000);
				domainRequestsList.add(temp);
			}
		}
		Collections.sort(domainRequestsList);
		try {
			FileWriter outputFile = new FileWriter(this.logsWriter.getLogDirPath().resolve(
					this.logsWriter.getStandardFileNameFromSuffix("requests")),
					false);
			for (Pair par : domainRequestsList) {
				outputFile.write(par.firstValue() + "  " + par.secondValue() + "\n");
			}
			outputFile.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.exit(0);
	}
}
