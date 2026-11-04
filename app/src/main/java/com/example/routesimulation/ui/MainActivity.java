package com.example.routesimulation.ui;

import android.content.Context;
import android.os.Bundle;

import android.Manifest;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.here.sdk.core.GeoCoordinates;
import com.here.sdk.core.engine.AuthenticationMode;
import com.here.sdk.core.engine.SDKNativeEngine;
import com.here.sdk.core.engine.SDKOptions;
import com.here.sdk.core.errors.InstantiationErrorException;
import com.here.sdk.mapview.MapMeasureDependentRenderSize;
import com.here.sdk.mapview.MapView;
import com.here.sdk.routing.Route;
import com.example.routesimulation.R;
import com.example.routesimulation.map.MapManager;
import com.example.routesimulation.map.NavegacionVisualManager;
import com.example.routesimulation.map.RouteManager;
import com.example.routesimulation.utils.SnapToRouteHelper;
import com.here.sdk.units.compass.CompassUnit;
import com.here.sdk.units.compass.CompassView;
import com.here.sdk.units.mapruler.MapScaleView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 42;

    private MapView mapView;
    private MapManager mapManager;
    private RouteManager routeManager;
    private NavegacionVisualManager navegacionVisualManager;

    // Almacena la ruta calculada por HERE para los cálculos del GPS
    private Route rutaActual;


    private android.os.Handler simuladorHandler;
    private Runnable simuladorRunnable;
    private int indicePuntoActual = 0;
    private boolean estaSimulando = false;
    private java.util.List<GeoCoordinates> verticesRuta; // Lista de puntos que componen la ruta

    // Coordenadas de ejemplo (Paseo de la Reforma, CDMX)
    GeoCoordinates puntoInicio = null;
    GeoCoordinates puntoDestino = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        initializeHERESDK();
        setContentView(R.layout.activity_main);

        // 1. Vincular e inicializar la vista nativa del mapa (Obligatorio en HERE SDK)
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        mapView.setOnReadyListener(new MapView.OnReadyListener() {
            @Override
            public void onMapViewReady() {
                // This will be called each time after this activity is resumed.
                // It will not be called before the first map scene was loaded.
                // Any code that requires map data may not work as expected until this event is received.
                Log.d(TAG, "HERE Rendering Engine attached.");
            }
        });

        Button bGenerateRoute = findViewById(R.id.generateRouteButton);
        Button bSimulateRoute = findViewById(R.id.runMapButton);
        Button bStopSimulateRoute = findViewById(R.id.stopRouteButton);
        Button bCleanRoute = findViewById(R.id.cleanMapButton);

        // 2. Instanciar nuestros managers según la arquitectura del árbol de archivos
        mapManager = new MapManager(this, mapView);
        routeManager = new RouteManager(this, mapView);
        navegacionVisualManager = new NavegacionVisualManager(this, mapView);

        puntoInicio = new GeoCoordinates(20.596065, -100.345894);
        puntoDestino = new GeoCoordinates(21.983664, -100.877346);

        // 3. Cargar el mapa base centrado en nuestro punto de inicio
        mapManager.inicializarMapa(puntoInicio, 300);

        // 4. Solicitar permisos de ubicación antes de activar el GPS real
        try {
            revisarPermisosYArrancar();
        } catch (InstantiationErrorException e) {
            throw new RuntimeException(e);
        }

        // Optionally, add HERE SDK Units to decorate the map view.
        setupCompass();
        setupMapScaleView();

        bGenerateRoute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                try {
                    iniciarTrazadoDeRuta();
                } catch (InstantiationErrorException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        bSimulateRoute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                comenzarSimulacionRuta();
            }
        });

        bStopSimulateRoute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                pararSimulacionRuta();
            }
        });

        bCleanRoute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                routeManager.limpiarRutaActual();
            }
        });
    }

    /**
     * Llama al cálculo de la ruta una vez que el mapa esté listo o se presione un botón.
     */
    private void iniciarTrazadoDeRuta() throws InstantiationErrorException {
        Toast.makeText(this, "Calculando trayecto...", Toast.LENGTH_SHORT).show();

        routeManager.calcularRuta(puntoInicio, puntoDestino, new RouteManager.RouteListener() {
            @Override
            public void onRutaLista(Route ruta) {
                // Guardamos la ruta localmente
                rutaActual = ruta;

                // Dibujamos la polilínea azul en el mapa
                try {
                    navegacionVisualManager.dibujarRuta(ruta);
                } catch (MapMeasureDependentRenderSize.InstantiationException e) {
                    throw new RuntimeException(e);
                }

                Toast.makeText(MainActivity.this, "Ruta lista. Esperando señal GPS.", Toast.LENGTH_SHORT).show();

                // SIMULACIÓN: Forzamos una primera actualización en el punto de inicio para la cámara
                actualizarPosicionGpsNavegacion(puntoInicio);
            }

            @Override
            public void onRutaError(String mensajeError) {
                Log.e(TAG, "Error: " + mensajeError);
                Toast.makeText(MainActivity.this, mensajeError, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Este método recibe las coordenadas crudas del GPS del teléfono, las procesa
     * con el SnapToRouteHelper y actualiza la UI/Cámara.
     */
    public void actualizarPosicionGpsNavegacion(GeoCoordinates gpsReal) {
        if (rutaActual == null) return;

        // 1. Corregir la coordenada para "pegarla" magnéticamente a la ruta azul
        GeoCoordinates coordenadaAcoplada = SnapToRouteHelper.obtenerPuntoMasCercano(gpsReal, rutaActual);

        // 2. Calcular la dirección física de la calle en ese punto de la ruta
        double rumboRuta = SnapToRouteHelper.calcularRumboHaciaSiguientePunto(coordenadaAcoplada, rutaActual);

        // 3. Mover el marcador gráfico del vehículo y orientar la cámara en 3D
        navegacionVisualManager.actualizarProgresoNavegacion(coordenadaAcoplada, rumboRuta);
    }

    private void comenzarSimulacionRuta() {
        if (rutaActual == null) {
            Toast.makeText(this, "Primero debes calcular una ruta", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. Obtener todos los puntos (vértices) geométricos de la ruta trazada
        verticesRuta = rutaActual.getGeometry().vertices;

        if (verticesRuta == null || verticesRuta.isEmpty()) return;

        // 2. Reiniciar el índice al primer punto de la ruta
        indicePuntoActual = 0;
        estaSimulando = true;

        // 3. Inicializar el Handler si es la primera vez
        if (simuladorHandler == null) {
            simuladorHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        }

        // 4. Definir la tarea repetitiva
        simuladorRunnable = new Runnable() {
            @Override
            public void run() {
                if (!estaSimulando || verticesRuta == null) return;

                if (indicePuntoActual < verticesRuta.size()) {
                    // Obtener las coordenadas del punto actual del trayecto
                    GeoCoordinates coordenadaSimulada = verticesRuta.get(indicePuntoActual);

                    // Enviamos esta coordenada a tu función existente de procesamiento
                    actualizarPosicionGpsNavegacion(coordenadaSimulada);

                    // Avanzar al siguiente punto para la próxima iteración
                    indicePuntoActual++;

                    // Volver a ejecutar esta misma tarea dentro de 1000 milisegundos (1 segundo)
                    // Puedes bajar este número (ej. 300) si quieres que el coche vaya más rápido
                    simuladorHandler.postDelayed(this, 1000);
                } else {
                    // Llegamos al destino final de la ruta
                    pararSimulacionRuta();
                    Toast.makeText(MainActivity.this, "¡Has llegado a tu destino!", Toast.LENGTH_LONG).show();
                }
            }
        };

        // Arrancar el bucle inmediatamente
        simuladorHandler.post(simuladorRunnable);
        Toast.makeText(this, "Simulación de viaje iniciada", Toast.LENGTH_SHORT).show();
    }

    // ==========================================
    // GESTIÓN DE PERMISOS Y CICLO DE VIDA
    // ==========================================

    private void revisarPermisosYArrancar() throws InstantiationErrorException {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    PERMISSION_REQUEST_CODE);
        } else {
            // Permisos concedidos, procedemos a trazar la ruta
            iniciarTrazadoDeRuta();
        }
    }

    private void pararSimulacionRuta() {
        estaSimulando = false;
        if (simuladorHandler != null && simuladorRunnable != null) {
            simuladorHandler.removeCallbacks(simuladorRunnable);
        }
    }

    private void initializeHERESDK() {
        // Set your credentials for the HERE SDK.
        String accessKeyID = "sSAF4XJSCpTkyINckWoPKg";
        String accessKeySecret = "ZXq9ob3Cv1h_4WSvFgu3tIOMfUYSK-SRnka9rKMRhevL7V9UGBhn-gu14-pOvQlJ5nBNp1Z4LdRWv7nZ79ww2g";
        AuthenticationMode authenticationMode = AuthenticationMode.withKeySecret(accessKeyID, accessKeySecret);
        SDKOptions options = new SDKOptions(authenticationMode);
        try {
            Context context = this;
            SDKNativeEngine.makeSharedInstance(context, options);
        } catch (InstantiationErrorException e) {
            throw new RuntimeException("Initialization of HERE SDK failed: " + e.error.name());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            try {
                iniciarTrazadoDeRuta();
            } catch (InstantiationErrorException e) {
                throw new RuntimeException(e);
            }
        } else {
            Toast.makeText(this, "Se requieren permisos de GPS para simular el trayecto.", Toast.LENGTH_LONG).show();
        }
    }

    private void setupMapScaleView() {
        MapScaleView mapScaleView = findViewById(R.id.map_ruler);
        mapScaleView.setup(mapView);
    }

    private void setupCompass() {
        CompassView compassView = findViewById(R.id.compass);
        CompassUnit compassUnit = compassView.compassUnit;
        compassUnit.setup(mapView, getSupportFragmentManager());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapManager != null) mapManager.onResume();
    }

    @Override
    protected void onPause() {
        pararSimulacionRuta();
        if (mapManager != null) mapManager.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (mapManager != null) mapManager.onDestroy();
        mapView.onDestroy();
        disposeHERESDK();
        super.onDestroy();
    }

    private void disposeHERESDK() {
        // Free HERE SDK resources before the application shuts down.
        // Usually, this should be called only on application termination.
        // Afterwards, the HERE SDK is no longer usable unless it is initialized again.
        SDKNativeEngine sdkNativeEngine = SDKNativeEngine.getSharedInstance();
        if (sdkNativeEngine != null) {
            sdkNativeEngine.dispose();
            // For safety reasons, we explicitly set the shared instance to null to avoid situations,
            // where a disposed instance is accidentally reused.
            SDKNativeEngine.setSharedInstance(null);
        }
    }
}