package tld.my.simpletv

import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionTest {
    private val sha = "f374e4d2c6f035f9c4e1544873403957e2a7c279a8518b48f67cc8f8eeabf003"
    private fun photo(id: String, group: String = "mountains", url: String = "https://github.com/x/$id.jpg") =
        """{"id":"$id","group":"$group","title":"T $id","url":"$url","size":10,"sha256":"$sha","thumb":"https://github.com/x/$id-thumb.jpg","thumbSize":5,"thumbSha256":"$sha"}"""

    @Test fun readsTheListAsTheWebOsLauncherDoes() {
        val list = Collection.parse("""{"version":2,"photos":[${photo("m1")},${photo("n1", "nature")},${photo("m1")},${photo("bad id")},${photo("x1", "cats")},${photo("h1", url = "http://github.com/x.jpg")}]}""")
        assertEquals(listOf("m1", "n1"), list.map { it.id })
        assertEquals("T m1", list[0].title)
        assertEquals(emptyList<CollectionPhoto>(), Collection.parse("not json"))
        assertEquals("m1", Collection.idOf("collection:m1"))
    }
}
