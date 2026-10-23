package com.btnotifier.scanner

/** All callers serialize access through BluetoothScanner's lock. */
class ScanLeaseRegistry {
    private var nextToken = 0L
    private var bleGeneration = 0L
    private var bleRegistered = false
    private var bleFailed = false
    private var bleAddresses: Set<String>? = emptySet()
    private val leases = linkedMapOf<Long, Set<String>?>()

    fun acquire(addresses: Set<String>?): Long = (++nextToken).also {
        leases[it] = addresses?.map { address -> address.uppercase() }?.toSet()
    }
    fun release(token: Long) { leases.remove(token) }
    fun effectiveAddresses(): Set<String>? =
        if (leases.values.any { it == null }) null else leases.values.filterNotNull().flatten().toSet()
    fun hasInterest(): Boolean = leases.values.any { it == null || it.isNotEmpty() }
    fun accepts(token: Long, address: String): Boolean =
        leases.containsKey(token) && (leases[token] == null || address.uppercase() in leases[token].orEmpty())
    fun shouldStartBle(explicitAcquire: Boolean): Boolean = hasInterest() &&
        (bleAddresses != effectiveAddresses() || (!bleRegistered && (!bleFailed || explicitAcquire)))

    fun beginBleRegistration(addresses: Set<String>?): Long {
        bleAddresses = addresses?.toSet()
        bleRegistered = true
        bleFailed = false
        return ++bleGeneration
    }

    fun failBleGeneration(generation: Long): Boolean {
        if (!acceptsBleGeneration(generation)) return false
        bleRegistered = false
        bleFailed = true
        bleGeneration++
        return true
    }

    fun restartBleGeneration(): Long {
        bleRegistered = false
        bleFailed = false
        return ++bleGeneration
    }
    fun acceptsBleGeneration(generation: Long): Boolean = generation == bleGeneration && hasInterest()
}
