package com.example;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BeaconDetectorPlugin extends CordovaPlugin implements RangeNotifier, MonitorNotifier {
    private static final String TAG = "BeaconDetectorPlugin";
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private static final int PERMISSION_REQUEST_FINE_LOCATION = 2;
    private static final int PERMISSION_REQUEST_BLUETOOTH_SCAN = 3;
    private static final int PERMISSION_REQUEST_BLUETOOTH_CONNECT = 4;
    
    // Add rate limiting variables
    private static final long MIN_SCAN_INTERVAL_MS = 5000; // 5 seconds minimum between scan operations
    private long lastScanOperationTime = 0;

    private BeaconManager beaconManager;
    private List<Map<String, Object>> beaconData;
    private CallbackContext beaconDetectionCallback;
    private Region region;
    private boolean isScanning = false;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        
        Activity activity = cordova.getActivity();
        beaconManager = BeaconManager.getInstanceForApplication(activity.getApplicationContext());
        
        // Configure scan periods to reduce frequency
        beaconManager.setForegroundScanPeriod(1100); // 1.1 seconds
        beaconManager.setForegroundBetweenScanPeriod(2200); // 2.2 seconds between scans
        beaconManager.setBackgroundScanPeriod(5000); // 5 seconds
        beaconManager.setBackgroundBetweenScanPeriod(60000); // 60 seconds between scans in background
        
        // Add support for iBeacon format
        beaconManager.getBeaconParsers().add(new BeaconParser()
                .setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));
        
        beaconManager.addRangeNotifier(this);
        beaconManager.addMonitorNotifier(this);
        
        beaconData = new ArrayList<>();
        
        Log.d(TAG, "BeaconDetectorPlugin initialized with optimized scan periods");
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if ("initialize".equals(action)) {
            initialize(args.getJSONArray(0), callbackContext);
            return true;
        } else if ("startScanning".equals(action)) {
            startScanning(callbackContext);
            return true;
        } else if ("stopScanning".equals(action)) {
            stopScanning(callbackContext);
            return true;
        } else if ("onBeaconDetected".equals(action)) {
            this.beaconDetectionCallback = callbackContext;
            return true;
        } else if ("isAvailable".equals(action)) {
            callbackContext.success("Plugin is available");
            return true;
        } else if ("checkCompatibility".equals(action)) {
            checkCompatibility(callbackContext);
            return true;
        } else if ("listDetectedBeacons".equals(action)) {
            listDetectedBeacons(callbackContext);
            return true;
        } else if ("debugBeaconScanner".equals(action)) {
            debugBeaconScanner(callbackContext);
            return true;
        }

        return false;
    }

    private void initialize(JSONArray beaconDataArray, CallbackContext callbackContext) {
        try {
            beaconData.clear();
            
            for (int i = 0; i < beaconDataArray.length(); i++) {
                JSONObject beaconObj = beaconDataArray.getJSONObject(i);
                Map<String, Object> beacon = new HashMap<>();
                
                beacon.put("title", beaconObj.getString("title"));
                beacon.put("uuid", beaconObj.getString("uuid"));
                beacon.put("major", beaconObj.getInt("major"));
                beacon.put("minor", beaconObj.getInt("minor"));
                beacon.put("url", beaconObj.getString("url"));
                
                beaconData.add(beacon);
            }
            
            Log.d(TAG, "Initialized with " + beaconData.size() + " beacons");
            callbackContext.success("Beacon data initialized");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing beacon data", e);
            callbackContext.error("Error initializing beacon data: " + e.getMessage());
        }
    }

    private void startScanning(CallbackContext callbackContext) {
        if (isScanning) {
            callbackContext.success("Already scanning");
            return;
        }
        
        // Add rate limiting check
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastScanOperationTime < MIN_SCAN_INTERVAL_MS) {
            Log.w(TAG, "Scan operation requested too soon after previous operation. Enforcing rate limit.");
            callbackContext.error("Please wait before starting another scan operation");
            return;
        }
        
        cordova.getThreadPool().execute(() -> {
            try {
                // Check and request permissions
                if (!checkAndRequestPermissions()) {
                    callbackContext.error("Required permissions not granted");
                    return;
                }
                
                // Use null identifiers to detect all beacons
                region = new Region("AllBeaconsRegion", null, null, null);
                Log.d(TAG, "Created region to scan for all beacons");
                
                // Start ranging beacons
                beaconManager.startRangingBeacons(region);
                beaconManager.startMonitoringBeaconsInRegion(region);
                
                isScanning = true;
                lastScanOperationTime = System.currentTimeMillis(); // Update timestamp
                Log.d(TAG, "Started scanning for beacons");
                callbackContext.success("Started scanning for beacons");
            } catch (Exception e) {
                Log.e(TAG, "Error starting beacon scanning", e);
                callbackContext.error("Error starting beacon scanning: " + e.getMessage());
            }
        });
    }

    private void stopScanning(CallbackContext callbackContext) {
        if (!isScanning) {
            callbackContext.success("Not scanning");
            return;
        }
        
        // Add rate limiting check
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastScanOperationTime < MIN_SCAN_INTERVAL_MS) {
            Log.w(TAG, "Scan operation requested too soon after previous operation. Enforcing rate limit.");
            callbackContext.error("Please wait before stopping scan operation");
            return;
        }
        
        cordova.getThreadPool().execute(() -> {
            try {
                if (region != null) {
                    beaconManager.stopRangingBeacons(region);
                    beaconManager.stopMonitoringBeaconsInRegion(region);
                }
                
                isScanning = false;
                lastScanOperationTime = System.currentTimeMillis(); // Update timestamp
                Log.d(TAG, "Stopped scanning for beacons");
                callbackContext.success("Stopped scanning for beacons");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping beacon scanning", e);
                callbackContext.error("Error stopping beacon scanning: " + e.getMessage());
            }
        });
    }

    // Also modify the listDetectedBeacons method to include rate limiting
    private void listDetectedBeacons(CallbackContext callbackContext) {
        if (beaconData.isEmpty()) {
            callbackContext.error("No beacon data initialized. Call initialize() first.");
            return;
        }
        
        // Add rate limiting check
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastScanOperationTime < MIN_SCAN_INTERVAL_MS) {
            Log.w(TAG, "Scan operation requested too soon after previous operation. Enforcing rate limit.");
            callbackContext.error("Please wait before requesting another beacon scan");
            return;
        }
        
        cordova.getThreadPool().execute(() -> {
            try {
                // Check permissions
                if (!checkAndRequestPermissions()) {
                    callbackContext.error("Required permissions not granted");
                    return;
                }
                
                // Create a temporary region for a single scan
                Region tempRegion = new Region("TempScanRegion", null, null, null);
                
                // Update timestamp
                lastScanOperationTime = System.currentTimeMillis();
                
                // Create a temporary callback for this scan
                RangeNotifier tempRangeNotifier = new RangeNotifier() {
                    @Override
                    public void didRangeBeaconsInRegion(Collection<Beacon> detectedBeacons, Region region) {
                        try {
                            JSONArray beaconArray = new JSONArray();
                            
                            for (Beacon beacon : detectedBeacons) {
                                String uuid = beacon.getId1().toString();
                                int major = beacon.getId2().toInt();
                                int minor = beacon.getId3().toInt();
                                
                                JSONObject beaconObj = new JSONObject();
                                beaconObj.put("uuid", uuid);
                                beaconObj.put("major", major);
                                beaconObj.put("minor", minor);
                                beaconObj.put("distance", beacon.getDistance());
                                beaconObj.put("rssi", beacon.getRssi());
                                
                                // Find matching beacon in our data
                                for (Map<String, Object> data : beaconData) {
                                    if (uuid.equalsIgnoreCase((String) data.get("uuid")) &&
                                        major == (int) data.get("major") &&
                                        minor == (int) data.get("minor")) {
                                        
                                        beaconObj.put("title", data.get("title"));
                                        beaconObj.put("url", data.get("url"));
                                        break;
                                    }
                                }
                                
                                beaconArray.put(beaconObj);
                            }
                            
                            // Stop ranging after getting results
                            beaconManager.stopRangingBeacons(tempRegion);
                            
                            callbackContext.success(beaconArray);
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing beacon list", e);
                            callbackContext.error("Error processing beacon list: " + e.getMessage());
                        }
                    }
                };
                
                // Set the temporary range notifier
                beaconManager.addRangeNotifier(tempRangeNotifier);
                
                // Start a single scan
                beaconManager.startRangingBeacons(tempRegion);
                
                // Set a timeout to ensure we return something even if no beacons are found
                android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                handler.postDelayed(() -> {
                    try {
                        beaconManager.stopRangingBeacons(tempRegion);
                        beaconManager.removeRangeNotifier(tempRangeNotifier);
                        
                        // If callback hasn't been invoked yet, return empty array
                        if (!callbackContext.isFinished()) {
                            callbackContext.success(new JSONArray());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in scan timeout", e);
                    }
                }, 5000); // 5 second timeout
                
            } catch (Exception e) {
                Log.e(TAG, "Error listing beacons", e);
                callbackContext.error("Error listing beacons: " + e.getMessage());
            }
        });
    }
    
    /**
     * Debug method to check beacon scanner status
     */
    private void debugBeaconScanner(CallbackContext callbackContext) {
        JSONObject debug = new JSONObject();
        try {
            debug.put("isScanning", isScanning);
            debug.put("beaconDataCount", beaconData.size());
            debug.put("hasCallback", beaconDetectionCallback != null);
            debug.put("beaconManagerActive", beaconManager != null);
            
            // Check if Bluetooth is enabled
            android.bluetooth.BluetoothAdapter bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            boolean bluetoothEnabled = bluetoothAdapter != null && bluetoothAdapter.isEnabled();
            debug.put("bluetoothEnabled", bluetoothEnabled);
            
            // Check permissions
            Activity activity = cordova.getActivity();
            boolean hasLocationPermission = activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED;
            debug.put("hasLocationPermission", hasLocationPermission);
            
            callbackContext.success(debug);
        } catch (Exception e) {
            Log.e(TAG, "Error in debug method", e);
            callbackContext.error("Debug error: " + e.getMessage());
        }
    }
    
    // Add the missing checkCompatibility method
    private void checkCompatibility(CallbackContext callbackContext) {
        try {
            Activity activity = cordova.getActivity();
            JSONObject result = new JSONObject();
            
            // Check Bluetooth support
            android.bluetooth.BluetoothAdapter bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            boolean bluetoothSupport = bluetoothAdapter != null;
            boolean bluetoothEnabled = bluetoothSupport && bluetoothAdapter.isEnabled();
            
            result.put("bluetoothSupport", bluetoothSupport);
            result.put("bluetoothEnabled", bluetoothEnabled);
            
            // Check location permissions
            boolean hasLocationPermission = activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED;
            result.put("locationPermissions", hasLocationPermission);
            
            // Check Bluetooth permissions for Android 12+
            boolean hasBluetoothPermissions = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                hasBluetoothPermissions = activity.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) 
                    == PackageManager.PERMISSION_GRANTED
                    && activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) 
                    == PackageManager.PERMISSION_GRANTED;
            }
            result.put("bluetoothPermissions", hasBluetoothPermissions);
            
            // Overall compatibility
            boolean isCompatible = bluetoothSupport && bluetoothEnabled && hasLocationPermission && hasBluetoothPermissions;
            result.put("isCompatible", isCompatible);
            
            callbackContext.success(result);
        } catch (Exception e) {
            Log.e(TAG, "Error checking compatibility", e);
            callbackContext.error("Error checking compatibility: " + e.getMessage());
        }
    }
    
    // Add the missing didRangeBeaconsInRegion method required by RangeNotifier interface
    @Override
    public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
        if (beaconDetectionCallback != null) {
            try {
                JSONArray beaconArray = new JSONArray();
                
                for (Beacon beacon : beacons) {
                    String uuid = beacon.getId1().toString();
                    int major = beacon.getId2().toInt();
                    int minor = beacon.getId3().toInt();
                    
                    JSONObject beaconObj = new JSONObject();
                    beaconObj.put("uuid", uuid);
                    beaconObj.put("major", major);
                    beaconObj.put("minor", minor);
                    beaconObj.put("distance", beacon.getDistance());
                    beaconObj.put("rssi", beacon.getRssi());
                    
                    // Find matching beacon in our data
                    for (Map<String, Object> data : beaconData) {
                        if (uuid.equalsIgnoreCase((String) data.get("uuid")) &&
                            major == (int) data.get("major") &&
                            minor == (int) data.get("minor")) {
                            
                            beaconObj.put("title", data.get("title"));
                            beaconObj.put("url", data.get("url"));
                            break;
                        }
                    }
                    
                    beaconArray.put(beaconObj);
                }
                
                // Solo enviar los datos sin redirección
                PluginResult result = new PluginResult(PluginResult.Status.OK, beaconArray);
                result.setKeepCallback(true); // Keep the callback for future beacon detections
                beaconDetectionCallback.sendPluginResult(result);
                
                Log.d(TAG, "Detected " + beacons.size() + " beacons");
            } catch (Exception e) {
                Log.e(TAG, "Error processing beacon detection", e);
                
                // Send error back to JavaScript
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, "Error processing beacon detection: " + e.getMessage());
                result.setKeepCallback(true);
                beaconDetectionCallback.sendPluginResult(result);
            }
        }
    }
    
    // Add the required methods for MonitorNotifier interface
    @Override
    public void didEnterRegion(Region region) {
        Log.d(TAG, "Entered region: " + region.getUniqueId());
    }
    
    @Override
    public void didExitRegion(Region region) {
        Log.d(TAG, "Exited region: " + region.getUniqueId());
    }
    
    @Override
    public void didDetermineStateForRegion(int state, Region region) {
        Log.d(TAG, "Region state changed to: " + (state == MonitorNotifier.INSIDE ? "INSIDE" : "OUTSIDE"));
    }
    
    // Mover este método dentro de la clase
    private boolean checkAndRequestPermissions() {
        Activity activity = cordova.getActivity();
        List<String> permissionsNeeded = new ArrayList<>();
        
        // Verificar permisos de ubicación
        if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        
        // Verificar permisos de Bluetooth para Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (activity.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }
        
        // Solicitar permisos si es necesario
        if (!permissionsNeeded.isEmpty()) {
            cordova.requestPermissions(this, 0, permissionsNeeded.toArray(new String[0]));
            return false;
        }
        
        return true;
    }
}