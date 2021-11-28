package concurrentcube;

// Concurrent cube.
// A class representing a Rubik's cube which one can rotate and look at but
// with additional support for concurrent usage.

import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;

public class Cube {
    // All cubes are 3 dimensional but with user defined numbers of layers.
    private final int size;

    // User given procedures that are called just before/after the respective
    // cube operation.
    private final BiConsumer<Integer, Integer> beforeRotation;
    private final BiConsumer<Integer, Integer> afterRotation;
    private final Runnable beforeShowing;
    private final Runnable afterShowing;

    // This 3D array represents how the actual cube looks.
    private final int[][][] faces;

    // Global variables for all of the threads to synchronise their movements:

    // Straight forward mutex for protecting sync variables.
    private final Semaphore mutex = new Semaphore(1, true);

    // Semaphores for different axis groups to wait on.
    private final Semaphore[] axisMutices = { new Semaphore(0, true),
        new Semaphore(0, true), new Semaphore(0, true) };

    // Mutual exclusion between layers.
    private final Semaphore[] layerMutices;

    // Counters of awaiting rotating threads.
    private final int[] waiting = { 0, 0, 0 };

    // Who has the right to rotate, which axis. Set to -1 if there's no willing
    // thread or the current axis number (0, 1, 2) or 4 as a dummy axis for
    // 'show()'.
    private int currentRotor = -1;

    // Last axis that have been used in rotation.
    private int lastAx = 0;

    // How many rotors rotating in the critical section right now.
    private int rotorsCount = 0;

    // Same but for showing.
    private int showersCount = 0;

    // Waiting show operations.
    private int waitingShows = 0;

    // Semaphore for 'show()' to wait on.
    private Semaphore showing = new Semaphore(0, true);

    // Number of waiting threads from different axis than ax.
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

    // Synchronisation for the rotate operations.
    private void rotateEntryProtocole(int ax, int layer) throws InterruptedException {
        mutex.acquire();

        if (currentRotor == -1) {
            currentRotor = ax;
        }

        if (currentRotor != ax || otherAxWaiting(ax) || waitingShows > 0) {
            ++waiting[ax];
            mutex.release();
            try {
                axisMutices[ax].acquire();
            } catch (InterruptedException e) {
                mutex.acquireUninterruptibly();
                --waiting[ax];
                mutex.release();
                throw e;
            }
            // We assume we inherit the mutex here having been woken up.
            --waiting[ax];
        }
        ++rotorsCount;

        if (waiting[ax] != 0) {
            // Waking up fellow thread from the same axis to enter the critical
            // section with us.
            axisMutices[ax].release();
        } else {
            mutex.release();
        }

        try {
            layerMutices[layer].acquire();
        } catch (InterruptedException e) {
            mutex.acquireUninterruptibly();
            --rotorsCount;
            // We might have been the last of our group.
            rotateLetOthersIn(ax);
            throw e;
        }
    }

