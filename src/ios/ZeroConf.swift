/*
 * Cordova ZeroConf Plugin
 *
 * ZeroConf plugin for Cordova/Phonegap 
 * by Sylvain Brejeon
 */
 
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
        
        let hostname = Hostname.get()
        
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
            print("ZeroConf: register \(type + domain + "@@@" + name)")
        #endif
        
        var txtRecord: [String: Data] = [:]
        if let dict = command.arguments[4] as? [String: String] {
            for (key, value) in dict {
                txtRecord[key] = value.data(using: String.Encoding.utf8)
            }
        }
        
        let publisher = Publisher(withDomain: domain, withType: type, withName: name, withPort: port, withTxtRecord: txtRecord, withCallbackId: command.callbackId)
        publisher.commandDelegate = commandDelegate
        publisher.register()
        publishers[type + domain + "@@@" + name] = publisher
        
    }
    
    public func unregister(_ command: CDVInvokedUrlCommand) {
        
        let type = command.argument(at: 0) as! String
        let domain = command.argument(at: 1) as! String
        let name = command.argument(at: 2) as! String
        
        #if DEBUG
            print("ZeroConf: unregister \(type + domain + "@@@" + name)")
        #endif
        
        if let publisher = publishers[type + domain + "@@@" + name] {
            publisher.unregister();
            publishers.removeValue(forKey: type + domain + "@@@" + name)
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
    }
    
    internal class Publisher: NSObject, NetServiceDelegate {
        
        var nsns: NetService?
        var domain: String
        var type: String
        var name: String
        var port: Int
        var txtRecord: [String: Data] = [:]
        var callbackId: String
        var commandDelegate: CDVCommandDelegate?
        
        init (withDomain domain: String, withType type: String, withName name: String, withPort port: Int, withTxtRecord txtRecord: [String: Data], withCallbackId callbackId: String) {
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
            nsns = service
            service.delegate = self
            service.setTXTRecord(NetService.data(fromTXTRecord: txtRecord))
            
            commandDelegate?.run(inBackground: {
                service.publish()
            })
            
        }
        
        func unregister() {
            
            if let service = nsns {
                
                commandDelegate?.run(inBackground: {
                    service.stop()
                })
                
                nsns = nil
                commandDelegate = nil
            }
            
        }
        
        func destroy() {
            
            if let service = nsns {
                service.stop()
                nsns = nil
                commandDelegate = nil
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
            
        }
        
        func unwatch() {
            
            if let service = nsb {
                
                commandDelegate?.run(inBackground: {
                    service.stop()
                })
                
                nsb = nil
                services.removeAll()
                commandDelegate = nil
            }
            
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_NO_RESULT)
            pluginResult?.setKeepCallbackAs(false)
            
        }
        
        func destroy() {
            
            if let service = nsb {
                service.stop()
                nsb = nil
                services.removeAll()
                commandDelegate = nil
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
                netService.resolve(withTimeout: 0)
                services[netService.name] = netService // keep strong reference to catch didResolveAddress
        }
        
        @objc func netServiceDidResolveAddress(_ netService: NetService) {
            #if DEBUG
                print("ZeroConf: netService:didResolveAddress:\(netService)")
            #endif
            
            let service = ZeroConf.jsonifyService(netService)
            
            let message: NSDictionary = NSDictionary(objects: ["added", service], forKeys: ["action" as NSCopying, "service" as NSCopying])
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
        
    }
    
    fileprivate static func jsonifyService(_ netService: NetService) -> NSDictionary {
        
        var ipv4Addresses: [String] = []
        var ipv6Addresses: [String] = []
        for address in netService.addresses! {
            let family = extractFamily(address)
            let addr = extractAddress(address)!
            if  family == .ipv4 {
                ipv4Addresses.append(addr)
            } else if family == .ipv6 {
                ipv6Addresses.append(addr)
            }
        }
        
        if ipv6Addresses.count > 1 {
            ipv6Addresses = Array(Set(ipv6Addresses))
        }
        
        var txtRecord: [String: String] = [:]
        let dict = NetService.dictionary(fromTXTRecord: netService.txtRecordData()!)
        for (key, data) in dict {
            txtRecord[key] = String(data: data, encoding:String.Encoding.utf8)
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
    
    public static func extractFamily(_ addressBytes:Data) -> Interface.Family {
        let inetAddressPointer = (addressBytes as NSData).bytes.bindMemory(to: sockaddr.self, capacity: addressBytes.count)
        if (inetAddressPointer.pointee.sa_family == sa_family_t(AF_INET)) {
            return .ipv4
        }
        else if (inetAddressPointer.pointee.sa_family == sa_family_t(AF_INET6)) {
            return .ipv6
        }
        else {
            return .other
        }
    }
    
    public static func extractAddress(_ addressBytes:Data) -> String? {
        let inetAddressPointer = (addressBytes as NSData).bytes.bindMemory(to: sockaddr.self, capacity: addressBytes.count)
        return Interface.extractAddress(inetAddressPointer.pointee)
    }
    
}
