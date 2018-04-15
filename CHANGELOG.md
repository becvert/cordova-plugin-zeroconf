## [1.3.3] - 2018-04-13

- [Android] updating JmDNS dependency to 3.5.4

## [1.3.2] - 2018-04-03

- [Android] preventing crash (ConcurrentModificationException and RuntimeException)
- [iOS] updating cordova-plugin-add-swift-support dependency to 1.7.2

## [1.3.1] - 2018-01-02

- [Android] acquiring Multicast lock on browsing only
- [iOS] updating cordova-plugin-add-swift-support dependency to version 1.7.1

## [1.3.0] - 2017-11-23

- Added `reInit()` method to allow for runtime plugin reset
- [iOS] Added `close()` callback (per [#52](https://github.com/becvert/cordova-plugin-zeroconf/issues/52))
- [iOS] Added `stop()` callback

## [1.2.8] - 2017-10-13

- [Android] fixing NPE when calling close right after unwatch and stop right after unregister
- [Android] fixing getHostName for API 26+
- [Android] upgrading jmdns should fix slow closing
- [Android] upgrading to [org.jmdns:jmdns:3.5.3](https://github.com/jmdns/jmdns)

## [1.2.7] - 2017-08-31

- [Android] fixing watchAddressFamily option not applied
- [iOS] setting version of cordova-plugin-add-swift-support dependency.

## [1.2.6] - 2017-08-07

- new 'resolved' event
- [Android] fixing unwatch does not return

## [1.2.5] - 2017-07-20

- Changelog moved to CHANGELOG.md
- [Android] speed discovery up using new configuration options: registerAddressFamily and watchAddressFamily
- [Android] upgrading to [org.jmdns:jmdns:3.5.2](https://github.com/jmdns/jmdns)

## [1.2.4]
- plugin.xml: moving js clobbers from global to only supported platforms
- [iOS] fix for empty txtRecords

## [1.2.3]
- [Android] listening on all multicast and non-loopback interfaces instead of first found IPv4 and first found IPv6
- [Android] fixing java.util.concurrent.RejectedExecutionException (BrowserManager.serviceAdded)

## [1.2.2]
- [Android] re-registering while DNS entry is still cached causes IllegalStateException

## [1.2.1]
- [Android] check that NetworkInterface.supportsMulticast
- [iOS] add Hostname.m to the target

## [1.2.0]

- new getHostname function
- added parameter for domain
- added success/failure callbacks
- normalized service object
- more ipv6 support