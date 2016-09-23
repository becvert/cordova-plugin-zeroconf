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

    public func register(_ command: CDVInvokedUrlCommand) {
        
        let fullType = command.argument(at: 0) as! String
        let fullTypeArr = fullType.components(separatedBy: ".")
        let domain = fullTypeArr[2]
        let type = fullTypeArr[0] + "." + fullTypeArr[1]
        let name = command.argument(at: 1) as! String
        let port = command.argument(at: 2) as! Int
        
        #if DEBUG
            print("ZeroConf: register \(fullType + "@@@" + name)")
        #endif
        
        var txtRecord: [String: Data] = [:]
        if let dict = command.arguments[3] as? [String: String] {
            for (key, value) in dict {
                txtRecord[key] = value.data(using: String.Encoding.utf8)
            }
        }
        
        let publisher = Publisher(withDomain: domain, withType: type, withName: name, withPort: port, withTxtRecord: txtRecord)
        publisher.commandDelegate = commandDelegate
        publisher.register()
        publishers[fullType + "@@@" + name] = publisher
        
    }
    
    public func unregister(_ command: CDVInvokedUrlCommand) {
        
        let fullType = command.argument(at: 0) as! String
        let name = command.argument(at: 1) as! String
        
        #if DEBUG
            print("ZeroConf: unregister \(fullType + "@@@" + name)")
        #endif
        
        if let publisher = publishers[fullType + "@@@" + name] {
            publisher.unregister();
            publishers.removeValue(forKey: fullType + "@@@" + name)
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
        
        let fullType = command.argument(at: 0) as! String
        let fullTypeArr = fullType.characters.split{$0 == "."}.map(String.init)
        let domain = fullTypeArr[2]
        let type = fullTypeArr[0] + "." + fullTypeArr[1]
        
        #if DEBUG
            print("ZeroConf: watch \(fullType)")
        #endif
        
        let browser = Browser(withDomain: domain, withType: type, withCallbackId: command.callbackId)
        browser.commandDelegate = commandDelegate
        browser.watch()
        browsers[fullType] = browser
        
    }
    
    public func unwatch(_ command: CDVInvokedUrlCommand) {
        
        let fullType = command.argument(at: 0) as! String
        
        #if DEBUG
            print("ZeroConf: unwatch \(fullType)")
        #endif
        
        if let browser = browsers[fullType] {
            browser.unwatch();
            browsers.removeValue(forKey: fullType)
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
    
    fileprivate class Publisher {
        
        var nsns: NetService?
        var domain: String
        var type: String
        var name: String
        var port: Int
        var txtRecord: [String: Data] = [:]
        var commandDelegate: CDVCommandDelegate?
        
        init (withDomain domain: String, withType type: String, withName name: String, withPort port: Int, withTxtRecord txtRecord: [String: Data]) {
            self.domain = domain
            self.type = type
            self.name = name
            self.port = port
            self.txtRecord = txtRecord
        }
        
        func register() {
            
            // Netservice
            let service = NetService(domain: domain, type: type , name: name, port: Int32(port))
            nsns = service
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

    }
    
    fileprivate class Browser: NSObject, NetServiceDelegate, NetServiceBrowserDelegate {
        
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
        
        @objc func netServiceBrowser(_ netServiceBrowser: NetServiceBrowser,
            didFind netService: NetService,
            moreComing moreServicesComing: Bool) {
                #if DEBUG
                    print("netServiceDidFindService:\(netService)")
                #endif
                netService.delegate = self
                netService.resolve(withTimeout: 0)
                services[netService.name] = netService // keep strong reference to catch didResolveAddress
        }
        
        @objc func netServiceBrowser(_ netServiceBrowser: NetServiceBrowser,
            didRemove netService: NetService,
            moreComing moreServicesComing: Bool) {
                #if DEBUG
                    print("netServiceDidRemoveService:\(netService)")
                #endif
                services.removeValue(forKey: netService.name)
                
                let service = jsonifyService(netService)
                
                let message: NSDictionary = NSDictionary(objects: ["removed", service], forKeys: ["action" as NSCopying, "service" as NSCopying])
                let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: message as! [AnyHashable: Any])
                pluginResult?.setKeepCallbackAs(true)
                commandDelegate?.send(pluginResult, callbackId: callbackId)
        }
        
        @objc func netServiceDidResolveAddress(_ netService: NetService) {
            #if DEBUG
                print("netServiceDidResolveAddress:\(netService)")
            #endif
            
            let service = jsonifyService(netService)
            
            let message: NSDictionary = NSDictionary(objects: ["added", service], forKeys: ["action" as NSCopying, "service" as NSCopying])
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: message as! [AnyHashable: Any])
            pluginResult?.setKeepCallbackAs(true)
            commandDelegate?.send(pluginResult, callbackId: callbackId)
        }

        fileprivate func jsonifyService(_ netService: NetService) -> NSDictionary {
            
            let addresses: [String] = IP(netService.addresses)
            
            var txtRecord: [String: String] = [:]
            let dict = NetService.dictionary(fromTXTRecord: netService.txtRecordData()!)
            for (key, data) in dict {
                txtRecord[key] = String(data: data, encoding:String.Encoding.utf8)
            }
            
            let service: NSDictionary = NSDictionary(
                objects: [netService.domain, netService.type, netService.port, netService.name, addresses, txtRecord],
                forKeys: ["domain" as NSCopying, "type" as NSCopying, "port" as NSCopying, "name" as NSCopying, "addresses" as NSCopying, "txtRecord" as NSCopying])
            
            return service
        }
        
        // http://dev.eltima.com/post/99996366184/using-bonjour-in-swift
        fileprivate func IP(_ addresses: [Data]?) -> [String] {
            var ips: [String] = []
            if addresses != nil {
                for addressBytes in addresses! {
                    var inetAddress : sockaddr_in!
                    var inetAddress6 : sockaddr_in6!
                    //NSData’s bytes returns a read-only pointer to the receiver’s contents.
                    let inetAddressPointer = (addressBytes as NSData).bytes.bindMemory(to: sockaddr_in.self, capacity: addressBytes.count)
                    //Access the underlying raw memory
                    inetAddress = inetAddressPointer.pointee
                    if inetAddress.sin_family == __uint8_t(AF_INET) {
                    }
                    else {
                        if inetAddress.sin_family == __uint8_t(AF_INET6) {
                            let inetAddressPointer6 = (addressBytes as NSData).bytes.bindMemory(to: sockaddr_in6.self, capacity: addressBytes.count)
                            inetAddress6 = inetAddressPointer6.pointee
                            inetAddress = nil
                        }
                        else {
                            inetAddress = nil
                        }
                    }
                    var ipString : UnsafePointer<CChar>?
                    //static func alloc(num: Int) -> UnsafeMutablePointer
                    let ipStringBuffer = UnsafeMutablePointer<CChar>.allocate(capacity: Int(INET6_ADDRSTRLEN))
                    if inetAddress != nil {
                        var addr = inetAddress.sin_addr
                        ipString = inet_ntop(Int32(inetAddress.sin_family),
                            &addr,
                            ipStringBuffer,
                            __uint32_t (INET6_ADDRSTRLEN))
                    } else {
                        if inetAddress6 != nil {
                            var addr = inetAddress6.sin6_addr
                            ipString = inet_ntop(Int32(inetAddress6.sin6_family),
                                &addr,
                                ipStringBuffer,
                                __uint32_t(INET6_ADDRSTRLEN))
                        }
                    }
                    if ipString != nil {
                        let ip = String(cString: ipString!)
                        ips.append(ip)
                    }
                }
            }
            return ips
        }
        
    }
    
}
