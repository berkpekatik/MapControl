package com.mapcontrol.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MapsLinkTest {

    @Test
    public void recognisesGoogleMapsLinks() {
        assertTrue(MapsLink.isGoogleMaps("https://maps.app.goo.gl/8n8N5oKDohmJtd668?g_st=ic"));
        assertTrue(MapsLink.isGoogleMaps("https://goo.gl/maps/abc"));
        assertTrue(MapsLink.isGoogleMaps("https://maps.google.com/?q=40.76,29.93"));
        assertTrue(MapsLink.isGoogleMaps("https://www.google.com/maps/place/Foo"));
        assertTrue(MapsLink.isGoogleMaps("https://www.google.com.tr/maps/@40.7,29.9,15z"));
        assertFalse(MapsLink.isGoogleMaps("https://www.google.com/search?q=maps"));
        assertFalse(MapsLink.isGoogleMaps("https://goo.gl/abc")); // harita kısası değil
        assertFalse(MapsLink.isGoogleMaps("https://yandex.com/maps/?ll=29.9,40.7"));
        assertFalse(MapsLink.isGoogleMaps("https://maps.example.com/"));
        assertFalse(MapsLink.isGoogleMaps("geo:40.76,29.93"));
        assertFalse(MapsLink.isGoogleMaps(null));
    }

    @Test
    public void shortLinksAreTheOnesWithoutCoordinates() {
        assertTrue(MapsLink.isShortLink("https://maps.app.goo.gl/8n8N5oKDohmJtd668?g_st=ic"));
        assertTrue(MapsLink.isShortLink("https://goo.gl/maps/abc"));
        assertFalse(MapsLink.isShortLink("https://maps.google.com/?q=1,2"));
        assertNull(MapsLink.coordinates("https://maps.app.goo.gl/8n8N5oKDohmJtd668?g_st=ic"));
    }

    @Test
    public void readsCoordinatesFromTheCommonShapes() {
        assertArrayEquals(new double[]{40.76, 29.93}, MapsLink.coordinates("https://maps.google.com/?q=40.76,29.93"), 1e-9);
        assertArrayEquals(new double[]{40.76, 29.93}, MapsLink.coordinates("https://maps.google.com/?q=40.76%2C29.93&ftid=x"), 1e-9);
        assertArrayEquals(new double[]{41.0, 28.9},
                MapsLink.coordinates("https://www.google.com/maps/dir/?api=1&destination=41.0,28.9"), 1e-9);
        // !3d!4d (yerin kendisi) @görünümden önce gelir.
        assertArrayEquals(new double[]{40.7611, 29.9322}, MapsLink.coordinates(
                "https://www.google.com/maps/place/Foo/@40.7600,29.9300,17z/data=!3m1!4b1!8m2!3d40.7611!4d29.9322"), 1e-9);
        assertArrayEquals(new double[]{40.76, 29.93}, MapsLink.coordinates("https://www.google.com/maps/@40.76,29.93,15z"), 1e-9);
        assertNull(MapsLink.coordinates("https://maps.google.com/?q=95.0,29.0")); // enlem aralık dışı
        assertNull(MapsLink.coordinates("https://maps.google.com/?q=Gebze"));
        assertNull(MapsLink.coordinates(null));
    }

    @Test
    public void readsPlaceNameFromPathOrQuery() {
        assertEquals("Gebze Teknik Üniversitesi",
                MapsLink.placeName("https://www.google.com/maps/place/Gebze+Teknik+%C3%9Cniversitesi/@40.8,29.4,17z"));
        assertEquals("Kordonboyu Caddesi", MapsLink.placeName("https://maps.google.com/?q=Kordonboyu%20Caddesi&ftid=0x1"));
        assertNull(MapsLink.placeName("https://maps.google.com/?q=40.76,29.93")); // koordinat ad değildir
        assertNull(MapsLink.placeName("https://maps.app.goo.gl/abc"));
    }

    @Test
    public void unwrapsTheConsentRedirect() {
        assertEquals("https://www.google.com/maps/place/Foo",
                MapsLink.consentContinue("https://consent.google.com/m?continue=https%3A%2F%2Fwww.google.com%2Fmaps%2Fplace%2FFoo&gl=TR"));
        assertNull(MapsLink.consentContinue("https://www.google.com/maps/place/Foo"));
        assertNull(MapsLink.consentContinue("https://consent.google.com/m"));
    }

    @Test
    public void buildsTheUrisGoogleMapsUnderstands() {
        assertEquals("google.navigation:q=40.760000,29.930000", MapsLink.navigationUri(40.76, 29.93));
        assertEquals("geo:40.760000,29.930000?q=40.760000,29.930000", MapsLink.geoUri(40.76, 29.93));
        assertEquals("geo:0,0?q=Gebze+Teknik+%C3%9Cniversitesi", MapsLink.searchUri("Gebze Teknik Üniversitesi"));
    }
}
