package org.zephyrsoft.updater;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.swing.JOptionPane;

public class Start {

	private static final String PROPERTY_LATEST_RELEASE_JSON_URL = "latest_release_json_url";
	private static final String PROPERTY_MANIFEST_ATTRIBUTE = "manifest_attribute";
	private static final String PROPERTY_ARCHIVE_DOWNLOAD_TEMPLATE = "archive_download_template";
	private static final String PROPERTY_LOCAL_JAR_FILE = "local_jar_file";

	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
	private static final String INSTALL_UPDATE = "Install Update";
	private static final String NOT_NOW = "Not Now";

	public static void main(final String[] args) {
		String ownLocation = null;
		try {
			ownLocation = Start.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
			System.out.println("own location: " + ownLocation);
		} catch (URISyntaxException e) {
			throw new RuntimeException("problem while probing paths", e);
		}

		try (InputStream propertiesStream = new FileInputStream(
				ownLocation + (ownLocation.endsWith("/") ? "" : "/") + "updater.properties")) {
			Properties properties = new Properties();
			properties.load(propertiesStream);

			VersionInfo localVersionInfo = loadLocalVersionInfo(properties);
			VersionInfo remoteVersionInfo = loadRemoteVersionInfo(properties);

			if (localVersionInfo.hasTimestamp() && remoteVersionInfo.hasTimestampAndDownloadUrl()
			// ten minutes grace period because the build process takes some time
			// and we compare the manifest entry (taken at start)
			// against the Github timestamp (taken at end)
					&& localVersionInfo.timestamp().isBefore(remoteVersionInfo.timestamp().minusMinutes(10))) {
				int userChoice = JOptionPane.showOptionDialog(null,
						"""
								Update found!

								New version timestamp: %s

								Currently installed: %s

								""".formatted(remoteVersionInfo.timestamp().format(TIMESTAMP_FORMAT),
								localVersionInfo.timestamp().format(TIMESTAMP_FORMAT)),
						"Update found", JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE, null,
						new Object[] { INSTALL_UPDATE, NOT_NOW }, INSTALL_UPDATE);
				if (userChoice == JOptionPane.OK_OPTION) {
					update(properties, remoteVersionInfo);
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private static void update(final Properties properties, final VersionInfo versionInfo) {
		Path zipFile = downloadArchive(versionInfo);

		try {
			byte[] buffer = new byte[1024];
			ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()));
			ZipEntry zipEntry = zis.getNextEntry();
			while (zipEntry != null) {
				File newFile = newFile(new File(properties.getProperty("")), zipEntry);
				if (zipEntry.isDirectory()) {
					if (!newFile.isDirectory() && !newFile.mkdirs()) {
						throw new IOException("Failed to create directory " + newFile);
					}
				} else {
					// fix for Windows-created archives
					File parent = newFile.getParentFile();
					if (!parent.isDirectory() && !parent.mkdirs()) {
						throw new IOException("Failed to create directory " + parent);
					}

					// write file content
					FileOutputStream fos = new FileOutputStream(newFile);
					int len;
					while ((len = zis.read(buffer)) > 0) {
						fos.write(buffer, 0, len);
					}
					fos.close();
				}
				zipEntry = zis.getNextEntry();
			}
			zis.closeEntry();
			zis.close();
		} catch (Exception e) {
			throw new RuntimeException("problem while unpacking the archive", e);
		}
	}

	private static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
		File destFile = new File(destinationDir, zipEntry.getName());

		String destDirPath = destinationDir.getCanonicalPath();
		String destFilePath = destFile.getCanonicalPath();

		if (!destFilePath.startsWith(destDirPath + File.separator)) {
			throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
		}

		return destFile;
	}

	private static Path downloadArchive(VersionInfo versionInfo) {
		HttpClient client = HttpClient.newBuilder().followRedirects(Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(15)).build();
		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(versionInfo.downloadUrl())).build();
		HttpResponse<Path> response;
		try {
			response = client.send(request, BodyHandlers.ofFile(Files.createTempFile("sdb2-", ".zip")));
			return response.body();
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException("problem while downloading archive", e);
		}
	}

	private static VersionInfo loadLocalVersionInfo(final Properties properties) throws Exception {
		String timestamp = null;
		try (JarFile jar = new JarFile(properties.getProperty(PROPERTY_LOCAL_JAR_FILE))) {
			timestamp = jar.getManifest().getMainAttributes()
					.getValue(properties.getProperty(PROPERTY_MANIFEST_ATTRIBUTE));
		}

		ZonedDateTime installedReleaseTimestamp = ZonedDateTime.parse(timestamp)
				.withZoneSameInstant(ZoneId.systemDefault());
		return new VersionInfo(installedReleaseTimestamp, null);
	}

	private static VersionInfo loadRemoteVersionInfo(final Properties properties) throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(new URI(properties.getProperty(PROPERTY_LATEST_RELEASE_JSON_URL))).GET().build();
		HttpResponse<String> latestReleaseResponse = HttpClient.newHttpClient().send(request,
				HttpResponse.BodyHandlers.ofString());
		String latestReleaseResponseBody = latestReleaseResponse.body();
		String timestamp = latestReleaseResponseBody.lines().filter(s -> s.contains("\"published_at\"")).findFirst()
				.orElse("").replaceAll("^.*\"published_at\": *\"([^\"]+)\".*$", "$1");
		ZonedDateTime latestReleaseTimestamp = ZonedDateTime.parse(timestamp)
				.withZoneSameInstant(ZoneId.systemDefault());
		String tagName = latestReleaseResponseBody.lines().filter(s -> s.contains("\"tag_name\"")).findFirst()
				.orElse("").replaceAll("^.*\"tag_name\": *\"([^\"]+)\".*$", "$1");
		String downloadUrl = properties.getProperty(PROPERTY_ARCHIVE_DOWNLOAD_TEMPLATE).formatted(tagName);
		return new VersionInfo(latestReleaseTimestamp, downloadUrl);
	}
}
