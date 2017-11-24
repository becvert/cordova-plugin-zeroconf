import Foundation

@objc(ZeroConf) public class ZeroConf : CDVPlugin  {

    fileprivate var publishers: [String: Publisher]!
    fileprivate var browsers: [String: Browser]!

    override public func pluginInitialize() {
        publishers  = [:]
        browsers = [:]
    }

    override public func onAppTerminate() {
        for (_, publisher) in publishers {
            publisher.destroy()
        }
        publishers.removeAll()

        for (_, browser) in browsers {
            browser.destroy();
        }
        browsers.removeAll()
    }

    public func getHostname(_ command: CDVInvokedUrlCommand) {

        let hostname = Hostname.get() as String

        #if DEBUG
            print("ZeroConf: hostname \(hostname)")
        #endif

        let pluginResult = CDVPluginResult(status:CDVCommandStatus_OK, messageAs: hostname)
        self.commandDelegate?.send(pluginResult, callbackId: command.callbackId)
    }

    public func register(_ command: CDVInvokedUrlCommand) {

        let type = command.argument(at: 0) as! String
        let domain = command.argument(at: 1) as! String
        let name = command.argument(at: 2) as! String
        let port = command.argument(at: 3) as! Int

        #if DEBUG
            print("ZeroConf: register \(name + "." + type + domain)")
        #endif

        var txtRecord: [String: Data]?
        if let dict = command.arguments[4] as? [String: String] {
            txtRecord = [:]
            for (key, value) in dict {
                txtRecord?[key] = value.data(using: String.Encoding.utf8)
            }
        }

        let publisher = Publisher(withDomain: domain, withType: type, withName: name, withPort: port, withTxtRecord: txtRecord, withCallbackId: command.callbackId)
        publisher.commandDelegate = commandDelegate
        publisher.register()
        publishers[name + "." + type + domain] = publisher

    }

    public func unregister(_ command: CDVInvokedUrlCommand) {

        let type = command.argument(at: 0) as! String
        let domain = command.argument(at: 1) as! String
        let name = command.argument(at: 2) as! String

        #if DEBUG
            print("ZeroConf: unregister \(name + "." + type + domain)")
        #endif

        if let publisher = publishers[name + "." + type + domain] {
            publisher.unregister();
            publishers.removeValue(forKey: name + "." + type + domain)
        }

    }

    public func stop(_ command: CDVInvokedUrlCommand) {
        #if DEBUG
            print("ZeroConf: stop")
        #endif

        for (_, publisher) in publishers {
            publisher.unregister()
        }
        publishers.removeAll()

        let pluginResult = CDVPluginResult(status:CDVCommandStatus_OK)
        self.commandDelegate?.send(pluginResult, callbackId: command.callbackId)
    }

    public func watch(_ command: CDVInvokedUrlCommand) {

        let type = command.argument(at: 0) as! String
        let domain = command.argument(at: 1) as! String

        #if DEBUG
            print("ZeroConf: watch \(type + domain)")
        #endif

        let browser = Browser(withDomain: domain, withType: type, withCallbackId: command.callbackId)
        browser.commandDelegate = commandDelegate
        browser.watch()
        browsers[type + domain] = browser

    }

    public func unwatch(_ command: CDVInvokedUrlCommand) {

        let type = command.argument(at: 0) as! String
        let domain = command.argument(at: 1) as! String

        #if DEBUG
            print("ZeroConf: unwatch \(type + domain)")
        #endif

        if let browser = browsers[type + domain] {
            browser.unwatch();
            browsers.removeValue(forKey: type + domain)
        }

    }

    public func close(_ command: CDVInvokedUrlCommand) {
        #if DEBUG
            print("ZeroConf: close")
        #endif

        for (_, browser) in browsers {
            browser.unwatch()
        }
        browsers.removeAll()

        let pluginResult = CDVPluginResult(status:CDVCommandStatus_OK)
        self.commandDelegate?.send(pluginResult, callbackId: command.callbackId)
    }

    public func reInit(_ command: CDVInvokedUrlCommand) {
        #if DEBUG
            print("ZeroConf: reInit")
        #endif

        // Terminate
        for (_, publisher) in publishers {
            publisher.destroy()
        }
        publishers.removeAll()

        for (_, browser) in browsers {
            browser.destroy();
        }
        browsers.removeAll()

        // init
        publishers  = [:]
        browsers = [:]

        let pluginResult = CDVPluginResult(status:CDVCommandStatus_OK)
        self.commandDelegate?.send(pluginResult, callbackId: command.callbackId)
    }

