package com.example.routesimulation.map;

import android.content.Context;

import com.example.routesimulation.R;
import com.here.sdk.animation.Easing;
import com.here.sdk.animation.EasingFunction;
import com.here.sdk.core.Color;
import com.here.sdk.core.GeoCoordinates;
import com.here.sdk.core.GeoCoordinatesUpdate;
import com.here.sdk.core.GeoOrientationUpdate;
import com.here.sdk.core.GeoPolyline;
import com.here.sdk.core.Location;
import com.here.sdk.mapview.LineCap;
import com.here.sdk.mapview.LocationIndicator;
import com.here.sdk.mapview.MapCameraAnimation;
import com.here.sdk.mapview.MapCameraAnimationFactory;
import com.here.sdk.mapview.MapCameraUpdate;
import com.here.sdk.mapview.MapCameraUpdateFactory;
import com.here.sdk.mapview.MapImage;
import com.here.sdk.mapview.MapImageFactory;
import com.here.sdk.mapview.MapMarker;
import com.here.sdk.mapview.MapMeasure;
import com.here.sdk.mapview.MapMeasureDependentRenderSize;
import com.here.sdk.mapview.MapPolyline;
import com.here.sdk.mapview.MapView;
import com.here.sdk.mapview.RenderSize;
import com.here.sdk.routing.Route;
import com.here.time.Duration;

import java.util.Date;

public class NavegacionVisualManager {
    private final MapView mapView;
    private final Context context;
    private MapPolyline mapPolyline;
    private MapMarker posicionMarker;

    LocationIndicator locationIndicator = new LocationIndicator();


    public NavegacionVisualManager(Context context, MapView mapView) {
        this.context = context;
        this.mapView = mapView;

        // Define el estilo del cursor (puede ser PEDESTRIAN o NAVIGATION para modo coche)
        locationIndicator.setLocationIndicatorStyle(LocationIndicator.IndicatorStyle.NAVIGATION);
    }

    // 1. Dibuja la ruta en el mapa
    public void dibujarRuta(Route ruta) throws MapMeasureDependentRenderSize.InstantiationException {
        if (mapPolyline != null) {
            mapView.getMapScene().removeMapPolyline(mapPolyline);
        }

        GeoPolyline geoPolyline = ruta.getGeometry();
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

        mapPolyline = new MapPolyline(geoPolyline, representation);

        mapPolyline.setProgressColor(Color.valueOf(context.getColor(R.color.red)));
        mapPolyline.setProgress(0.50f);                          // 45% de la ruta completada
        mapPolyline.setProgressGradientLength(lineWidth2);

        // Agregar al mapa
        mapView.getMapScene().addMapPolyline(mapPolyline);
    }

    // 2. Actualiza el marcador y enfoca la cámara usando orientación (rumbo)
    public void actualizarProgresoNavegacion(GeoCoordinates coordenadaAcoplada, double rumboGrados) {
        // 1. Mover el marcador gráfico en el mapa
        if (posicionMarker == null) {
            /*MapImage mapImage = MapImageFactory.fromResource(context.getResources(), android.R.drawable.ic_menu_compass);
            posicionMarker = new MapMarker(coordenadaAcoplada, mapImage);
            mapView.getMapScene().addMapMarker(posicionMarker);*/

            Location miUbicacion = new Location(coordenadaAcoplada);
            miUbicacion.time = new Date();
            miUbicacion.bearingInDegrees = rumboGrados; // Ángulo hacia donde apunta el cursor

            locationIndicator.updateLocation(miUbicacion);
            locationIndicator.enable(mapView); // Activa el cursor visible en el mapa

        } else {
            posicionMarker.setCoordinates(coordenadaAcoplada);
        }

        // 2. Envolver la coordenada en un GeoCoordinatesUpdate
        GeoCoordinatesUpdate targetCoordinatesUpdate = new GeoCoordinatesUpdate(coordenadaAcoplada);

        // 3. Definir la orientación (Rumbo en grados de la calle e inclinación 3D)
        GeoOrientationUpdate orientacionUpdate = new GeoOrientationUpdate(rumboGrados, 60.0);

        // 4. Configurar el nivel de zoom correcto usando MapMeasure
        MapMeasure zoomMeasure = new MapMeasure(MapMeasure.Kind.DISTANCE_IN_METERS, 80.0);

        // 5. Generar la actualización de cámara usando la firma estricta de la API de HERE
        MapCameraUpdate actualizacionCamara = MapCameraUpdateFactory.lookAt(
                targetCoordinatesUpdate,
                orientacionUpdate,
                zoomMeasure
        );

        // 6. Aplicar la interpolación lineal para evitar saltos toscos
        Easing suavizadoLineal = new Easing(EasingFunction.LINEAR);

        // 7. Crear e inicializar la animación acoplada al tiempo del Handler (1 segundo)
        MapCameraAnimation animacionCamara = MapCameraAnimationFactory.createAnimation(
                actualizacionCamara,
                Duration.ofMillis(1000),
                suavizadoLineal
        );

        // 8. Lanzar animación
        mapView.getCamera().startAnimation(animacionCamara);
    }
}