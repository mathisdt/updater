![license](https://img.shields.io/github/license/mathisdt/updater.svg?style=flat) [![last released](https://img.shields.io/github/release-date/mathisdt/updater.svg?label=last%20released&style=flat)](https://github.com/mathisdt/updater/releases)

# Updater

This is a utility for Java projects which is only interesting for developers, not for end users.
It supports automatic updates on application startup or shutdown, depending on your usage of it.

The resulting artifact deliberately makes a compromise on JSON parsing and UI rendering
to be able to ship without any third-party dependencies. This way, you can use it with
any JRE 17 or later while not having to bother with complicated classpaths or many big files.

## Using it for your project

You can get the JAR from the [latest release](https://github.com/mathisdt/updater/releases/latest)
and put it into your project's distribution, but you have to add a file named `updater.properties`
to configure it. An example can be found [here](https://github.com/mathisdt/updater/blob/master/src/main/resources/updater.properties).

* `latest_release_json_url`: the URL where the latest release's information can be found
  (at the moment, only Github's JSON format is supported)
* `archive_download_template`: the URL where the binary artifact can be downloaded,
  with a placeholder `%s` for the release name
* `local_jar_file`: the location of the JAR file which may need to be updated
* `manifest_attribute`: the attribute name inside the JAR manifest which contains the timestamp
  (it has to be of the form `yyyy-MM-dd'T'HH:mm:ss'Z'`)

Having configured it, you should include it in your start script - either start it before
your application (so it can update your application before it is used) or after your application
has shut down (so the user can use the updated version next time).
