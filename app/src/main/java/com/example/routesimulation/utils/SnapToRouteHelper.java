package com.example.routesimulation.utils;

import com.here.sdk.core.GeoCoordinates;
import com.here.sdk.routing.Route;
import java.util.List;

public class SnapToRouteHelper {

    // Devuelve la coordenada exacta de la ruta más cercana al GPS real
    public static GeoCoordinates obtenerPuntoMasCercano(GeoCoordinates gpsReal, Route ruta) {
        List<GeoCoordinates> puntosRuta = ruta.getGeometry().vertices;
        GeoCoordinates puntoMasCercano = puntosRuta.get(0);
        double distanciaMinima = gpsReal.distanceTo(puntoMasCercano);

        for (GeoCoordinates punto : puntosRuta) {
            double d = gpsReal.distanceTo(punto);
            if (d < distanciaMinima) {
                distanciaMinima = d;
                puntoMasCercano = punto;
            }
        }
        return puntoMasCercano;
    }

    // Calcula de forma matemática el rumbo (Bearing) entre el punto actual de la ruta y el siguiente
    public static double calcularRumboHaciaSiguientePunto(GeoCoordinates actual, Route ruta) {
        List<GeoCoordinates> puntosRuta = ruta.getGeometry().vertices;
        int indexActual = puntosRuta.indexOf(actual);

        // Si es el último punto o no se encuentra, mantenemos el rumbo por defecto
        if (indexActual == -1 || indexActual >= puntosRuta.size() - 1) {
            return 0.0;
        }

        GeoCoordinates siguientePunto = puntosRuta.get(indexActual + 1);

        double lat1 = Math.toRadians(actual.latitude);
        double lon1 = Math.toRadians(actual.longitude);
        double lat2 = Math.toRadians(siguientePunto.latitude);
        double lon2 = Math.toRadians(siguientePunto.longitude);

        double dLon = lon2 - lon1;
        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);

        double rumboRadianes = Math.atan2(y, x);
        return (Math.toDegrees(rumboRadianes) + 360.0) % 360.0;
    }
}