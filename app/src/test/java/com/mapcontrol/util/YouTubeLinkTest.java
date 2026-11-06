package com.mapcontrol.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class YouTubeLinkTest {

    @Test
    public void recognizesYouTubeHosts() {
        assertTrue(YouTubeLink.isYouTube("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertTrue(YouTubeLink.isYouTube("https://youtube.com/watch?v=dQw4w9WgXcQ"));
        assertTrue(YouTubeLink.isYouTube("https://m.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertTrue(YouTubeLink.isYouTube("https://youtu.be/dQw4w9WgXcQ?si=abc"));
        assertTrue(YouTubeLink.isYouTube("http://www.youtube.com/"));
        assertTrue(YouTubeLink.isYouTube("HTTPS://WWW.YOUTUBE.COM/shorts/abcdefghijk"));
        assertTrue(YouTubeLink.isYouTube("  https://youtu.be/dQw4w9WgXcQ  "));
    }

    @Test
    public void rejectsOtherSitesAndLookalikes() {
        assertFalse(YouTubeLink.isYouTube(null));
        assertFalse(YouTubeLink.isYouTube(""));
        assertFalse(YouTubeLink.isYouTube("https://music.youtube.com/watch?v=dQw4w9WgXcQ")); // ayrı uygulama
        assertFalse(YouTubeLink.isYouTube("https://tv.youtube.com/"));
        assertFalse(YouTubeLink.isYouTube("https://www.youtube.com.evil.example/watch?v=dQw4w9WgXcQ"));
        assertFalse(YouTubeLink.isYouTube("https://evil.example/youtube.com/watch?v=dQw4w9WgXcQ"));
        assertFalse(YouTubeLink.isYouTube("https://notyoutube.com/"));
        assertFalse(YouTubeLink.isYouTube("https://maps.google.com/?q=1,2"));
        assertFalse(YouTubeLink.isYouTube("not a url"));
    }

    @Test
    public void onlyHttpAndHttpsAreYouTubeLinks() {
        assertFalse(YouTubeLink.isYouTube("javascript://www.youtube.com/%0Aalert(1)"));
        assertFalse(YouTubeLink.isYouTube("file://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertFalse(YouTubeLink.isYouTube("vnd.youtube:dQw4w9WgXcQ"));
    }

    @Test
    public void extractsVideoIdFromEveryVideoForm() {
        assertEquals("dQw4w9WgXcQ", YouTubeLink.videoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertEquals("dQw4w9WgXcQ", YouTubeLink.videoId("https://www.youtube.com/watch?feature=share&v=dQw4w9WgXcQ&t=42s"));
        assertEquals("dQw4w9WgXcQ", YouTubeLink.videoId("https://youtu.be/dQw4w9WgXcQ"));
        assertEquals("dQw4w9WgXcQ", YouTubeLink.videoId("https://youtu.be/dQw4w9WgXcQ?si=xyz&t=10"));
        assertEquals("abcdefghijk", YouTubeLink.videoId("https://www.youtube.com/shorts/abcdefghijk"));
        assertEquals("abcdefghijk", YouTubeLink.videoId("https://m.youtube.com/live/abcdefghijk?feature=share"));
        assertEquals("abcdefghijk", YouTubeLink.videoId("https://www.youtube.com/embed/abcdefghijk"));
    }

    @Test
    public void noVideoIdForChannelsPlaylistsAndHomepage() {
        assertNull(YouTubeLink.videoId("https://www.youtube.com/"));
        assertNull(YouTubeLink.videoId("https://www.youtube.com/@mapcontrol"));
        assertNull(YouTubeLink.videoId("https://www.youtube.com/playlist?list=PLabcdefghijk"));
        assertNull(YouTubeLink.videoId("https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv"));
        assertNull(YouTubeLink.videoId("https://www.youtube.com/watch")); // v yok
        assertNull(YouTubeLink.videoId("https://www.youtube.com/watch?v=kisa")); // 11 karakter değil
        assertNull(YouTubeLink.videoId("https://youtu.be/"));
        assertNull(YouTubeLink.videoId("https://evil.example/watch?v=dQw4w9WgXcQ"));
        assertNull(YouTubeLink.videoId(null));
    }

    @Test
    public void officialAppIsTriedBeforeReVanced() {
        assertEquals(2, YouTubeLink.PACKAGES.length);
        assertEquals("com.google.android.youtube", YouTubeLink.PACKAGES[0]);
        assertEquals("app.revanced.android.youtube", YouTubeLink.PACKAGES[1]);
    }
}