    internal class Publisher: NSObject, NetServiceDelegate {

        var nsp: NetService?
        var domain: String
        var type: String
        var name: String
        var port: Int
        var txtRecord: [String: Data]?
        var callbackId: String
        var commandDelegate: CDVCommandDelegate?

        init (withDomain domain: String, withType type: String, withName name: String, withPort port: Int, withTxtRecord txtRecord: [String: Data]?, withCallbackId callbackId: String) {
            self.domain = domain
            self.type = type
            self.name = name
            self.port = port
            self.txtRecord = txtRecord
            self.callbackId = callbackId
        }

        func register() {

            // Netservice
            let service = NetService(domain: domain, type: type , name: name, port: Int32(port))
            nsp = service
            service.delegate = self

            if let record = txtRecord {
                if record.count > 0 {
                    service.setTXTRecord(NetService.data(fromTXTRecord: record))
                }
            }

            commandDelegate?.run(inBackground: {
                service.publish()
            })

        }

        func unregister() {

            if let service = nsp {
                service.stop()
            }

        }

        func destroy() {

            if let service = nsp {
                service.stop()
            }

        }

        @objc func netServiceDidPublish(_ netService: NetService) {
            #if DEBUG
                print("ZeroConf: netService:didPublish:\(netService)")
            #endif

            let service = ZeroConf.jsonifyService(netService)

            let message: NSDictionary = NSDictionary(objects: ["registered", service], forKeys: ["action" as NSCopying, "service" as NSCopying])
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: message as! [AnyHashable: Any])
            commandDelegate?.send(pluginResult, callbackId: callbackId)
        }

