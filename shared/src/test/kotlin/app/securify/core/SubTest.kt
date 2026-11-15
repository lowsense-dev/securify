package app.securify.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubTest {
    @Test
    fun customNameSurvivesEncodeDecodeAndWinsOverPanelName() {
        val sub = Sub("id", "https://ex/sub", "panel", emptyList(), customName = "mine")
        val back = decode(encode(AppState(subs = listOf(sub)))).subs.single()
        assertEquals("mine", back.customName)
        assertEquals("mine", back.title)
        assertEquals("panel", back.name)
    }

    @Test
    fun titleFallsBackToPanelNameAndIsEmptyForManualGroup() {
        assertEquals("panel", Sub("id", "https://ex/sub", "panel", emptyList()).title)
        assertEquals("", Sub(MANUAL_ID, "", "", emptyList()).title)
    }

    @Test
    fun stateWrittenBeforeCustomNameLoadsWithEmptyOne() {
        val old = JSONObject().put(
            "subs",
            JSONArray().put(
                JSONObject().put("id", "id").put("url", "https://ex/sub").put("name", "panel")
                    .put("entries", JSONArray()),
            ),
        )
        val sub = decode(old).subs.single()
        assertEquals("", sub.customName)
        assertEquals("panel", sub.title)
        assertNull(sub.hwid)
        assertNull(sub.agent)
    }

    @Test
    fun settingsOverridesSurviveRoundtrip() {
        val sub = Sub("id", "https://ex/sub", "panel", emptyList(), hwid = false, agent = SubAgent.HAPP)
        val back = decode(encode(AppState(subs = listOf(sub)))).subs.single()
        assertEquals(false, back.hwid)
        assertEquals(SubAgent.HAPP, back.agent)

        val plain = decode(encode(AppState(subs = listOf(Sub("i", "https://ex/s", "n", emptyList()))))).subs.single()
        assertNull(plain.hwid)
        assertNull(plain.agent)
    }
}
