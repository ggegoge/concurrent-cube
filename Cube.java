
// rewrite idea:
// - use futures with executors. No single threads for all axis and layers
//   but rather conceive another one each time (how to make sure one for the
//   same layer?)
// - make show() a monitor? show is always in the main thread right
// - atomics everywhere so that after interrupt i can amanage my variables
// - think about the cubing itself
// - simplify the mutices!!!

import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;

public class Cube {
    private final int size;
    private final BiConsumer<Integer, Integer> beforeRotation;
    private final BiConsumer<Integer, Integer> afterRotation;
    private final Runnable beforeShowing;
    private final Runnable afterShowing;

    private final int[][][] faces;

    // Global variables for all of the threads to synchronise their movements.

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
    private int lastAx = 0;
    // how many rotors rotating right now
    private int rotorsCount = 0;
    // waiting show operations
    private int waitingShows = 0;
    private Semaphore showing = new Semaphore(0, true);

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

        try {
            layerMutices[layer].acquire();
        } catch (InterruptedException e) {
            mutex.acquireUninterruptibly();
            --rotorsCount;
            // we might have been the last of our group
            rotateLetOthersIn(ax, layer);
            throw e;
        }
    }


    private void rotateLetOthersIn(int ax, int layer) {
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
    
    private void rotateExitProtocole(int ax, int layer) throws InterruptedException {
        layerMutices[layer].release();
        mutex.acquireUninterruptibly();
        --rotorsCount;
        rotateLetOthersIn(ax, layer);
    }
    
    private void rotate(int ax, int layer, int origSide, int origLayer)
        throws InterruptedException {
        rotateEntryProtocole(ax, layer);
        criticalRotate(ax, layer, origSide, origLayer);
        rotateExitProtocole(ax, layer);
    }

    public void rotate(int side, int layer) throws InterruptedException {
        // this way we get axis as 0 or 1 or 2        
        int ax = side < 3 ? side : oppositeFace(side);
        int transpLayer = layer;
        if (side != ax) {
            // reflection
            transpLayer = size - layer - 1;
        }

        rotate(ax, transpLayer, side, layer);
    }

    private void showEntryProtocole() throws InterruptedException {
        System.out.println(Thread.currentThread().getName() + " show entry");
        mutex.acquire();
        System.out.println(Thread.currentThread().getName() + " show: mutex acquired");
        if (otherAxWaiting(4) || currentRotor != -1) {
            System.out.println(Thread.currentThread().getName() + " show: other waiting, wypierdalam!");
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
            System.out.println(Thread.currentThread().getName() + " show wake up");
            --waitingShows;
            // ?
        } else {
            System.out.println("show: we can show!");
            currentRotor = 4;            
        }
        mutex.release();
    }

    private void showExitProtocole() throws InterruptedException {
        System.out.println(Thread.currentThread().getName() + " show exiting showing");
        mutex.acquireUninterruptibly();
        System.out.println(Thread.currentThread().getName() + "mutex acquired, lastAx = " + lastAx);
        
        for (int i = (lastAx + 1) % 3; i != lastAx; i = (i + 1) % 3) {
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

    }
    
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
        default: throw new AssertionError("sraka");
        }
    }
    
    private void criticalRotate(int ax, int layer, int origSide, int origLayer) {
        System.out.println("critical rotate ax = " + ax + ", layer = " + layer);
        // We're here, finally doin some rotating.
        beforeRotation.accept(origSide, origLayer);

        boolean clockwise = ax == origSide;
        // If this layer is a face layer then we also need to rotate the face.
        if (layer == 0) {
            // top face --> 0 || 1 || 2 == ax
            // bottom face --> origSide == 5 || 3 || 4
            // clockwise negated due to sraka
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

    private void swapFaces(CubeSquare a, CubeSquare b, CubeSquare c, CubeSquare d, boolean clockwise) {
        for (int i = clockwise ? 1 : 3; i > 0; i--) {
            int tmp = faces[a.getFace()][a.getI()][a.getJ()];

        faces[a.getFace()][a.getI()][a.getJ()] = faces[b.getFace()][b.getI()][b.getJ()];
        faces[b.getFace()][b.getI()][b.getJ()] = faces[c.getFace()][c.getI()][c.getJ()];
        faces[c.getFace()][c.getI()][c.getJ()] = faces[d.getFace()][d.getI()][d.getJ()];
        faces[d.getFace()][d.getI()][d.getJ()] = tmp;
    }
}

    
    private void swap4(CubeSquare s0, CubeSquare s1,CubeSquare s2,CubeSquare s3,
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
    
    private void rotate0(int layer, boolean clockwise) {
        
        for (int i = 0; i < size; ++i) {
            swap4(new CubeSquare(1, layer, i), new CubeSquare(2, layer, i),
                  new CubeSquare(3, layer, i), new CubeSquare(4, layer, i), clockwise);
        }
    }

    

    private void rotate1(int layer, boolean clockwise) {
        for (int i = 0; i < size; ++i) {
            swap4(new CubeSquare(4, size - i - 1, size - layer - 1),
                  new CubeSquare(5, i, layer),
                  new CubeSquare(2, i, layer),
                  new CubeSquare(0, i, layer), 
                  clockwise);
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
        
    public String criticalShow() throws InterruptedException {
        System.out.println(Thread.currentThread().getName() + " critical show");
        beforeShowing.run();
        StringBuilder sb = new StringBuilder();
        for (int f = 0; f < 6; ++f) {
            for (int i = 0; i < size; ++i) {
                for (int j = 0; j < size; ++j) {
                    sb.append(faces[f][i][j]);                    
                }
                sb.append("\n");
            }
            sb.append("\n");
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
