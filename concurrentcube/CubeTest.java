package concurrentcube;

import org.junit.Test;

import concurrentcube.Cube;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

// This is a jUnit test class for the implementation of a concurrent Rubik's
// cube. It tests its various qualites such as rotation correctness, their
// atomicity or lack thereof, synchronisation, concurrency and liveness.
public class CubeTest {
    // A utility function for assering whether a cube is physically correct
    // ie. the number of squares with each of the six `colours' is equal.
    private static void assertCorrectCube(Cube cube) {

        try {
            String cubeString = cube.show();
            int expectedAmount = cubeString.length() / 6;
            int occurences[] = { 0, 0, 0, 0, 0, 0 };
            for (int i = 0; i < cubeString.length(); ++i) {
                ++occurences[Character.getNumericValue(cubeString.charAt(i))];
            }

            for (int i = 0; i < 6; ++i) {
                if (occurences[i] != expectedAmount) {
                    throw new AssertionError
                        ("there are not enough occurences of colour '"
                         + i + "'; " + occurences[i] + ", expected " + expectedAmount);
                }
            }

        } catch (InterruptedException e) {
            throw new AssertionError("Unexpected interruption!");
        }
    }

    // A helper function for an integerous square root.
    private static int sqrt(int square) {
        return (int) Math.sqrt(square);
    }

    // Check if the cube is solved.
    private static void assertSolvedCube(Cube cube) {
        try {
            String cubeString = cube.show();
            Cube solvedCube = new Cube(sqrt(cubeString.length() / 6), (x, y) -> {
            }, (x, y) -> {
            }, () -> {
            }, () -> {
            });
            String solvedString = solvedCube.show();
            if (!cubeString.equals(solvedString)) {
                throw new AssertionError("Expected a solved cube but did not get one!");
            }
        } catch (InterruptedException e) {
            throw new AssertionError("Unexpected interruption!");
        }
    }

    // This test checks the correctness of rotations. It is based on the group
    // theoretic mean of analysis of the Rubik's group which describes the mechanics
    // of the Rubik's cube. The largest order of an element in this group is 1260,
    // which means that there exist combinations of rotations that repeated result
    // in the cube coming back to its initial state.
    // https://en.wikipedia.org/wiki/Rubik%27s_Cube_group#Group_structure
    // This behaviour is tested here.
    @Test
    public void order1260() {
        Cube cube = new Cube(10, (x, y) -> {
        }, (x, y) -> {
        }, () -> {
        }, () -> {
        });

        // 1260
        try {
            for (int i = 0; i < 1260; ++i) {
                cube.rotate(3, 0);
                cube.rotate(0, 0);
                cube.rotate(0, 0);
                cube.rotate(5, 0);
                cube.rotate(5, 0);
                cube.rotate(5, 0);
                cube.rotate(4, 0);
                cube.rotate(5, 0);
                cube.rotate(5, 0);
                cube.rotate(5, 0);
            }
        } catch (InterruptedException e) {
            throw new AssertionError("Unexpected interruption!");
        }

        assertCorrectCube(cube);
        assertSolvedCube(cube);
    }

    // A helper function for returning the axis (0 or 1 or 2) of a face.
    private static int ax(int f) {
        switch (f) {

        case 0: case 5: return 0;

        case 1: case 3: return 1;

        case 2: case 4: return 2;

        default:
            throw new AssertionError("sraka");
        }
    }

