package tld.my.simpletv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    private val release = """{"tag_name":"v1.3.0","html_url":"https://github.com/fardroid/AndroidTV_Simple_Launcher/releases/tag/v1.3.0",
        "draft":false,"prerelease":false,"body":"## What's new\r\n\r\n- **News** card, see [the page](https://x.example/p)\r\n- Quote of the day"}"""

    @Test fun readsALatestRelease() {
        val u = Updates.parse(release)!!
        assertEquals("1.3.0", u.version)
        assertEquals("https://github.com/fardroid/AndroidTV_Simple_Launcher/releases/tag/v1.3.0", u.url)
        assertEquals("What's new\n• News card, see the page\n• Quote of the day", u.notes)
    }

    @Test fun ignoresWhatIsNotARelease() {
        assertNull(Updates.parse("{}"))
        assertNull(Updates.parse("not json"))
        assertNull(Updates.parse(release.replace("\"prerelease\":false", "\"prerelease\":true")))
        assertNull(Updates.parse(release.replace("https://github.com/", "https://evil.example/")))
    }

    @Test fun findsTheApkAndItsChecksum() {
        val sha = "0d87e718f4ffcc47298d69207a8d167ba89a99cd58cb6bfe07a5829b8dc41b4e"
        val withApk = release.replace("\"draft\":false", "\"assets\":[{\"name\":\"notes.txt\",\"digest\":\"sha256:aa\",\"browser_download_url\":\"https://github.com/x/notes.txt\"}," +
            "{\"name\":\"SimpleTVLauncher-1.3.0.apk\",\"size\":27951498,\"digest\":\"sha256:${sha.uppercase()}\",\"browser_download_url\":\"https://github.com/fardroid/AndroidTV_Simple_Launcher/releases/download/v1.3.0/SimpleTVLauncher-1.3.0.apk\"}],\"draft\":false")
        val u = Updates.parse(withApk)!!
        assertEquals("https://github.com/fardroid/AndroidTV_Simple_Launcher/releases/download/v1.3.0/SimpleTVLauncher-1.3.0.apk", u.apk)
        assertEquals(27951498L, u.apkSize)
        assertEquals(sha, u.sha256)
        assertEquals(u, Updates.fromJson(Updates.toJson(u, 5L))!!.second)
        // an APK without a checksum, or from elsewhere, is not offered for installing
        assertEquals("", Updates.parse(withApk.replace("sha256:${sha.uppercase()}", ""))!!.apk)
        assertEquals("", Updates.parse(release)!!.apk)
    }

    @Test fun comparesVersions() {
        assertTrue(Updates.newer("1.2.0", "1.1.0")); assertTrue(Updates.newer("1.10.0", "1.9.5")); assertTrue(Updates.newer("2.0", "1.9.9"))
        assertFalse(Updates.newer("1.1.0", "1.1.0")); assertFalse(Updates.newer("1.0.9", "1.1.0"))
        assertFalse(Updates.newer("1.1.0-rc1", "1.1.0"))      // anything that is not a number ends the comparison, as in the webOS version
        assertTrue(Updates.newer("1.1.1", "1.1"))
    }

    @Test fun theSavedResultRoundTrips() {
        val u = UpdateInfo("1.3.0", "https://github.com/x/y", "notes")
        assertEquals(5L to u, Updates.fromJson(Updates.toJson(u, 5)))
        assertEquals(7L to null, Updates.fromJson(Updates.toJson(null, 7)))
        assertNull(Updates.fromJson("garbage")); assertNull(Updates.fromJson(null))
    }
}
