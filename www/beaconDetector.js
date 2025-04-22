var exec = require('cordova/exec');

var BeaconDetector = {
    /**
     * Initialize the beacon detector with beacon data
     * @param {Array} beaconData - Array of beacon objects with uuid, major, minor, and url
     * @param {Function} successCallback - Success callback
     * @param {Function} errorCallback - Error callback
     */
    initialize: function(beaconData, successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'BeaconDetector', 'initialize', [beaconData]);
    },
    
    /**
     * Start scanning for beacons
     * @param {Function} successCallback - Success callback
     * @param {Function} errorCallback - Error callback
     */
    startScanning: function(successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'BeaconDetector', 'startScanning', []);
    },
    
    /**
     * Stop scanning for beacons
     * @param {Function} successCallback - Success callback
     * @param {Function} errorCallback - Error callback
     */
    stopScanning: function(successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'BeaconDetector', 'stopScanning', []);
    },
    
    /**
     * Set callback for beacon detection events
     * @param {Function} callback - Callback function that receives beacon data
     */
    onBeaconDetected: function(callback) {
        exec(callback, function(error) {
            console.error('Error in beacon detection callback: ' + error);
        }, 'BeaconDetector', 'onBeaconDetected', []);
    },
    
    /**
     * Check if the plugin is available
     * @param {Function} successCallback - Success callback with boolean result
     * @param {Function} errorCallback - Error callback
     */
    isPluginAvailable: function(successCallback, errorCallback) {
        if (typeof cordova !== 'undefined' && typeof window.beaconDetector !== 'undefined') {
            successCallback(true);
        } else {
            // Try to check via exec
            exec(
                function() { successCallback(true); },
                function() { successCallback(false); },
                'BeaconDetector', 
                'isAvailable', 
                []
            );
        }
    },
    
    /**
     * Check if the device supports beacon detection
     * @param {Function} successCallback - Success callback with compatibility info
     * @param {Function} errorCallback - Error callback
     */
    checkCompatibility: function(successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'BeaconDetector', 'checkCompatibility', []);
    },
    
    /**
     * Get a list of detected beacons without starting continuous scanning
     * @param {Function} successCallback - Success callback with list of beacons
     * @param {Function} errorCallback - Error callback
     */
    listDetectedBeacons: function(successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'BeaconDetector', 'listDetectedBeacons', []);
    },
    
    // Add this before the module.exports line
    /**
     * Debug the beacon scanner status
     * @param {Function} successCallback - Success callback with debug info
     * @param {Function} errorCallback - Error callback
     */
    debugBeaconScanner: function(successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'BeaconDetector', 'debugBeaconScanner', []);
    }
};

module.exports = BeaconDetector;