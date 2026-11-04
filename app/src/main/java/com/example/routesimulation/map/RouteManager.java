package com.example.routesimulation.map;

import android.content.Context;

import androidx.appcompat.content.res.AppCompatResources;

import com.example.routesimulation.R;
import com.here.sdk.core.Color;
import com.here.sdk.core.GeoCoordinates;
import com.here.sdk.core.GeoPolyline;
import com.here.sdk.core.errors.InstantiationErrorException;
import com.here.sdk.mapview.LineCap;
import com.here.sdk.mapview.MapMarker;
import com.here.sdk.mapview.MapMeasureDependentRenderSize;
import com.here.sdk.mapview.MapPolyline;
import com.here.sdk.mapview.MapView;
import com.here.sdk.mapview.RenderSize;
import com.here.sdk.routing.CalculateRouteCallback;
import com.here.sdk.routing.CarOptions;
import com.here.sdk.routing.OptimizationMode;
import com.here.sdk.routing.Route;
import com.here.sdk.routing.RoutingEngine;
import com.here.sdk.routing.RoutingError;
import com.here.sdk.routing.RoutingOptions;
import com.here.sdk.routing.Waypoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

public class RouteManager  {

    private final MapView mapView;

    private final Context context;
    private RoutingEngine routingEngine;
    private MapPolyline mapPolylineActual;

    // Guarda la ruta dibujada actual
    private MapPolyline currentMapPolyline = null;

    // Interfaz para comunicar el resultado a la Actividad principal
    public interface RouteListener {
        void onRutaLista(Route ruta);
        void onRutaError(String mensajeError);
    }

    public RouteManager(Context context, MapView mapView) {
        this.mapView = mapView;
        this.context = context;
        try {
            // Inicializa el motor de rutas (incluido en la edición Explore)
            this.routingEngine = new RoutingEngine();
        } catch (InstantiationErrorException e) {
            android.util.Log.e("RouteManager", "Error al inicializar RoutingEngine: " + e.getMessage());
        }
    }

    /**
     * Calcula la ruta terrestre para automóvil entre dos puntos geográficos.
     */
    public void calcularRuta(GeoCoordinates inicio, GeoCoordinates destino, RouteListener listener) {
        if (routingEngine == null) {
            if (listener != null) listener.onRutaError("El motor de rutas no está inicializado.");
            return;
        }

        Waypoint startWaypoint = new Waypoint(inicio);
        Waypoint destinationWaypoint = new Waypoint(destino);
        List<Waypoint> waypoints = new ArrayList<>(Arrays.asList(startWaypoint, destinationWaypoint));

        // CarOptions configura preferencias como evitar peajes, autopistas, etc.
        RoutingOptions carOptions = new RoutingOptions();
        carOptions.routeOptions.optimizationMode = OptimizationMode.FASTEST;

        routingEngine.calculateRoute(
                waypoints,
                carOptions,
                new CalculateRouteCallback() {
                    @Override
                    public void onRouteCalculated(RoutingError routingError, List<Route> routes) {
                        if (routingError == null && !routes.isEmpty()) {
                            Route rutaObtenida = routes.get(0);

                            // 1. Pintamos la línea en el mapa inmediatamente
                            try {
                                dibujarRutaEnMapa(rutaObtenida);
                            } catch (MapMeasureDependentRenderSize.InstantiationException e) {
                                throw new RuntimeException(e);
                            }

                            // 2. Notificamos a la actividad que la ruta fue exitosa
                            if (listener != null) {
                                listener.onRutaLista(rutaObtenida);
                            }
                        } else {
                            String errorMsg = (routingError != null) ? routingError.name() : "Error desconocido";
                            if (listener != null) {
                                listener.onRutaError("No se pudo calcular la ruta: " + errorMsg);
                            }
                        }
                    }
                });
    }

    /**
     * Genera la geometría visual sobre el mapa.
     */
    private void dibujarRutaEnMapa(Route route) throws MapMeasureDependentRenderSize.InstantiationException {
        // Remover la polilínea del trayecto anterior si existía una activa
        limpiarRutaActual();

        // Extrae el conjunto de vértices de la ruta calculada
        GeoPolyline routeGeoPolyline = route.getGeometry();

        // Configuración estética de la línea de guía
        float widthInPixels = 20.0f;
        Color lineColor = Color.valueOf(context.getColor(R.color.black));

        MapMeasureDependentRenderSize lineWidth = new MapMeasureDependentRenderSize(
                RenderSize.Unit.PIXELS,
                widthInPixels
        );

        MapMeasureDependentRenderSize lineWidth2 = new MapMeasureDependentRenderSize(
                RenderSize.Unit.PIXELS,
                5000.0f
        );

        MapPolyline.SolidRepresentation representation = null;
        try {
            representation = new MapPolyline.SolidRepresentation(
                    lineWidth,
                    lineColor,
                    LineCap.ROUND
            );
        } catch (MapPolyline.Representation.InstantiationException e) {
            throw new RuntimeException(e);
        }

        mapPolylineActual = new MapPolyline(routeGeoPolyline, representation);

        mapPolylineActual.setProgressColor(Color.valueOf(context.getColor(R.color.red)));
        mapPolylineActual.setProgress(0.50f);                          // 45% de la ruta completada
        mapPolylineActual.setProgressGradientLength(lineWidth2);

        // Agregar al mapa
        mapView.getMapScene().addMapPolyline(mapPolylineActual);

    }

    /**
     * Borra la ruta del mapa. Útil si deseas cancelar la navegación o limpiar la pantalla.
     */
    public void limpiarRutaActual() {
        if (mapPolylineActual != null) {
            mapView.getMapScene().removeMapPolyline(mapPolylineActual);
            mapPolylineActual = null;
        }
    }
}