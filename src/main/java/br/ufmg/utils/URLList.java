package br.ufmg.utils;

import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.DirectoryStream;
import java.util.HashSet;

public class URLList {
	private Charset charset = Charset.forName("UTF-8");
	private Path urlListFilePath;
	private HashSet<String> urlsSet;

	public URLList(Path urlListFilePath) throws IOException {
		urlsSet = new HashSet<String>();
		this.urlListFilePath = urlListFilePath;
		if (urlListFilePath != null) {
			readURLsFromFile();
		}
	}

	private void addURLsFromFile(Path urlFilePath) throws IOException {
		BufferedReader fileData = Files.newBufferedReader(urlFilePath, charset);
		for (String line = fileData.readLine(); line != null; line = fileData.readLine()) {
			this.urlsSet.add(line);
		}
		fileData.close();
	}

	private void readURLsFromFile() throws IOException {
		File repository = urlListFilePath.toFile();
		if (repository.exists()) {
			if (repository.isFile()) {
				this.addURLsFromFile(this.urlListFilePath);
			} else if (repository.isDirectory()) {
				DirectoryStream<Path> directory = Files.newDirectoryStream(this.urlListFilePath);
				for (Path filePath : directory) {
					this.addURLsFromFile(filePath);
				}
				directory.close();
			} else {
				System.err.println("[WARN] The file or directory " + this.urlListFilePath.toString()
						+ " does not exists. So it will be ignored.");
			}
		} else {
			System.err.println("[WARN] The file or directory " + this.urlListFilePath.toString()
					+ " does not exists. So it will be ignored.");
		}
	}

	synchronized public boolean has(String url) {
		return this.urlsSet.contains(url);
	}
}
