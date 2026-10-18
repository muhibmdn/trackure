package com.trackure.app.core.database

import androidx.room.TypeConverter
import com.trackure.app.domain.model.EndConditionType
import com.trackure.app.domain.model.RecurrenceFreq

class Converters {
    @TypeConverter fun setToString(value: Set<Int>): String = value.sorted().joinToString(",")
    @TypeConverter fun stringToSet(value: String): Set<Int> =
        if (value.isBlank()) emptySet() else value.split(',').map { it.trim().toInt() }.toSet()
    @TypeConverter fun frequencyToString(value: RecurrenceFreq): String = value.name
    @TypeConverter fun stringToFrequency(value: String): RecurrenceFreq = RecurrenceFreq.valueOf(value)
    @TypeConverter fun endConditionToString(value: EndConditionType): String = value.name
    @TypeConverter fun stringToEndCondition(value: String): EndConditionType = EndConditionType.valueOf(value)
}
