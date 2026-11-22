package com.desaysv.launcher.svwallpaper.data;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Matches {@code com.desaysv.launcher.svwallpaper.data.WallpaperInfo} Parcelable contract in SVLauncher.apk.
 */
public class WallpaperInfo implements Parcelable {

    public static final String FILE_LOCATION_2D = "FILE_LOCATION_2D";
    public static final String FILE_LOCATION_VIDEO = "FILE_LOCATION_VIDEO";
    public static final String FILE_URL_2D = "FILE_URL_2D";
    public static final String FILE_URL_VIDEO = "FILE_URL_VIDEO";
    public static final String KEY_USER_WALLPAPER = "user_wallpaper";
    public static final String LAUNCHER_SYSTEM_RESOURCE_PREFIX = "launcher_wallpaper";

    String coverImagePath;
    String dayImagePath;
    String fileType;
    String id;
    String imagePath;
    String nightImagePath;
    String originalImagePath;
    String type;
    String userEditedImagePath;
    WallpaperStyle wallpaperStyle;

    public static final Creator<WallpaperInfo> CREATOR = new Creator<WallpaperInfo>() {
        @Override
        public WallpaperInfo createFromParcel(Parcel parcel) {
            return new WallpaperInfo(parcel);
        }

        @Override
        public WallpaperInfo[] newArray(int size) {
            return new WallpaperInfo[size];
        }
    };

    public WallpaperInfo() {
        this.type = LAUNCHER_SYSTEM_RESOURCE_PREFIX;
        this.id = "";
        this.imagePath = "";
        this.dayImagePath = "";
        this.nightImagePath = "";
        this.fileType = FILE_LOCATION_2D;
        this.wallpaperStyle = WallpaperStyle.IMAGE;
        this.coverImagePath = "";
        this.userEditedImagePath = "";
        this.originalImagePath = "";
    }

    public WallpaperInfo(String type, String id, String imagePath) {
        this();
        this.type = type != null ? type : LAUNCHER_SYSTEM_RESOURCE_PREFIX;
        this.id = id != null ? id : "";
        this.imagePath = imagePath != null ? imagePath : "";
        setOriginalImagePath(this.imagePath);
    }

    protected WallpaperInfo(Parcel parcel) {
        readFromParcel(parcel);
    }

    public static WallpaperInfo create() {
        return new WallpaperInfo();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(this.type);
        parcel.writeString(this.id);
        parcel.writeString(this.imagePath);
        parcel.writeString(this.dayImagePath);
        parcel.writeString(this.nightImagePath);
        parcel.writeString(this.fileType);
        parcel.writeInt((this.wallpaperStyle != null ? this.wallpaperStyle : WallpaperStyle.IMAGE).ordinal());
        parcel.writeString(this.coverImagePath);
        parcel.writeString(this.userEditedImagePath);
        parcel.writeString(this.originalImagePath);
    }

    public void readFromParcel(Parcel parcel) {
        this.type = safeStr(parcel.readString(), LAUNCHER_SYSTEM_RESOURCE_PREFIX);
        this.id = safeStr(parcel.readString(), "");
        this.imagePath = safeStr(parcel.readString(), "");
        this.dayImagePath = safeStr(parcel.readString(), "");
        this.nightImagePath = safeStr(parcel.readString(), "");
        this.fileType = safeStr(parcel.readString(), FILE_LOCATION_2D);
        int ordinal = parcel.readInt();
        WallpaperStyle[] values = WallpaperStyle.values();
        this.wallpaperStyle = (ordinal >= 0 && ordinal < values.length) ? values[ordinal] : WallpaperStyle.IMAGE;
        this.coverImagePath = safeStr(parcel.readString(), "");
        this.userEditedImagePath = safeStr(parcel.readString(), "");
        this.originalImagePath = safeStr(parcel.readString(), "");
    }

    private static String safeStr(String value, String fallback) {
        return value != null ? value : fallback;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof WallpaperInfo)) {
            return false;
        }
        WallpaperInfo other = (WallpaperInfo) obj;
        return TextUtils.equals(other.id, this.id)
                && TextUtils.equals(other.imagePath, this.imagePath)
                && this.wallpaperStyle == other.wallpaperStyle
                && TextUtils.equals(other.userEditedImagePath, this.userEditedImagePath);
    }

    public String getCoverImagePath() {
        return coverImagePath;
    }

    public void setCoverImagePath(String coverImagePath) {
        this.coverImagePath = safeStr(coverImagePath, "");
    }

    public String getDayImagePath() {
        return dayImagePath;
    }

    public void setDayImagePath(String dayImagePath) {
        this.dayImagePath = safeStr(dayImagePath, "");
    }

    public WallpaperInfo setDayNightImagePath(String dayPath, String nightPath) {
        this.dayImagePath = safeStr(dayPath, "");
        this.nightImagePath = safeStr(nightPath, "");
        return this;
    }

    public String getFileType() {
        return fileType;
    }

    public WallpaperInfo setFileType(String fileType) {
        this.fileType = safeStr(fileType, FILE_LOCATION_2D);
        return this;
    }

    public String getId() {
        return id;
    }

    public WallpaperInfo setId(String id) {
        this.id = safeStr(id, "");
        return this;
    }

    public String getImagePath() {
        return imagePath;
    }

    public WallpaperInfo setImagePath(String imagePath) {
        this.imagePath = safeStr(imagePath, "");
        return this;
    }

    public String getNightImagePath() {
        return nightImagePath;
    }

    public void setNightImagePath(String nightImagePath) {
        this.nightImagePath = safeStr(nightImagePath, "");
    }

    public String getOriginalImagePath() {
        return originalImagePath;
    }

    public void setOriginalImagePath(String originalImagePath) {
        if (KEY_USER_WALLPAPER.equals(this.type)) {
            this.originalImagePath = safeStr(originalImagePath, "");
        }
    }

    public String getType() {
        return type;
    }

    public WallpaperInfo setType(String type) {
        this.type = safeStr(type, LAUNCHER_SYSTEM_RESOURCE_PREFIX);
        return this;
    }

    public String getUserEditedImagePath() {
        return userEditedImagePath;
    }

    public void setUserEditedImagePath(String userEditedImagePath) {
        this.userEditedImagePath = safeStr(userEditedImagePath, "");
    }

    public WallpaperStyle getWallpaperStyle() {
        return wallpaperStyle;
    }

    public WallpaperInfo setWallpaperStyle(WallpaperStyle wallpaperStyle) {
        this.wallpaperStyle = wallpaperStyle != null ? wallpaperStyle : WallpaperStyle.IMAGE;
        return this;
    }

    public String toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("type", safeStr(type, KEY_USER_WALLPAPER));
            obj.put("id", safeStr(id, ""));
            obj.put("imagePath", safeStr(imagePath, ""));
            obj.put("dayImagePath", safeStr(dayImagePath, ""));
            obj.put("nightImagePath", safeStr(nightImagePath, ""));
            obj.put("fileType", safeStr(fileType, FILE_LOCATION_2D));
            obj.put("wallpaperStyle", (wallpaperStyle != null ? wallpaperStyle : WallpaperStyle.IMAGE).name());
            obj.put("coverImagePath", safeStr(coverImagePath, ""));
            obj.put("userEditedImagePath", safeStr(userEditedImagePath, ""));
            obj.put("originalImagePath", safeStr(originalImagePath, ""));
        } catch (JSONException ignored) {
        }
        return obj.toString();
    }

    @Override
    public String toString() {
        return "WallpaperInfo{type='" + type + "', id='" + id + "', imagePath='" + imagePath
                + "', style=" + wallpaperStyle + "}";
    }
}
