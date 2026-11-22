package com.mapcontrol.files;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Dosya gezgininde ikon, renk ve dokunma davranışını belirleyen tür (uzantıdan). */
public enum FileKind {
    FOLDER, APK, IMAGE, VIDEO, AUDIO, ARCHIVE, DOCUMENT, OTHER;

    private static final Set<String> IMAGE_EXT = set("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif");
    private static final Set<String> VIDEO_EXT = set("mp4", "mkv", "avi", "mov", "3gp", "webm", "ts", "m4v", "wmv", "flv");
    private static final Set<String> AUDIO_EXT = set("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "wma", "amr");
    private static final Set<String> ARCHIVE_EXT = set("zip", "rar", "7z", "tar", "gz", "tgz", "xz", "bz2", "apks", "xapk", "apkm");
    private static final Set<String> DOCUMENT_EXT = set("pdf", "txt", "log", "json", "xml", "csv", "md", "doc", "docx",
            "xls", "xlsx", "ppt", "pptx", "html", "htm", "kml", "gpx");

    @NonNull
    public static FileKind of(@Nullable String name, boolean directory) {
        if (directory) {
            return FOLDER;
        }
        String ext = extension(name);
        if (ext.equals("apk")) return APK;
        if (IMAGE_EXT.contains(ext)) return IMAGE;
        if (VIDEO_EXT.contains(ext)) return VIDEO;
        if (AUDIO_EXT.contains(ext)) return AUDIO;
        if (ARCHIVE_EXT.contains(ext)) return ARCHIVE;
        if (DOCUMENT_EXT.contains(ext)) return DOCUMENT;
        return OTHER;
    }

    @NonNull
    public static String extension(@Nullable String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot >= 0 && dot < name.length() - 1 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static Set<String> set(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }
}
