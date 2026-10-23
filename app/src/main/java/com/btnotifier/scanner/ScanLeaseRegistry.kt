package com.btnotifier.scanner

/** All callers serialize access through BluetoothScanner's lock. */
class ScanLeaseRegistry {
    private var nextToken = 0L
    private var bleGeneration = 0L
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
    fun restartBleGeneration(): Long = ++bleGeneration
    fun acceptsBleGeneration(generation: Long): Boolean = generation == bleGeneration && hasInterest()
}
