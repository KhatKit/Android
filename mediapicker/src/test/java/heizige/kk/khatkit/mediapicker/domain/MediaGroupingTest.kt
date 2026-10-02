package heizige.kk.khatkit.mediapicker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class MediaGroupingTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun media(
        id: Long,
        timestamp: Long,
        taken: Long? = null,
        mimeType: String = "image/jpeg",
        label: String = "IMG_$id.jpg",
    ) = Media(
        id = id,
        label = label,
        uri = "content://media/$id",
        path = "/storage/emulated/0/DCIM/$label",
        relativePath = "DCIM/",
        albumID = 1,
        albumLabel = "Camera",
        timestamp = timestamp,
        takenTimestamp = taken,
        mimeType = mimeType,
    )

    private fun epochOf(date: String): Long =
        LocalDateTime.parse(date).atZone(zone).toEpochSecond()

    @Test
    fun `dateGroup None keeps a flat list with no headers`() {
        val result = listOf(media(1, epochOf("2024-05-01T10:00")))
            .groupMedia(
                settings = MediaDisplaySettings(dateGroup = MediaDateGroup.None),
                zoneId = zone,
                dateLabel = { _, _ -> "unused" },
            )

        assertEquals(1, result.size)
        assertTrue(result.single() is MediaItem.MediaViewItem)
    }

    @Test
    fun `Day grouping emits one header per calendar day, newest first`() {
        val day1 = epochOf("2024-05-01T10:00")
        val day2 = epochOf("2024-05-02T10:00")
        val result = listOf(media(1, day1), media(2, day2)).groupMedia(
            settings = MediaDisplaySettings(dateGroup = MediaDateGroup.Day),
            zoneId = zone,
            dateLabel = { _, _ -> "label" },
        )

        assertEquals(4, result.size)
        assertEquals(listOf("header_DateModified", "media_2_IMG_2.jpg", "header_DateModified", "media_1_IMG_1.jpg"),
            result.map { it.key }.map { key -> if (key.startsWith("header_")) "header_DateModified" else key })
        // 每组头后面紧跟自己那一组的数据
        assertTrue(result[0] is MediaItem.Header)
        assertTrue(result[2] is MediaItem.Header)
    }

    @Test
    fun `Day grouping collapses same-day media under a single header`() {
        val morning = epochOf("2024-05-01T09:00")
        val evening = epochOf("2024-05-01T21:00")
        val result = listOf(media(1, morning), media(2, evening)).groupMedia(
            settings = MediaDisplaySettings(dateGroup = MediaDateGroup.Day),
            zoneId = zone,
            dateLabel = { _, _ -> "label" },
        )

        assertEquals(3, result.size)
        val header = result.first() as MediaItem.Header
        assertEquals(2, header.data.size)
    }

    @Test
    fun `Year grouping puts every month of a year in one header`() {
        val january = epochOf("2024-01-15T10:00")
        val december = epochOf("2024-12-15T10:00")
        val result = listOf(media(1, january), media(2, december)).groupMedia(
            settings = MediaDisplaySettings(dateGroup = MediaDateGroup.Year),
            zoneId = zone,
            dateLabel = { _, _ -> "label" },
        )

        assertEquals(3, result.size)
        assertEquals(2, (result.first() as MediaItem.Header).data.size)
    }

    @Test
    fun `groupOrder Ascending reverses the default newest-first order`() {
        val older = epochOf("2024-05-01T10:00")
        val newer = epochOf("2024-05-02T10:00")
        val result = listOf(media(1, older), media(2, newer)).groupMedia(
            settings = MediaDisplaySettings(
                dateGroup = MediaDateGroup.Day,
                groupOrder = OrderType.Ascending,
            ),
            zoneId = zone,
            dateLabel = { _, _ -> "label" },
        )

        assertEquals("media_1_IMG_1.jpg", result[1].key)
    }

    @Test
    fun `DateTaken grouping buckets by capture time, not by mtime`() {
        // 两条的修改时间完全相同，只有拍摄时间不同
        val modified = epochOf("2024-05-01T10:00")
        val result = listOf(
            media(1, modified, taken = epochOf("2024-05-01T09:00") * 1000),
            media(2, modified, taken = epochOf("2024-04-01T09:00") * 1000),
        ).groupMedia(
            settings = MediaDisplaySettings(grouping = MediaGrouping.DateTaken),
            zoneId = zone,
            dateLabel = { _, _ -> "label" },
        )

        // 按 taken 分成两组（而不是按 mtime 挤成一组）；默认分组顺序是降序，
        // 所以 5-01 那组在前
        assertEquals(4, result.size)
        assertEquals("header_DateTaken_2024-05-01", result[0].key)
        assertEquals("media_1_IMG_1.jpg", result[1].key)
        assertEquals("header_DateTaken_2024-04-01", result[2].key)
        assertEquals("media_2_IMG_2.jpg", result[3].key)
    }

    @Test
    fun `DateModified grouping collapses the same pair that DateTaken splits`() {
        val modified = epochOf("2024-05-01T10:00")
        val result = listOf(
            media(1, modified, taken = epochOf("2024-05-01T09:00") * 1000),
            media(2, modified, taken = epochOf("2024-04-01T09:00") * 1000),
        ).groupMedia(
            settings = MediaDisplaySettings(grouping = MediaGrouping.DateModified),
            zoneId = zone,
            dateLabel = { _, _ -> "label" },
        )

        assertEquals(3, result.size)
        assertEquals(2, (result[0] as MediaItem.Header).data.size)
    }

    @Test
    fun `Extension grouping is case and locale insensitive`() {
        val result = listOf(
            media(1, epochOf("2024-05-01T10:00"), mimeType = "image/jpeg", label = "A.JPEG"),
            media(2, epochOf("2024-05-01T11:00"), mimeType = "image/jpeg", label = "B.jpeg"),
            media(3, epochOf("2024-05-01T12:00"), mimeType = "image/png", label = "C.png"),
        ).groupMedia(
            settings = MediaDisplaySettings(grouping = MediaGrouping.Extension),
            zoneId = zone,
            dateLabel = { _, _ -> "label" },
        )

        // JPEG 大小写归到同一组，PNG 单独一组
        val headers = result.filterIsInstance<MediaItem.Header>()
        assertEquals(2, headers.size)
        assertEquals(setOf(1L, 2L), headers.first { it.data.any { m -> m.id == 1L } }.data.map { it.id }.toSet())
    }

    @Test
    fun `dateLabel receives the timestamp of the group's first item`() {
        val first = epochOf("2024-05-01T10:00")
        val seen = mutableListOf<Long>()
        listOf(media(1, first), media(2, first + 60)).groupMedia(
            settings = MediaDisplaySettings(dateGroup = MediaDateGroup.Day),
            zoneId = zone,
            dateLabel = { timestamp, _ -> seen.add(timestamp); "label" },
        )

        assertEquals(listOf(first), seen)
    }
}
