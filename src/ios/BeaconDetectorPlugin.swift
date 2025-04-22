import CoreLocation
import CoreBluetooth

@objc(BeaconDetectorPlugin)
class BeaconDetectorPlugin: CDVPlugin, CLLocationManagerDelegate {
    private var locationManager: CLLocationManager!
    private var beaconData: [[String: Any]] = []
    private var beaconDetectionCallbackId: String?
    private var isScanning = false
    private var monitoredRegions: [CLBeaconRegion] = []
    
    override func pluginInitialize() {
        super.pluginInitialize()
        
        locationManager = CLLocationManager()
        locationManager.delegate = self
        locationManager.pausesLocationUpdatesAutomatically = false
        
        if #available(iOS 9.0, *) {
            locationManager.allowsBackgroundLocationUpdates = true
        }
        
        print("BeaconDetectorPlugin initialized")
    }
    
    @objc(initialize:)
    func initialize(command: CDVInvokedUrlCommand) {
        beaconData.removeAll()
        
        if let beaconDataArray = command.arguments[0] as? [[String: Any]] {
            beaconData = beaconDataArray
            print("Initialized with \(beaconData.count) beacons")
            
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: "Beacon data initialized")
            self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
        } else {
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "Invalid beacon data format")
            self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
        }
    }
    
    @objc(startScanning:)
    func startScanning(command: CDVInvokedUrlCommand) {
        if isScanning {
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: "Already scanning")
            self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
            return
        }
        
        self.commandDelegate.run {
            // Request authorization
            self.requestLocationAuthorization()
            
            // Create regions for each unique UUID in the beacon data
            var uniqueUUIDs: Set<String> = []
            
            for beacon in self.beaconData {
                if let uuidString = beacon["uuid"] as? String,
                   let uuid = UUID(uuidString: uuidString) {
                    uniqueUUIDs.insert(uuidString)
                    
                    // Create a region for this UUID
                    let region = CLBeaconRegion(uuid: uuid, identifier: "Region-\(uuidString)")
                    region.notifyEntryStateOnDisplay = true
                    region.notifyOnEntry = true
                    region.notifyOnExit = true
                    
                    // Start monitoring and ranging
                    self.locationManager.startMonitoring(for: region)
                    self.locationManager.startRangingBeacons(in: region)
                    
                    self.monitoredRegions.append(region)
                    print("Started monitoring region for UUID: \(uuidString)")
                }
            }
            
            self.isScanning = true
            print("Started scanning for \(uniqueUUIDs.count) unique beacon UUIDs")
            
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: "Started scanning for beacons")
            self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
        }
    }
    
    @objc(stopScanning:)
    func stopScanning(command: CDVInvokedUrlCommand) {
        if !isScanning {
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: "Not scanning")
            self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
            return
        }
        
        self.commandDelegate.run {
            // Stop monitoring and ranging for all regions
            for region in self.monitoredRegions {
                self.locationManager.stopMonitoring(for: region)
                self.locationManager.stopRangingBeacons(in: region)
            }
            
            self.monitoredRegions.removeAll()
            self.isScanning = false
            
            print("Stopped scanning for beacons")
            
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: "Stopped scanning for beacons")
            self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
        }
    }
    
    @objc(onBeaconDetected:)
    func onBeaconDetected(command: CDVInvokedUrlCommand) {
        self.beaconDetectionCallbackId = command.callbackId
        
        let pluginResult = CDVPluginResult(status: CDVCommandStatus_NO_RESULT)
        pluginResult?.setKeepCallbackAs(true)
        self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
    }
    
    // MARK: - CLLocationManagerDelegate
    
    // Improve the beacon detection in locationManager:didRangeBeacons:inRegion:
    func locationManager(_ manager: CLLocationManager, didRangeBeacons beacons: [CLBeacon], in region: CLBeaconRegion) {
        print("Ranged \(beacons.count) beacons in region: \(region.identifier)")
        
        guard let callbackId = beaconDetectionCallbackId else {
            print("No callback ID for beacon detection")
            return
        }
        
        var beaconArray: [[String: Any]] = []
        
        for beacon in beacons {
            let uuid = beacon.uuid.uuidString
            let major = beacon.major.intValue
            let minor = beacon.minor.intValue
            
            print("Raw beacon detected: UUID=\(uuid), Major=\(major), Minor=\(minor), Proximity=\(beacon.proximity.rawValue)")
            
            var beaconInfo: [String: Any] = [
                "uuid": uuid,
                "major": major,
                "minor": minor,
                "rssi": beacon.rssi,
                "proximity": beacon.proximity.rawValue
            ]
            
            // Calculate approximate distance
            if #available(iOS 13.0, *) {
                beaconInfo["distance"] = beacon.proximity.rawValue
            } else {
                // Estimate distance for older iOS versions
                let txPower = -59 // Default value, adjust if needed
                beaconInfo["distance"] = calculateDistance(rssi: beacon.rssi, txPower: txPower)
            }
            
            // Find matching beacon in our data
            for data in beaconData {
                if let dataUUID = data["uuid"] as? String,
                   let dataMajor = data["major"] as? Int,
                   let dataMinor = data["minor"] as? Int,
                   dataUUID.lowercased() == uuid.lowercased(),
                   dataMajor == major,
                   dataMinor == minor {
                    
                    if let title = data["title"] as? String {
                        beaconInfo["title"] = title
                    }
                    
                    if let url = data["url"] as? String {
                        beaconInfo["url"] = url
                    }
                    
                    break
                }
            }
            
            beaconArray.append(beaconInfo)
        }
        
        // Solo enviar los datos sin redirección
        if !beaconArray.isEmpty {
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: beaconArray)
            pluginResult?.setKeepCallbackAs(true)
            self.commandDelegate.send(pluginResult, callbackId: callbackId)
        }
    }
    
    func locationManager(_ manager: CLLocationManager, didEnterRegion region: CLRegion) {
        print("Entered region: \(region.identifier)")
    }
    
    func locationManager(_ manager: CLLocationManager, didExitRegion region: CLRegion) {
        print("Exited region: \(region.identifier)")
    }
    
    func locationManager(_ manager: CLLocationManager, didDetermineState state: CLRegionState, for region: CLRegion) {
        print("Region state changed: \(state.rawValue) for region \(region.identifier)")
    }
    
    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        print("Location authorization status changed to: \(status.rawValue)")
    }
    
    // MARK: - Helper Methods
    
    private func requestLocationAuthorization() {
        let status = CLLocationManager.authorizationStatus()
        
        if status == .notDetermined {
            locationManager.requestAlwaysAuthorization()
        } else if status == .denied || status == .restricted {
            print("Location services are disabled for this app")
        }
    }
    
    private func calculateDistance(rssi: Int, txPower: Int) -> Double {
        if rssi == 0 {
            return -1.0 // If we cannot determine accuracy, return -1
        }
        
        let ratio = Double(rssi) / Double(txPower)
        if ratio < 1.0 {
            return pow(ratio, 10)
        } else {
            return 0.89976 * pow(ratio, 7.7095) + 0.111
        }
    }
}