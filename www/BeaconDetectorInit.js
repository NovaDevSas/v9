// BeaconDetectorInit.js
document.addEventListener('deviceready', function() {
    // Verificar si el plugin está disponible
    if (typeof window.beaconDetector === 'undefined') {
        console.error("El plugin beaconDetector no está disponible. Asegúrate de que el plugin esté correctamente instalado.");
        console.log("Objetos disponibles en window:", Object.keys(window).filter(key => key.toLowerCase().includes('beacon')));
        return;
    }
    
    // Add debouncing functionality
    function debounce(func, wait) {
        let timeout;
        return function() {
            const context = this;
            const args = arguments;
            clearTimeout(timeout);
            timeout = setTimeout(function() {
                func.apply(context, args);
            }, wait);
        };
    }
    
    // Define la interfaz BeaconDetectorPlugin
    window.BeaconDetectorPlugin = {
        // Add a flag to track if operations are in progress
        _operationInProgress: false,
        
        initialize: function(beaconData) {
            return new Promise(function(resolve, reject) {
                window.beaconDetector.initialize(beaconData, resolve, reject);
            });
        },
        
        startScanning: debounce(function() {
            if (this._operationInProgress) {
                console.warn("Operation already in progress, please wait");
                return Promise.reject("Operation already in progress");
            }
            
            this._operationInProgress = true;
            return new Promise((resolve, reject) => {
                window.beaconDetector.startScanning(
                    (result) => {
                        this._operationInProgress = false;
                        resolve(result);
                    },
                    (error) => {
                        this._operationInProgress = false;
                        reject(error);
                    }
                );
            });
        }, 5000), // 5 second debounce
        
        stopScanning: debounce(function() {
            if (this._operationInProgress) {
                console.warn("Operation already in progress, please wait");
                return Promise.reject("Operation already in progress");
            }
            
            this._operationInProgress = true;
            return new Promise((resolve, reject) => {
                window.beaconDetector.stopScanning(
                    (result) => {
                        this._operationInProgress = false;
                        resolve(result);
                    },
                    (error) => {
                        this._operationInProgress = false;
                        reject(error);
                    }
                );
            });
        }, 5000), // 5 second debounce
        
        onBeaconDetected: function(callback) {
            console.log("Configurando callback de detección de beacons");
            // Envolvemos el callback original para agregar logs y mejorar la visualización
            const wrappedCallback = function(beacons) {
                if (beacons && beacons.length > 0) {
                    console.log("Beacons detectados:", JSON.stringify(beacons));
                    beacons.forEach(beacon => {
                        console.log(`Beacon detectado: ${beacon.title || 'Desconocido'} UUID: ${beacon.uuid}, Major: ${beacon.major}, Minor: ${beacon.minor}, Distancia: ${beacon.distance.toFixed(2)}m`);
                    });
                } else {
                    console.log("No se detectaron beacons en este escaneo");
                }
                // Solo pasamos los datos al callback sin redirección
                callback(beacons);
            };
            window.beaconDetector.onBeaconDetected(wrappedCallback);
        },
        
        isPluginAvailable: function() {
            return new Promise(function(resolve, reject) {
                window.beaconDetector.isPluginAvailable(resolve, reject);
            });
        },
        
        checkCompatibility: function() {
            return new Promise(function(resolve, reject) {
                window.beaconDetector.checkCompatibility(resolve, reject);
            });
        },
        
        listDetectedBeacons: function() {
            return new Promise(function(resolve, reject) {
                window.beaconDetector.listDetectedBeacons(resolve, reject);
            });
        },
        
        // Añadido coma para corregir el error de sintaxis
        debugBeaconScanner: function() {
            return new Promise(function(resolve, reject) {
                window.beaconDetector.debugBeaconScanner(
                    function(result) {
                        console.log("Debug scanner result:", result);
                        resolve(result);
                    }, 
                    function(error) {
                        console.error("Debug scanner error:", error);
                        reject(error);
                    }
                );
            });
        },
        
        continuousScanning: function(options = {}) {
            const defaults = {
                interval: 10000,  // 10 seconds between scans
                maxAttempts: 0,   // 0 means infinite
                onBeaconDetected: null,
                onScanComplete: null,
                onError: null
            };
            
            const settings = {...defaults, ...options};
            let attempts = 0;
            let isRunning = true;
            let timeoutId = null;
            
            const performScan = () => {
                if (!isRunning || (settings.maxAttempts > 0 && attempts >= settings.maxAttempts)) {
                    console.log("Continuous scanning stopped after " + attempts + " attempts");
                    return;
                }
                
                attempts++;
                console.log("Performing scan attempt " + attempts);
                
                this.listDetectedBeacons()
                    .then(beacons => {
                        if (beacons && beacons.length > 0 && settings.onBeaconDetected) {
                            settings.onBeaconDetected(beacons);
                        }
                        
                        if (settings.onScanComplete) {
                            settings.onScanComplete(beacons);
                        }
                        
                        // Schedule next scan
                        timeoutId = setTimeout(performScan, settings.interval);
                    })
                    .catch(error => {
                        console.error("Error during beacon scan:", error);
                        if (settings.onError) {
                            settings.onError(error);
                        }
                        
                        // Even on error, continue scanning
                        timeoutId = setTimeout(performScan, settings.interval);
                    });
            };
            
            // Start the first scan
            performScan();
            
            // Return control methods
            return {
                stop: function() {
                    isRunning = false;
                    if (timeoutId) {
                        clearTimeout(timeoutId);
                    }
                    console.log("Continuous scanning stopped manually");
                },
                isActive: function() {
                    return isRunning;
                }
            };
        }
    };
    
    console.log("BeaconDetectorPlugin interface initialized successfully");
    
    // Verificar compatibilidad automáticamente e iniciar escaneo continuo
    window.BeaconDetectorPlugin.checkCompatibility()
        .then(function(result) {
            console.log("Compatibilidad del dispositivo:", result);
            if (result.isCompatible) {
                // Iniciar escaneo continuo
                window.continuousScanController = window.BeaconDetectorPlugin.continuousScanning({
                    interval: 15000, // 15 segundos entre escaneos
                    onBeaconDetected: function(beacons) {
                        console.log("Beacons detectados en escaneo continuo:", beacons.length);
                        beacons.forEach(beacon => {
                            console.log(`Beacon detectado: ${beacon.title || 'Desconocido'} UUID: ${beacon.uuid}, Major: ${beacon.major}, Minor: ${beacon.minor}, Distancia: ${beacon.distance.toFixed(2)}m`);
                        });
                    },
                    onError: function(error) {
                        console.error("Error en escaneo continuo:", error);
                    }
                });
                console.log("Escaneo continuo iniciado");
            } else {
                console.warn("El dispositivo no es compatible con la detección de beacons");
            }
        })
        .catch(function(error) {
            console.error("Error al verificar compatibilidad:", error);
        });
}, false);