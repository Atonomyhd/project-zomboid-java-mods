package com.sentientsimulations.projectzomboid.extralogging.patch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sentientsimulations.projectzomboid.extralogging.animal.AnimalFatalHealthDrop;
import java.io.InputStream;
import java.lang.reflect.Field;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;
import zombie.characters.CharacterStat;
import zombie.characters.Stats;
import zombie.characters.animals.IsoAnimal;

/**
 * Weaves {@link IsoAnimalPatch} against the real {@code IsoAnimal} bytes and exercises the recorder
 * on an {@code Unsafe}-allocated animal, so no game world is needed.
 */
class IsoAnimalPatchTest {

    private static final String HELPER_CLASS =
            AnimalFatalHealthDrop.class.getName().replace('.', '/');

    @Test
    void patchWeavesSetHealthOnly() throws Exception {
        IsoAnimalPatch patch = new IsoAnimalPatch();
        byte[] rawClass = readClassBytes(patch.getClassName());
        byte[] transformed = patch.transform(rawClass);
        assertNotNull(transformed);

        assertEquals(0, countCalls(rawClass, "setHealth", null));
        assertTrue(
                countCalls(transformed, "setHealth", "healthBefore") >= 1,
                "setHealth must read the pre-call health through " + HELPER_CLASS);
        assertTrue(
                countCalls(transformed, "setHealth", "afterSet") >= 1,
                "setHealth must hand the after-call health to " + HELPER_CLASS);
        assertEquals(0, countCalls(transformed, "hitConsequences", null));
        assertEquals(0, countCalls(transformed, "update", null));
    }

    @Test
    void recordsOnlyTheFirstFatalTransition() throws Exception {
        IsoAnimal animal = bareAnimal(0.7F, 0.9F);
        AnimalFatalHealthDrop.clear(animal);

        setHealthField(animal, 0.4F);
        AnimalFatalHealthDrop.afterSet(animal, 1.0F);
        assertNull(AnimalFatalHealthDrop.get(animal), "a non-fatal change must not record");

        setHealthField(animal, 0.0F);
        AnimalFatalHealthDrop.afterSet(animal, 0.4F);
        AnimalFatalHealthDrop.Record first = AnimalFatalHealthDrop.get(animal);
        assertNotNull(first);
        assertEquals(0.4F, first.healthBefore());
        assertEquals(0.0F, first.healthAfter());
        assertEquals(0.7F, first.hunger());
        assertEquals(0.9F, first.thirst());
        assertTrue(first.path().startsWith("IsoAnimalPatchTest."), first.path());
        assertEquals("IsoAnimalPatchTest.recordsOnlyTheFirstFatalTransition", first.site());
        assertFalse(first.duringMetaCatchUp());

        // Kill() inside die() calls setHealth(0) again on an already-dead animal.
        AnimalFatalHealthDrop.afterSet(animal, 0.0F);
        assertEquals(first, AnimalFatalHealthDrop.get(animal));

        AnimalFatalHealthDrop.clear(animal);
        assertNull(AnimalFatalHealthDrop.get(animal));
    }

    @Test
    void nonAnimalsAndNonFatalCallsAreIgnored() {
        assertTrue(Float.isNaN(AnimalFatalHealthDrop.healthBefore(new Object())));
        AnimalFatalHealthDrop.afterSet(new Object(), 1.0F);
        assertNull(AnimalFatalHealthDrop.get(new Object()));
    }

    @Test
    void pathDescribesCallersNearestFirstAndCapsDepth() {
        String path = AnimalFatalHealthDrop.capturePath();
        assertTrue(path.startsWith("IsoAnimalPatchTest.pathDescribesCallersNearestFirst"), path);
        assertTrue(path.split(" <- ").length <= AnimalFatalHealthDrop.MAX_FRAMES, path);

        AnimalFatalHealthDrop.Record meta =
                new AnimalFatalHealthDrop.Record(
                        0.01F,
                        -0.02F,
                        0.95F,
                        0.1F,
                        "AnimalData.updateHealth:380 <- AnimalData.hourGrow:401"
                                + " <- IsoAnimal.updateStatsAway:1723");
        assertEquals("AnimalData.updateHealth", meta.site());
        assertTrue(meta.duringMetaCatchUp());
    }

    private static IsoAnimal bareAnimal(float hunger, float thirst) throws Exception {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Unsafe unsafe = (Unsafe) f.get(null);
        IsoAnimal animal = (IsoAnimal) unsafe.allocateInstance(IsoAnimal.class);
        Stats stats = new Stats();
        stats.set(CharacterStat.HUNGER, hunger);
        stats.set(CharacterStat.THIRST, thirst);
        Field statsField = findField(IsoAnimal.class, "stats");
        statsField.setAccessible(true);
        statsField.set(animal, stats);
        return animal;
    }

    private static void setHealthField(IsoAnimal animal, float health) throws Exception {
        Field healthField = findField(IsoAnimal.class, "health");
        healthField.setAccessible(true);
        healthField.setFloat(animal, health);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // keep climbing
            }
        }
        throw new NoSuchFieldException(name + " on " + type);
    }

    private static byte[] readClassBytes(String className) throws Exception {
        String resource = "/" + className.replace('.', '/') + ".class";
        try (InputStream is = IsoAnimalPatchTest.class.getResourceAsStream(resource)) {
            assertNotNull(is, resource + " must be on the test classpath");
            return is.readAllBytes();
        }
    }

    private static int countCalls(byte[] classBytes, String method, String calledName) {
        int[] hits = new int[1];
        new ClassReader(classBytes)
                .accept(
                        new ClassVisitor(Opcodes.ASM9) {
                            @Override
                            public MethodVisitor visitMethod(
                                    int access,
                                    String name,
                                    String descriptor,
                                    String signature,
                                    String[] exceptions) {
                                if (!method.equals(name)) {
                                    return null;
                                }
                                return new MethodVisitor(Opcodes.ASM9) {
                                    @Override
                                    public void visitMethodInsn(
                                            int opcode,
                                            String owner,
                                            String mName,
                                            String mDesc,
                                            boolean isInterface) {
                                        if (HELPER_CLASS.equals(owner)
                                                && (calledName == null
                                                        || calledName.equals(mName))) {
                                            hits[0]++;
                                        }
                                    }
                                };
                            }
                        },
                        0);
        return hits[0];
    }
}
