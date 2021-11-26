
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

public class Chuj {
        
    public static void order1260() {
        var counter = new Object() { int value = 0; };
        Cube cube = new Cube(10,
                             (x, y) -> {
                                 ++counter.value;
                                 System.out.println("\tbefore rotating"); },
                             (x, y) -> {
                                 ++counter.value;
                                 System.out.println("\tafter rotating"); },
                             () -> {
                                 ++counter.value;
                                 System.out.println("\tbefore showing"); },
                             () -> {
                                 ++counter.value;
                                 System.out.println("\tafter showing"); }
                             );

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
            System.out.println(cube.show());
        } catch (Exception e) {
            
        }        
    }

    private static int ax(int f) {
        switch (f) {
            
        case 0: case 5: return 0;
            
        case 1: case 3: return 1;
            
        case 2: case 4: return 2;
            
        default: throw new AssertionError("sraka");
        }
    }

    public static void loggingTest() {
        int size = 10;
        int NR_THREADS = 100;
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        
        Cube cube = new Cube(size,
                             (x, y) -> { log.add("i" + ax(x)); },
                             (x, y) -> { log.add("o" + ax(x)); },
                             () -> { log.add("iS"); },
                             () -> { log.add("oS");}
                             );

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < NR_THREADS; ++i) {
            threads.add(new Thread(() -> {try {
                            if (ThreadLocalRandom.current().nextDouble() < 0.3) {
                                cube.show();
                            } else {
                                cube.rotate(ThreadLocalRandom.current().nextInt(0,2137) % 6,
                                            ThreadLocalRandom.current().nextInt(0,2137) % size);
                            }
                        } catch (InterruptedException e) {
                            // TODO
                            e.printStackTrace();
                        } }));
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
                        
        for (String state : log) {
            if (state.charAt(1) != current) {
                if (balance != 0)
                    throw new AssertionError("found '" + state.charAt(1) + "' inside a block of '" + current + "'");

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

    public static void assertCorrectCube(Cube cube) {

        try {
            String cubeString = cube.show();
            int expectedAmount = cubeString.length() / 6;
            int occurences[] = {0, 0, 0, 0, 0, 0};
            for (int i = 0; i < cubeString.length(); ++i) {
                System.out.println(cubeString.charAt(i) + " = " + Character.getNumericValue(cubeString.charAt(i)));
                ++occurences[Character.getNumericValue(cubeString.charAt(i))];
            }

            for (int i = 0; i < 6; ++i) {
                if (occurences[i] != expectedAmount) {
                    throw new AssertionError("there are not enough occurences of colour '"
                                             + i + "'; " + occurences[i] + ", expected " + expectedAmount);
                }
            }
            
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}        
    }
    
    public static void manyThreads() {
        int size = 10;
        Cube cube = new Cube(size,
                             (x, y) -> { },
                             (x, y) -> { },
                             () -> { },
                             () -> { }
                             );
        
        ArrayList<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 1000; ++i) {
            final int a = i;
            System.out.println("a = " + a);
            threads.add(new Thread(() -> {try {
                            cube.rotate(0, a % size);                        
                        } catch (InterruptedException e) {
                            
                        } }));
            
            threads.add(new Thread(() -> {try {
                            cube.rotate(a % 5 + 1, 0);                        
                        } catch (InterruptedException e) {
                            
                        } }));
        }
        threads.forEach(Thread::start);
        threads.forEach(arg0 -> {
			try {
				arg0.join();
			} catch (InterruptedException e) {
				System.err.println("INTERRUPTED!!!!!");
			}
		});
        System.out.println("finito");
    }

    
    
    public static void main(String[] args) {
                    
        // order1260();
        // manyThreads();
        loggingTest();

        // ExecutorService executor = Executors.newFixedThreadPool(10);
        // Future<Object> guwno = executor.submit(() -> cube.rotate(4, 0));
        // guwno.get();
    }
}