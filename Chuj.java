
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

public class Chuj {

    // A utility function for assering whether a cube is physically correct
    // ie. the number of squares with each of the six `colours' is equal.
    public static void assertCorrectCube(Cube cube) {

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
    public static void assertSolvedCube(Cube cube) {
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
    public static void order1260() {
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
        System.out.println("OKAY");
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


    // This test aims to test whether the synchronisation of cube operations
    // is correct ie. only non-colliding operations are inside of the critical
    // section at the same time.
    public static void loggingTest() {
        int size = 10;
        int NR_THREADS = 1000;
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

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < NR_THREADS; ++i) {
            threads.add(new Thread(() -> {
                try {
                    if (ThreadLocalRandom.current().nextDouble() < 0.3) {
                        cube.show();
                    } else {
                        cube.rotate(ThreadLocalRandom.current().nextInt(0, 2137) % 6,
                                ThreadLocalRandom.current().nextInt(0, 2137) % size);
                    }
                } catch (InterruptedException e) {
                    // TODO
                    e.printStackTrace();
                }
            }));
        }
        threads.forEach(Thread::start);

        threads.forEach(arg0 -> {
            try {
                arg0.join();
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
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

        if (balance != 0)
            throw new AssertionError("non zero final i-o balance!");
        else
            System.out.println("OKAY");

        assertCorrectCube(cube);
    }

    // Check if many aleatory threads of rotations and shows disrupt the cube's
    // structure.
    public static void aleatoryThreads() {
        int size = 100;
        int NR_THREADS = 10000;
        Cube cube = new Cube(size, (x, y) -> {
        }, (x, y) -> {
        }, () -> {
        }, () -> {
        });

        ArrayList<Thread> threads = new ArrayList<>();
        for (int i = 0; i < NR_THREADS; ++i) {
            threads.add(new Thread(() -> {
                try {
                    if (ThreadLocalRandom.current().nextDouble() < 0.3) {
                        cube.show();
                    } else {
                        cube.rotate(ThreadLocalRandom.current().nextInt(0, 2137) % 6,
                                ThreadLocalRandom.current().nextInt(0, 2137) % size);
                    }
                } catch (InterruptedException e) {
                    // TODO
                    e.printStackTrace();
                }
            }));
        }
        threads.forEach(Thread::start);
        threads.forEach(arg0 -> {
            try {
                arg0.join();
            } catch (InterruptedException e) {
                System.err.println("INTERRUPTED!!!!!");
            }
        });

        assertCorrectCube(cube);
        System.out.println("OKAY");
    }

    // This test intends to check whether non-coliding rotations of the cube
    // will enter the critical section at the same time.
    public static void concurrencyTest() {
        int size = 100;

        
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
        threads.forEach(arg0 -> {
            try {
                arg0.join();
            } catch (InterruptedException e) {
            }
        });

        assertCorrectCube(cube);
        System.out.println("OKAY");
    }

    // This test intends to check the cube behaviour in a scenario where some
    // threads get randomly interrupted.
    // Although I am fairly certain that there are some interleavings for which
    // there cannot possibly be a good solution.
    public static void interruptionsTest() {
        int size = 10;
        int NR_THREADS = 100;

        Cube cube = new Cube(size, (x, y) -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ingored) {
            }
        }, (x, y) -> {
        }, () -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ingored) {
            }
        }, () -> {
        });

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < NR_THREADS; ++i) {
            threads.add(new Thread(() -> {
                try {
                    if (ThreadLocalRandom.current().nextDouble() < 0.2) {
                        cube.show();
                    } else {
                        cube.rotate(ThreadLocalRandom.current().nextInt(0, 2137) % 6,
                                    ThreadLocalRandom.current().nextInt(0, 2137) % size);
                    }
                } catch (InterruptedException interrupted) {
                }
            }));
        }
        threads.forEach(Thread::start);

        Random r = new Random();
        for (int i = 0; i < threads.size(); i += r.nextInt(10) + 1) {
            threads.get(i).interrupt();
        }
        threads.forEach(arg0 -> {
            try {
                arg0.join();
            } catch (InterruptedException e) {
            }
        });

        assertCorrectCube(cube);
        System.out.println("OKAY");
    }

    public static void main(String[] args) {
        order1260();
        aleatoryThreads();
        loggingTest();
        interruptionsTest();
        concurrencyTest();
    }
}
