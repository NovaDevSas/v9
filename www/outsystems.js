// Inicializa el valor de Message para que nunca esté vacío
$actions.UpdateFeedbackMessage("Inicializando detector de beacons...");

// Asegúrate de que el dispositivo esté listo antes de usar el plugin
if (typeof cordova !== 'undefined') {
    $actions.UpdateFeedbackMessage("Cordova detectado, esperando a que el dispositivo esté listo...");
    
    document.addEventListener('deviceready', function() {
        $actions.UpdateFeedbackMessage("Dispositivo listo, verificando disponibilidad del plugin...");
        
        // Verifica que BeaconDetectorPlugin esté disponible después de que BeaconDetectorInit.js lo haya inicializado
        setTimeout(function() {
            if (window.BeaconDetectorPlugin) {
                $actions.UpdateFeedbackMessage("Plugin detectado, verificando compatibilidad...");
                
                // Verificar compatibilidad del dispositivo primero
                BeaconDetectorPlugin.checkCompatibility()
                    .then(function(result) {
                        if (!result.isCompatible) {
                            throw new Error("El dispositivo no es compatible con la detección de beacons: " + 
                                  (result.bluetoothSupport ? "" : "No soporta Bluetooth. ") +
                                  (result.bluetoothEnabled ? "" : "Bluetooth desactivado. ") +
                                  (result.locationPermissions ? "" : "Permisos de ubicación no concedidos. ") +
                                  (result.bluetoothPermissions ? "" : "Permisos de Bluetooth no concedidos."));
                        }
                        
                        $actions.UpdateFeedbackMessage("Dispositivo compatible, preparando datos de beacons...");
                        
                        // Define tus datos de beacons
                        var beaconData = [
                          {
                            "title": "Audio",
                            "uuid": "8c3889dc-3338-ff37-0e19-28bb83b37217",
                            "minor": 1,
                            "major": 11,
                            "url": "https://miportal.entel.cl/personas/catalogo/accesorios/audio"
                          },
                          {
                            "title": "Energía y protección para tú equipo",
                            "uuid": "8c3889dc-3338-ff37-0e19-28bb83b37217",
                            "minor": 1,
                            "major": 12,
                            "url": "https://miportal.entel.cl/personas/catalogo/accesorios/_/N-1z140lnZ1z140faZ1z0of6i"
                          },
                          {
                            "title": "Dispositivos Apple",
                            "uuid": "8c3889dc-3338-ff37-0e19-28bb83b37217",
                            "minor": 1,
                            "major": 13,
                            "url": "https://miportal.entel.cl/personas/catalogo/celulares/_/N-1z141c1"
                          },
                          {
                            "title": "Dispositivos Samsung",
                            "uuid": "8c3889dc-3338-ff37-0e19-28bb83b37217",
                            "minor": 1,
                            "major": 14,
                            "url": "https://miportal.entel.cl/personas/catalogo/celulares/_/N-1z1416i"
                          }
                        ];

                        $actions.UpdateFeedbackMessage("Datos de beacons preparados, inicializando plugin...");
                        
                        // Inicializa el plugin
                        return BeaconDetectorPlugin.initialize(beaconData);
                    })
                    .then(function() {
                        $actions.UpdateFeedbackMessage("Plugin inicializado correctamente, comenzando escaneo...");
                        
                        // Comienza a escanear beacons SOLO UNA VEZ
                        return BeaconDetectorPlugin.startScanning();
                    })
                    .then(function() {
                        $actions.UpdateFeedbackMessage("Escaneo de beacons iniciado correctamente. Esperando detección...");
                        
                        // Añadir un temporizador para verificar periódicamente los beacons detectados
                        var checkInterval = setInterval(function() {
                            // Usamos listDetectedBeacons para obtener los beacons actuales sin iniciar un nuevo escaneo
                            BeaconDetectorPlugin.listDetectedBeacons()
                                .then(function(beacons) {
                                    if (beacons && beacons.length > 0) {
                                        // Mostrar información detallada de cada beacon encontrado
                                        var beaconInfo = "Se encontraron " + beacons.length + " beacons cercanos:\n";
                                        
                                        beacons.forEach(function(beacon, index) {
                                            // Destacar UUID, Major y Minor en el mensaje
                                            beaconInfo += "\n" + (index + 1) + ". UUID: " + beacon.uuid + 
                                                ", Major: " + beacon.major + 
                                                ", Minor: " + beacon.minor +
                                                (beacon.title ? " (" + beacon.title + ")" : "") +
                                                ", Distancia: " + (beacon.distance ? beacon.distance.toFixed(2) + "m" : "desconocida");
                                        });
                                        
                                        $actions.UpdateFeedbackMessage(beaconInfo);
                                    } else {
                                        console.log("No se detectaron beacons en esta verificación");
                                    }
                                })
                                .catch(function(error) {
                                    console.error("Error al listar beacons:", error);
                                });
                        }, 5000); // Verificar cada 5 segundos
                        
                        // Configura el callback de detección de beacons - SOLO SE CONFIGURA UNA VEZ
                        BeaconDetectorPlugin.onBeaconDetected(function(beacon) {
                            // Actualizar el mensaje con información del beacon
                            var beaconMsg = "Beacon detectado: " + (beacon.title || "Desconocido") + 
                                          " (UUID: " + beacon.uuid + 
                                          ", Major: " + beacon.major + 
                                          ", Minor: " + beacon.minor + ")";
                            
                            $actions.UpdateFeedbackMessage(beaconMsg);
                        });
                    })
                    .catch(function(error) {
                        var errorMsg = "Error en la detección de beacons: " + error;
                        $actions.UpdateFeedbackMessage(errorMsg);
                    });
            } else {
                $actions.UpdateFeedbackMessage("BeaconDetectorPlugin no está disponible. Asegúrate de que el plugin esté correctamente instalado.");
            }
        }, 500); // Pequeño retraso para asegurar que BeaconDetectorInit.js haya terminado
    }, false);
} else {
    $actions.UpdateFeedbackMessage("Cordova no está disponible. Esta acción solo funciona en dispositivos móviles.");
}
