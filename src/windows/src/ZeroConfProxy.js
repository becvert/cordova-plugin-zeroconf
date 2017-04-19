/*global Windows:true */
var enumeration = Windows.Devices.Enumeration;

var DnsSdProtocol = "4526e8c1-8aac-4153-9b16-55e86ada0e54";

// Filter results by domain and service name
var getHostname = function (success, failure) {
    var hostnames = Windows.Networking.Connectivity.NetworkInformation.getHostNames();
    var hostname = hostnames.find(function (hostname) {
        return hostname.canonicalName.endsWith(".local");
    });
    if (hostname === undefined) {
        failure();
    } else {
        success(hostname.canonicalName);
    }
}

var watchers = {};

var unwatch = function (success, failure, type, domain) {
    if (!watchers[domain]) {
        if (failure) {
            setImmediate(failure, "domain: '" + domain + "' not used");
        }
        return;
    }
    if (!watchers[domain][type]) {
        if (failure) {
            setImmediate(failure, "type: '" + type + "' not used in domain: '" + domain + "'");
        }
        return;
    }
    var watchersToRemove = watchers[domain][type];
    var numberOfWatchers = watchersToRemove.length;
    for (var index = 0; index != numberOfWatchers; ++index) {
        var watcher = watchersToRemove[index];
        watcher.stop();
    }
    watchers[domain][type] = [];
    
    if (success) {
        setImmediate(success);
    }
}

var close = function (success, failure) {
    for (var domain in watchers) {
        for (var type in watchers[domain]) {
            unwatch(domain, type);
        }
    }
}

var watch = function (success, failure, type, domain) {
    var queryString = "System.Devices.AepService.ProtocolId:={" + DnsSdProtocol + "} AND " + "System.Devices.Dnssd.Domain:=\"local\" AND System.Devices.Dnssd.ServiceName:=\"_rolety._tcp\"";
    //var queryString = "System.Devices.AepService.ProtocolId:={" + DnsSdProtocol + "}";

    // Start a watcher with the query string, and request other properties (discover & resolve)
    //https://docs.microsoft.com/en-us/windows/uwp/devices-sensors/enumerate-devices-over-a-network
    //var watcher = enumeration.DeviceInformation.createWatcher(queryString, [], enumeration.DeviceInformationKind.AssociationEndpointService);
    //var watcher = enumeration.DeviceInformation.createWatcher(queryString, [], enumeration.DeviceInformationKind.associationEndpointService);
    var watcher = enumeration.DeviceInformation.createWatcher(queryString, [
        "System.Devices.IpAddress",
        "System.Devices.Dnssd.InstanceName",
        "System.Devices.Dnssd.PortNumber",
        "System.Devices.Dnssd.ServiceName",
        "System.Devices.Dnssd.HostName",
        "System.Devices.Dnssd.TextAttributes"
    ], enumeration.DeviceInformationKind.associationEndpointService);

    if (!watchers[domain]) {
        watchers[domain] = {};
    }
    if (!watchers[domain][type]) {
        watchers[domain][type] = [];
    }
    watchers[domain][type].push(watcher);

    function publishChange(sender, args) {
        var x = sender;
        var devices = sender.detail;
        var l = devices.length;
        for (var i = 0; i != l; ++i) {
            var device = sender.detail[i];
            var result = {
                action: sender.type,
                service: {
                    type: type,
                    name: device.name,
                    port: device.properties["System.Devices.Dnssd.PortNumber"],
                    hostname: device.properties["System.Devices.Dnssd.HostName"],
                    ipv4Addresses: [],
                    ipv6Addresses: [],
                    txtRecord: {}
                }
            };
            var ips = device.properties["System.Devices.IpAddress"];
            var number_of_ips = ips.length;
            for (var address_index = 0; address_index != number_of_ips; ++address_index) {
                var ip = new Windows.Networking.HostName(ips[address_index]);
                if (ip.type === Windows.Networking.HostNameType.ipv4) {
                    result.service.ipv4Addresses.push(ip);
                } else {
                    result.service.ipv6Addresses.push(ip);
                }
            }
            var textAttributes = device.properties["System.Devices.Dnssd.TextAttributes"];
            var numberOfTextAttributes = textAttributes.length;
            for (var attribute_index = 0; numberOfTextAttributes != attribute_index; ++attribute_index) {
                var pair = textAttributes[attribute_index].split("=");
                result.service.txtRecord[pair[0]] = pair[1];
            }
            success(result);
        }
    }

    // Add callback to watcher 
    // Add event handlers
    watcher.addEventListener("added", publishChange);
    watcher.addEventListener("removed", publishChange);
    watcher.addEventListener("updated", publishChange);
    watcher.addEventListener("enumerationcompleted",
        function () { });
    watcher.addEventListener("stopped", function () { });
    // Start enumerating and listening for events
    watcher.start();
};

module.exports = {
    watch:watch,
    unwatch:unwatch,
    close:close,
    getHostname:getHostname
};

require("cordova/exec/proxy").add("ZeroConf",module.exports);
