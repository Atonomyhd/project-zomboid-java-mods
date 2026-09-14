package com.sentientsimulations.projectzomboid.extralogging.animal;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import zombie.characters.animals.IsoAnimal;

/**
 * Records, per animal, the call path that first took its health from above zero to zero or below.
 * Every vanilla animal health drain goes through the {@code IsoAnimal.setHealth(float)} override:
 * weapon hits, animal fights, starvation, thirst, old age, milk overfill, the wild drop-dead timer,
 * vehicle crashes, the hutch predator, dead-at-birth babies, world-gen ranches that spawn their
 * stock already dead, and save-file loads of animals that died before their chunk unloaded. The
 * death event fires later, from the state machine, by which time the caller that zeroed the health
 * is long gone. The death handler reads the stored {@link Record} to attribute the death.
 *
 * <p>Only the first fatal transition is kept; later {@code setHealth(0)} calls on an already-dead
 * animal (for example {@code Kill()} inside {@code die()}) do not overwrite it. The map is
 * weak-keyed, so entries vanish with the animal.
 *
 * <p>Methods take {@code Object} and cast internally so the inlined advice does not encode game
 * class checkcasts at the call site.
 */
public final class AnimalFatalHealthDrop {

    /**
     * @param healthBefore health before the fatal call
     * @param healthAfter health the fatal call left behind
     * @param hunger hunger at the moment of the drop, before any later feeding step
     * @param thirst thirst at the moment of the drop, before any later watering step
     * @param path caller frames nearest first, {@code Class.method:line} joined by {@code " <- "}
     */
    public record Record(
            float healthBefore, float healthAfter, float hunger, float thirst, String path) {

        /** The nearest caller frame, {@code Class.method} without the line number. */
        public String site() {
            int end = path.indexOf(" <- ");
            String first = end < 0 ? path : path.substring(0, end);
            int colon = first.lastIndexOf(':');
            return colon < 0 ? first : first.substring(0, colon);
        }

        /** Whether the drop happened inside {@code IsoAnimal.updateStatsAway} (meta catch-up). */
        public boolean duringMetaCatchUp() {
            return path.contains("IsoAnimal.updateStatsAway");
        }
    }

    public static final int MAX_FRAMES = 12;

    private static final String SELF = AnimalFatalHealthDrop.class.getName();
    private static final String SET_HEALTH_OWNER = IsoAnimal.class.getName();

    private static final Map<IsoAnimal, Record> DROPS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private AnimalFatalHealthDrop() {}

    /** Health at {@code setHealth} entry, threaded to {@link #afterSet} through the advice. */
    public static float healthBefore(Object animalObj) {
        return animalObj instanceof IsoAnimal animal ? animal.getHealth() : Float.NaN;
    }

    /** Records the drop when {@code before > 0} and the animal's health is now {@code <= 0}. */
    public static void afterSet(Object animalObj, float before) {
        if (!(animalObj instanceof IsoAnimal animal) || !(before > 0.0F)) {
            return;
        }
        float after = animal.getHealth();
        if (after > 0.0F) {
            return;
        }
        DROPS.putIfAbsent(
                animal,
                new Record(before, after, animal.getHunger(), animal.getThirst(), capturePath()));
    }

    /** The recorded fatal drop, or {@code null} when none was seen for this animal. */
    public static Record get(Object animalObj) {
        return animalObj instanceof IsoAnimal animal ? DROPS.get(animal) : null;
    }

    /** Forgets the animal's record; a revived animal can then record a fresh fatal drop. */
    public static void clear(Object animalObj) {
        if (animalObj instanceof IsoAnimal animal) {
            DROPS.remove(animal);
        }
    }

    public static String capturePath() {
        return StackWalker.getInstance()
                .walk(frames -> describe(frames.dropWhile(AnimalFatalHealthDrop::isSeamFrame)));
    }

    private static String describe(Stream<StackWalker.StackFrame> callers) {
        List<String> parts =
                callers.limit(MAX_FRAMES)
                        .map(AnimalFatalHealthDrop::describe)
                        .collect(Collectors.toList());
        return parts.isEmpty() ? "none" : String.join(" <- ", parts);
    }

    private static boolean isSeamFrame(StackWalker.StackFrame frame) {
        String cls = frame.getClassName();
        return cls.equals(SELF)
                || (cls.equals(SET_HEALTH_OWNER) && frame.getMethodName().equals("setHealth"));
    }

    private static String describe(StackWalker.StackFrame frame) {
        String cls = frame.getClassName();
        String simple = cls.substring(cls.lastIndexOf('.') + 1);
        int line = frame.getLineNumber();
        return line > 0
                ? simple + "." + frame.getMethodName() + ":" + line
                : simple + "." + frame.getMethodName();
    }
}
