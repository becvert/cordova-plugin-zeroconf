/*
 * Cordova ZeroConf Plugin
 *
 * ZeroConf plugin for Cordova/Phonegap 
 * by Sylvain Brejeon
 */
 
import Foundation

@objc(ZeroConf) class ZeroConf : CDVPlugin  {
    
    private var publishers: [String: Publisher] = [:]
    private var browsers: [String: Browser] = [:]
    
    override func pluginInitialize() {
        publishers  = [:]
        browsers = [:]
    }
    
    override func onAppTerminate() {
        for (_, publisher) in publishers {
            publisher.destroy()
        }
        publishers.removeAll()
        
        for (_, browser) in browsers {
            browser.destroy();
        }
        browsers.removeAll()
    }

    func register(command: CDVInvokedUrlCommand) {
        
        let fullType = command.argumentAtIndex(0) as! String
        let fullTypeArr = fullType.componentsSeparatedByString(".")
        let domain = fullTypeArr[2]
        let type = fullTypeArr[0] + "." + fullTypeArr[1]
        let name = command.argumentAtIndex(1) as! String
        let port = command.argumentAtIndex(2) as! Int
        
        #if DEBUG
            print("ZeroConf: register \(fullType + "@@@" + name)")
        #endif
        
        var txtRecord: [String: NSData] = [:]
        if let dict = command.arguments[3] as? [String: String] {
            for (key, value) in dict {
                txtRecord[key] = value.dataUsingEncoding(NSUTF8StringEncoding)
            }
        }
        
        let publisher = Publisher(withDomain: domain, withType: type, withName: name, withPort: port, withTxtRecord: txtRecord)
        publisher.commandDelegate = commandDelegate
        publisher.register()
        publishers[fullType + "@@@" + name] = publisher
        
    }
    
    func unregister(command: CDVInvokedUrlCommand) {
        
        let fullType = command.argumentAtIndex(0) as! String
        let name = command.argumentAtIndex(1) as! String
        
        #if DEBUG
            print("ZeroConf: unregister \(fullType + "@@@" + name)")
        #endif
        
        if let publisher = publishers[fullType + "@@@" + name] {
            publisher.unregister();
            publishers.removeValueForKey(fullType + "@@@" + name)
        }
        
    }
    
    func stop(command: CDVInvokedUrlCommand) {
        #if DEBUG
            print("ZeroConf: stop")
        #endif
        
        for (_, publisher) in publishers {
            publisher.unregister()
        }
        publishers.removeAll()
    }
    
