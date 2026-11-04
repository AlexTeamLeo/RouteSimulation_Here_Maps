package com.example.routesimulation.map;

import android.content.Context;
import android.os.Bundle;
import com.here.sdk.core.GeoCoordinates;
import com.here.sdk.core.LanguageCode;
import com.here.sdk.mapview.MapError;
import com.here.sdk.mapview.MapMeasure;
import com.here.sdk.mapview.MapScene;
import com.here.sdk.mapview.MapScheme;
import com.here.sdk.mapview.MapView;

public class MapManager {

    private final MapView mapView;
    private final Context context;

    public MapManager(Context context, MapView mapView) {
        this.context = context;
        this.mapView = mapView;
    }

    /**
     * Inicializa la escena del mapa con un diseño específico y posiciona la cámara en un punto inicial.
     * @param coordenadasIniciales Punto donde se centrará el mapa al abrir la app.
     * @param nivelZoom Escala del mapa (valores comunes entre 10.0 y 16.0).
     */
    public void inicializarMapa(GeoCoordinates coordenadasIniciales, double nivelZoom) {
        // 1. Cargar el esquema visual del mapa (NORMAL_DAY es el estándar para navegación)
        mapView.getMapScene().loadScene(MapScheme.NORMAL_DAY, new MapScene.LoadSceneCallback() {
            @Override
            public void onLoadScene(MapError mapError) {
                if (mapError == null) {
                    // La escena se cargó correctamente, procedemos a configurar la cámara básica
                    configurarCamaraInicial(coordenadasIniciales, nivelZoom);

                    // Opcional: Configurar el idioma del mapa al español
                    //mapView.setPrimaryLanguageCode(LanguageCode.ES_ES);
                } else {
                    // Manejar error de carga del mapa (ej. falta de credenciales o sin internet)
                    android.util.Log.e("MapManager", "Error al cargar la escena del mapa: " + mapError.name());
                }
            }
        });
    }

    /**
     * Posiciona la cámara inicialmente de forma plana (sin inclinación) antes de iniciar la navegación.
     */
    private void configurarCamaraInicial(GeoCoordinates centro, double zoom) {
        // Enfoque inicial básico de reconocimiento del terreno
        MapMeasure zoomMeasure = new MapMeasure(MapMeasure.Kind.ZOOM_LEVEL, zoom);
        mapView.getCamera().lookAt(centro, zoomMeasure);
    }

    /**
     * Maneja el ciclo de vida del MapView. Debe llamarse desde el onDestroy de la Actividad o Fragmento.
     * Es mandatorio en el HERE SDK para liberar la memoria RAM y procesos hilos (threads) de renderizado de la GPU.
     */
    public void onDestroy() {
        if (mapView != null) {
            mapView.onDestroy();
        }
    }

    /**
     * Maneja el ciclo de vida del MapView. Debe llamarse desde el onPause de la Actividad.
     */
    public void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }
    }

    /**
     * Maneja el ciclo de vida del MapView. Debe llamarse desde el onResume de la Actividad.
     */
    public void onResume() {
        if (mapView != null) {
            mapView.onResume();
        }
    }
}