    // This procedure makes the current wake up those that are waiting if the
    // time is appropriate.
    private void rotateLetOthersIn(int ax) {
        // After a rotation we prioritise the entry of a waiting 'show' thread
        // (and vice versa) thus ending up safe from starvation problems.
        if (rotorsCount == 0 && waitingShows > 0) {
            lastAx = ax;
            // Note: there is no such axis, it is an indicator that we let
            // showing happen instead of another axis group.
            currentRotor = 4;
            showing.release();
        } else if (rotorsCount == 0) {
            for (int j = 1; j <= 3; ++j) {
                int i = (ax + j) % 3;
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
            mutex.release();
        }
    }

    // The exit protocole after a successful rotation.
    private void rotateExitProtocole(int ax, int layer) {
        layerMutices[layer].release();
        mutex.acquireUninterruptibly();
        --rotorsCount;
        rotateLetOthersIn(ax);
    }

    // The rotation function. It knows the axis number and the layer (with
    // respect to the axis) that it wants to rotate (this info is crucial in
    // synchronising the threads). It also stays vigilant of the original
    // parameters that were passed to rotate(int, int) (see below) as they are
    // needed as arguments for {before,after}Rotation procedures.
    private void rotate(int ax, int layer, int origSide, int origLayer)
        throws InterruptedException {
        rotateEntryProtocole(ax, layer);
        criticalRotate(ax, layer, origSide, origLayer);
        rotateExitProtocole(ax, layer);
    }

    // User visible rotate function. Will perform a clockwise rotation of
    // a selected layer facing a given side.
    public void rotate(int side, int layer) throws InterruptedException {
        // this way we get axis as 0 or 1 or 2
        int ax = side < 3 ? side : oppositeFace(side);
        int transpLayer = layer;
        if (side != ax) {
            // Reëvaluate the layer with respect to the axis.
            transpLayer = size - layer - 1;
        }

        // Call the function defined earlier.
        rotate(ax, transpLayer, side, layer);
    }
    
    // Synchronisation for the 'show()' operations. Similar to rotations' sync.
    private void showEntryProtocole() throws InterruptedException {
        mutex.acquire();
        if (otherAxWaiting(4) || currentRotor != -1) {
            ++waitingShows;
            mutex.release();
            try {
                showing.acquire();
            } catch (InterruptedException e) {
                mutex.acquireUninterruptibly();
                --waitingShows;
                mutex.release();
                throw e;
            }
            --waitingShows;
        } else {
            currentRotor = 4;
        }
        ++showersCount;
        if (waitingShows != 0) {
            showing.release();
        } else {
            mutex.release();
        }
    }

    // Ditto.
    private void showExitProtocole() throws InterruptedException {
        mutex.acquireUninterruptibly();
        --showersCount;
        if (showersCount == 0) {
            // Prioritise axes over showings here.
            for (int j = 1; j <= 3; ++j) {
                int i = (lastAx + j) % 3;
                if (waiting[i] > 0) {
                    currentRotor = i;
                    axisMutices[i].release();
                    // I don't release the mutex as it will be inherited
                    return;
                }
            }
            if (waitingShows > 0) {
                showing.release();
            } else {
                currentRotor = -1;
                mutex.release();
            }
        } else {
            mutex.release();
        }
    }

    // Return a string with a representation of the cube.
    public String show() throws InterruptedException {
        showEntryProtocole();
        String cubeString = criticalShow();
        showExitProtocole();
        return cubeString;
    }

    private int oppositeFace(int f) {
        switch (f) {
            
        case 0: return 5;
        case 1: return 3;
        case 2: return 4;
        case 3: return 1;
        case 4: return 2;
        case 5: return 0;

        default: throw new AssertionError("Invalid face!");
        }
    }

    // The true place where rotations take place, the critical section in
    // concurrent programming terminology -- here the threads actually can
    // access the cube.
    private void criticalRotate(int ax, int layer, int origSide, int origLayer) {
        // We're here, finally doin some rotatin'.
        beforeRotation.accept(origSide, origLayer);

        boolean clockwise = ax == origSide;
        // If this layer is a face layer then we also need to rotate the face.
        // Mind the reverted clockwiseness.
        if (layer == 0) {
            rotateFace(ax, clockwise);
        } else if (layer == size - 1) {
            rotateFace(oppositeFace(ax), !clockwise);
        }
        
        switch (ax) {

        case 0:
            rotate0(layer, clockwise);
            break;
        case 1:
            rotate1(layer, clockwise);
            break;
        case 2:
            rotate2(layer, clockwise);
            break;
        }

        afterRotation.accept(origSide, origLayer);
    }

    private void rotateFace(int which, boolean clockwise) {
        if (clockwise) {
            transpose(faces[which]);
            reflect(faces[which]);
        } else {
            reflect(faces[which]);
            transpose(faces[which]);
        }
    }

    public void transpose(int[][] matrix) {
        int n = matrix.length;
        for (int i = 0; i < n; i++) {
            for (int j = i; j < n; j++) {
                int tmp = matrix[j][i];
                matrix[j][i] = matrix[i][j];
                matrix[i][j] = tmp;
            }
        }
    }

    public void reflect(int[][] matrix) {
        int n = matrix.length;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n / 2; j++) {
                int tmp = matrix[i][j];
                matrix[i][j] = matrix[i][n - j - 1];
                matrix[i][n - j - 1] = tmp;
            }
        }
    }

    // A quadruple swap of cubes' squares.
    private void swap4(CubeSquare s0, CubeSquare s1, CubeSquare s2, CubeSquare s3,
                       boolean clockwise) {
        int[] tmp = {
            faces[s0.getFace()][s0.getI()][s0.getJ()],
            faces[s1.getFace()][s1.getI()][s1.getJ()],
            faces[s2.getFace()][s2.getI()][s2.getJ()],
            faces[s3.getFace()][s3.getI()][s3.getJ()]
        };

        if (clockwise) {
            faces[s0.getFace()][s0.getI()][s0.getJ()] = tmp[1];
            faces[s1.getFace()][s1.getI()][s1.getJ()] = tmp[2];
            faces[s2.getFace()][s2.getI()][s2.getJ()] = tmp[3];
            faces[s3.getFace()][s3.getI()][s3.getJ()] = tmp[0];
        } else {
            faces[s0.getFace()][s0.getI()][s0.getJ()] = tmp[3];
            faces[s1.getFace()][s1.getI()][s1.getJ()] = tmp[0];
            faces[s2.getFace()][s2.getI()][s2.getJ()] = tmp[1];
            faces[s3.getFace()][s3.getI()][s3.getJ()] = tmp[2];
        }
    }

    // All functions from the rotateN (N in {0,1,2}) family rotate a given layer
    // around the Nth axis. Clockwise or anticlockwise.
    private void rotate0(int layer, boolean clockwise) {

        for (int i = 0; i < size; ++i) {
            swap4(new CubeSquare(1, layer, i), new CubeSquare(2, layer, i),
                  new CubeSquare(3, layer, i), new CubeSquare(4, layer, i),
                  clockwise);
        }
    }

    private void rotate1(int layer, boolean clockwise) {
        for (int i = 0; i < size; ++i) {
            swap4(new CubeSquare(4, size - i - 1, size - layer - 1),
                  new CubeSquare(5, i, layer), new CubeSquare(2, i, layer),
                  new CubeSquare(0, i, layer),  clockwise);
        }
    }

    private void rotate2(int layer, boolean clockwise) {
        for (int i = 0; i < size; ++i) {
            swap4(new CubeSquare(0, size - layer - 1, i),
                  new CubeSquare(1, size - i - 1, size - layer - 1),
                  new CubeSquare(5, layer, size - i - 1), 
                  new CubeSquare(3, i, layer),
                  clockwise);
        }
    }

    // The place where actual showing of the cube takes place.
    public String criticalShow() throws InterruptedException {
        beforeShowing.run();
        StringBuilder sb = new StringBuilder();
        for (int f = 0; f < 6; ++f) {
            for (int i = 0; i < size; ++i) {
                for (int j = 0; j < size; ++j) {
                    sb.append(faces[f][i][j]);
                }
            }
        }
        afterShowing.run();
        return sb.toString();
    }

    // Cube's constructor.
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

        faces = new int[6][][];
        for (int f = 0; f < 6; ++f) {
            faces[f] = new int[size][size];
            for (int i = 0; i < size; ++i) {
                for (int j = 0; j < size; j++) {
                    faces[f][i][j] = f;
                }
            }
        }
    }
}