    // Generate a list of threads (without starting them) that perform random
    // operations on a cube. Both rotations and shows are inlcuded in the list
    // with a given probavility;
    private List<Thread> aleatoryRotorsShowers(int nrThreads, double showProbability,
                                               Cube cube, int size, int nrTasks) {
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < nrThreads; ++i) {
            threads.add(new Thread(() -> {
                try {
                    for (int j = 0; j < nrTasks; ++j) {
                        if (ThreadLocalRandom.current().nextDouble() < showProbability) {
                            cube.show();
                        } else {
                            cube.rotate(ThreadLocalRandom.current().nextInt(0, 2137) % 6,
                                        ThreadLocalRandom.current().nextInt(0, 2137) % size);
                        }
                    }
                } catch (InterruptedException e) {
                }
            }));
        }
        return threads;
    }

    // This test aims to test whether the synchronisation of cube operations
    // is correct ie. only non-colliding operations are inside of the critical
    // section at the same time.
    @Test
    public void loggingTest() {
        int size = 10;
        int NR_THREADS = 1000;
        int maxDelay = 100;
        // We'll have a list that will serve as a log of all of the operations
        // commited to the cube.
        List<String> log = Collections.synchronizedList(new ArrayList<>());

        // Each operation will log itself to the list via adding a description
        // of its current state which is a two character string.
        // First character is either 'i' or 'o' and it indicates whether the
        // operation is entering the cube ('i' like 'in') or on the contrary:
        // exiting it ('o' like 'out'). The second character is either the axis
        // number or 'S' if the operation in question is a 'show()'.
        Cube cube = new Cube(size, (x, y) -> {
            log.add("i" + ax(x));
        }, (x, y) -> {
            log.add("o" + ax(x));
        }, () -> {
            log.add("iS");
        }, () -> {
            log.add("oS");
        });

        List<Thread> threads = aleatoryRotorsShowers(NR_THREADS, 0.3, cube, size, 5);
        threads.forEach(Thread::start);

        threads.forEach(t -> {
            try {
                t.join(maxDelay);
                if (t.isAlive()) {
                    throw new AssertionError("Threads haven't finished in time!");
                }
            } catch (InterruptedException e) {
            }
        });

        char current = log.get(0).charAt(1);
        int balance = 0;

        // The log describes a correctly synchronised cube iff for each "iX"
        // there is a corresponding "oX" and when isnide a block of "iX .." only
        // threads from the group 'X' enter the cube eg. ["i0", "i1" .. ] is
        // deemed incorrect as a rotation of axis 1 happened together with a
        // different axis. ["i1", "o1", "i0"] would be correct.
        for (String state : log) {
            if (state.charAt(1) != current) {
                if (balance != 0)
                    throw new AssertionError
                        ("found '" + state.charAt(1) + "' inside a block of '"
                         + current + "'");

                current = state.charAt(1);
            }

            if (state.charAt(0) == 'i')
                ++balance;
            else
                --balance;

            if (balance < 0)
                throw new AssertionError("negative i-o balance!");
        }

        // it looks cool so you might want to see that
        // System.out.println(log);
        
        if (balance != 0)
            throw new AssertionError("non zero final i-o balance!");

        assertCorrectCube(cube);
    }

    // Check if many aleatory threads of rotations and shows disrupt the cube's
    // structure. Sends 10000 threads each willing to do 10 cube operations on
    // a 100*100*100 Rubik's cube. It takes quite some time.
    @Test
    public void aleatoryThreads() {
        int size = 100;
        int NR_THREADS = 10000;
        // This is a long test so the delay is selected appropriately.
        int maxDelay = 5000;
        Cube cube = new Cube(size, (x, y) -> {
        }, (x, y) -> {
        }, () -> {
        }, () -> {
        });

        List<Thread> threads = aleatoryRotorsShowers(NR_THREADS, 0.3, cube, size, 10);

        threads.forEach(Thread::start);
        threads.forEach(t -> {
            try {
                t.join(maxDelay);
                if (t.isAlive()) {
                    throw new AssertionError("Threads haven't finished in time!");
                }
            } catch (InterruptedException e) {
            }
        });

        assertCorrectCube(cube);
    }

    // This test intends to check whether non-coliding rotations of the cube
    // will enter the critical section at the same time.
    @Test
    public void concurrencyTest() {
        int size = 100;
        int maxDelay = 100;
        CyclicBarrier beforeBarrier = new CyclicBarrier(size);
        CyclicBarrier afterBarrier = new CyclicBarrier(size);
        // Both before and after rotation shall wait for their fellow threads so
        // to check if they enter the critical section (and exit it) together.
        Cube cube = new Cube(size, (x, y) -> {
            try {
                beforeBarrier.await();
            } catch (InterruptedException | BrokenBarrierException e) {
            }
        }, (x, y) -> {
            try {
                afterBarrier.await();
            } catch (InterruptedException | BrokenBarrierException e) {
            }
        }, () -> {
        }, () -> {
        });

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < size; ++i) {
            final int a = i;
            threads.add(new Thread(() -> {
                try {
                    cube.rotate(0, a);
                } catch (InterruptedException e) {
                }
            }));
        }
        threads.forEach(Thread::start);
        threads.forEach(t -> {
            try {
                t.join(maxDelay);
                if (t.isAlive()) {
                    throw new AssertionError("Threads haven't finished in time!");
                }
            } catch (InterruptedException e) {
            }
        });

        assertCorrectCube(cube);
    }

    // This test intends to check the cube behaviour in a scenario where some
    // threads get randomly interrupted.
    // Although I am fairly certain that there are some interleavings for which
    // there cannot possibly be a good solution.
    @Test
    public void interruptionsTest() {
        int size = 10;
        int NR_THREADS = 20;
        int maxDelay = 1500;

        Cube cube = new Cube(size, (x, y) -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException ingored) {
            }
        }, (x, y) -> {
        }, () -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException ingored) {
            }
        }, () -> {
        });

        List<Thread> threads = aleatoryRotorsShowers(NR_THREADS, 0, cube, size, 1);
        threads.forEach(Thread::start);

        Random r = new Random();
        for (int i = 0; i < threads.size(); i += r.nextInt(10) + 1) {
            threads.get(i).interrupt();
        }
        threads.forEach(t -> {
            try {
                t.join(maxDelay);
                if (t.isAlive()) {
                    throw new AssertionError("Threads haven't finished in time!");
                }
            } catch (InterruptedException e) {
            }
        });

        assertCorrectCube(cube);
    }

    // There are no builtin pairs in this damn language.
    private class Rotation {
        private final int side;
        private final int layer;

        public Rotation(int side, int layer) {
            this.side = side;
            this.layer = layer;
        }

        public int getSide() {
            return side;
        }

        public int getLayer() {
            return layer;
        }
    }

    @Test
    public void concurrentEquivalentSequential() {
        int size = 10;
        int NR_THREADS = 1000;
        int maxDelay = 100;
        // We'll have a list that will serve as a log of all of the operations
        // commited to the cube.
        List<Rotation> rotations = Collections.synchronizedList(new ArrayList<>());
        Cube cube = new Cube(size, (x, y) -> {
        }, (x, y) -> {
            rotations.add(new Rotation(x, y));
        }, () -> {
        }, () -> {
        });

        List<Thread> threads = aleatoryRotorsShowers(NR_THREADS, 0, cube, size, 5);
        threads.forEach(Thread::start);

        threads.forEach(t -> {
            try {
                t.join(maxDelay);
                if (t.isAlive()) {
                    throw new AssertionError("Threads haven't finished in time!");
                }
            } catch (InterruptedException e) {
            }
        });

        Cube freshCube = new Cube(size, (x, y) -> {
        }, (x, y) -> {
        }, () -> {
        }, () -> {
        });

        try {
            rotations.forEach(r -> {
                try {
                    freshCube.rotate(r.getSide(), r.getLayer());
                } catch (InterruptedException e) {
                    throw new AssertionError("Unexpected interruption!");
                }
            });

            if (!freshCube.show().equals(cube.show())) {
                throw new AssertionError(
                        "The concurrently changed cube and sequentialy" +
                        " changed one should be identical!");
            }
        } catch (InterruptedException e) {
            throw new AssertionError("Unexpected interruption!");
        }
    }
}
