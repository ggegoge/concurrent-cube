
// rewrite idea:
// - use futures with executors. No single threads for all axis and layers
//   but rather conceive another one each time (how to make sure one for the
//   same layer?)
// - make show() a monitor? show is always in the main thread right
// - atomics everywhere so that after interrupt i can amanage my variables
// - think about the cubing itself
// - simplify the mutices!!!

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;

public class Cube {
    private final int size;
    private final BiConsumer<Integer, Integer> beforeRotation;
    private final BiConsumer<Integer, Integer> afterRotation;
    private final Runnable beforeShowing;
    private final Runnable afterShowing;

    private final String[] faces = new String[6];

    // global variables for all of the threads to synchronise their movements.

    // a straight forward mutex for protecting synch variables
    private final Semaphore mutex = new Semaphore(1, true);
    // semaphore for all of the axis groups to wait on
    private final Semaphore[] axisMutices = { new Semaphore(0, true),
        new Semaphore(0, true), new Semaphore(0, true) };
    private final Semaphore[] layerMutices;
    // counters of awaiting threads
    private final int[] waiting = { 0, 0, 0 };
    // who has the right to rotate, which axis
    private int currentRotor = -1;
    // last axis that have been used in rotation
    private int lastAx = -1;
    // how many rotors rotating right now
    private int rotorsCount = 0;
    // waiting show operations
    private int waitingShows = 0;
    private Semaphore showing = new Semaphore(1, true);

    private boolean otherAxWaiting(int ax) {
        if (ax == 0) {
            return waiting[1] > 0 && waiting[2] > 0;
        } else if (ax == 1) {
            return waiting[0] > 0 && waiting[2] > 0;
        } else if (ax == 2) {
            return waiting[0] > 0 && waiting[1] > 0;
        } else {
            return waiting[0] > 0 && waiting[1] > 0 && waiting[2] > 0;
        }
    }

    private void rotate(int ax, int layer, int origSide, int origLayer) throws InterruptedException {
        mutex.acquire();

        if (currentRotor == -1) {
            currentRotor = ax;
        }

        if (currentRotor != ax || otherAxWaiting(ax) || waitingShows > 0) {
            ++waiting[ax];
            mutex.release();
            axisMutices[ax].acquire();
            // we inherit the mutex here
            --waiting[ax];
        }
        ++rotorsCount;

        if (waiting[ax] != 0) {
            // waking up fellow thread from the same axis
            axisMutices[ax].release();
        } else {
            mutex.release();
        }

        layerMutices[layer].acquire();
        criticalRotate(ax, layer, origSide, origLayer);
        layerMutices[layer].release();

        mutex.acquire();
        --rotorsCount;
        if (rotorsCount == 0 && waitingShows > 0) {
            lastAx = ax;
            // note: there is no such axis, it is an indicator that we let
            // showing happed instead of another axis group
            currentRotor = 4;
            showing.release();
        } else if (rotorsCount == 0) {
            for (int i = (ax + 1) % 3; i != ax; i = (i + 1) % 3) {
                if (waiting[i] > 0) {
                    currentRotor = i;
                    axisMutices[i].release();
                    // I don't release the mutex as it will be inherited
                    return;
                }
            }
            currentRotor = -1;
            mutex.release();
        } else {
            // we're not the last thread from our group so we just fuck off
            mutex.release();
        }
    }

    private void rotate(int side, int layer) throws InterruptedException {
        // TODO call to the axis knowing rotate from above
        // why this? this way we can test for an effect known from astragals ie
        // all opposing sides summing up to the same number -- 7. Thus I shall index
        // axes 1-6, 2-5, 3-4 each with the smaller number from the pair of faces.
        int ax = side + 1;
        int transpLayer = layer;
        if (ax > 3) {
            // reflection
            transpLayer = size - layer + 1;
            ax = 7 - ax;
        }

        rotate(ax, transpLayer, side, layer);
    }

    private String show() throws InterruptedException {
        mutex.acquire();
        if (otherAxWaiting(4) || currentRotor != -1) {
            ++waitingShows;
            mutex.release();
            showing.acquire();
            --waitingShows;
            // ?
        } else {
            currentRotor = 4;
            mutex.release();
        }

        String res = criticalShow();

        mutex.acquire();
        for (int i = (lastAx + 1) % 3; i != lastAx; i = (i + 1) % 3) {
            if (waiting[i] > 0) {
                currentRotor = i;
                axisMutices[i].release();
                // I don't release the mutex as it will be inherited
                return res;
            }
        }
        if (waitingShows > 0) {
            showing.release();
            return res;
        } else {
            currentRotor = -1;
            mutex.release();
            return res;
        }
    }

    private void criticalRotate(int ax, int layer, int origSide, int origLayer) {
        beforeRotation.accept(origSide, origLayer);

        // TODO actual rotation bs

        afterRotation.accept(origSide, origLayer);
    }

    // TODO - 6 lists of length n^2
    public String criticalShow() throws InterruptedException {
        beforeShowing.run();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; ++i) {
            sb.append(faces[i]);
        }
        afterShowing.run();
        return sb.toString();
    }

    public Cube(int size, BiConsumer<Integer, Integer> beforeRotation,
                BiConsumer<Integer, Integer> afterRotation,
                Runnable beforeShowing, Runnable afterShowing) {

        this.size = size;
        this.beforeRotation = beforeRotation;
        this.afterRotation = afterRotation;
        this.beforeShowing = beforeShowing;
        this.afterShowing = afterShowing;
        this.layerMutices = new Semaphore[size];

        for (int i = 0; i < size; ++i) {
            layerMutices[i] = new Semaphore(1, true);
        }
    }
}
