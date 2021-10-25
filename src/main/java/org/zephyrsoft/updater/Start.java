package org.zephyrsoft.updater;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.jar.JarFile;

import javax.swing.JOptionPane;

public class Start {

	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
	private static final String INSTALL_UPDATE = "Install Update";
	private static final String NOT_NOW = "Not Now";

	public static void main(final String[] args) {
		try (InputStream propertiesStream = new FileInputStream("updater.properties")) {
			Properties properties = new Properties();
			properties.load(propertiesStream);

			VersionInfo localVersionInfo = loadLocalVersionInfo(properties);
			VersionInfo remoteVersionInfo = loadRemoteVersionInfo(properties);

			if (localVersionInfo.hasTimestamp()
				&& remoteVersionInfo.hasTimestampAndDownloadUrl()
				// ten minutes grace period because the build process takes some time
				// and we compare the manifest entry (taken at start)
				// against the Github timestamp (taken at end)
				&& localVersionInfo.timestamp().isBefore(remoteVersionInfo.timestamp().minusMinutes(10))) {
				int userChoice = JOptionPane.showOptionDialog(null, """
					Update found!

					New version timestamp: %s

					Currently installed: %s

					""".formatted(remoteVersionInfo.timestamp().format(TIMESTAMP_FORMAT),
					localVersionInfo.timestamp().format(TIMESTAMP_FORMAT)),
					"Update found", JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE,
					null, new Object[] { INSTALL_UPDATE, NOT_NOW }, INSTALL_UPDATE);
				if (userChoice == JOptionPane.OK_OPTION) {
					update(properties, remoteVersionInfo);
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private static void update(final Properties properties, final VersionInfo versionInfo) {
		// TODO
	}

	private static VersionInfo loadLocalVersionInfo(final Properties properties) throws Exception {
		String timestamp = null;
		try (JarFile jar = new JarFile(properties.getProperty("local_jar_file"))) {
			timestamp = jar.getManifest().getMainAttributes()
				.getValue(properties.getProperty("manifest_attribute"));
		}

		ZonedDateTime installedReleaseTimestamp = ZonedDateTime.parse(timestamp)
			.withZoneSameInstant(ZoneId.systemDefault());
		return new VersionInfo(installedReleaseTimestamp, null);
	}

	private static VersionInfo loadRemoteVersionInfo(final Properties properties) throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
			.uri(new URI(properties.getProperty("latest_release_json_url")))
			.GET()
			.build();
		HttpResponse<String> latestReleaseResponse = HttpClient.newHttpClient()
			.send(request, HttpResponse.BodyHandlers.ofString());
		String latestReleaseResponseBody = latestReleaseResponse.body();
		String timestamp = latestReleaseResponseBody.lines()
			.filter(s -> s.contains("\"published_at\""))
			.findFirst().orElse("")
			.replaceAll("^.*\"published_at\": *\"([^\"]+)\".*$", "$1");
		ZonedDateTime latestReleaseTimestamp = ZonedDateTime.parse(timestamp)
			.withZoneSameInstant(ZoneId.systemDefault());
		String tagName = latestReleaseResponseBody.lines()
			.filter(s -> s.contains("\"tag_name\""))
			.findFirst().orElse("")
			.replaceAll("^.*\"tag_name\": *\"([^\"]+)\".*$", "$1");
		String downloadUrl = properties.getProperty("archive_download_template").formatted(tagName);
		return new VersionInfo(latestReleaseTimestamp, downloadUrl);
	}
}
