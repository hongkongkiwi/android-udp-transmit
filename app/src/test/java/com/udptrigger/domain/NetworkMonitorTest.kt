package com.udptrigger.domain

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher

/**
 * Unit tests for NetworkMonitor.
 * Tests network state monitoring and availability flow.
 */
class NetworkMonitorTest {

    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var mockContext: Context
    private lateinit var mockConnectivityManager: ConnectivityManager
    private lateinit var mockNetworkCapabilities: NetworkCapabilities
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        mockContext = mockk()
        mockConnectivityManager = mockk()
        mockNetworkCapabilities = mockk()

        every { mockContext.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockConnectivityManager

        // Mock network has capabilities by default
        every { mockNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { mockNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true

        // Mock registerNetworkCallback with specific types
        every {
            mockConnectivityManager.registerNetworkCallback(
                any<NetworkRequest>(),
                any<ConnectivityManager.NetworkCallback>()
            )
        } just Runs

        // Mock unregisterNetworkCallback with specific type
        every {
            mockConnectivityManager.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>())
        } just Runs

        networkMonitor = NetworkMonitor(mockContext)
    }

    @After
    fun tearDown() {
        // Note: NetworkMonitor doesn't have a release method in the current implementation
        // The callback is managed by the callbackFlow lifetime
        unmockkAll()
    }

    @Test
    fun `network monitor creates successfully`() {
        assertNotNull(networkMonitor)
        assertNotNull(networkMonitor.isNetworkAvailableFlow)
    }

    @Test
    fun `isNetworkAvailableFlow is not null`() {
        assertNotNull(networkMonitor.isNetworkAvailableFlow)
    }
}