        @objc func netService(_ netService: NetService, didNotPublish errorDict: [String : NSNumber]) {
            #if DEBUG
                print("ZeroConf: netService:didNotPublish:\(netService) \(errorDict)")
            #endif

            let pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR)
            commandDelegate?.send(pluginResult, callbackId: callbackId)
        }

        @objc func netServiceDidStop(_ netService: NetService) {
            nsp = nil
            commandDelegate = nil

            let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK)
            commandDelegate?.send(pluginResult, callbackId: callbackId)
        }

    }

    internal class Browser: NSObject, NetServiceDelegate, NetServiceBrowserDelegate {

        var nsb: NetServiceBrowser?
        var domain: String
        var type: String
        var callbackId: String
        var services: [String: NetService] = [:]
        var commandDelegate: CDVCommandDelegate?

        init (withDomain domain: String, withType type: String, withCallbackId callbackId: String) {
            self.domain = domain
            self.type = type
            self.callbackId = callbackId
        }

        func watch() {

             // Net service browser
            let browser = NetServiceBrowser()
            nsb = browser
            browser.delegate = self

            commandDelegate?.run(inBackground: {
                browser.searchForServices(ofType: self.type, inDomain: self.domain)
            })

            let pluginResult = CDVPluginResult(status: CDVCommandStatus_NO_RESULT)
            pluginResult?.setKeepCallbackAs(true)
            commandDelegate?.send(pluginResult, callbackId: callbackId)
        }

        func unwatch() {

            if let service = nsb {
                service.stop()
            }

        }

        func destroy() {

            if let service = nsb {
                service.stop()
            }

        }

        @objc func netServiceBrowser(_ browser: NetServiceBrowser, didNotSearch errorDict: [String : NSNumber]) {
            #if DEBUG
                print("ZeroConf: netServiceBrowser:didNotSearch:\(netService) \(errorDict)")
            #endif

            let pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR)
            commandDelegate?.send(pluginResult, callbackId: callbackId)
        }

        @objc func netServiceBrowser(_ netServiceBrowser: NetServiceBrowser,
                                     didFind netService: NetService,
                                     moreComing moreServicesComing: Bool) {
            #if DEBUG
                print("ZeroConf: netServiceBrowser:didFindService:\(netService)")
            #endif
            netService.delegate = self
            netService.resolve(withTimeout: 5000)
            services[netService.name] = netService // keep strong reference to catch didResolveAddress

            let service = ZeroConf.jsonifyService(netService)

            let message: NSDictionary = NSDictionary(objects: ["added", service], forKeys: ["action" as NSCopying, "service" as NSCopying])
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: message as! [AnyHashable: Any])
            pluginResult?.setKeepCallbackAs(true)
            commandDelegate?.send(pluginResult, callbackId: callbackId)
        }

        @objc func netServiceDidResolveAddress(_ netService: NetService) {
            #if DEBUG
                print("ZeroConf: netService:didResolveAddress:\(netService)")
            #endif

            let service = ZeroConf.jsonifyService(netService)

            let message: NSDictionary = NSDictionary(objects: ["resolved", service], forKeys: ["action" as NSCopying, "service" as NSCopying])
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: message as! [AnyHashable: Any])
            pluginResult?.setKeepCallbackAs(true)
            commandDelegate?.send(pluginResult, callbackId: callbackId)
        }

        @objc func netService(_ netService: NetService, didNotResolve errorDict: [String : NSNumber]) {
            #if DEBUG
                print("ZeroConf: netService:didNotResolve:\(netService) \(errorDict)")
            #endif

            let pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR)
            pluginResult?.setKeepCallbackAs(true)
            commandDelegate?.send(pluginResult, callbackId: callbackId)
        }

        @objc func netServiceBrowser(_ netServiceBrowser: NetServiceBrowser,
                                     didRemove netService: NetService,
                                     moreComing moreServicesComing: Bool) {
            #if DEBUG
                print("ZeroConf: netServiceBrowser:didRemoveService:\(netService)")
            #endif
            services.removeValue(forKey: netService.name)

            let service = ZeroConf.jsonifyService(netService)

            let message: NSDictionary = NSDictionary(objects: ["removed", service], forKeys: ["action" as NSCopying, "service" as NSCopying])
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: message as! [AnyHashable: Any])
            pluginResult?.setKeepCallbackAs(true)
            commandDelegate?.send(pluginResult, callbackId: callbackId)
        }

        @objc func netServiceDidStop(_ netService: NetService) {
            nsb = nil
            services.removeAll()
            commandDelegate = nil

            let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK)
            commandDelegate?.send(pluginResult, callbackId: callbackId)
        }

    }

    fileprivate static func jsonifyService(_ netService: NetService) -> NSDictionary {

        var ipv4Addresses: [String] = []
        var ipv6Addresses: [String] = []
        for address in netService.addresses! {
            if let family = extractFamily(address) {
                if  family == 4 {
                    if let addr = extractAddress(address) {
                        ipv4Addresses.append(addr)
                    }
                } else if family == 6 {
                    if let addr = extractAddress(address) {
                        ipv6Addresses.append(addr)
                    }
                }
            }
        }

        if ipv6Addresses.count > 1 {
            ipv6Addresses = Array(Set(ipv6Addresses))
        }

        var txtRecord: [String: String] = [:]
        if let txtRecordData = netService.txtRecordData() {
            let dict = NetService.dictionary(fromTXTRecord: txtRecordData)
            for (key, data) in dict {
                txtRecord[key] = String(data: data, encoding:String.Encoding.utf8)
            }
        }

        var hostName:String = ""
        if netService.hostName != nil {
            hostName = netService.hostName!
        }

        let service: NSDictionary = NSDictionary(
            objects: [netService.domain, netService.type, netService.name, netService.port, hostName, ipv4Addresses, ipv6Addresses, txtRecord],
            forKeys: ["domain" as NSCopying, "type" as NSCopying, "name" as NSCopying, "port" as NSCopying, "hostname" as NSCopying, "ipv4Addresses" as NSCopying, "ipv6Addresses" as NSCopying, "txtRecord" as NSCopying])

        return service
    }

    fileprivate static func extractFamily(_ addressBytes:Data) -> Int? {
        let addr = (addressBytes as NSData).bytes.load(as: sockaddr.self)
        if (addr.sa_family == sa_family_t(AF_INET)) {
            return 4
        }
        else if (addr.sa_family == sa_family_t(AF_INET6)) {
            return 6
        }
        else {
            return nil
        }
    }

    fileprivate static func extractAddress(_ addressBytes:Data) -> String? {
        var addr = (addressBytes as NSData).bytes.load(as: sockaddr.self)
        var hostname = [CChar](repeating: 0, count: Int(NI_MAXHOST))
        if (getnameinfo(&addr, socklen_t(addr.sa_len), &hostname,
                        socklen_t(hostname.count), nil, socklen_t(0), NI_NUMERICHOST) == 0) {
            return String(cString: hostname)
        }
        return nil
    }

}
