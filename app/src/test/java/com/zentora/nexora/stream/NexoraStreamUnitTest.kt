/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream

import com.zentora.nexora.stream.data.database.entities.HistoryEntity
import com.zentora.nexora.stream.data.database.entities.InteractionEntity
import com.zentora.nexora.stream.data.database.entities.VideoEntity
import com.zentora.nexora.stream.engine.*
import org.junit.Assert.*
import org.junit.Test

class NexoraStreamUnitTest {

    @Test
    fun testCommunityShieldFiltersBlacklistedContent() {
        val safeText = "Incredible 4K rendering by Zentora CLC!"
        assertTrue("Safe text should pass shield", CommunityShield.isContentSafe(safeText))

        val spamText = "Click here for free money and crypto giveaway!"
        assertFalse("Spam text must be flagged by shield", CommunityShield.isContentSafe(spamText))

        val sanitized = CommunityShield.filterText("free money")
        assertTrue("Blacklisted term must be sanitized", sanitized.contains("***"))
    }

    @Test
    fun testNexoraDrmUriMasking() {
        val physicalUri = "file:///storage/emulated/0/Movies/nexora_render.mp4"
        val masked = NexoraDrm.maskUri(physicalUri)

        assertTrue("Masked URI should use nexora drm scheme", masked.startsWith("nexora://drm-secure/"))
        val resolved = NexoraDrm.resolveUri(masked)
        assertEquals("Resolved URI must match original physical URI", physicalUri, resolved)
    }

    @Test
    fun testSemanticSearchMultiTermRanking() {
        val videos = listOf(
            VideoEntity("1", "uri1", "Tears of Steel Sci-Fi VFX", "CGI demo", "Tech", "VFX, CGI", 1000L, 60000L),
            VideoEntity("2", "uri2", "Mountain Biking Alps Extreme", "Alpine trail", "Sports", "MTB, GoPro", 1000L, 60000L),
            VideoEntity("3", "uri3", "Big Buck Bunny 3D Animation", "Forest story", "Animation", "3D, Cartoon", 1000L, 60000L)
        )

        val results = NexoraSemanticSearch.search(videos, "VFX Tech")
        assertTrue("Search should match VFX video", results.isNotEmpty())
        assertEquals("Top result should be Tears of Steel", "1", results.first().id)
    }

    @Test
    fun testCreatorLevelCalculation() {
        assertEquals("Rising Creator", NexoraIdManager.calculateCreatorLevel(0L, 0L))
        assertEquals("Gold Creator", NexoraIdManager.calculateCreatorLevel(1_500L, 5_000L))
        assertEquals("Diamond Streamer", NexoraIdManager.calculateCreatorLevel(150_000L, 2_000_000L))
    }

    @Test
    fun testSmartRankAlgorithm() {
        val videos = listOf(
            VideoEntity("1", "uri1", "Video 1", "Desc", "Tech", "tags", System.currentTimeMillis(), 60000L, 10L),
            VideoEntity("2", "uri2", "Video 2", "Desc", "Sports", "tags", System.currentTimeMillis() - 10000000L, 60000L, 2L)
        )
        val history = listOf(HistoryEntity("1", 50000L, System.currentTimeMillis(), 0.85f))
        val interactions = mapOf("1" to InteractionEntity("1", likesCount = 5L))

        val ranked = NexoraSmartRank.rankVideos(videos, interactions, history)
        assertEquals(2, ranked.size)
        assertEquals("Video with higher retention & affinity should rank first", "1", ranked.first().id)
    }

    @Test
    fun testNcpNetworkClientConfiguration() {
        assertNotNull("API service instance must be initialized", NexoraNetworkClient.apiService)
        assertTrue("Base URL should point to Zentora service", NexoraNetworkClient.getBaseUrl().contains("zentora"))

        val testUrl = "https://custom.zentora.cloud/api/"
        NexoraNetworkClient.updateBaseUrl(testUrl)
        assertEquals("Updated URL should match", testUrl, NexoraNetworkClient.getBaseUrl())

        // Revert back to default
        NexoraNetworkClient.updateBaseUrl("https://stream.zentora.internal/")
    }

    @Test
    fun testNcpProtocolDtoIntegrity() {
        val publishReq = PublishVideoRequest(
            videoId = "vid_ncp_test",
            title = "NCP Test Stream",
            description = "Dual sync validation",
            category = "Tech",
            tags = "NCP, Vault",
            mediaUri = "nexora://drm-secure/123",
            thumbnailUri = "https://example.com/thumb.jpg",
            duration = 120000L,
            authorChannelId = "ch_zentora_core",
            nexoraAuthToken = "ZT-TEST"
        )
        assertEquals("vid_ncp_test", publishReq.videoId)

        val interactReq = InteractRequest(
            videoId = "vid_ncp_test",
            viewDelta = 1L,
            likeDelta = 1L,
            sparksDelta = 10L,
            nexoraId = "NX-TEST"
        )
        assertEquals(1L, interactReq.viewDelta)
        assertEquals(10L, interactReq.sparksDelta)
    }

    @Test
    fun testVault4BlobstoreEndpoint() {
        assertEquals(
            "https://nexora-backend-fq54.onrender.com/api/v1/videos/upload-stream",
            NexoraNetworkClient.VAULT_4_BLOBSTORE_URL
        )
    }
}