    func watch(command: CDVInvokedUrlCommand) {
        
        let fullType = command.argumentAtIndex(0) as! String
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
    
    func unwatch(command: CDVInvokedUrlCommand) {
        
        let fullType = command.argumentAtIndex(0) as! String
        
        #if DEBUG
            print("ZeroConf: unwatch \(fullType)")
        #endif
        
        if let browser = browsers[fullType] {
            browser.unwatch();
            browsers.removeValueForKey(fullType)
        }
        
    }
    
    func close(command: CDVInvokedUrlCommand) {
        #if DEBUG
            print("ZeroConf: close")
        #endif
        
        for (_, browser) in browsers {
            browser.unwatch()
        }
        browsers.removeAll()
    }
    
    private class Publisher {
        
        var nsns: NSNetService?
        var domain: String
        var type: String
        var name: String
        var port: Int
        var txtRecord: [String: NSData] = [:]
        var commandDelegate: CDVCommandDelegate?
        
        init (withDomain domain: String, withType type: String, withName name: String, withPort port: Int, withTxtRecord txtRecord: [String: NSData]) {
            self.domain = domain
            self.type = type
            self.name = name
            self.port = port
            self.txtRecord = txtRecord
        }
        
        func register() {
            
            // Netservice
            let service = NSNetService(domain: domain, type: type , name: name, port: Int32(port))
            nsns = service
            service.setTXTRecordData(NSNetService.dataFromTXTRecordDictionary(txtRecord))
            
            commandDelegate?.runInBackground({
                service.publish()
            })
            
        }
        
        func unregister() {
            
            if let service = nsns {
                
                commandDelegate?.runInBackground({
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
    
    private class Browser: NSObject, NSNetServiceDelegate, NSNetServiceBrowserDelegate {
        
        var nsb: NSNetServiceBrowser?
        var domain: String
        var type: String
        var callbackId: String
        var services: [String: NSNetService] = [:]
        var commandDelegate: CDVCommandDelegate?
        
        init (withDomain domain: String, withType type: String, withCallbackId callbackId: String) {
            self.domain = domain
            self.type = type
            self.callbackId = callbackId
        }
        
        func watch() {
            
             // Net service browser
            let browser = NSNetServiceBrowser()
            nsb = browser
            browser.delegate = self
            
            commandDelegate?.runInBackground({
                browser.searchForServicesOfType(self.type, inDomain: self.domain)
            })
            
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_NO_RESULT)
            pluginResult.setKeepCallbackAsBool(true)
            
        }
        
        func unwatch() {
            
            if let service = nsb {
                
                commandDelegate?.runInBackground({
                    service.stop()
                })
                
                nsb = nil
                services.removeAll()
                commandDelegate = nil
            }
            
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_NO_RESULT)
            pluginResult.setKeepCallbackAsBool(false)
            
        }
        
        func destroy() {
            
            if let service = nsb {
                service.stop()
                nsb = nil
                services.removeAll()
                commandDelegate = nil
            }
            
        }
        
        @objc func netServiceBrowser(netServiceBrowser: NSNetServiceBrowser,
            didFindService netService: NSNetService,
            moreComing moreServicesComing: Bool) {
                #if DEBUG
                    print("netServiceDidFindService:\(netService)")
                #endif
                netService.delegate = self
                netService.resolveWithTimeout(0)
                services[netService.name] = netService // keep strong reference to catch didResolveAddress
        }
        
        @objc func netServiceBrowser(netServiceBrowser: NSNetServiceBrowser,
            didRemoveService netService: NSNetService,
            moreComing moreServicesComing: Bool) {
                #if DEBUG
                    print("netServiceDidRemoveService:\(netService)")
                #endif
                services.removeValueForKey(netService.name)
                
                let service = jsonifyService(netService)
                
                let message: NSDictionary = NSDictionary(objects: ["removed", service], forKeys: ["action", "service"])
                let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAsDictionary: message as [NSObject : AnyObject])
                pluginResult.setKeepCallbackAsBool(true)
                commandDelegate?.sendPluginResult(pluginResult, callbackId: callbackId)
        }
        
        @objc func netServiceDidResolveAddress(netService: NSNetService) {
            #if DEBUG
                print("netServiceDidResolveAddress:\(netService)")
            #endif
            
            let service = jsonifyService(netService)
            
            let message: NSDictionary = NSDictionary(objects: ["added", service], forKeys: ["action", "service"])
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAsDictionary: message as [NSObject : AnyObject])
            pluginResult.setKeepCallbackAsBool(true)
            commandDelegate?.sendPluginResult(pluginResult, callbackId: callbackId)
        }

        private func jsonifyService(netService: NSNetService) -> NSDictionary {
            
            let addresses: [String] = IP(netService.addresses)
            
            var txtRecord: [String: String] = [:]
            let dict = NSNetService.dictionaryFromTXTRecordData(netService.TXTRecordData()!)
            for (key, data) in dict {
                txtRecord[key] = String(data: data, encoding:NSUTF8StringEncoding)
            }
            
            let service: NSDictionary = NSDictionary(
                objects: [netService.domain, netService.type, netService.port, netService.name, addresses, txtRecord],
                forKeys: ["domain", "type", "port", "name", "addresses", "txtRecord"])
            
            return service
        }
        
        // http://dev.eltima.com/post/99996366184/using-bonjour-in-swift
        private func IP(addresses: [NSData]?) -> [String] {
            var ips: [String] = []
            if addresses != nil {
                for addressBytes in addresses! {
                    var inetAddress : sockaddr_in!
                    var inetAddress6 : sockaddr_in6!
                    //NSData’s bytes returns a read-only pointer to the receiver’s contents.
                    let inetAddressPointer = UnsafePointer<sockaddr_in>(addressBytes.bytes)
                    //Access the underlying raw memory
                    inetAddress = inetAddressPointer.memory
                    if inetAddress.sin_family == __uint8_t(AF_INET) {
                    }
                    else {
                        if inetAddress.sin_family == __uint8_t(AF_INET6) {
                            let inetAddressPointer6 = UnsafePointer<sockaddr_in6>(addressBytes.bytes)
                            inetAddress6 = inetAddressPointer6.memory
                            inetAddress = nil
                        }
                        else {
                            inetAddress = nil
                        }
                    }
                    var ipString : UnsafePointer<CChar>?
                    //static func alloc(num: Int) -> UnsafeMutablePointer
                    let ipStringBuffer = UnsafeMutablePointer<CChar>.alloc(Int(INET6_ADDRSTRLEN))
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
                        let ip = String.fromCString(ipString!)
                        if ip != nil {
                            ips.append(ip!)
                        }
                    }
                }
            }
            return ips
        }
        
    }
    
}