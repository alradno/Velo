package com.alberto.velo

/** Bridges the short interval between a screening decision and its asynchronous disk write. */
internal class RecentAttempts(private val capacity: Int = 256, private val lifetime: Long = 30 * 60_000L) {
    private val values = object : LinkedHashMap<String,Long>(capacity,.75f,true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String,Long>?) = size > capacity
    }
    @Synchronized fun record(number: String, time: Long) { if(number.isNotBlank()) values[number] = time }
    @Synchronized fun latest(number: String, now: Long): Long? {
        val time = values[number] ?: return null
        return if(now-time in 0..lifetime) time else { values.remove(number);null }
    }
}
