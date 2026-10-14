package com.sentientsimulations.projectzomboid.survivorskillobelisk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import se.krka.kahlua.j2se.KahluaTableImpl;
import se.krka.kahlua.vm.KahluaTable;

/** Round trips and edge cases for the hand-rolled Kahlua ↔ JSON codec. */
class LuaTableJsonTest {

    private static final Supplier<KahluaTable> NEW_TABLE =
            () -> new KahluaTableImpl(new HashMap<>());

    private static KahluaTable table() {
        return NEW_TABLE.get();
    }

    private static KahluaTable array(Object... values) {
        KahluaTable t = table();
        for (int i = 0; i < values.length; i++) {
            t.rawset((double) (i + 1), values[i]);
        }
        return t;
    }

    @Test
    void encodesSortedObjectsAndContiguousArrays() {
        KahluaTable t = table();
        t.rawset("name", "NeuralHat");
        t.rawset("level", 7.0);
        t.rawset("ratio", 0.25);
        t.rawset("broken", Boolean.FALSE);
        t.rawset("durability", array(40.0, 45.0));
        t.rawset("overdrive", array(Boolean.FALSE, 0.0, 0.0));

        assertEquals(
                "{\"broken\":false,\"durability\":[40,45],\"level\":7,\"name\":\"NeuralHat\","
                        + "\"overdrive\":[false,0,0],\"ratio\":0.25}",
                LuaTableJson.encode(t));
    }

    @Test
    void roundTripsNestedInventionShape() {
        KahluaTable improvement = table();
        improvement.rawset("efficiency", array(2.0, 9.0));
        improvement.rawset("safe", array(0.0, 1.0));
        KahluaTable inventionData = table();
        inventionData.rawset("efficiencyMult", array(0.2, 0.4, 0.1));
        inventionData.rawset("fuelItem", "Battery");
        inventionData.rawset("running", Boolean.FALSE);
        KahluaTable entry = table();
        entry.rawset("invName", "NeuralHat");
        entry.rawset("improvementData", improvement);
        entry.rawset("inventionData", inventionData);
        entry.rawset("numCreated", 3.0);
        KahluaTable invData = table();
        invData.rawset("NeuralHat", entry);
        invData.rawset("Forgotten", Boolean.FALSE);
        invData.rawset("specialMax", 2.0);
        invData.rawset("specialMax_lvl10", Boolean.TRUE);

        KahluaTable back = LuaTableJson.decode(LuaTableJson.encode(invData), NEW_TABLE);

        assertEquals(2.0, back.rawget("specialMax"));
        assertEquals(Boolean.TRUE, back.rawget("specialMax_lvl10"));
        assertEquals(Boolean.FALSE, back.rawget("Forgotten"));
        KahluaTable backEntry = (KahluaTable) back.rawget("NeuralHat");
        assertEquals("NeuralHat", backEntry.rawget("invName"));
        assertEquals(3.0, backEntry.rawget("numCreated"));
        KahluaTable backImprovement = (KahluaTable) backEntry.rawget("improvementData");
        assertEquals(2.0, ((KahluaTable) backImprovement.rawget("efficiency")).rawget(1.0));
        assertEquals(9.0, ((KahluaTable) backImprovement.rawget("efficiency")).rawget(2.0));
        KahluaTable backInvData = (KahluaTable) backEntry.rawget("inventionData");
        assertEquals(0.4, ((KahluaTable) backInvData.rawget("efficiencyMult")).rawget(2.0));
        assertEquals("Battery", backInvData.rawget("fuelItem"));
        assertEquals(Boolean.FALSE, backInvData.rawget("running"));
        assertEquals(LuaTableJson.encode(invData), LuaTableJson.encode(back));
    }

    @Test
    void sparseNumericKeysSurviveAsNumbers() {
        KahluaTable t = table();
        t.rawset(1.0, "a");
        t.rawset(3.0, "c");

        String json = LuaTableJson.encode(t);
        KahluaTable back = LuaTableJson.decode(json, NEW_TABLE);

        assertEquals("{\"1\":\"a\",\"3\":\"c\"}", json);
        assertEquals("a", back.rawget(1.0));
        assertEquals("c", back.rawget(3.0));
        assertNull(back.rawget("3"));
    }

    @Test
    void emptyTableRoundTrips() {
        KahluaTable back = LuaTableJson.decode(LuaTableJson.encode(table()), NEW_TABLE);
        assertEquals("{}", LuaTableJson.encode(table()));
        assertTrue(back.isEmpty());
    }

    @Test
    void stringEscapesRoundTrip() {
        KahluaTable t = table();
        String tricky = "quote\" backslash\\ newline\n tab\t ctrl\u0001 unicodeé";
        t.rawset("s", tricky);

        KahluaTable back = LuaTableJson.decode(LuaTableJson.encode(t), NEW_TABLE);

        assertEquals(tricky, back.rawget("s"));
    }

    @Test
    void dropsValuesJsonCannotCarry() {
        KahluaTable t = table();
        t.rawset("nan", Double.NaN);
        t.rawset("inf", Double.POSITIVE_INFINITY);
        t.rawset("object", new Object());
        t.rawset("kept", 1.0);
        t.rawset(Boolean.TRUE, "boolean key");

        assertEquals("{\"kept\":1}", LuaTableJson.encode(t));
    }

    @Test
    void nullArrayElementsBecomeHoles() {
        KahluaTable back = LuaTableJson.decode("[1,null,3]", NEW_TABLE);

        assertEquals(1.0, back.rawget(1.0));
        assertNull(back.rawget(2.0));
        assertEquals(3.0, back.rawget(3.0));
    }

    @Test
    void decodeRejectsMalformedAndNonTableRoots() {
        assertNull(LuaTableJson.decode(null, NEW_TABLE));
        assertNull(LuaTableJson.decode("", NEW_TABLE));
        assertNull(LuaTableJson.decode("5", NEW_TABLE));
        assertNull(LuaTableJson.decode("\"text\"", NEW_TABLE));
        assertNull(LuaTableJson.decode("{\"a\":}", NEW_TABLE));
        assertNull(LuaTableJson.decode("{\"a\":1", NEW_TABLE));
        assertNull(LuaTableJson.decode("{\"a\":1} trailing", NEW_TABLE));
        assertNull(LuaTableJson.decode("[1,2,]", NEW_TABLE));
    }

    @Test
    void decodeToleratesWhitespace() {
        KahluaTable back = LuaTableJson.decode(" { \"a\" : [ 1 , 2 ] , \"b\" : { } } ", NEW_TABLE);

        assertEquals(2.0, ((KahluaTable) back.rawget("a")).rawget(2.0));
        assertTrue(((KahluaTable) back.rawget("b")).isEmpty());
    }
}
