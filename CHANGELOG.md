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