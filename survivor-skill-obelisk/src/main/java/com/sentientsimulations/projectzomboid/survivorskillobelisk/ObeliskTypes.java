package com.sentientsimulations.projectzomboid.survivorskillobelisk;

/**
 * Obelisk type ids that are not perk names. Everything else stored in {@code obelisk_types.type} is
 * either {@link #NONE} or a {@code PerkFactory.Perks} id.
 */
public final class ObeliskTypes {

    public static final String NONE = "None";

    /** Grants a timed all-skill XP bonus instead of skill recovery. */
    public static final String BLESSING = "Blessing";

    private ObeliskTypes() {}
}
