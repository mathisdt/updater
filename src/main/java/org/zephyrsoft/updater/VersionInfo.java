package org.zephyrsoft.updater;

import java.time.ZonedDateTime;

public record VersionInfo(ZonedDateTime timestamp, String downloadUrl) {
	public boolean hasTimestampAndDownloadUrl() {
		return timestamp != null && downloadUrl != null;
	}

	public boolean hasTimestamp() {
		return timestamp != null;
	}
}