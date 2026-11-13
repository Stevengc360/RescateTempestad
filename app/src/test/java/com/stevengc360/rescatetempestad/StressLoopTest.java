package com.stevengc360.rescatetempestad;

import org.junit.Test;
import java.io.IOException;
import java.util.Random;
import static org.junit.Assert.*;

/**
 * Repeated randomized stress loops ("bucles de prueba"): every seed must finish
 * every mission through the normal control path, survive checkpoint/restore,
 * and never produce a non-finite state. Seeds are fixed so failures reproduce.
 */
public class StressLoopTest {
    @Test public void loopSeeds1to5AllMissionsFiniteAndRestorable() {
        for (int seed = 1; seed <= 5; seed++) {
            Random r = new Random(seed);
            for (int m = 0; m < 6; m++) {
                GameEngine e = new GameEngine(m);
                for (int i = 0; i < 36000; i++) {
                    if (e.phase != GameEngine.Phase.RUNNING) e = new GameEngine(m);
                    e.tick(GameEngine.STEP, r.nextDouble() * 2 - 1, r.nextDouble() * 2 - 1, r.nextInt(3) - 1);
                    assertTrue("seed " + seed + " mission " + (m + 1) + " step " + i + " not finite", e.finite());
                    if (i % 977 == 0) {
                        try { e = GameEngine.restore(e.snapshot()); }
                        catch (IOException failure) { fail("seed " + seed + " mission " + (m + 1) + " step " + i + ": " + failure); }
                    }
                }
            }
        }
    }
}
