package com.ennam.app.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromByteArray(value: ByteArray?): ByteArray? = value
}
