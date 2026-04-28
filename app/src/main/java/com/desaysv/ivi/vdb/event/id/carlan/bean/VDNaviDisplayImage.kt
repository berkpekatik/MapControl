package com.desaysv.ivi.vdb.event.id.carlan.bean

import android.os.Parcel
import android.os.Parcelable
import com.desaysv.ivi.vdb.event.VDEvent
import com.desaysv.ivi.vdb.event.base.VDKey

/**
 * Option B: Yeni navigasyon resim event'i için bean sınıfı
 * Doğrudan resim verisi taşıyan yeni event tipi
 */
class VDNaviDisplayImage : Parcelable {
    
    var displayImage: String = ""           // Base64 encoded image data
    var imageWidth: Int = 0                 // Resim genişliği
    var imageHeight: Int = 0                // Resim yüksekliği
    var imageFormat: String = "PNG"         // Resim formatı (PNG, JPEG)
    var showOnCluster: Boolean = true       // Cluster'da göster
    var perspective: Int = 0                 // Perspektif
    
    constructor()
    
    constructor(parcel: Parcel) {
        displayImage = parcel.readString() ?: ""
        imageWidth = parcel.readInt()
        imageHeight = parcel.readInt()
        imageFormat = parcel.readString() ?: "PNG"
        showOnCluster = parcel.readByte() != 0.toByte()
        perspective = parcel.readInt()
    }
    
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(displayImage)
        parcel.writeInt(imageWidth)
        parcel.writeInt(imageHeight)
        parcel.writeString(imageFormat)
        parcel.writeByte(if (showOnCluster) 1 else 0)
        parcel.writeInt(perspective)
    }
    
    override fun describeContents(): Int {
        return 0
    }
    
    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<VDNaviDisplayImage> {
            override fun createFromParcel(parcel: Parcel): VDNaviDisplayImage {
                return VDNaviDisplayImage(parcel)
            }
            
            override fun newArray(size: Int): Array<VDNaviDisplayImage?> {
                return arrayOfNulls(size)
            }
        }
        
        /**
         * Bundle payload oluşturur
         */
        fun createPayload(image: VDNaviDisplayImage): android.os.Bundle {
            val bundle = android.os.Bundle()
            bundle.setClassLoader(VDNaviDisplayImage::class.java.classLoader)
            bundle.putParcelable(VDKey.DATA, image)
            // Bundle'ı marshalled/unmarshalled sınıfın bulabilmesi için classloader set et
            bundle.setClassLoader(VDNaviDisplayImage::class.java.classLoader)
            return bundle
        }
        
        /**
         * Event oluşturur
         */
        fun createEvent(eventId: Int, image: VDNaviDisplayImage): VDEvent {
            return VDEvent(eventId, createPayload(image))
        }
        
        /**
         * Event'ten veri alır
         */
        fun getValue(event: VDEvent?): VDNaviDisplayImage? {
            if (event == null || event.payload == null) {
                return null
            }
            event.payload.setClassLoader(VDNaviDisplayImage::class.java.classLoader)
            return event.payload.getParcelable(VDKey.DATA)
        }
        
        /**
         * Bitmap'den VDNaviDisplayImage oluşturur
         */
        fun fromBitmap(
            bitmap: android.graphics.Bitmap,
            showOnCluster: Boolean = true,
            format: android.graphics.Bitmap.CompressFormat = android.graphics.Bitmap.CompressFormat.PNG
        ): VDNaviDisplayImage {
            val outputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(format, 90, outputStream)
            val byteArray = outputStream.toByteArray()
            val base64Image = android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
            
            return VDNaviDisplayImage().apply {
                displayImage = base64Image
                imageWidth = bitmap.width
                imageHeight = bitmap.height
                imageFormat = if (format == android.graphics.Bitmap.CompressFormat.PNG) "PNG" else "JPEG"
                this.showOnCluster = showOnCluster
            }
        }
        
        /**
         * Test resmi oluşturur
         */
        fun createTestImage(
            width: Int = 300,
            height: Int = 200,
            color: Int = 0xFF85B6FF.toInt(),
            showOnCluster: Boolean = true
        ): VDNaviDisplayImage {
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            
            // Gradient test resmi
            for (x in 0 until width) {
                for (y in 0 until height) {
                    val alpha = ((x.toFloat() / width) * 255).toInt()
                    val red = (color shr 16) and 0xFF
                    val green = (color shr 8) and 0xFF
                    val blue = color and 0xFF
                    bitmap.setPixel(x, y, (alpha shl 24) or (red shl 16) or (green shl 8) or blue)
                }
            }
            
            return fromBitmap(bitmap, showOnCluster)
        }
    }
    
    override fun toString(): String {
        return "VDNaviDisplayImage{displayImage='${displayImage.take(50)}...', imageWidth=$imageWidth, imageHeight=$imageHeight, imageFormat='$imageFormat', showOnCluster=$showOnCluster, perspective=$perspective}"
    }
}
